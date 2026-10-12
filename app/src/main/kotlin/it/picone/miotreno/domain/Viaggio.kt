package it.picone.miotreno.domain

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId

/**
 * Una tratta di un viaggio con cambi: un treno, o un tratto a piedi/metro fra due stazioni.
 *
 * I codici ViaggiaTreno delle stazioni di salita e discesa spesso non si conoscono (LeFrecce
 * dà solo i nomi): la fermata giusta nell'`andamentoTreno` si trova per orario programmato
 * ([indicePerOrario]), che è esatto al minuto e non dipende da come è scritto il nome.
 */
@Serializable
data class Tratta(
    /** Null = a piedi o metro: niente da tracciare. */
    val numeroTreno: Int?,
    val categoria: String,
    val nomePartenza: String,
    val nomeArrivo: String,
    val partenzaMs: Long,
    val arrivoMs: Long,
    /** Codice della stazione d'origine del treno, per `andamentoTreno`. */
    val codOrigine: String? = null,
    /** Mezzanotte del giorno della corsa, per `andamentoTreno`. */
    val dataPartenzaTrenoMs: Long? = null,
    val codPartenza: String? = null,
    val codArrivo: String? = null,
    val ritardoMinuti: Int = 0,
    val binario: String? = null,
) {
    val suTreno: Boolean get() = numeroTreno != null
}

/** Cambi di treno; le tratte a piedi non contano. 0 per un diretto. */
val ProssimoTreno.cambi: Int get() = (tratte.count { it.suTreno } - 1).coerceAtLeast(0)

/** Solo le tratte in treno, quelle che si seguono con `andamentoTreno`. */
val ProssimoTreno.tratteInTreno: List<Tratta> get() = tratte.filter { it.suTreno }

/** "1 cambio · Verona Porta Nuova", o null per un diretto. */
fun ProssimoTreno.testoCambi(): String? {
    if (cambi == 0) return null
    val dove = tratteInTreno.dropLast(1).joinToString(", ") { it.nomeArrivo }
    return (if (cambi == 1) "1 cambio" else "$cambi cambi") + " · $dove"
}

/** Mezzanotte locale del giorno di [ms]: il `dataPartenzaTreno` che vuole `andamentoTreno`. */
fun mezzanotte(ms: Long, zona: ZoneId = ZoneId.systemDefault()): Long =
    Instant.ofEpochMilli(ms).atZone(zona).toLocalDate().atStartOfDay(zona).toInstant().toEpochMilli()

/** Sotto questo margine al cambio, con il ritardo attuale, la coincidenza è a rischio. */
const val MARGINE_RISCHIO_MIN = 2

/** Margine minimo richiesto ai cambi che costruiamo noi da ViaggiaTreno (non a quelli di LeFrecce). */
const val MARGINE_CAMBIO_MIN = 5

/**
 * Minuti fra l'arrivo previsto della prima tratta (con il suo ritardo) e la partenza prevista
 * del treno successivo, tolti gli eventuali tratti a piedi in mezzo. Null per un diretto.
 */
fun ProssimoTreno.margineCambioMinuti(): Int? {
    val prima = tratte.firstOrNull() ?: return null
    val iSuccessivo = tratte.indexOfFirst { it !== prima && it.suTreno }.takeIf { it > 0 } ?: return null
    val successivo = tratte[iSuccessivo]
    val aPiedi = tratte.subList(1, iSuccessivo).sumOf { it.arrivoMs - it.partenzaMs }
    val arrivo = prima.arrivoMs + ritardoMinuti * 60_000L + aPiedi
    val partenza = successivo.partenzaMs + successivo.ritardoMinuti * 60_000L
    return ((partenza - arrivo) / 60_000L).toInt()
}

/** Il ritardo della prima tratta si è mangiato (quasi) tutto il tempo per cambiare. */
val ProssimoTreno.coincidenzaARischio: Boolean
    get() = ritardoMinuti > 0 && (margineCambioMinuti() ?: Int.MAX_VALUE) < MARGINE_RISCHIO_MIN

/**
 * Toglie le soluzioni chiaramente peggiori: una è nascosta se un'altra parte non prima, arriva
 * non dopo e cambia non di più (e non è identica). Un treno cancellato non nasconde niente, e
 * la corsa seguita non si nasconde mai.
 *
 * ponytail: confronto sugli orari di tabella, O(n²) su una lista di poche decine di voci.
 */
fun filtraDominati(viaggi: List<ProssimoTreno>, seguito: Int? = null): List<ProssimoTreno> {
    fun ProssimoTreno.domina(altro: ProssimoTreno): Boolean {
        val arrivo = orarioArrivoDestinazioneMs ?: return false
        val arrivoAltro = altro.orarioArrivoDestinazioneMs ?: return false
        if (cancellato) return false
        val nonPeggio = orarioPartenzaMs >= altro.orarioPartenzaMs && arrivo <= arrivoAltro && cambi <= altro.cambi
        val meglio = orarioPartenzaMs > altro.orarioPartenzaMs || arrivo < arrivoAltro || cambi < altro.cambi
        return nonPeggio && meglio
    }
    return viaggi.filter { v -> v.numeroTreno == seguito || viaggi.none { it.domina(v) } }
}

/**
 * Un solo viaggio per numero del primo treno: tutta l'app (seguito, widget, notifiche) identifica
 * un viaggio così. Fra due con lo stesso primo treno resta quello con meno cambi, poi il più rapido.
 */
fun unoPerPrimoTreno(viaggi: List<ProssimoTreno>): List<ProssimoTreno> =
    viaggi.sortedWith(compareBy({ it.cambi }, { it.orarioArrivoDestinazioneMs ?: Long.MAX_VALUE }))
        .distinctBy { it.numeroTreno }

/**
 * Indice della fermata il cui orario di tabella coincide con [ms]: in arrivo ([arrivo] = true)
 * per dove si scende, in partenza per dove si sale. -1 se non c'è.
 */
fun DettaglioTreno.Ok.indicePerOrario(ms: Long, arrivo: Boolean): Int = fermate.indexOfFirst { f ->
    val t = if (arrivo) f.arrivoProgrammatoMs ?: f.programmataMs else f.partenzaProgrammataMs ?: f.programmataMs
    t != null && kotlin.math.abs(t - ms) < 60_000L
}

/** Il viaggio come treno singolo, per la tratta [tratta] (il primo treno resta com'è nel feed). */
fun ProssimoTreno.vistaTratta(tratta: Tratta, dettaglio: DettaglioTreno.Ok?): ProssimoTreno {
    if (tratta.numeroTreno == numeroTreno) return copy(orarioArrivoDestinazioneMs = tratta.arrivoMs, destinazione = tratta.nomeArrivo)
    val salita = dettaglio?.fermate?.getOrNull(dettaglio.indicePartenza)
    return ProssimoTreno(
        numeroTreno = tratta.numeroTreno ?: numeroTreno,
        categoria = tratta.categoria,
        destinazione = tratta.nomeArrivo,
        codOrigine = tratta.codOrigine ?: codOrigine,
        codPartenza = salita?.codice ?: tratta.codPartenza,
        dataPartenzaTrenoMs = tratta.dataPartenzaTrenoMs ?: mezzanotte(tratta.partenzaMs),
        orarioPartenzaMs = tratta.partenzaMs,
        orarioArrivoDestinazioneMs = tratta.arrivoMs,
        ritardoMinuti = dettaglio?.ritardoMinuti ?: tratta.ritardoMinuti,
        binario = salita?.binario ?: tratta.binario,
        binarioConfermato = false,
        stato = if (dettaglio?.cancellato == true) StatoTreno.Cancellato("Treno cancellato") else StatoTreno.Regolare,
    )
}

/** Un treno diretto come unica tratta, per sostituire il resto di un viaggio dopo una coincidenza persa. */
fun ProssimoTreno.comeTratte(nomePartenza: String): List<Tratta> = tratte.ifEmpty {
    listOf(
        Tratta(
            numeroTreno = numeroTreno, categoria = categoria, nomePartenza = nomePartenza,
            nomeArrivo = destinazione, partenzaMs = orarioPartenzaMs,
            arrivoMs = orarioArrivoDestinazioneMs ?: orarioPartenzaMs, codOrigine = codOrigine,
            dataPartenzaTrenoMs = dataPartenzaTrenoMs, codPartenza = codPartenza,
            ritardoMinuti = ritardoMinuti, binario = binario,
        ),
    )
}

/**
 * La coincidenza dopo la tratta in corso è persa: il treno successivo è cancellato, o
 * parte prima che arrivi quello su cui sei (entrambi con il ritardo attuale).
 */
fun coincidenzaPersa(arrivoPrevistoMs: Long?, partenzaSuccessivaPrevistaMs: Long?, successivoCancellato: Boolean): Boolean =
    successivoCancellato ||
        (arrivoPrevistoMs != null && partenzaSuccessivaPrevistaMs != null && arrivoPrevistoMs > partenzaSuccessivaPrevistaMs)

/** È il momento dell'avviso di cambio: [minutiPrima] = 0 lo disattiva. */
fun avvisoCambioDovuto(arrivoPrevistoMs: Long?, adesso: Long, minutiPrima: Int): Boolean =
    minutiPrima > 0 && arrivoPrevistoMs != null && arrivoPrevistoMs - adesso <= minutiPrima * 60_000L

/** L'andamento conferma che il treno ha raggiunto la fermata dove scendi. */
val DettaglioTreno.Ok.arrivato: Boolean get() = indiceDestinazione >= 0 && indiceCorrente >= indiceDestinazione

/**
 * Il resto del viaggio dopo una coincidenza persa: le tratte fino a quella in corso
 * ([inCorso], compresa) restano, il seguito diventa [alternativa] dalla stazione di cambio.
 */
fun TrenoSeguito.ripianificato(inCorso: Tratta, alternativa: ProssimoTreno): TrenoSeguito {
    val fino = tratte.indexOf(inCorso).takeIf { it >= 0 } ?: return this
    return copy(
        tratte = tratte.take(fino + 1) + alternativa.comeTratte(nomePartenza = inCorso.nomeArrivo),
        arrivoDestinazioneMs = alternativa.orarioArrivoDestinazioneMs ?: arrivoDestinazioneMs,
        // il nuovo cambio merita il suo avviso, anche se quello vecchio era già partito
        avvisoCambioPer = trattaCorrente - 1,
    )
}

/** Come sta la coincidenza con i ritardi attuali: il colore del cambio in dettaglio. */
enum class StatoCambio { Ok, ARischio, Persa }

/**
 * Minuti per cambiare con i ritardi veri: partenza prevista del treno dopo meno arrivo previsto
 * di quello prima, meno i tratti a piedi in mezzo ([aPiediMs]). Null se manca un orario.
 */
fun margineLiveMinuti(arrivoPrevistoMs: Long?, partenzaPrevistaMs: Long?, aPiediMs: Long = 0): Int? {
    if (arrivoPrevistoMs == null || partenzaPrevistaMs == null) return null
    return ((partenzaPrevistaMs - arrivoPrevistoMs - aPiediMs) / 60_000L).toInt()
}

fun statoCambio(margineMinuti: Int?): StatoCambio = when {
    margineMinuti == null -> StatoCambio.Ok
    margineMinuti < 0 -> StatoCambio.Persa
    margineMinuti < MARGINE_RISCHIO_MIN -> StatoCambio.ARischio
    else -> StatoCambio.Ok
}
