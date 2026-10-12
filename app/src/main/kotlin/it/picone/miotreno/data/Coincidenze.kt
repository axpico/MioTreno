package it.picone.miotreno.data

import it.picone.miotreno.domain.MARGINE_CAMBIO_MIN
import it.picone.miotreno.domain.ProssimoTreno
import it.picone.miotreno.domain.Tratta
import it.picone.miotreno.domain.mezzanotte
import java.time.OffsetDateTime

/** Più di due cambi non li vuole nessuno: viaggi così si scartano. */
private const val MAX_CAMBI = 2

/** Quanti scali di cambio interrogare nel ripiego su ViaggiaTreno (4 chiamate ciascuno). */
private const val MAX_HUB = 3

/**
 * Viaggi con cambi dalle soluzioni LeFrecce.
 *
 * Il primo treno deve comparire fra le [partenze] del cluster: è il treno che prendi da qui, e
 * da lì arrivano ritardo, binario e codici veri. Un viaggio che parte con un altro treno (fuori
 * finestra, o da un'altra stazione) si scarta. I tratti a piedi in testa sono quindi dentro il
 * cluster (Garibaldi → Passante) e si tolgono; quelli in coda pure, se l'ultimo treno arriva
 * davvero alla destinazione ([arriviDestinazione]). Quelli in mezzo restano: dicono di cambiare
 * stazione a piedi o in metro. I diretti non si prendono da qui: li dà già ViaggiaTreno.
 */
internal fun connessioniLeFrecce(
    soluzioni: List<SoluzioneDto>,
    partenze: List<Pair<String, PartenzaArrivoDto>>,
    arriviDestinazione: Map<Int, PartenzaArrivoDto>,
): List<ProssimoTreno> = soluzioni.mapNotNull { s ->
    var tratte = s.nodes.map { it.comeTratta() ?: return@mapNotNull null }
    tratte = tratte.dropWhile { !it.suTreno }
    if (tratte.lastOrNull { it.suTreno }?.numeroTreno in arriviDestinazione) tratte = tratte.dropLastWhile { !it.suTreno }
    val inTreno = tratte.filter { it.suTreno }
    if (inTreno.size !in 2..MAX_CAMBI + 1) return@mapNotNull null

    val prima = inTreno.first()
    val (codSalita, p) = partenze.firstOrNull { (_, p) ->
        p.numeroTreno == prima.numeroTreno && p.orarioPartenza?.let { kotlin.math.abs(it - prima.partenzaMs) < 60_000L } == true
    } ?: return@mapNotNull null
    val primaArricchita = prima.copy(
        // la categoria di ViaggiaTreno, la stessa mostrata in lista ("REG", non "RE")
        categoria = p.categoriaPulita(),
        codPartenza = codSalita,
        codOrigine = p.codOrigine ?: prima.codOrigine,
        dataPartenzaTrenoMs = p.dataPartenzaTreno ?: prima.dataPartenzaTrenoMs,
        ritardoMinuti = p.ritardo ?: 0,
        binario = p.binario(),
    )
    p.comeProssimoTreno(codSalita, arrivoMs = tratte.last().arrivoMs, codStazioneRipiego = codSalita)
        ?.copy(tratte = listOf(primaArricchita) + tratte.drop(1))
}

private fun NodoDto.comeTratta(): Tratta? {
    val partenza = departureTime?.let(::epoch) ?: return null
    val arrivo = arrivalTime?.let(::epoch) ?: return null
    val sigla = train?.acronym?.trim().orEmpty()
    val numero = train?.name?.trim()?.toIntOrNull()?.takeIf { sigla != "UB" }
    val categoria = when {
        numero != null -> sigla.ifEmpty { "TRENO" }
        train?.trainCategory?.contains("walk", ignoreCase = true) == true -> "A piedi"
        else -> "Metro"
    }
    return Tratta(
        numeroTreno = numero,
        categoria = categoria,
        nomePartenza = origin.orEmpty(),
        nomeArrivo = destination.orEmpty(),
        partenzaMs = partenza,
        arrivoMs = arrivo,
        codOrigine = bdoOrigin?.takeIf { numero != null },
        // ponytail: giorno della partenza da *questa* stazione; sbaglia solo per un treno
        // partito dal suo capolinea prima di mezzanotte, caso che si accetta
        dataPartenzaTrenoMs = numero?.let { mezzanotte(partenza) },
    )
}

private fun epoch(iso: String): Long? = runCatching { OffsetDateTime.parse(iso).toInstant().toEpochMilli() }.getOrNull()

/**
 * Scali dove cambiare nel ripiego su ViaggiaTreno: capolinea dei treni in partenza da qui che
 * sono anche origine di treni in arrivo a destinazione — cioè dove un treno finisce e un altro
 * comincia. I più frequenti per primi. Un cambio in una stazione di passaggio non si vede
 * (servirebbe un `andamentoTreno` per treno): limite accettato.
 */
internal fun hubCandidati(
    partenze: List<Pair<String, PartenzaArrivoDto>>,
    arriviDestinazione: Collection<PartenzaArrivoDto>,
    esclusi: Set<String>,
    max: Int = MAX_HUB,
): List<String> {
    val origini = arriviDestinazione.mapNotNull { it.codOrigine }.toSet()
    return partenze.mapNotNull { it.second.codDestinazione }
        .filter { it in origini && it !in esclusi }
        .groupingBy { it }.eachCount()
        .entries.sortedByDescending { it.value }
        .take(max).map { it.key }
}

/**
 * Viaggi con un cambio allo scalo [hub], costruiti solo con dati ViaggiaTreno: un treno da qui
 * che finisce a [hub] ([arriviHub] dà a che ora ci arriva) e il primo treno da [hub]
 * ([partenzeHub]) che arriva a destinazione con almeno [MARGINE_CAMBIO_MIN] minuti per
 * cambiare. Per ogni primo treno si tiene il secondo che arriva prima.
 */
internal fun connessioniViaHub(
    hub: String,
    partenze: List<Pair<String, PartenzaArrivoDto>>,
    arriviHub: List<PartenzaArrivoDto>,
    partenzeHub: List<PartenzaArrivoDto>,
    arriviDestinazione: Map<Int, PartenzaArrivoDto>,
    nomePartenza: String,
    nomeDestinazione: String,
): List<ProssimoTreno> {
    val arrivoAlHub = arriviHub.filter { it.numeroTreno != null }.associateBy { it.numeroTreno!! }
    return partenze.mapNotNull { (codSalita, p) ->
        if (p.codDestinazione != hub) return@mapNotNull null
        val numero = p.numeroTreno ?: return@mapNotNull null
        val partenzaMs = p.orarioPartenza ?: return@mapNotNull null
        val alHub = arrivoAlHub[numero]?.orarioArrivo?.takeIf { it > partenzaMs } ?: return@mapNotNull null

        val (q, arrivoFinale) = partenzeHub.mapNotNull { q ->
            val partenzaQ = q.orarioPartenza ?: return@mapNotNull null
            val arrivo = arriviDestinazione[q.numeroTreno]?.orarioArrivo ?: return@mapNotNull null
            if (partenzaQ < alHub + MARGINE_CAMBIO_MIN * 60_000L || arrivo <= partenzaQ) return@mapNotNull null
            q to arrivo
        }.minByOrNull { it.second } ?: return@mapNotNull null

        val nomeHub = p.destinazione.orEmpty().nomeStazione()
        val prima = Tratta(
            numeroTreno = numero, categoria = p.categoriaPulita(), nomePartenza = nomePartenza, nomeArrivo = nomeHub,
            partenzaMs = partenzaMs, arrivoMs = alHub, codOrigine = p.codOrigine,
            dataPartenzaTrenoMs = p.dataPartenzaTreno, codPartenza = codSalita, codArrivo = hub,
            ritardoMinuti = p.ritardo ?: 0, binario = p.binario(),
        )
        val seconda = Tratta(
            numeroTreno = q.numeroTreno, categoria = q.categoriaPulita(), nomePartenza = nomeHub,
            nomeArrivo = nomeDestinazione,
            partenzaMs = q.orarioPartenza!!, arrivoMs = arrivoFinale, codOrigine = q.codOrigine,
            dataPartenzaTrenoMs = q.dataPartenzaTreno, codPartenza = hub,
            ritardoMinuti = q.ritardo ?: 0, binario = q.binario(),
        )
        p.comeProssimoTreno(codSalita, arrivoMs = arrivoFinale, codStazioneRipiego = codSalita)
            ?.copy(tratte = listOf(prima, seconda))
    }
}
