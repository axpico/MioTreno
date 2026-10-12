package it.picone.miotreno

import it.picone.miotreno.domain.DettaglioTreno
import it.picone.miotreno.domain.FermataTreno
import it.picone.miotreno.domain.ProssimoCambio
import it.picone.miotreno.domain.ProssimoTreno
import it.picone.miotreno.domain.StatoCambio
import it.picone.miotreno.domain.StatoCorsa
import it.picone.miotreno.domain.StatoTreno
import it.picone.miotreno.domain.Tono
import it.picone.miotreno.domain.Tratta
import it.picone.miotreno.domain.TrenoSeguito
import it.picone.miotreno.domain.aPiediFra
import it.picone.miotreno.domain.arrivoPrevisto
import it.picone.miotreno.domain.avvisoCambioDovuto
import it.picone.miotreno.domain.cambi
import it.picone.miotreno.domain.coincidenzaARischio
import it.picone.miotreno.domain.coincidenzaPersa
import it.picone.miotreno.domain.filtraDominati
import it.picone.miotreno.domain.indicePerOrario
import it.picone.miotreno.domain.indiceSalita
import it.picone.miotreno.domain.margineCambioMinuti
import it.picone.miotreno.domain.margineLiveMinuti
import it.picone.miotreno.domain.partenzaPrevista
import it.picone.miotreno.domain.primoCambio
import it.picone.miotreno.domain.ripianificato
import it.picone.miotreno.domain.statoCambio
import it.picone.miotreno.domain.statoCorsa
import it.picone.miotreno.domain.testoCambi
import it.picone.miotreno.domain.unoPerPrimoTreno
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val MIN = 60_000L

private fun tratta(numero: Int?, da: String, a: String, partenza: Long, arrivo: Long, ritardo: Int = 0) =
    Tratta(numero, if (numero == null) "A piedi" else "RE", da, a, partenza * MIN, arrivo * MIN, ritardoMinuti = ritardo)

private fun viaggio(
    numero: Int,
    partenza: Long,
    arrivo: Long,
    tratte: List<Tratta> = emptyList(),
    ritardo: Int = 0,
    stato: StatoTreno = StatoTreno.Regolare,
) =
    ProssimoTreno(
        numeroTreno = numero, categoria = "RE", destinazione = "X", codOrigine = "S0", dataPartenzaTrenoMs = 0,
        orarioPartenzaMs = partenza * MIN, orarioArrivoDestinazioneMs = arrivo * MIN, ritardoMinuti = ritardo,
        binario = null, binarioConfermato = false, stato = stato, tratte = tratte,
    )

class ViaggioTest {
    private val conCambio = viaggio(
        1, 0, 60,
        listOf(tratta(1, "A", "Verona", 0, 30), tratta(null, "Verona", "Verona P.", 30, 33), tratta(2, "Verona P.", "B", 40, 60)),
    )

    @Test
    fun `i tratti a piedi non contano come cambi`() {
        assertEquals(1, conCambio.cambi)
        assertEquals("1 cambio · Verona", conCambio.testoCambi())
        assertNull(viaggio(9, 0, 10).testoCambi())
    }

    @Test
    fun `il margine al cambio toglie ritardo e tratto a piedi`() {
        // arrivo 30 + 3 a piedi, riparte a 40: 7 minuti; con +6 ne resta 1, sotto la soglia
        assertEquals(7, conCambio.margineCambioMinuti())
        assertFalse(conCambio.coincidenzaARischio)
        assertTrue(conCambio.copy(ritardoMinuti = 6).coincidenzaARischio)
    }

    @Test
    fun `un viaggio peggiore su tutto si nasconde, la corsa seguita mai`() {
        val diretto = viaggio(5, 5, 50)
        val peggio = viaggio(1, 0, 60, conCambio.tratte)
        assertEquals(listOf(diretto), filtraDominati(listOf(diretto, peggio)))
        assertEquals(listOf(diretto, peggio), filtraDominati(listOf(diretto, peggio), seguito = 1))
        // un cancellato non nasconde niente
        val cancellato = diretto.copy(stato = StatoTreno.Cancellato("x"))
        assertEquals(2, filtraDominati(listOf(cancellato, peggio)).size)
        // più veloce ma con più cambi: restano entrambi
        val veloce = viaggio(7, 5, 40, conCambio.tratte)
        assertEquals(2, filtraDominati(listOf(diretto, veloce)).size)
    }

    @Test
    fun `stesso primo treno, resta il viaggio con meno cambi`() {
        val diretto = viaggio(1, 0, 70)
        assertEquals(listOf(diretto), unoPerPrimoTreno(listOf(conCambio, diretto)))
    }

    @Test
    fun `coincidenza persa se il treno dopo parte prima o e' cancellato`() {
        assertTrue(coincidenzaPersa(arrivoPrevistoMs = 41 * MIN, partenzaSuccessivaPrevistaMs = 40 * MIN, successivoCancellato = false))
        assertFalse(coincidenzaPersa(40 * MIN, 40 * MIN, successivoCancellato = false))
        assertTrue(coincidenzaPersa(null, null, successivoCancellato = true))
    }

    @Test
    fun `avviso cambio entro i minuti scelti, spento a zero`() {
        assertTrue(avvisoCambioDovuto(arrivoPrevistoMs = 10 * MIN, adesso = 5 * MIN, minutiPrima = 5))
        assertFalse(avvisoCambioDovuto(10 * MIN, 4 * MIN, 5))
        assertFalse(avvisoCambioDovuto(10 * MIN, 9 * MIN, 0))
    }

    @Test
    fun `salita e discesa di una tratta si trovano per orario di tabella`() {
        fun f(cod: String, arr: Long?, par: Long?) =
            FermataTreno(cod, cod, par ?: arr, null, false, arrivoProgrammatoMs = arr?.times(MIN), partenzaProgrammataMs = par?.times(MIN))
        val d = DettaglioTreno.Ok(
            fermate = listOf(f("S1", null, 0), f("S2", 9, 10), f("S3", 20, 21), f("S4", 30, null)),
            ritardoMinuti = 0, indiceCorrente = -1, indiceDestinazione = -1,
        )
        assertEquals(1, d.indicePerOrario(10 * MIN, arrivo = false))
        assertEquals(2, d.indicePerOrario(20 * MIN, arrivo = true))
        assertEquals(-1, d.indicePerOrario(15 * MIN, arrivo = true))
        // con la salita nota, i codici del cluster GPS non contano più
        assertEquals(1, d.copy(indicePartenza = 1).indiceSalita(listOf("S3")))
    }

    @Test
    fun `ripianificare tiene le tratte fatte e sostituisce il resto`() {
        val seguito = TrenoSeguito(1, "2026-10-13", 60 * MIN, tratte = conCambio.tratte, trattaCorrente = 0, avvisoCambioPer = 0)
        val alternativa = viaggio(3, 45, 80)

        val nuovo = seguito.ripianificato(conCambio.tratte.first(), alternativa)

        assertEquals(listOf(1, 3), nuovo.tratte.map { it.numeroTreno })
        assertEquals("Verona", nuovo.tratte[1].nomePartenza)
        assertEquals(80 * MIN, nuovo.arrivoDestinazioneMs)
        // il nuovo cambio avrà il suo avviso
        assertEquals(-1, nuovo.avvisoCambioPer)
    }

    @Test
    fun `il margine live usa i ritardi di entrambi i treni e colora il cambio`() {
        // arrivo 30 (+2), ripartenza 36 (+2), 1 minuto a piedi: 5 minuti, tutto ok
        val ok = margineLiveMinuti(32 * MIN, 38 * MIN, aPiediMs = 1 * MIN)
        assertEquals(5, ok)
        assertEquals(StatoCambio.Ok, statoCambio(ok))
        assertEquals(StatoCambio.ARischio, statoCambio(margineLiveMinuti(37 * MIN, 38 * MIN)))
        assertEquals(StatoCambio.Persa, statoCambio(margineLiveMinuti(40 * MIN, 38 * MIN)))
        assertNull(margineLiveMinuti(null, 38 * MIN))
    }

    @Test
    fun `senza andamento gli orari previsti sono tabella piu' ritardo noto`() {
        val t = tratta(1, "A", "B", 10, 30, ritardo = 3)
        assertEquals(33 * MIN, t.arrivoPrevisto(null))
        assertEquals(13 * MIN, t.partenzaPrevista(null))
        // il ritardo del primo treno arriva dal feed partenze, più fresco di quello salvato in tratta
        assertEquals(35 * MIN, t.arrivoPrevisto(null, ritardo = 5))
    }

    @Test
    fun `primo cambio e tratti a piedi fra due treni`() {
        val treni = conCambio.tratte.filter { it.suTreno }
        assertEquals("Verona", conCambio.primoCambio)
        assertNull(viaggio(9, 0, 10).primoCambio)
        assertEquals(listOf("Verona P."), aPiediFra(conCambio.tratte, treni[0], treni[1]).map { it.nomeArrivo })
    }

    @Test
    fun `il testo del cambio dice stazione e dettaglio, o la coincidenza persa`() {
        assertEquals("Cambio a Rho · SU 1 parte 19:19", ProssimoCambio("Rho", "SU 1 parte 19:19").testo)
        assertEquals("Coincidenza persa a Rho", ProssimoCambio("Rho", null, persa = true).testo)
    }

    @Test
    fun `la riga di stato dice cosa fare adesso`() {
        val treno = viaggio(1, 10, 40).copy(binario = "2")
        assertEquals("Vai al binario 2 · parte tra 6 min", statoCorsa(treno, null, "Busto", 4 * MIN).testo)
        assertEquals("Binario non ancora assegnato · parte tra 6 min", statoCorsa(treno.copy(binario = null), null, "Busto", 4 * MIN).testo)
        assertEquals(StatoCorsa("In partenza dal binario 2", Tono.Attenzione), statoCorsa(treno, null, "Busto", 9 * MIN))
        assertEquals("A bordo · scendi a Busto", statoCorsa(treno, null, "Busto", 20 * MIN).testo)
        assertEquals(
            "A bordo · scendi a Rho e cambia",
            statoCorsa(treno, ProssimoCambio("Rho", "SU 1 parte 19:19"), "Busto", 20 * MIN).testo,
        )
    }

    @Test
    fun `cancellato, coincidenza persa e a rischio sono allarmi o attenzioni`() {
        val treno = viaggio(1, 10, 40)
        assertEquals(Tono.Allarme, statoCorsa(treno.copy(stato = StatoTreno.Cancellato("x")), null, "B", 0).tono)
        assertEquals(
            StatoCorsa("Coincidenza persa a Rho", Tono.Allarme),
            statoCorsa(treno, ProssimoCambio("Rho", null, persa = true), "B", 20 * MIN),
        )
        val aRischio = conCambio.copy(ritardoMinuti = 6)
        assertEquals(StatoCorsa("Coincidenza a rischio a Verona", Tono.Attenzione), statoCorsa(aRischio, null, "B", -5 * MIN))
    }
}
