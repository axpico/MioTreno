package it.picone.miotreno.work

import android.content.Context
import it.picone.miotreno.Deps
import it.picone.miotreno.data.Impostazioni
import it.picone.miotreno.domain.DettaglioTreno
import it.picone.miotreno.domain.MARGINE_RISCHIO_MIN
import it.picone.miotreno.domain.ProssimoCambio
import it.picone.miotreno.domain.ProssimoTreno
import it.picone.miotreno.domain.Tratta
import it.picone.miotreno.domain.TrenoSeguito
import it.picone.miotreno.domain.arrivato
import it.picone.miotreno.domain.arrivoPrevisto
import it.picone.miotreno.domain.avvisoCambioDovuto
import it.picone.miotreno.domain.coincidenzaPersa
import it.picone.miotreno.domain.comeOra
import it.picone.miotreno.domain.partenzaPrevista
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
    /** Il prossimo cambio, o la coincidenza persa; null per un diretto o all'ultima tratta. */
    val cambio: ProssimoCambio? = null,
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
    val treni = viaggio.tratteInTreno
    if (treni.isEmpty()) {
        val d = runCatching { Deps.repository.dettaglio(viaggio, codDestinazione) }.getOrNull()
        return StatoSeguito(viaggio, d, primaTratta = true, arrivato = (d as? DettaglioTreno.Ok)?.arrivato == true)
    }

    val (k, d) = trattaInCorso(treni, seguito.trattaCorrente)
    val tratta = treni[k]
    val vista = viaggio.vistaTratta(tratta, d as? DettaglioTreno.Ok)
    // seguito dal widget: le tratte arrivano dal feed al primo controllo
    var aggiornato = seguito.copy(trattaCorrente = k, tratte = seguito.tratte.ifEmpty { viaggio.tratte })
    var cambio: ProssimoCambio? = null

    if (k < treni.lastIndex) {
        val imp = Deps.impostazioni.flow.first()
        val arrivo = tratta.arrivoPrevisto(d, vista.ritardoMinuti)
        val succ = treni[k + 1]
        val dSucc = runCatching { Deps.repository.dettaglioTratta(succ) }.getOrNull() as? DettaglioTreno.Ok
        val partenzaSucc = succ.partenzaPrevista(dSucc)

        if (coincidenzaPersa(arrivo, partenzaSucc, dSucc?.cancellato == true)) {
            val esito = ripianifica(ctx, imp, aggiornato, tratta, d as? DettaglioTreno.Ok, arrivo, codDestinazione)
            aggiornato = esito.first
            cambio = esito.second
        } else {
            val binario = dSucc?.fermate?.getOrNull(dSucc.indicePartenza)?.binario ?: succ.binario
            cambio = ProssimoCambio(
                tratta.nomeArrivo,
                "${succ.categoria} ${succ.numeroTreno} parte ${partenzaSucc.comeOra()}" + (binario?.let { " · bin $it" } ?: ""),
            )
            if (seguito.avvisoCambioPer < k && avvisoCambioDovuto(arrivo, adesso, imp.avvisoCambioMinuti)) {
                aggiornato = aggiornato.copy(avvisoCambioPer = k)
                val minuti = ((arrivo - adesso) / 60_000L).coerceAtLeast(0)
                if (imp.notifiche) mostraAvvisoViaggio(ctx, "Cambio a ${tratta.nomeArrivo} tra $minuti min", "→ ${cambio.dettaglio}")
            }
        }
    }

    // nel frattempo l'utente può aver smesso di seguire: non va resuscitato
    val ancoraSeguito = Deps.impostazioni.seguito.first()?.numeroTreno == seguito.numeroTreno
    if (aggiornato != seguito && ancoraSeguito) Deps.impostazioni.segui(aggiornato)
    return StatoSeguito(
        vista, d, primaTratta = k == 0, nomeSalita = tratta.nomePartenza.takeIf { k > 0 }, cambio = cambio,
        arrivato = (d as? DettaglioTreno.Ok)?.arrivato == true,
    )
}

/** Dalla tratta salvata in avanti, salta quelle che l'andamento dice già concluse. */
private suspend fun trattaInCorso(treni: List<Tratta>, salvata: Int): Pair<Int, DettaglioTreno?> {
    suspend fun andamento(i: Int) = runCatching { Deps.repository.dettaglioTratta(treni[i]) }.getOrNull()
    var k = salvata.coerceIn(0, treni.lastIndex)
    var d = andamento(k)
    while ((d as? DettaglioTreno.Ok)?.arrivato == true && k < treni.lastIndex) d = andamento(++k)
    return k to d
}

/**
 * Coincidenza persa: il primo viaggio utile dalla stazione di cambio sostituisce il resto del
 * seguito, e lo si dice con una notifica. Senza alternative resta solo l'avviso.
 */
private suspend fun ripianifica(
    ctx: Context,
    imp: Impostazioni,
    seguito: TrenoSeguito,
    tratta: Tratta,
    d: DettaglioTreno.Ok?,
    arrivo: Long,
    codDestinazione: String,
): Pair<TrenoSeguito, ProssimoCambio> {
    val codCambio = d?.fermate?.getOrNull(d.indiceDestinazione)?.codice ?: tratta.codArrivo
    val alternativa = codCambio?.let { cod ->
        runCatching { Deps.repository.prossimiTreni(listOf(cod), codDestinazione, Date(arrivo)) }.getOrNull()
            ?.firstOrNull { !it.cancellato && it.orarioPartenzaMs + it.ritardoMinuti * 60_000L >= arrivo + MARGINE_RISCHIO_MIN * 60_000L }
    } ?: return seguito to ProssimoCambio(tratta.nomeArrivo, null, persa = true)

    val testo = "Ora ${alternativa.categoria} ${alternativa.numeroTreno} · parte ${alternativa.orarioPartenzaMs.comeOra()}" +
        (alternativa.binario?.let { " · bin $it" } ?: "") +
        (alternativa.orarioArrivoDestinazioneMs?.let { " · arrivo ${it.comeOra()}" } ?: "")
    if (imp.notifiche) mostraAvvisoViaggio(ctx, "Coincidenza persa a ${tratta.nomeArrivo}", testo)
    return seguito.ripianificato(tratta, alternativa) to ProssimoCambio(tratta.nomeArrivo, testo, persa = true)
}
