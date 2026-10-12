package it.picone.miotreno

import it.picone.miotreno.domain.ProssimoTreno
import it.picone.miotreno.domain.StatoTreno
import it.picone.miotreno.domain.TrenoSeguito
import it.picone.miotreno.ui.listaHome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private fun treno(numero: Int, partenza: Long, stato: StatoTreno = StatoTreno.Regolare) = ProssimoTreno(
    numeroTreno = numero, categoria = "REG", destinazione = "X", codOrigine = "S0", dataPartenzaTrenoMs = 0,
    orarioPartenzaMs = partenza, orarioArrivoDestinazioneMs = partenza + 30, ritardoMinuti = 0,
    binario = null, binarioConfermato = false, stato = stato,
)

class ListaHomeTest {
    private val treni = (1..7).map { treno(it, it * 10L) }

    @Test
    fun `senza corsa seguita l'ordine e' quello degli orari e si vede tutto`() {
        val lista = listaHome(treni, seguito = null, mostraTutte = false)

        assertEquals(treni, lista.visibili)
        assertEquals(0, lista.nascoste)
        assertFalse(lista.seguendo)
        // la prima è la card grande, le altre righe di un unico gruppo
        assertEquals(listOf(false) + List(6) { true }, lista.compatte)
        assertTrue(lista.inizioGruppo(1))
        assertTrue(lista.fineGruppo(6))
    }

    @Test
    fun `la corsa seguita va in cima e delle altre se ne vedono tre`() {
        val lista = listaHome(treni, TrenoSeguito(5, "2026-10-13", 0), mostraTutte = false)

        assertEquals(listOf(5, 1, 2, 3), lista.visibili.map { it.numeroTreno })
        assertEquals(3, lista.nascoste)
        assertTrue(lista.seguendo)
        assertTrue(lista.fineGruppo(3))
    }

    @Test
    fun `mostra tutte toglie il limite`() {
        val lista = listaHome(treni, TrenoSeguito(5, "2026-10-13", 0), mostraTutte = true)

        assertEquals(7, lista.visibili.size)
        assertEquals(0, lista.nascoste)
    }

    @Test
    fun `senza seguito, se il primo e' cancellato la card grande passa al primo valido`() {
        val conCancellato = listOf(treno(1, 10, StatoTreno.Cancellato("x"))) + treni.drop(1)
        val lista = listaHome(conCancellato, seguito = null, mostraTutte = false)

        // il cancellato resta in cima ma non compatto, e anche il 2 (in evidenza) è una card
        assertEquals(listOf(false, false, true), lista.compatte.take(3))
    }
}
