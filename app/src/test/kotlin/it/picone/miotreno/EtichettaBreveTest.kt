package it.picone.miotreno

import it.picone.miotreno.domain.etichettaBreve
import org.junit.Assert.assertEquals
import org.junit.Test

class EtichettaBreveTest {
    @Test
    fun `le etichette lunghe si tagliano a quattro lettere`() {
        assertEquals("SOTT", etichettaBreve("Sotterranea"))
    }

    @Test
    fun `le etichette corte restano intere`() {
        assertEquals("SUSA", etichettaBreve("Susa"))
        assertEquals("NUOVA", etichettaBreve("Nuova"))
    }

    @Test
    fun `spazi di troppo non contano nella lunghezza`() {
        assertEquals("SUSA", etichettaBreve("  Susa  "))
    }

    @Test
    fun `una etichetta di piu parole si taglia comunque a quattro`() {
        assertEquals("PORT", etichettaBreve("Porta Nuova"))
    }

    @Test
    fun `stringa vuota resta vuota`() {
        assertEquals("", etichettaBreve(""))
    }
}
