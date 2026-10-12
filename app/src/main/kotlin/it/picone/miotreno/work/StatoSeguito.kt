package it.picone.miotreno.work

import android.content.Context
import it.picone.miotreno.Deps
import it.picone.miotreno.domain.DettaglioTreno
import it.picone.miotreno.domain.MARGINE_RISCHIO_MIN
import it.picone.miotreno.domain.ProssimoTreno
import it.picone.miotreno.domain.TrenoSeguito
import it.picone.miotreno.domain.arrivato
import it.picone.miotreno.domain.avvisoCambioDovuto
import it.picone.miotreno.domain.coincidenzaPersa
import it.picone.miotreno.domain.comeOra
import it.picone.miotreno.domain.orario
import it.picone.miotreno.domain.ripianificato
import it.picone.miotreno.domain.tratteInTreno
import it.picone.miotreno.domain.vistaTratta
import kotlinx.coroutines.flow.first
import java.util.Date

/** Esito di un controllo della corsa seguita. */
data class StatoSeguito(
    /** La tratta in corso come treno singolo (per un diretto, il treno stesso). */
    val treno: ProssimoTreno,
    val dettaglio: DettaglioTreno?,
    /** Prima tratta: solo qui ha senso la finestra di preavviso pre-partenza. */
    val primaTratta: Boolean,
    /** Dove si sale per la tratta in corso; null = la stazione di partenza. */
    val nomeSalita: String? = null,
    /** "Cambio a Verona · RE 2617 parte 10:17 · bin 4", o la coincidenza persa. */
    val rigaCambio: String? = null,
    /** Arrivato a destinazione (ultima tratta): il chiamante smette di seguire. */
    val arrivato: Boolean = false,
)

/**
 * Controlla la corsa seguita: arrivo, tratta in corso, coincidenza persa e avviso di cambio.
 *
 * Unico posto con questa logica, chiamato dall'app e dal tracking in background. Salva il
 * seguito aggiornato e pubblica gli avvisi (se le notifiche sono attive). Una coincidenza persa
 * si ripianifica da sola dalla stazione di cambio: è la scelta dell'utente, e lo si avvisa.
 */
suspend fun controllaSeguito(
    ctx: Context,
    seguito: TrenoSeguito,
    viaggio: ProssimoTreno,
    codDestinazione: String,
    adesso: Long = System.currentTimeMillis(),
): StatoSeguito {
    val repo = Deps.repository
    val treni = viaggio.tratteInTreno
    if (treni.isEmpty()) {
        val d = runCatching { repo.dettaglio(viaggio, codDestinazione) }.getOrNull()
        return StatoSeguito(viaggio, d, primaTratta = true, arrivato = (d as? DettaglioTreno.Ok)?.arrivato == true)
    }

    suspend fun andamento(i: Int) = runCatching { repo.dettaglioTratta(treni[i]) }.getOrNull()
    var k = seguito.trattaCorrente.coerceIn(0, treni.lastIndex)
    var d = andamento(k)
    while ((d as? DettaglioTreno.Ok)?.arrivato == true && k < treni.lastIndex) d = andamento(++k)
    val ok = d as? DettaglioTreno.Ok
    val tratta = treni[k]
    val vista = viaggio.vistaTratta(tratta, ok)
    // seguito dal widget: le tratte arrivano dal feed al primo controllo
    var aggiornato = seguito.copy(trattaCorrente = k, tratte = seguito.tratte.ifEmpty { viaggio.tratte })
    var rigaCambio: String? = null

    if (k < treni.lastIndex) {
        val imp = Deps.impostazioni.flow.first()
        val arrivoPrevisto = ok?.fermate?.getOrNull(ok.indiceDestinazione)?.orario(ok.ritardoMinuti)?.previstoMs
            ?: (tratta.arrivoMs + vista.ritardoMinuti * 60_000L)
        val succ = treni[k + 1]
        val dSucc = runCatching { repo.dettaglioTratta(succ) }.getOrNull() as? DettaglioTreno.Ok
        val salitaSucc = dSucc?.fermate?.getOrNull(dSucc.indicePartenza)
        val partenzaSucc = salitaSucc?.orario(dSucc.ritardoMinuti)?.previstoMs ?: (succ.partenzaMs + succ.ritardoMinuti * 60_000L)
        val binarioSucc = salitaSucc?.binario ?: succ.binario

        if (coincidenzaPersa(arrivoPrevisto, partenzaSucc, dSucc?.cancellato == true)) {
            val codCambio = ok?.fermate?.getOrNull(ok.indiceDestinazione)?.codice ?: tratta.codArrivo
            val alternativa = codCambio?.let { cod ->
                runCatching { repo.prossimiTreni(listOf(cod), codDestinazione, Date(arrivoPrevisto)) }.getOrNull()
                    ?.firstOrNull {
                        !it.cancellato &&
                            it.orarioPartenzaMs + it.ritardoMinuti * 60_000L >= arrivoPrevisto + MARGINE_RISCHIO_MIN * 60_000L
                    }
            }
            if (alternativa != null) {
                aggiornato = aggiornato.ripianificato(tratta, alternativa)
                val testo = "Ora ${alternativa.categoria} ${alternativa.numeroTreno} · parte ${alternativa.orarioPartenzaMs.comeOra()}" +
                    (alternativa.binario?.let { " · bin $it" } ?: "") +
                    (alternativa.orarioArrivoDestinazioneMs?.let { " · arrivo ${it.comeOra()}" } ?: "")
                rigaCambio = "Coincidenza persa · $testo"
                if (imp.notifiche) mostraAvvisoViaggio(ctx, "Coincidenza persa a ${tratta.nomeArrivo}", testo)
            } else {
                rigaCambio = "Coincidenza persa a ${tratta.nomeArrivo}"
            }
        } else {
            rigaCambio = "Cambio a ${tratta.nomeArrivo} · ${succ.categoria} ${succ.numeroTreno} parte ${partenzaSucc.comeOra()}" +
                (binarioSucc?.let { " · bin $it" } ?: "")
            if (seguito.avvisoCambioPer < k && avvisoCambioDovuto(arrivoPrevisto, adesso, imp.avvisoCambioMinuti)) {
                aggiornato = aggiornato.copy(avvisoCambioPer = k)
                val minuti = ((arrivoPrevisto - adesso) / 60_000L).coerceAtLeast(0)
                if (imp.notifiche) {
                    mostraAvvisoViaggio(
                        ctx, "Cambio a ${tratta.nomeArrivo} tra $minuti min",
                        "→ ${succ.categoria} ${succ.numeroTreno}" + (binarioSucc?.let { " · bin $it" } ?: "") +
                            " · parte ${partenzaSucc.comeOra()}",
                    )
                }
            }
        }
    }
    // nel frattempo l'utente può aver smesso di seguire: non va resuscitato
    val ancoraSeguito = Deps.impostazioni.seguito.first()?.numeroTreno == seguito.numeroTreno
    if (aggiornato != seguito && ancoraSeguito) Deps.impostazioni.segui(aggiornato)
    return StatoSeguito(
        vista, d, primaTratta = k == 0, nomeSalita = tratta.nomePartenza.takeIf { k > 0 }, rigaCambio = rigaCambio,
        arrivato = ok?.arrivato == true,
    )
}
