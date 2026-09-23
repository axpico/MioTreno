package it.picone.miotreno.domain

/**
 * Orario da mostrare per una fermata, con la distinzione fra fatto e previsione.
 *
 * ViaggiaTreno valorizza `effettiva` **solo per le fermate già raggiunte**: per tutto il
 * resto della corsa il feed restituisce il solo orario di tabella. Mostrarlo nudo significa
 * dire "arrivo alle 14:20" a chi è su un treno con +12, cioè mentire con precisione.
 *
 * Qui l'orario mancante viene proiettato (programmata + ritardo corrente) e marcato come
 * [confermato] = false, così la UI può mostrarlo come stima e non come dato acquisito.
 */
data class OrarioFermata(
    /** Orario di tabella, sempre dal feed. Null solo se il feed non lo dà. */
    val programmataMs: Long?,
    /** Orario da mostrare in evidenza: reale se confermato, altrimenti proiettato. */
    val previstoMs: Long?,
    /** Scarto in minuti rispetto alla programmata. Null se non calcolabile. */
    val ritardoMinuti: Int?,
    /** Vero solo se l'orario viene dal feed reale, non da una proiezione. */
    val confermato: Boolean,
) {
    /** Vero quando vale la pena mostrare programmata e previsto insieme. */
    val inRitardo: Boolean get() = ritardoMinuti != null && ritardoMinuti != 0 && programmataMs != null
}

/**
 * Proietta l'orario di questa fermata usando [ritardoTreno] (minuti) quando manca l'effettiva.
 *
 * ponytail: il ritardo del treno viene applicato uguale a tutte le fermate future. Recuperare
 * o perdere tempo fra una fermata e l'altra è normale, ma ViaggiaTreno non espone una stima
 * per fermata: inventarne una sarebbe più precisa in apparenza e meno vera. Se un giorno il
 * feed desse un ritardo previsto per fermata, va usato qui e basta.
 */
fun FermataTreno.orario(ritardoTreno: Int): OrarioFermata {
    val p = programmataMs
    val e = effettivaMs
    if (e != null) {
        return OrarioFermata(
            programmataMs = p,
            previstoMs = e,
            ritardoMinuti = p?.let { ((e - it) / 60_000L).toInt() },
            confermato = true,
        )
    }
    if (p == null) return OrarioFermata(null, null, null, confermato = false)
    return OrarioFermata(
        programmataMs = p,
        previstoMs = p + ritardoTreno * 60_000L,
        ritardoMinuti = ritardoTreno,
        confermato = false,
    )
}

/**
 * Stessa proiezione per un orario sciolto (partenza/arrivo di [ProssimoTreno]), dove non
 * esiste un "effettiva": il feed partenze dà programmata + ritardo e nient'altro.
 */
fun orarioProiettato(programmataMs: Long?, ritardoTreno: Int): OrarioFermata =
    if (programmataMs == null) OrarioFermata(null, null, null, confermato = false)
    else OrarioFermata(
        programmataMs = programmataMs,
        previstoMs = programmataMs + ritardoTreno * 60_000L,
        ritardoMinuti = ritardoTreno,
        confermato = false,
    )
