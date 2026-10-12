package it.picone.miotreno

import it.picone.miotreno.data.Andamento
import it.picone.miotreno.data.AndamentoTrenoDto
import it.picone.miotreno.data.JsonStore
import it.picone.miotreno.data.LeFrecceApi
import it.picone.miotreno.data.PartenzaArrivoDto
import it.picone.miotreno.data.RicercaStazioneDto
import it.picone.miotreno.data.RispostaSoluzioniDto
import it.picone.miotreno.data.ScioperiApi
import it.picone.miotreno.data.SoluzioneDto
import it.picone.miotreno.data.StazioneDto
import it.picone.miotreno.data.TrenoRepository
import it.picone.miotreno.data.ViaggiaTrenoApi
import it.picone.miotreno.data.connessioniLeFrecce
import it.picone.miotreno.data.connessioniViaHub
import it.picone.miotreno.data.hubCandidati
import it.picone.miotreno.data.idLeFrecce
import it.picone.miotreno.domain.cambi
import it.picone.miotreno.domain.testoCambi
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.time.OffsetDateTime
import java.util.Date

private val json = Json { ignoreUnknownKeys = true; isLenient = true }

private fun ms(iso: String) = OffsetDateTime.parse(iso).toInstant().toEpochMilli()

private const val MIN = 60_000L

/** Soluzioni reali LeFrecce, Milano Porta Garibaldi → Busto Arsizio, 13/10/2026 dalle 08:00. */
private fun soluzioniGaribaldiBusto(): List<SoluzioneDto> =
    json.decodeFromString<RispostaSoluzioniDto>(
        checkNotNull(object {}.javaClass.classLoader!!.getResourceAsStream("soluzioni_garibaldi_busto.json"))
            .bufferedReader().readText(),
    ).solutions.mapNotNull { it.solution }

private fun partenza(numero: Int, ora: String, codDestinazione: String? = null, ritardo: Int = 0) =
    PartenzaArrivoDto(
        numeroTreno = numero, categoria = "REG", orarioPartenza = ms(ora), codOrigine = "S01645",
        codDestinazione = codDestinazione, destinazione = "CAPOLINEA", ritardo = ritardo,
        binarioProgrammatoPartenzaDescrizione = "7",
    )

private fun arrivo(numero: Int, ora: String, codOrigine: String? = null) =
    PartenzaArrivoDto(numeroTreno = numero, orarioArrivo = ms(ora), codOrigine = codOrigine)

class LeFrecceTest {
    @Test
    fun `id LeFrecce e' 83 piu' le cifre del codice su sette posizioni`() {
        assertEquals("830001700", idLeFrecce("S01700"))
        assertEquals("830001645", idLeFrecce("S01645"))
    }

    @Test
    fun `dalle soluzioni reali restano solo i viaggi con cambi il cui primo treno parte da qui`() {
        val partenze = listOf(
            "S01645" to partenza(25302, "2026-10-13T08:05:00+02:00"),
            "S01647" to partenza(24602, "2026-10-13T08:19:00+02:00"),
            // diretti: nel feed, ma LeFrecce non li deve duplicare
            "S01645" to partenza(2516, "2026-10-13T08:32:00+02:00"),
        )
        val arriviDestinazione = listOf(24520, 2416, 2516).associateWith { arrivo(it, "2026-10-13T09:00:00+02:00") }

        val viaggi = connessioniLeFrecce(soluzioniGaribaldiBusto(), partenze, arriviDestinazione)

        assertEquals(listOf(25302, 24602), viaggi.map { it.numeroTreno })
        assertTrue(viaggi.all { it.cambi == 1 })
        assertEquals("1 cambio · Rho-Fiera Milano", viaggi.first().testoCambi())
    }

    @Test
    fun `il tratto a piedi dentro il cluster di partenza non e' un cambio`() {
        val partenze = listOf("S01647" to partenza(24602, "2026-10-13T08:19:00+02:00"))
        val arriviDestinazione = mapOf(2416 to arrivo(2416, "2026-10-13T08:56:00+02:00"))

        val viaggio = connessioniLeFrecce(soluzioniGaribaldiBusto(), partenze, arriviDestinazione).single()

        // UB Garibaldi → Passante tolto: due treni, un cambio, e si sale dalla sotterranea
        assertEquals(listOf(24602, 2416), viaggio.tratte.map { it.numeroTreno })
        assertEquals("S01647", viaggio.codPartenza)
        assertEquals(ms("2026-10-13T08:56:00.000+02:00"), viaggio.orarioArrivoDestinazioneMs)
    }

    @Test
    fun `il primo treno porta ritardo e binario dal feed partenze`() {
        val partenze = listOf("S01645" to partenza(25302, "2026-10-13T08:05:00+02:00", ritardo = 4))
        val viaggio = connessioniLeFrecce(soluzioniGaribaldiBusto(), partenze, emptyMap()).single()

        assertEquals(4, viaggio.ritardoMinuti)
        assertEquals(4, viaggio.tratte.first().ritardoMinuti)
        assertEquals("7", viaggio.tratte.first().binario)
        // il codice d'origine del secondo treno (per andamentoTreno) viene da bdoOrigin
        assertEquals("S01708", viaggio.tratte[1].codOrigine)
    }
}

class HubTest {
    private val t0 = ms("2026-10-13T08:00:00+02:00")

    private fun p(numero: Int, partenza: Long, hub: String? = null) =
        PartenzaArrivoDto(numeroTreno = numero, orarioPartenza = partenza, codDestinazione = hub, destinazione = "HUB")

    @Test
    fun `gli scali sono i capolinea da qui che sono origine verso la destinazione`() {
        val partenze = listOf("A" to p(1, t0, "H"), "A" to p(2, t0, "H"), "A" to p(3, t0, "X"), "A" to p(4, t0, "D"))
        val arrivi = listOf(
            arrivo(9, "2026-10-13T09:00:00+02:00", codOrigine = "H"),
            arrivo(8, "2026-10-13T09:00:00+02:00", codOrigine = "D"),
        )

        // D è la destinazione stessa: un treno che ci finisce è un diretto, non uno scalo
        assertEquals(listOf("H"), hubCandidati(partenze, arrivi, esclusi = setOf("A", "D")))
    }

    @Test
    fun `serve un cambio di almeno cinque minuti e vince il secondo treno che arriva prima`() {
        val partenze = listOf("A" to p(1, t0, "H"))
        val arriviHub = listOf(PartenzaArrivoDto(numeroTreno = 1, orarioArrivo = t0 + 30 * MIN))
        val partenzeHub = listOf(
            p(10, t0 + 33 * MIN), // 3 minuti: troppo stretto
            p(11, t0 + 40 * MIN),
            p(12, t0 + 45 * MIN),
        )
        val arriviDestinazione = mapOf(
            10 to PartenzaArrivoDto(numeroTreno = 10, orarioArrivo = t0 + 60 * MIN),
            11 to PartenzaArrivoDto(numeroTreno = 11, orarioArrivo = t0 + 90 * MIN),
            12 to PartenzaArrivoDto(numeroTreno = 12, orarioArrivo = t0 + 80 * MIN),
        )

        val viaggio = connessioniViaHub("H", partenze, arriviHub, partenzeHub, arriviDestinazione, "Partenza", "Arrivo").single()

        assertEquals(listOf(1, 12), viaggio.tratte.map { it.numeroTreno })
        assertEquals(t0 + 80 * MIN, viaggio.orarioArrivoDestinazioneMs)
        assertEquals("H", viaggio.tratte[1].codPartenza)
        assertEquals("Arrivo", viaggio.tratte[1].nomeArrivo)
    }
}

/** Fake per l'orchestrazione: un elenco fisso per stazione, a qualsiasi ora. */
private class FakeApi(
    private val partenze: Map<String, List<PartenzaArrivoDto>>,
    private val arrivi: Map<String, List<PartenzaArrivoDto>>,
) : ViaggiaTrenoApi {
    override suspend fun partenze(codStazione: String, quando: Date) = partenze[codStazione].orEmpty()
    override suspend fun arrivi(codStazione: String, quando: Date) = arrivi[codStazione].orEmpty()
    override suspend fun andamentoTreno(codOrigine: String, numeroTreno: Int, partenzaMs: Long): Andamento =
        Andamento.Ok(AndamentoTrenoDto())
    override suspend fun elencoStazioni(idRegione: Int): List<StazioneDto> = emptyList()
    override suspend fun cercaStazione(prefisso: String): List<RicercaStazioneDto> = emptyList()
    override suspend fun regione(codStazione: String): Int? = null
    override suspend fun dettaglioStazione(codStazione: String, idRegione: Int): StazioneDto? = null
}

class ProssimiViaggiTest {
    private val t0 = ms("2026-10-13T08:00:00+02:00")

    /** Diretto 1 (A→D, 08:10–09:30) e, via scalo H, 2 (A→H 08:05–08:30) + 20 (H 08:40 → D 09:10). */
    private val api = FakeApi(
        partenze = mapOf(
            "A" to listOf(
                PartenzaArrivoDto(numeroTreno = 1, orarioPartenza = t0 + 10 * MIN, codDestinazione = "D"),
                PartenzaArrivoDto(numeroTreno = 2, orarioPartenza = t0 + 5 * MIN, codDestinazione = "H", destinazione = "HUB"),
            ),
            "H" to listOf(PartenzaArrivoDto(numeroTreno = 20, orarioPartenza = t0 + 40 * MIN)),
        ),
        arrivi = mapOf(
            "D" to listOf(
                PartenzaArrivoDto(numeroTreno = 1, orarioArrivo = t0 + 90 * MIN),
                PartenzaArrivoDto(numeroTreno = 20, orarioArrivo = t0 + 70 * MIN, codOrigine = "H"),
            ),
            "H" to listOf(PartenzaArrivoDto(numeroTreno = 2, orarioArrivo = t0 + 30 * MIN)),
        ),
    )

    private fun repo(leFrecce: LeFrecceApi?, conCambi: Boolean = true) = TrenoRepository(
        api = api,
        scioperiApi = ScioperiApi(OkHttpClient()),
        store = JsonStore(Files.createTempDirectory("miotreno-test").toFile()),
        leFrecce = leFrecce,
        conCambi = { conCambi },
    )

    private val leFrecceGiu = object : LeFrecceApi {
        override suspend fun soluzioni(codPartenza: String, codArrivo: String, quando: Date): List<SoluzioneDto> =
            throw java.io.IOException("giù")
    }

    @Test
    fun `se LeFrecce non risponde i cambi arrivano da ViaggiaTreno accanto ai diretti`() = runBlocking {
        val viaggi = repo(leFrecceGiu).prossimiTreni(listOf("A"), "D", Date(t0))

        assertEquals(listOf(2, 1), viaggi.map { it.numeroTreno })
        assertEquals(listOf(1, 0), viaggi.map { it.cambi })
    }

    @Test
    fun `con l'interruttore spento restano solo i diretti`() = runBlocking {
        val viaggi = repo(leFrecceGiu, conCambi = false).prossimiTreni(listOf("A"), "D", Date(t0))

        assertEquals(listOf(1), viaggi.map { it.numeroTreno })
    }

    @Test
    fun `un viaggio con cambi che parte prima e arriva dopo un diretto si nasconde`() = runBlocking {
        // stesso scalo, ma il secondo treno arriva alle 09:40: peggio del diretto delle 09:30
        val lento = FakeApi(
            partenze = mapOf(
                "A" to listOf(
                    PartenzaArrivoDto(numeroTreno = 1, orarioPartenza = t0 + 10 * MIN, codDestinazione = "D"),
                    PartenzaArrivoDto(numeroTreno = 2, orarioPartenza = t0 + 5 * MIN, codDestinazione = "H", destinazione = "HUB"),
                ),
                "H" to listOf(PartenzaArrivoDto(numeroTreno = 20, orarioPartenza = t0 + 40 * MIN)),
            ),
            arrivi = mapOf(
                "D" to listOf(
                    PartenzaArrivoDto(numeroTreno = 1, orarioArrivo = t0 + 90 * MIN),
                    PartenzaArrivoDto(numeroTreno = 20, orarioArrivo = t0 + 100 * MIN, codOrigine = "H"),
                ),
                "H" to listOf(PartenzaArrivoDto(numeroTreno = 2, orarioArrivo = t0 + 30 * MIN)),
            ),
        )
        val repo = TrenoRepository(
            lento, ScioperiApi(OkHttpClient()), JsonStore(Files.createTempDirectory("miotreno-test").toFile()),
            leFrecce = leFrecceGiu, conCambi = { true },
        )

        assertEquals(listOf(1), repo.prossimiTreni(listOf("A"), "D", Date(t0)).map { it.numeroTreno })
    }
}
