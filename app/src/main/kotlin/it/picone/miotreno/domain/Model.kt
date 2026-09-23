package it.picone.miotreno.domain

import kotlinx.serialization.Serializable

/** Stato del treno secondo provvedimento/circolante/subTitle di ViaggiaTreno. */
sealed interface StatoTreno {
    data object Regolare : StatoTreno
    data class ParzialmenteSoppresso(val dettaglio: String) : StatoTreno
    data class Deviato(val dettaglio: String) : StatoTreno
    data class Cancellato(val dettaglio: String) : StatoTreno

    /**
     * `subTitle` valorizzato ma nessun codice noto (provvedimento 1/2/3): il servizio è
     * alterato in un modo che ViaggiaTreno non documenta. Non è [ParzialmenteSoppresso]: quello
     * è un'affermazione precisa (provvedimento 2) che qui non è verificata, e potrebbe trattarsi
     * anche di una soppressione totale mostrata così com'è, senza indovinare la gravità.
     */
    data class Alterato(val dettaglio: String) : StatoTreno
}

data class ProssimoTreno(
    val numeroTreno: Int,
    val categoria: String,
    val destinazione: String,
    val codOrigine: String,
    /**
     * Codice della stazione da cui si sale, che nel cluster non è unico: a Milano Porta
     * Garibaldi i treni si dividono fra superficie (S01645) e sotterranea/Passante (S01647).
     * Serve a dire all'utente su quale dei due binari presentarsi. Null nello snapshot del
     * treno seguito, che non lo registra.
     */
    val codPartenza: String? = null,
    val dataPartenzaTrenoMs: Long,
    val orarioPartenzaMs: Long,
    val orarioArrivoBustoMs: Long?,
    val ritardoMinuti: Int,
    val binario: String?,
    val binarioConfermato: Boolean,
    val stato: StatoTreno,
) {
    val cancellato: Boolean get() = stato is StatoTreno.Cancellato
    /** Minuti alla partenza reale (programmata + ritardo) rispetto a [ora]. */
    fun minutiAllaPartenza(ora: Long): Int =
        ((orarioPartenzaMs + ritardoMinuti * 60_000L - ora) / 60_000L).toInt()

    /**
     * Quanta parte del viaggio verso Busto è alle spalle, da 0 a 1.
     *
     * Il ritardo sposta **entrambi** gli estremi: un treno con +10 non è più avanti di uno in
     * orario alla stessa ora, è indietro. Interpolando sugli orari programmati nudi la barra
     * diceva "a metà viaggio" mentre il treno non era ancora partito.
     *
     * Interpolazione sull'orologio, non la posizione reale: usata come fallback quando manca
     * [DettaglioTreno.Ok.progressoReale] (vedi lì per la posizione vera da andamentoTreno).
     */
    fun avanzamento(ora: Long): Float {
        val arrivo = orarioArrivoBustoMs ?: return 0f
        val scarto = ritardoMinuti * 60_000L
        val partenza = orarioPartenzaMs + scarto
        val arrivoEffettivo = arrivo + scarto
        if (arrivoEffettivo <= partenza) return 0f
        return ((ora - partenza).toFloat() / (arrivoEffettivo - partenza)).coerceIn(0f, 1f)
    }
}

data class FermataTreno(
    val codice: String,
    val nome: String,
    val programmataMs: Long?,
    val effettivaMs: Long?,
    val passata: Boolean,
)

/** Esito di andamentoTreno per la schermata dettaglio. */
sealed interface DettaglioTreno {
    data class Ok(
        val fermate: List<FermataTreno>,
        val ritardoMinuti: Int,
        val indiceCorrente: Int,
        val indiceBusto: Int,
    ) : DettaglioTreno

    data object DatiNonDisponibili : DettaglioTreno
}

/** Posizione reale (fermate passate su fermate totali fino a Busto), 0 a 1. Null se Busto non in lista. */
fun DettaglioTreno.Ok.progressoReale(): Float? =
    if (indiceBusto <= 0) null
    else ((indiceCorrente + 1).toFloat() / (indiceBusto + 1)).coerceIn(0f, 1f)

/** Forma comune a [Stazione] e [RisultatoStazione]: tutto quello che serve a un selettore. */
interface ElementoStazione {
    val nome: String
    val codice: String
}

@Serializable
data class Stazione(
    override val codice: String,
    override val nome: String,
    val lat: Double,
    val lon: Double,
) : ElementoStazione

data class StazioneVicina(val stazione: Stazione, val distanzaMetri: Int)

/** Riga di risultato della ricerca nazionale: solo nome e codice, niente coordinate. */
data class RisultatoStazione(override val nome: String, override val codice: String) : ElementoStazione

/**
 * Ultimo gruppo di stazioni risolto da una posizione vera.
 *
 * Serve al widget e ai worker: in background un fix GPS spesso non c'è (telefono in tasca,
 * GPS freddo), e mostrare i treni dell'ultima stazione nota è enormemente più utile che
 * mostrare un errore. La stazione di un pendolare non cambia di minuto in minuto.
 */
@Serializable
data class UltimoCluster(val nome: String, val codici: List<String>)

@Serializable
data class Sciopero(
    val dataInizio: String,   // ISO yyyy-MM-dd
    val dataFine: String,     // ISO yyyy-MM-dd
    val settore: String,
    val rilevanza: String,
    val regione: String,
    val modalita: String,
)

/** Una riga per treno/giorno, sovrascritta finché il treno non è partito. */
@Serializable
data class RitardoRecord(
    val numeroTreno: Int,
    val categoria: String,
    val stazionePartenzaCodice: String,
    val dataRiferimento: String,     // ISO yyyy-MM-dd
    val orarioProgrammato: Long,
    val ritardoMinuti: Int,
    val binarioProgrammato: String?,
    val binarioEffettivo: String?,
    val rilevatoAlle: Long,
) {
    val chiave: String get() = "$numeroTreno@$dataRiferimento"
}

/**
 * Corsa che l'utente ha scelto di seguire. Non un preferito ricorrente: vale per oggi.
 *
 * [data] in ISO serve a far scadere la selezione da sola al cambio di giorno, e
 * [arrivoBustoMs] a farla scadere quando il treno ha superato Busto — senza worker di pulizia.
 */
@Serializable
data class TrenoSeguito(
    val numeroTreno: Int,
    val data: String,
    val arrivoBustoMs: Long,
    /** Snapshot della corsa: permette il tracking anche quando sparisce dalle partenze. */
    val categoria: String? = null,
    val destinazione: String? = null,
    val codOrigine: String? = null,
    val dataPartenzaTrenoMs: Long? = null,
    val orarioPartenzaMs: Long? = null,
    val ritardoMinuti: Int = 0,
    val binario: String? = null,
    val binarioConfermato: Boolean = false,
)

/** Vero se il treno ferma davvero alla stazione [codicePassaggio], secondo il tracking reale. */
fun passaPer(dettaglio: DettaglioTreno, codicePassaggio: String): Boolean =
    (dettaglio as? DettaglioTreno.Ok)?.fermate?.any { it.codice == codicePassaggio } == true
