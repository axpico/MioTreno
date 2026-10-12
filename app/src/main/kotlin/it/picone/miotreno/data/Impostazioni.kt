package it.picone.miotreno.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import it.picone.miotreno.domain.ProssimoTreno
import it.picone.miotreno.domain.RisultatoStazione
import it.picone.miotreno.domain.Tratta
import it.picone.miotreno.domain.TrenoSeguito
import it.picone.miotreno.domain.aggiornaRecenti
import it.picone.miotreno.domain.comeSeguito
import it.picone.miotreno.domain.seguitoAttivo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore("impostazioni")

data class Impostazioni(
    val notifiche: Boolean = true,
    val anticipoMinuti: Int = 15,
    val avvisiSciopero: Boolean = true,
    /** Codice ViaggiaTreno scelto a mano al posto del GPS; null = stazione rilevata. */
    val stazioneManuale: String? = null,
    /** Stazione intermedia che l'utente vuole sempre vedere evidenziata; null = disattivato. */
    val stazionePassaggio: String? = null,
    /** Stazione di destinazione scelta dall'utente; null = non ancora configurata (primo avvio). */
    val stazioneDestinazione: String? = null,
    /** Scelta in onboarding (o cambiata dopo in Impostazioni): se false, niente banner/interstitial. */
    val adsAbilitate: Boolean = true,
    /** "Mostra anche soluzioni con cambi": accanto ai diretti, i viaggi con 1–2 cambi. */
    val conCambi: Boolean = true,
    /** Minuti prima dell'arrivo alla stazione di cambio per l'avviso; 0 = spento. */
    val avvisoCambioMinuti: Int = 5,
    /** Il riquadro "Prossimo treno" è nelle impostazioni rapide (lo dice il sistema al tile). */
    val tileAggiunto: Boolean = false,
)

class ImpostazioniStore(private val context: Context) {
    private val notifiche = booleanPreferencesKey("notifiche")
    private val anticipo = intPreferencesKey("anticipo_minuti")
    private val sciopero = booleanPreferencesKey("avvisi_sciopero")
    private val stazioneManuale = stringPreferencesKey("stazione_manuale")
    private val stazionePassaggio = stringPreferencesKey("stazione_passaggio")
    private val stazioneDestinazione = stringPreferencesKey("stazione_destinazione")
    private val adsAbilitate = booleanPreferencesKey("ads_abilitate")
    private val contatoreCambioStazione = intPreferencesKey("contatore_cambio_stazione")
    private val recentiKey = stringPreferencesKey("stazioni_recenti")
    private val conCambi = booleanPreferencesKey("con_cambi")
    private val avvisoCambio = intPreferencesKey("avviso_cambio_minuti")
    private val tileAggiunto = booleanPreferencesKey("tile_aggiunto")

    /** Ultime stazioni scelte nei selettori, per non aprire mai una ricerca vuota. Una per riga: "codice\tnome". */
    val recenti: Flow<List<RisultatoStazione>> = context.dataStore.data.map { leggiRecenti(it[recentiKey]) }

    suspend fun aggiungiRecente(stazione: RisultatoStazione) = context.dataStore.edit { p ->
        p[recentiKey] = aggiornaRecenti(leggiRecenti(p[recentiKey]), stazione).joinToString("\n") { "${it.codice}\t${it.nome}" }
    }.let {}

    private fun leggiRecenti(testo: String?): List<RisultatoStazione> = testo.orEmpty().lines().mapNotNull { riga ->
        riga.split('\t').takeIf { it.size == 2 }?.let { (codice, nome) -> RisultatoStazione(nome, codice) }
    }

    val flow: Flow<Impostazioni> = context.dataStore.data.map { p ->
        Impostazioni(
            notifiche = p[notifiche] ?: true,
            anticipoMinuti = p[anticipo] ?: 15,
            avvisiSciopero = p[sciopero] ?: true,
            stazioneManuale = p[stazioneManuale]?.takeIf { it.isNotBlank() },
            stazionePassaggio = p[stazionePassaggio]?.takeIf { it.isNotBlank() },
            stazioneDestinazione = p[stazioneDestinazione]?.takeIf { it.isNotBlank() },
            adsAbilitate = p[adsAbilitate] ?: true,
            conCambi = p[conCambi] ?: true,
            avvisoCambioMinuti = p[avvisoCambio] ?: 5,
            tileAggiunto = p[tileAggiunto] ?: false,
        )
    }

    suspend fun setNotifiche(v: Boolean) = context.dataStore.edit { it[notifiche] = v }.let {}
    suspend fun setAnticipo(v: Int) = context.dataStore.edit { it[anticipo] = v }.let {}
    suspend fun setAvvisiSciopero(v: Boolean) = context.dataStore.edit { it[sciopero] = v }.let {}
    suspend fun setStazioneManuale(codice: String?) = context.dataStore.edit {
        if (codice == null) it.remove(stazioneManuale) else it[stazioneManuale] = codice
    }.let {}
    suspend fun setStazionePassaggio(codice: String?) = context.dataStore.edit {
        if (codice == null) it.remove(stazionePassaggio) else it[stazionePassaggio] = codice
    }.let {}
    suspend fun setStazioneDestinazione(codice: String?) = context.dataStore.edit {
        if (codice == null) it.remove(stazioneDestinazione) else it[stazioneDestinazione] = codice
    }.let {}
    suspend fun setAdsAbilitate(v: Boolean) = context.dataStore.edit { it[adsAbilitate] = v }.let {}
    suspend fun setConCambi(v: Boolean) = context.dataStore.edit { it[conCambi] = v }.let {}
    suspend fun setAvvisoCambio(v: Int) = context.dataStore.edit { it[avvisoCambio] = v }.let {}
    suspend fun setTileAggiunto(v: Boolean) = context.dataStore.edit { it[tileAggiunto] = v }.let {}

    /**
     * Cadenza dell'interstitial: un contatore persistito, azzerato ogni 4 cambi di
     * stazione/destinazione. Ritorna true quando è il momento di mostrarlo.
     */
    suspend fun incrementaContatoreCambioStazione(): Boolean {
        var mostra = false
        context.dataStore.edit {
            val n = ((it[contatoreCambioStazione] ?: 0) + 1) % 4
            it[contatoreCambioStazione] = n
            mostra = n == 0
        }
        return mostra
    }

    // ── corsa seguita ─────────────────────────────────────────────────────────
    // Non è una preferenza ma stato transitorio, quindi flusso a parte: `Impostazioni`
    // resta quello che l'utente configura in Impostazioni.

    private val seguitoNumero = intPreferencesKey("seguito_numero")
    private val seguitoData = stringPreferencesKey("seguito_data")
    private val seguitoArrivo = longPreferencesKey("seguito_arrivo")
    private val seguitoCategoria = stringPreferencesKey("seguito_categoria")
    private val seguitoDestinazione = stringPreferencesKey("seguito_destinazione")
    private val seguitoOrigine = stringPreferencesKey("seguito_origine")
    private val seguitoDataCorsa = longPreferencesKey("seguito_data_corsa")
    private val seguitoPartenza = longPreferencesKey("seguito_partenza")
    private val seguitoRitardo = intPreferencesKey("seguito_ritardo")
    private val seguitoBinario = stringPreferencesKey("seguito_binario")
    private val seguitoBinarioConfermato = booleanPreferencesKey("seguito_binario_confermato")
    private val seguitoTratte = stringPreferencesKey("seguito_tratte")
    private val seguitoTrattaCorrente = intPreferencesKey("seguito_tratta_corrente")
    private val seguitoAvvisoCambio = intPreferencesKey("seguito_avviso_cambio")
    private val json = Json { ignoreUnknownKeys = true }

    /** Già filtrato: quello che arriva qui è attivo adesso, scaduto è come non selezionato. */
    val seguito: Flow<TrenoSeguito?> = context.dataStore.data.map { p ->
        val numero = p[seguitoNumero] ?: return@map null
        seguitoAttivo(
            TrenoSeguito(
                numeroTreno = numero,
                data = p[seguitoData].orEmpty(),
                arrivoDestinazioneMs = p[seguitoArrivo] ?: 0L,
                categoria = p[seguitoCategoria], destinazione = p[seguitoDestinazione],
                codOrigine = p[seguitoOrigine], dataPartenzaTrenoMs = p[seguitoDataCorsa],
                orarioPartenzaMs = p[seguitoPartenza], ritardoMinuti = p[seguitoRitardo] ?: 0,
                binario = p[seguitoBinario], binarioConfermato = p[seguitoBinarioConfermato] ?: false,
                tratte = p[seguitoTratte]?.let { runCatching { json.decodeFromString<List<Tratta>>(it) }.getOrNull() }.orEmpty(),
                trattaCorrente = p[seguitoTrattaCorrente] ?: 0,
                avvisoCambioPer = p[seguitoAvvisoCambio] ?: -1,
            ),
        )
    }

    suspend fun segui(treno: ProssimoTreno) = segui(treno.comeSeguito())

    suspend fun segui(s: TrenoSeguito) {
        context.dataStore.edit {
            it[seguitoNumero] = s.numeroTreno
            it[seguitoData] = s.data
            it[seguitoArrivo] = s.arrivoDestinazioneMs
            s.categoria?.let { v -> it[seguitoCategoria] = v }; s.destinazione?.let { v -> it[seguitoDestinazione] = v }
            s.codOrigine?.let { v -> it[seguitoOrigine] = v }; s.dataPartenzaTrenoMs?.let { v -> it[seguitoDataCorsa] = v }
            s.orarioPartenzaMs?.let { v -> it[seguitoPartenza] = v }; it[seguitoRitardo] = s.ritardoMinuti
            s.binario?.let { v -> it[seguitoBinario] = v }; it[seguitoBinarioConfermato] = s.binarioConfermato
            if (s.tratte.isEmpty()) it.remove(seguitoTratte) else it[seguitoTratte] = json.encodeToString(s.tratte)
            it[seguitoTrattaCorrente] = s.trattaCorrente
            it[seguitoAvvisoCambio] = s.avvisoCambioPer
        }
    }

    /**
     * Rinfresca lo snapshot del primo treno con i dati del feed. Le tratte, quella in corso e
     * l'avviso di cambio già dato restano quelli salvati: il feed non sa della tratta in corso
     * né di una coincidenza ripianificata, e le riscriverebbe con il viaggio originale.
     */
    suspend fun aggiornaSeguito(treno: ProssimoTreno) {
        val attuale = seguito.firstOrNull()?.takeIf { it.numeroTreno == treno.numeroTreno } ?: return
        val nuovo = treno.comeSeguito()
        segui(
            nuovo.copy(
                tratte = attuale.tratte.ifEmpty { nuovo.tratte },
                arrivoDestinazioneMs = if (attuale.tratte.isEmpty()) nuovo.arrivoDestinazioneMs else attuale.arrivoDestinazioneMs,
                trattaCorrente = attuale.trattaCorrente,
                avvisoCambioPer = attuale.avvisoCambioPer,
            ),
        )
    }

    suspend fun smettiDiSeguire() {
        context.dataStore.edit {
            it.remove(seguitoNumero)
            it.remove(seguitoData)
            it.remove(seguitoArrivo)
            it.remove(seguitoCategoria); it.remove(seguitoDestinazione); it.remove(seguitoOrigine)
            it.remove(seguitoDataCorsa); it.remove(seguitoPartenza); it.remove(seguitoRitardo)
            it.remove(seguitoBinario); it.remove(seguitoBinarioConfermato)
            it.remove(seguitoTratte); it.remove(seguitoTrattaCorrente); it.remove(seguitoAvvisoCambio)
        }
    }
}
