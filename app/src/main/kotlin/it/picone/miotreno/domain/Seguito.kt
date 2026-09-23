package it.picone.miotreno.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Filtro unico di validità della corsa seguita, applicato in lettura.
 *
 * La corsa termina solo dopo una conferma dell'andamentoTreno, non a un orario stimato: un
 * ritardo non deve far sparire il treno mentre l'utente è ancora a bordo ([TrenoViewModel]
 * chiama `smettiDiSeguire()` quando l'andamentoTreno conferma l'arrivo a destinazione). Qui
 * scartiamo solo la selezione ormai chiaramente dimenticata: una corsa notturna può
 * attraversare la mezzanotte, quindi un giorno di scarto è tollerato, ma da due in poi lo
 * stesso numero di treno (servizio regolare) ricomparirebbe nei giorni successivi e verrebbe
 * scambiato per la corsa di oggi.
 */
fun seguitoAttivo(
    seguito: TrenoSeguito?,
    ora: Long = System.currentTimeMillis(),
    zona: ZoneId = ZoneId.systemDefault(),
): TrenoSeguito? {
    if (seguito == null) return null
    val dataCorsa = runCatching { LocalDate.parse(seguito.data) }.getOrNull() ?: return seguito
    val oggi = Instant.ofEpochMilli(ora).atZone(zona).toLocalDate()
    return if (ChronoUnit.DAYS.between(dataCorsa, oggi) >= 2) null else seguito
}

/**
 * Un treno merita ancora di stare in lista?
 *
 * Di norma no appena è partito: non lo prendi più. Ma la corsa **seguita** resta finché non
 * arriva a destinazione — è esattamente il momento in cui sei a bordo e vuoi sapere quanto manca.
 * Senza questa eccezione la barra di avanzamento non poteva riempirsi mai, perché ogni treno
 * in lista doveva ancora partire.
 */
fun trenoAncoraUtile(
    partenzaEffettivaMs: Long,
    arrivoMs: Long,
    adesso: Long,
    seguito: Boolean,
): Boolean = when {
    partenzaEffettivaMs >= adesso - 60_000L -> true
    seguito -> adesso < arrivoMs
    else -> false
}

/** Costruisce la selezione a partire dal treno aperto. */
fun ProssimoTreno.comeSeguito(zona: ZoneId = ZoneId.systemDefault()): TrenoSeguito = TrenoSeguito(
    numeroTreno = numeroTreno,
    data = Instant.ofEpochMilli(orarioPartenzaMs).atZone(zona).toLocalDate().toString(),
    arrivoDestinazioneMs = orarioArrivoDestinazioneMs ?: (orarioPartenzaMs + 90 * 60_000L),
    categoria = categoria,
    destinazione = destinazione,
    codOrigine = codOrigine,
    dataPartenzaTrenoMs = dataPartenzaTrenoMs,
    orarioPartenzaMs = orarioPartenzaMs,
    ritardoMinuti = ritardoMinuti,
    binario = binario,
    binarioConfermato = binarioConfermato,
)

/** Ultimo dato noto, usato come rete di sicurezza dopo che la corsa lascia il feed partenze. */
fun TrenoSeguito.comeProssimoTreno(): ProssimoTreno? {
    val origine = codOrigine ?: return null
    val dataCorsa = dataPartenzaTrenoMs ?: return null
    val partenza = orarioPartenzaMs ?: return null
    return ProssimoTreno(
        numeroTreno = numeroTreno, categoria = categoria ?: "TRENO", destinazione = destinazione.orEmpty(),
        codOrigine = origine, dataPartenzaTrenoMs = dataCorsa, orarioPartenzaMs = partenza,
        orarioArrivoDestinazioneMs = arrivoDestinazioneMs, ritardoMinuti = ritardoMinuti, binario = binario,
        binarioConfermato = binarioConfermato, stato = StatoTreno.Regolare,
    )
}

/**
 * Il treno che merita la card grande in cima alla home.
 *
 * Quello seguito se è ancora in lista, altrimenti il primo utile. Un treno cancellato non è
 * mai un buon punto focale: la card in evidenza promette "questo è il tuo treno", e un treno
 * cancellato non lo è. Se sono tutti cancellati non c'è evidenza, e va bene così.
 */
fun trenoInEvidenza(treni: List<ProssimoTreno>, seguito: TrenoSeguito?): ProssimoTreno? =
    treni.firstOrNull { it.numeroTreno == seguito?.numeroTreno && !it.cancellato }
        ?: treni.firstOrNull { !it.cancellato }
