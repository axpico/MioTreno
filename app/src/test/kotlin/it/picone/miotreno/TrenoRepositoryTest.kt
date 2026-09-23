package it.picone.miotreno

import it.picone.miotreno.data.Andamento
import it.picone.miotreno.data.PartenzaArrivoDto
import it.picone.miotreno.data.RicercaStazioneDto
import it.picone.miotreno.data.ScioperiApi
import it.picone.miotreno.data.JsonStore
import it.picone.miotreno.data.StazioneDto
import it.picone.miotreno.data.TrenoRepository
import it.picone.miotreno.data.ViaggiaTrenoApi
import it.picone.miotreno.domain.TrenoSeguito
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files
import java.util.Date

/** Destinazione di prova: al posto del vecchio BUSTO_ARSIZIO_FS hardcoded. */
private const val DESTINAZIONE_TEST = "S09876"

/**
 * Fake in memoria degli endpoint ViaggiaTreno: per stazione, una lista fissa di
 * partenze/arrivi, indipendente dall'orario richiesto. Basta per esercitare l'orchestrazione
 * di [TrenoRepository] (finestre multiple, merge, dedup) senza rete.
 */
private class FakeViaggiaTrenoApi(
    private val partenzePerStazione: Map<String, List<PartenzaArrivoDto>> = emptyMap(),
    private val arriviPerStazione: Map<String, List<PartenzaArrivoDto>> = emptyMap(),
    private val stazioni: List<StazioneDto> = emptyList(),
) : ViaggiaTrenoApi {
    override suspend fun partenze(codStazione: String, quando: Date) = partenzePerStazione[codStazione].orEmpty()
    override suspend fun arrivi(codStazione: String, quando: Date) = arriviPerStazione[codStazione].orEmpty()
    override suspend fun andamentoTreno(codOrigine: String, numeroTreno: Int, partenzaMs: Long) =
        Andamento.DatiNonDisponibili
    override suspend fun elencoStazioni(idRegione: Int) = stazioni
    override suspend fun cercaStazione(prefisso: String): List<RicercaStazioneDto> = emptyList()
    override suspend fun regione(codStazione: String): Int? = null
    override suspend fun dettaglioStazione(codStazione: String, idRegione: Int): StazioneDto? = null
}

private fun repository(api: ViaggiaTrenoApi) = TrenoRepository(
    api = api,
    scioperiApi = ScioperiApi(OkHttpClient()),
    store = JsonStore(Files.createTempDirectory("miotreno-test").toFile()),
)

class ProssimiTreniTest {

    @Test
    fun `un treno che compare in partenze e arrivi e' diretto`() = runBlocking {
        val t0 = 10_000_000L
        val repo = repository(
            FakeViaggiaTrenoApi(
                partenzePerStazione = mapOf(
                    "S09999" to listOf(PartenzaArrivoDto(numeroTreno = 11, orarioPartenza = t0)),
                ),
                arriviPerStazione = mapOf(
                    DESTINAZIONE_TEST to listOf(PartenzaArrivoDto(numeroTreno = 11, orarioArrivo = t0 + 1_000)),
                ),
            ),
        )
        val treni = repo.prossimiTreni("S09999", DESTINAZIONE_TEST, Date(t0))
        assertEquals(listOf(11), treni.map { it.numeroTreno })
    }

    /** Stesso numero nella direzione opposta (arrivo prima della partenza): va scartato. */
    @Test
    fun `la direzione sbagliata viene scartata attraverso il repository`() = runBlocking {
        val t0 = 10_000_000L
        val repo = repository(
            FakeViaggiaTrenoApi(
                partenzePerStazione = mapOf(
                    "S09999" to listOf(
                        PartenzaArrivoDto(numeroTreno = 10, orarioPartenza = t0),
                        PartenzaArrivoDto(numeroTreno = 11, orarioPartenza = t0),
                    ),
                ),
                arriviPerStazione = mapOf(
                    DESTINAZIONE_TEST to listOf(
                        PartenzaArrivoDto(numeroTreno = 10, orarioArrivo = t0 - 1_000), // opposta
                        PartenzaArrivoDto(numeroTreno = 11, orarioArrivo = t0 + 1_000), // diretto
                    ),
                ),
            ),
        )
        val treni = repo.prossimiTreni("S09999", DESTINAZIONE_TEST, Date(t0))
        assertEquals(listOf(11), treni.map { it.numeroTreno })
    }

    /**
     * La corsa seguita è già partita e non compare più nel feed partenze: il fallback su
     * [it.picone.miotreno.domain.comeProssimoTreno] deve comunque restituirla.
     */
    @Test
    fun `la corsa seguita sparita dal feed resta in lista via fallback`() = runBlocking {
        val t0 = 10_000_000L
        val repo = repository(FakeViaggiaTrenoApi())
        val seguito = TrenoSeguito(
            numeroTreno = 42, data = "2026-09-16", arrivoDestinazioneMs = t0 + 5_000,
            codOrigine = "S09999", dataPartenzaTrenoMs = t0, orarioPartenzaMs = t0,
        )
        val treni = repo.prossimiTreni(listOf("S09999"), DESTINAZIONE_TEST, Date(t0), seguito = seguito)
        assertEquals(listOf(42), treni.map { it.numeroTreno })
    }
}
