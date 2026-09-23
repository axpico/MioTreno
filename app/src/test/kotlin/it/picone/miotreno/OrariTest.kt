package it.picone.miotreno

import it.picone.miotreno.domain.FermataTreno
import it.picone.miotreno.domain.orario
import it.picone.miotreno.domain.orarioProiettato
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val MIN = 60_000L
private const val T = 1_700_000_000_000L

class OrariTest {
    private fun fermata(programmata: Long?, effettiva: Long?, passata: Boolean = false) =
        FermataTreno("S01700", "Milano Centrale", programmata, effettiva, passata)

    // Il bug: una fermata futura non ha effettiva, quindi mostrava l'orario di tabella
    // senza alcun segno del ritardo. Ora va proiettata.
    @Test
    fun `fermata futura proietta programmata piu ritardo del treno`() {
        val o = fermata(programmata = T, effettiva = null).orario(ritardoTreno = 12)

        assertEquals(T + 12 * MIN, o.previstoMs)
        assertEquals(T, o.programmataMs)
        assertEquals(12, o.ritardoMinuti)
        assertFalse(o.confermato)
        assertTrue(o.inRitardo)
    }

    @Test
    fun `fermata raggiunta usa l'effettiva ed e confermata`() {
        val o = fermata(programmata = T, effettiva = T + 9 * MIN).orario(ritardoTreno = 12)

        assertEquals(T + 9 * MIN, o.previstoMs)
        assertEquals(9, o.ritardoMinuti)
        assertTrue(o.confermato)
    }

    @Test
    fun `treno in orario non produce ritardo da mostrare`() {
        val o = fermata(programmata = T, effettiva = null).orario(ritardoTreno = 0)

        assertEquals(T, o.previstoMs)
        assertEquals(0, o.ritardoMinuti)
        assertFalse(o.inRitardo)
    }

    @Test
    fun `anticipo produce ritardo negativo`() {
        val o = fermata(programmata = T, effettiva = T - 3 * MIN).orario(ritardoTreno = 0)

        assertEquals(-3, o.ritardoMinuti)
        assertTrue(o.inRitardo)
    }

    @Test
    fun `senza programmata ne effettiva non si inventa nulla`() {
        val o = fermata(programmata = null, effettiva = null).orario(ritardoTreno = 12)

        assertNull(o.previstoMs)
        assertNull(o.ritardoMinuti)
        assertFalse(o.confermato)
        assertFalse(o.inRitardo)
    }

    // Una fermata già passata di cui il feed non ha registrato l'effettiva resta una stima,
    // non un dato: non va mai marcata confermata.
    @Test
    fun `fermata passata senza effettiva resta non confermata`() {
        val o = fermata(programmata = T, effettiva = null, passata = true).orario(ritardoTreno = 5)

        assertFalse(o.confermato)
        assertEquals(T + 5 * MIN, o.previstoMs)
    }

    @Test
    fun `orario sciolto si proietta come una fermata`() {
        val o = orarioProiettato(T, ritardoTreno = 7)

        assertEquals(T + 7 * MIN, o.previstoMs)
        assertEquals(7, o.ritardoMinuti)
        assertFalse(o.confermato)
    }

    @Test
    fun `orario sciolto nullo resta nullo`() {
        assertNull(orarioProiettato(null, ritardoTreno = 7).previstoMs)
    }
}
