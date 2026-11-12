package it.picone.miotreno

import it.picone.miotreno.data.ordinaPerPartenzaPrevista
import it.picone.miotreno.domain.ProssimoTreno
import it.picone.miotreno.domain.StatoTreno
import org.junit.Assert.assertEquals
import org.junit.Test

class OrdineTest {
    private fun treno(numero: Int, partenzaMin: Long, ritardo: Int) = ProssimoTreno(
        numeroTreno = numero, categoria = "REG", destinazione = "X", codOrigine = "S0", dataPartenzaTrenoMs = 0,
        orarioPartenzaMs = partenzaMin * 60_000L, orarioArrivoDestinazioneMs = (partenzaMin + 30) * 60_000L,
        ritardoMinuti = ritardo, binario = null, binarioConfermato = false, stato = StatoTreno.Regolare,
    )

    @Test
    fun `la lista segue la partenza prevista, non la tabella`() {
        // 17:53 con +9 parte alle 18:02, dopo il 17:58 puntuale
        val inRitardo = treno(10235, 17 * 60 + 53, 9)
        val puntuale = treno(2535, 17 * 60 + 58, 0)
        assertEquals(listOf(2535, 10235), ordinaPerPartenzaPrevista(listOf(inRitardo, puntuale)).map { it.numeroTreno })
    }
}
