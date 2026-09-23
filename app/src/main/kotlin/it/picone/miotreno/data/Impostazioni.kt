package it.picone.miotreno.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import it.picone.miotreno.domain.ProssimoTreno
import it.picone.miotreno.domain.TrenoSeguito
import it.picone.miotreno.domain.comeSeguito
import it.picone.miotreno.domain.seguitoAttivo
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.firstOrNull

private val Context.dataStore by preferencesDataStore("impostazioni")

data class Impostazioni(
    val soloDiretti: Boolean = true,
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
    /** Scritto a fine onboarding, non ancora letto come gate: vedi OnboardingScreen.kt. */
    val onboardingCompletato: Boolean = false,
)

class ImpostazioniStore(private val context: Context) {
    private val soloDiretti = booleanPreferencesKey("solo_diretti")
    private val notifiche = booleanPreferencesKey("notifiche")
    private val anticipo = intPreferencesKey("anticipo_minuti")
    private val sciopero = booleanPreferencesKey("avvisi_sciopero")
    private val stazioneManuale = stringPreferencesKey("stazione_manuale")
    private val stazionePassaggio = stringPreferencesKey("stazione_passaggio")
    private val stazioneDestinazione = stringPreferencesKey("stazione_destinazione")
    private val adsAbilitate = booleanPreferencesKey("ads_abilitate")
    private val onboardingCompletato = booleanPreferencesKey("onboarding_completato")
    private val contatoreCambioStazione = intPreferencesKey("contatore_cambio_stazione")

    val flow: Flow<Impostazioni> = context.dataStore.data.map { p ->
        Impostazioni(
            soloDiretti = p[soloDiretti] ?: true,
            notifiche = p[notifiche] ?: true,
            anticipoMinuti = p[anticipo] ?: 15,
            avvisiSciopero = p[sciopero] ?: true,
            stazioneManuale = p[stazioneManuale]?.takeIf { it.isNotBlank() },
            stazionePassaggio = p[stazionePassaggio]?.takeIf { it.isNotBlank() },
            stazioneDestinazione = p[stazioneDestinazione]?.takeIf { it.isNotBlank() },
            adsAbilitate = p[adsAbilitate] ?: true,
            onboardingCompletato = p[onboardingCompletato] ?: false,
        )
    }

    suspend fun setSoloDiretti(v: Boolean) = context.dataStore.edit { it[soloDiretti] = v }.let {}
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
    suspend fun setOnboardingCompletato(v: Boolean) = context.dataStore.edit { it[onboardingCompletato] = v }.let {}

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

    /** Già filtrato: quello che arriva qui è attivo adesso, scaduto è come non selezionato. */
    val seguito: Flow<TrenoSeguito?> = context.dataStore.data.map { p ->
        val numero = p[seguitoNumero] ?: return@map null
        seguitoAttivo(
            TrenoSeguito(
                numeroTreno = numero,
                data = p[seguitoData].orEmpty(),
                arrivoBustoMs = p[seguitoArrivo] ?: 0L,
                categoria = p[seguitoCategoria], destinazione = p[seguitoDestinazione],
                codOrigine = p[seguitoOrigine], dataPartenzaTrenoMs = p[seguitoDataCorsa],
                orarioPartenzaMs = p[seguitoPartenza], ritardoMinuti = p[seguitoRitardo] ?: 0,
                binario = p[seguitoBinario], binarioConfermato = p[seguitoBinarioConfermato] ?: false,
            ),
        )
    }

    suspend fun segui(treno: ProssimoTreno) = segui(treno.comeSeguito())

    suspend fun segui(s: TrenoSeguito) {
        context.dataStore.edit {
            it[seguitoNumero] = s.numeroTreno
            it[seguitoData] = s.data
            it[seguitoArrivo] = s.arrivoBustoMs
            s.categoria?.let { v -> it[seguitoCategoria] = v }; s.destinazione?.let { v -> it[seguitoDestinazione] = v }
            s.codOrigine?.let { v -> it[seguitoOrigine] = v }; s.dataPartenzaTrenoMs?.let { v -> it[seguitoDataCorsa] = v }
            s.orarioPartenzaMs?.let { v -> it[seguitoPartenza] = v }; it[seguitoRitardo] = s.ritardoMinuti
            s.binario?.let { v -> it[seguitoBinario] = v }; it[seguitoBinarioConfermato] = s.binarioConfermato
        }
    }

    suspend fun aggiornaSeguito(treno: ProssimoTreno) {
        if (seguito.firstOrNull()?.numeroTreno == treno.numeroTreno) segui(treno)
    }

    suspend fun smettiDiSeguire() {
        context.dataStore.edit {
            it.remove(seguitoNumero)
            it.remove(seguitoData)
            it.remove(seguitoArrivo)
            it.remove(seguitoCategoria); it.remove(seguitoDestinazione); it.remove(seguitoOrigine)
            it.remove(seguitoDataCorsa); it.remove(seguitoPartenza); it.remove(seguitoRitardo)
            it.remove(seguitoBinario); it.remove(seguitoBinarioConfermato)
        }
    }
}
