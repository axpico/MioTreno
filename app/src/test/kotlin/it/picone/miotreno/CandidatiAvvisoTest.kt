package it.picone.miotreno

import it.picone.miotreno.domain.ProssimoTreno
import it.picone.miotreno.domain.StatoTreno
import it.picone.miotreno.work.candidatiAvviso
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val ADESSO = 1_700_000_000_000L
private const val MIN = 60_000L

// partenzaFraMinuti e l orario di tabella: minutiAllaPartenza ci somma il ritardo
private fun treno(numero: Int, partenzaFraMinuti: Int, ritardo: Int = 0) = ProssimoTreno(
    numeroTreno = numero,
    categoria = "REG",
    destinazione = "Varese",
    codOrigine = "S01645",
    dataPartenzaTrenoMs = ADESSO,
    orarioPartenzaMs = ADESSO + partenzaFraMinuti * MIN,
    orarioArrivoBustoMs = ADESSO + 60 * MIN,
    ritardoMinuti = ritardo,
    binario = "2",
    binarioConfermato = true,
    stato = StatoTreno.Regolare,
)

class CandidatiAvvisoTest {
    @Test
    fun `i treni gia partiti non sono candidati`() {
        val treni = listOf(treno(1, partenzaFraMinuti = -5), treno(2, partenzaFraMinuti = 10))

        val c = candidatiAvviso(treni, seguitoNumero = null, adesso = ADESSO)

        assertEquals(listOf(2), c.map { it.numeroTreno })
    }

    // Il bug: la corsa seguita, una volta partita, usciva dai candidati e l'avviso passava
    // a un treno qualsiasi mentre l'utente era a bordo di un altro.
    @Test
    fun `la corsa seguita resta candidata anche dopo la partenza`() {
        val treni = listOf(treno(1, partenzaFraMinuti = -5), treno(2, partenzaFraMinuti = 10))

        val c = candidatiAvviso(treni, seguitoNumero = 1, adesso = ADESSO)

        assertTrue("il treno seguito deve restare", c.any { it.numeroTreno == 1 })
        assertEquals(2, c.size)
    }

    @Test
    fun `seguire un treno non fa entrare gli altri gia partiti`() {
        val treni = listOf(treno(1, partenzaFraMinuti = -5), treno(9, partenzaFraMinuti = -20))

        val c = candidatiAvviso(treni, seguitoNumero = 1, adesso = ADESSO)

        assertEquals(listOf(1), c.map { it.numeroTreno })
    }

    @Test
    fun `il ritardo sposta la partenza e puo tenere un treno fra i candidati`() {
        // programmato 2 minuti fa, ma con +10 parte fra 8 minuti
        val treni = listOf(treno(1, partenzaFraMinuti = -2, ritardo = 10))

        val c = candidatiAvviso(treni, seguitoNumero = null, adesso = ADESSO)

        assertEquals(listOf(1), c.map { it.numeroTreno })
    }

    @Test
    fun `lista vuota resta vuota`() {
        assertTrue(candidatiAvviso(emptyList(), seguitoNumero = 7, adesso = ADESSO).isEmpty())
    }
}
