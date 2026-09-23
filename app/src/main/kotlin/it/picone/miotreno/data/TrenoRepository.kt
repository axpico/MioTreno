package it.picone.miotreno.data

import it.picone.miotreno.domain.DettaglioTreno
import it.picone.miotreno.domain.FermataTreno
import it.picone.miotreno.domain.ProssimoTreno
import it.picone.miotreno.domain.RisultatoStazione
import it.picone.miotreno.domain.RitardoRecord
import it.picone.miotreno.domain.Sciopero
import it.picone.miotreno.domain.StatoTreno
import it.picone.miotreno.domain.TrenoSeguito
import it.picone.miotreno.domain.comeProssimoTreno
import it.picone.miotreno.domain.trenoAncoraUtile
import it.picone.miotreno.domain.UltimoCluster
import it.picone.miotreno.domain.Stazione
import it.picone.miotreno.domain.stazioniVicine
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

private const val FILE_STAZIONI = "stazioni.json"
private const val FILE_STAZIONI_NAZIONALI = "stazioni_nazionali.json"
/** ViaggiaTreno numera le regioni da 0 (estero) a 22; 23 in su non risponde più nulla. */
private val REGIONI_TUTTE = 0..22
private const val FILE_RITARDI = "ritardi.json"
private const val FILE_SCIOPERI = "scioperi.json"
private const val FILE_ULTIMA_STAZIONE = "ultima_stazione.json"
private const val FILE_REGIONE_DESTINAZIONE = "regione_destinazione.json"

class TrenoRepository(
    private val api: ViaggiaTrenoApi,
    private val scioperiApi: ScioperiApi,
    private val store: JsonStore,
) {
    /** Elenco stazioni della regione, scaricato una volta sola e poi servito dalla cache. */
    suspend fun stazioni(): List<Stazione> {
        store.leggi<List<Stazione>>(FILE_STAZIONI, emptyList())
            .takeIf { it.isNotEmpty() }
            ?.let { return it }

        val scaricate = api.elencoStazioni().mapNotNull { dto ->
            val codice = dto.codiceStazione ?: return@mapNotNull null
            val nome = dto.localita?.nomeLungo?.trim().orEmpty().nomeStazione()
            val lat = dto.lat ?: return@mapNotNull null
            val lon = dto.lon ?: return@mapNotNull null
            // lat/lon a 0 = stazione senza coordinate valide nel dataset
            if (nome.isEmpty() || (lat == 0.0 && lon == 0.0)) return@mapNotNull null
            Stazione(codice, nome, lat, lon)
        }
        if (scaricate.isNotEmpty()) store.scrivi(FILE_STAZIONI, scaricate)
        return scaricate
    }

    /**
     * Elenco di tutte le stazioni italiane, scaricato una volta sola (23 chiamate regionali
     * in parallelo) e poi servito dalla cache. Usato per la ricerca nazionale: l'endpoint
     * `cercaStazione` di ViaggiaTreno fa match solo sul PREFISSO del nome intero ("MILANO
     * PORTA GARIBALDI" non si trova cercando "garibaldi"), quindi qui si filtra lato client
     * per sottostringa, cosa che quell'endpoint non permette.
     */
    private suspend fun stazioniNazionali(): List<Stazione> {
        store.leggi<List<Stazione>>(FILE_STAZIONI_NAZIONALI, emptyList())
            .takeIf { it.isNotEmpty() }
            ?.let { return it }

        val scaricate = coroutineScope {
            REGIONI_TUTTE.map { id -> async { runCatching { api.elencoStazioni(id) }.getOrDefault(emptyList()) } }
                .awaitAll()
                .flatten()
        }.mapNotNull { dto ->
            val codice = dto.codiceStazione ?: return@mapNotNull null
            val nome = dto.localita?.nomeLungo?.trim().orEmpty().nomeStazione()
            val lat = dto.lat ?: return@mapNotNull null
            val lon = dto.lon ?: return@mapNotNull null
            if (nome.isEmpty() || (lat == 0.0 && lon == 0.0)) return@mapNotNull null
            Stazione(codice, nome, lat, lon)
        }.distinctBy { it.codice }

        if (scaricate.isNotEmpty()) store.scrivi(FILE_STAZIONI_NAZIONALI, scaricate)
        return scaricate
    }

    /**
     * Ricerca nazionale per nome, per sottostringa (non solo prefisso): usata dai selettori
     * di stazione di partenza e destinazione, che non sono più limitati alla regione
     * precaricata.
     */
    suspend fun cercaStazioni(query: String): List<RisultatoStazione> {
        val q = query.trim().normalizzata()
        if (q.isEmpty()) return emptyList()
        return stazioniNazionali()
            .filter { it.nome.normalizzata().contains(q) }
            .sortedWith(
                compareBy(
                    { !it.nome.normalizzata().startsWith(q) },
                    { it.nome.length },
                    { it.nome },
                ),
            )
            .take(40)
            .map { RisultatoStazione(it.nome, it.codice) }
    }

    /**
     * Risolve una stazione scelta dalla ricerca nazionale in una [Stazione] completa di
     * coordinate (serve a [it.picone.miotreno.domain.stazioniVicine] per il cluster
     * superficie/sotterranea) e la aggiunge alla cache locale, così le letture successive
     * (incluso [RisolutoreStazione]) la trovano senza rifare la chiamata.
     */
    suspend fun risolviStazione(codice: String): Stazione? {
        stazioni().firstOrNull { it.codice == codice }?.let { return it }
        val idRegione = api.regione(codice) ?: return null
        val dto = api.dettaglioStazione(codice, idRegione) ?: return null
        val nome = dto.localita?.nomeLungo?.trim().orEmpty().nomeStazione()
        val lat = dto.lat ?: return null
        val lon = dto.lon ?: return null
        if (nome.isEmpty() || (lat == 0.0 && lon == 0.0)) return null
        val stazione = Stazione(codice, nome, lat, lon)
        val cache = store.leggi<List<Stazione>>(FILE_STAZIONI, emptyList())
        store.scrivi(FILE_STAZIONI, cache + stazione)
        return stazione
    }

    /** Id regione ViaggiaTreno della stazione, per il filtro degli scioperi rilevanti. */
    suspend fun regioneDi(codice: String): Int? = api.regione(codice)

    /** Codici del cluster della destinazione (vedi [stazioniVicine]); senza coordinate, solo sé stessa. */
    private suspend fun codiciClusterDestinazione(codDestinazione: String): List<String> {
        val staz = risolviStazione(codDestinazione) ?: return listOf(codDestinazione)
        val cluster = stazioniVicine(stazioni(), staz.lat, staz.lon).map { it.stazione.codice }
        return cluster.ifEmpty { listOf(codDestinazione) }
    }

    suspend fun salvaRegioneDestinazione(idRegione: Int) = store.scrivi(FILE_REGIONE_DESTINAZIONE, idRegione)

    suspend fun regioneDestinazione(): Int? = store.leggi<Int?>(FILE_REGIONE_DESTINAZIONE, null)

    /**
     * Treni diretti da [codStazione] a [codDestinazione].
     *
     * Niente whitelist di direttrici e niente andamentoTreno per treno: basta intersecare
     * le partenze dalla stazione con gli arrivi a destinazione sul numero di treno. La finestra
     * di `arrivi` è più stretta della durata del viaggio, quindi la si interroga due volte
     * (adesso e fra un'ora) e si uniscono i risultati.
     */
    suspend fun prossimiTreni(codStazione: String, codDestinazione: String, adesso: Date = Date()): List<ProssimoTreno> =
        prossimiTreni(listOf(codStazione), codDestinazione, adesso)

    /**
     * Variante su più codici: lo stesso scalo può averne più d'uno (superficie e sotterranea)
     * e i treni si dividono fra loro. Vedi [it.picone.miotreno.domain.stazioniVicine].
     *
     * Lo stesso cluster va risolto anche sul lato destinazione: senza, un treno che arriva al
     * codice gemello della destinazione scelta (stesso scalo, altro codice ViaggiaTreno) sarebbe
     * invisibile all'intersezione, esattamente come lo era lato partenza prima di [codStazioni].
     */
    suspend fun prossimiTreni(
        codStazioni: List<String>,
        codDestinazione: String,
        adesso: Date = Date(),
        /** Corsa seguita: resta in lista anche dopo la partenza, fino all'arrivo. */
        seguito: TrenoSeguito? = null,
    ): List<ProssimoTreno> = runCatching {
        val codDestinazioni = codiciClusterDestinazione(codDestinazione)
        coroutineScope {
            val codStazione = codStazioni.first()
            val partenzeReq = async {
                codStazioni.map { async { api.partenze(it, adesso) } }.flatMap { it.await() }
            }
            val arriviOra = async {
                codDestinazioni.map { async { api.arrivi(it, adesso) } }.flatMap { it.await() }
            }
            val arriviDopo = async {
                codDestinazioni.map { async { api.arrivi(it, Date(adesso.time + 60 * 60_000L)) } }.flatMap { it.await() }
            }

            val arriviPerTreno = (arriviOra.await() + arriviDopo.await())
                .filter { it.numeroTreno != null }
                .associateBy { it.numeroTreno!! }

            partenzeReq.await().mapNotNull { p ->
                val numero = p.numeroTreno ?: return@mapNotNull null
                val arrivo = arriviPerTreno[numero] ?: return@mapNotNull null
                val partenzaMs = p.orarioPartenza ?: return@mapNotNull null
                val arrivoMs = arrivo.orarioArrivo ?: return@mapNotNull null
                // stesso numero ma direzione opposta: scarta
                if (partenzaMs >= arrivoMs) return@mapNotNull null
                val ritardo = p.ritardo ?: 0
                val ancoraUtile = trenoAncoraUtile(
                    partenzaEffettivaMs = partenzaMs + ritardo * 60_000L,
                    arrivoMs = arrivoMs,
                    adesso = adesso.time,
                    seguito = numero == seguito?.numeroTreno,
                )
                if (!ancoraUtile) return@mapNotNull null

                val binEff = p.binarioEffettivoPartenzaDescrizione?.trim()?.takeIf { it.isNotEmpty() }
                ProssimoTreno(
                    numeroTreno = numero,
                    categoria = (p.categoria ?: p.categoriaDescrizione).orEmpty().trim()
                        .ifEmpty { "TRENO" },
                    destinazione = (p.destinazione ?: arrivo.destinazione).orEmpty()
                        .trim().nomeStazione(),
                    codOrigine = p.codOrigine ?: codStazione,
                    dataPartenzaTrenoMs = p.dataPartenzaTreno ?: partenzaMs,
                    orarioPartenzaMs = partenzaMs,
                    orarioArrivoBustoMs = arrivoMs,
                    ritardoMinuti = ritardo,
                    binario = binEff
                        ?: p.binarioProgrammatoPartenzaDescrizione?.trim()?.takeIf { it.isNotEmpty() },
                    binarioConfermato = binEff != null,
                    stato = statoDa(p),
                )
            }.let { dalFeed ->
                // Dopo la partenza il feed `partenze` può smettere di elencare la corsa. Il
                // seguito persiste invece fino alla conferma di arrivo, usando l'ultimo snapshot.
                val fallback = seguito?.comeProssimoTreno()
                if (fallback != null && dalFeed.none { it.numeroTreno == fallback.numeroTreno }) dalFeed + fallback else dalFeed
            }.distinctBy { it.numeroTreno }.sortedBy { it.orarioPartenzaMs }
        }
    }.getOrElse { errore ->
        // Un errore momentaneo non cancella il viaggio che l'utente sta seguendo: la UI lo
        // mostrerà con l'ultimo dato noto e potrà ritentare il tracking al refresh successivo.
        seguito?.comeProssimoTreno()?.let(::listOf) ?: throw errore
    }

    /** Tracking fermata-per-fermata. HTTP 204 → [DettaglioTreno.DatiNonDisponibili]. */
    suspend fun dettaglio(treno: ProssimoTreno, codDestinazione: String): DettaglioTreno {
        val res = api.andamentoTreno(treno.codOrigine, treno.numeroTreno, treno.dataPartenzaTrenoMs)
        val dto = (res as? Andamento.Ok)?.treno ?: return DettaglioTreno.DatiNonDisponibili
        if (dto.fermate.isEmpty()) return DettaglioTreno.DatiNonDisponibili

        val fermate = dto.fermate.map { f ->
            val effettiva = f.effettiva ?: f.partenzaReale ?: f.arrivoReale
            FermataTreno(
                codice = f.id.orEmpty(),
                nome = f.stazione.orEmpty().nomeStazione(),
                programmataMs = f.programmata ?: f.partenza_teorica ?: f.arrivo_teorico,
                effettivaMs = effettiva,
                passata = effettiva != null,
            )
        }
        return DettaglioTreno.Ok(
            fermate = fermate,
            ritardoMinuti = dto.ritardo ?: treno.ritardoMinuti,
            indiceCorrente = fermate.indexOfLast { it.passata },
            indiceBusto = fermate.indexOfFirst { it.codice == codDestinazione },
        )
    }

    // ── ultima stazione nota ──────────────────────────────────────────────────

    suspend fun salvaUltimoCluster(nome: String, codici: List<String>) =
        store.scrivi(FILE_ULTIMA_STAZIONE, UltimoCluster(nome, codici))

    suspend fun ultimoCluster(): UltimoCluster? =
        store.leggi<UltimoCluster?>(FILE_ULTIMA_STAZIONE, null)

    // ── scioperi ──────────────────────────────────────────────────────────────

    suspend fun scioperiInCache(): List<Sciopero> = store.leggi(FILE_SCIOPERI, emptyList())

    suspend fun aggiornaScioperi(): List<Sciopero> {
        val regioneNome = regioneDestinazione()?.let { NOMI_REGIONI[it] } ?: NOMI_REGIONI.getValue(REGIONE_LOMBARDIA)
        val s = scioperiApi.scaricaRilevanti(regioneNome)
        store.scrivi(FILE_SCIOPERI, s)
        return s
    }

    // ── storico ritardi ───────────────────────────────────────────────────────

    suspend fun storicoRitardi(): List<RitardoRecord> = store.leggi(FILE_RITARDI, emptyList())

    /**
     * Una riga per treno/giorno, sovrascritta a ogni lettura finché il treno non è partito:
     * per il caso d'uso "pattern nel tempo" basta il valore finale.
     */
    suspend fun salvaRitardi(treni: List<ProssimoTreno>, codStazione: String, adesso: Long) {
        if (treni.isEmpty()) return
        val zona = ZoneId.systemDefault()
        val nuovi = treni.filterNot { it.cancellato }.map { t ->
            RitardoRecord(
                numeroTreno = t.numeroTreno,
                categoria = t.categoria,
                stazionePartenzaCodice = codStazione,
                dataRiferimento = Instant.ofEpochMilli(t.orarioPartenzaMs)
                    .atZone(zona).toLocalDate().toString(),
                orarioProgrammato = t.orarioPartenzaMs,
                ritardoMinuti = t.ritardoMinuti,
                binarioProgrammato = t.binario.takeIf { !t.binarioConfermato },
                binarioEffettivo = t.binario.takeIf { t.binarioConfermato },
                rilevatoAlle = adesso,
            )
        }
        val perChiave = storicoRitardi().associateBy { it.chiave }.toMutableMap()
        nuovi.forEach { perChiave[it.chiave] = it }
        val limite = LocalDate.now().minusDays(1095).toString()
        store.scrivi(FILE_RITARDI, perChiave.values.filter { it.dataRiferimento >= limite })
    }
}

/**
 * Deriva lo stato da provvedimento/tipoTreno/subTitle (setup §6.1).
 *
 * Nota verificata sui dati reali: `circolante` NON vuol dire "non cancellato". Su una risposta
 * qualsiasi di `partenze` è false per la maggior parte dei treni, semplicemente perché non sono
 * ancora partiti. Usarlo per marcare le cancellazioni farebbe apparire cancellati quasi tutti
 * i treni in lista: qui viene deliberatamente ignorato.
 */
internal fun statoDa(p: PartenzaArrivoDto): StatoTreno {
    val dettaglio = p.subTitle?.trim().orEmpty()
    return when {
        p.provvedimento == 3 || p.tipoTreno == "DV" ->
            StatoTreno.Deviato(dettaglio.ifEmpty { "Treno deviato" })
        p.provvedimento == 1 -> StatoTreno.Cancellato(dettaglio.ifEmpty { "Treno cancellato" })
        p.provvedimento == 2 || p.tipoTreno in setOf("PP", "SI", "SF") ->
            StatoTreno.ParzialmenteSoppresso(dettaglio.ifEmpty { "Parzialmente soppresso" })
        // ponytail: il codice della soppressione totale non è documentato; quando c'è, il
        // testo esplicativo arriva comunque in subTitle. Mostrarlo è meglio che indovinare —
        // ma etichettarlo "parziale" sarebbe un'affermazione non verificata, quindi stato
        // neutro invece di riusare ParzialmenteSoppresso.
        dettaglio.isNotEmpty() -> StatoTreno.Alterato(dettaglio)
        else -> StatoTreno.Regolare
    }
}

/** Minuscolo e senza accenti, per confronti di ricerca tolleranti ("età" == "eta"). */
fun String.normalizzata(): String =
    java.text.Normalizer.normalize(lowercase(), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")

/** ViaggiaTreno restituisce i nomi in maiuscolo: "MILANO PORTA GARIBALDI" → "Milano Porta Garibaldi". */
fun String.nomeStazione(): String =
    trim().split(" ").joinToString(" ") { parola ->
        when {
            parola.length <= 2 && parola.all { it.isLetter() } -> parola.uppercase()
            else -> parola.lowercase().replaceFirstChar { it.uppercase() }
        }
    }
