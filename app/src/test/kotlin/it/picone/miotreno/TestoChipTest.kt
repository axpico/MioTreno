package it.picone.miotreno

import it.picone.miotreno.work.testoChip
import org.junit.Assert.assertEquals
import org.junit.Test

class TestoChipTest {
    @Test
    fun `prima della partenza conta i minuti`() = assertEquals("7′", testoChip(false, 7, 4))

    @Test
    fun `a bordo conta le fermate mancanti, non il ritardo`() = assertEquals("3 ferm.", testoChip(false, -5, 3))

    @Test
    fun `zero fermate mancanti e' arrivato`() = assertEquals("arrivato", testoChip(false, -40, 0))

    @Test
    fun `senza percorso dopo la partenza dice in viaggio`() = assertEquals("in viaggio", testoChip(false, -2, null))

    @Test
    fun `cancellato vince su tutto`() = assertEquals("canc.", testoChip(true, 5, 3))
}
