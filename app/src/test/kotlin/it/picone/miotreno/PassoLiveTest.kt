package it.picone.miotreno

import it.picone.miotreno.work.passoLive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PassoLiveTest {
    @Test
    fun `sotto i cinque minuti si aggiorna al passo minimo`() {
        assertEquals(30_000L, passoLive(0))
        assertEquals(30_000L, passoLive(5))
    }

    @Test
    fun `lontano dall'evento si aggiorna al passo massimo`() {
        assertEquals(5 * 60_000L, passoLive(30))
        assertEquals(5 * 60_000L, passoLive(120))
    }

    @Test
    fun `fra i due estremi il passo cresce senza uscire dai limiti`() {
        val passi = (0..40).map { passoLive(it) }

        assertTrue("mai sotto il minimo", passi.all { it >= 30_000L })
        assertTrue("mai sopra il massimo", passi.all { it <= 5 * 60_000L })
    }

    @Test
    fun `il passo non diminuisce mai allontanandosi dall'evento`() {
        val passi = (0..60).map { passoLive(it) }

        assertEquals(passi.sorted(), passi)
    }

    // Un treno molto in anticipo non deve far esplodere il calcolo in overflow.
    @Test
    fun `un evento lontanissimo resta al passo massimo`() {
        assertEquals(5 * 60_000L, passoLive(Int.MAX_VALUE))
    }
}
