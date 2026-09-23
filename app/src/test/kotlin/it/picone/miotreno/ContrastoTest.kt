package it.picone.miotreno

import it.picone.miotreno.ui.componenti.ALPHA_TINTA
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * WCAG 2 contrasto ≥ 4.5:1 (AA, testo normale) per ogni coppia testo/superficie usata nell'app.
 * I colori sono ripetuti qui come interi perché `Color` di Compose non gira in un test JVM puro.
 */
class ContrastoTest {
    private val bg = 0x000000
    private val sf = 0x141416
    private val sf2 = 0x1E1E21
    private val bianco = 0xFFF9F0
    private val tx = 0xEB / 255.0
    private val sub = 0x8C / 255.0
    private val ter = 0x7A / 255.0
    private val accento = 0xFFB300
    private val accento2 = 0xFFCC5C
    private val semaforo = listOf("verde" to 0x3ECF6E, "ambra" to 0xFFB300, "arancio" to 0xFF6B35, "rosso" to 0xFF3B30)
    private val sciopero = 0xFF4FD8
    private val onAccento = 0x1A1100

    private fun rgb(c: Int) = doubleArrayOf((c shr 16 and 0xFF).toDouble(), (c shr 8 and 0xFF).toDouble(), (c and 0xFF).toDouble())
    private fun blend(fg: DoubleArray, a: Double, bg: DoubleArray) = DoubleArray(3) { fg[it] * a + bg[it] * (1 - a) }
    private fun lin(c: Double) = (c / 255).let { if (it <= 0.03928) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
    private fun lum(c: DoubleArray) = 0.2126 * lin(c[0]) + 0.7152 * lin(c[1]) + 0.0722 * lin(c[2])
    private fun contrasto(a: DoubleArray, b: DoubleArray): Double {
        val (l1, l2) = listOf(lum(a), lum(b)).sortedDescending()
        return (l1 + 0.05) / (l2 + 0.05)
    }
    private fun aa(nome: String, testo: DoubleArray, sfondo: DoubleArray) {
        val c = contrasto(testo, sfondo)
        assertTrue("$nome: ${"%.2f".format(c)} < 4.5", c >= 4.5)
    }

    private val card = rgb(sf)
    private val superfici = listOf("bg" to rgb(bg), "card" to card, "sf2" to rgb(sf2))

    @Test
    fun `testo bianco a tre livelli su ogni superficie`() {
        for ((n, s) in superfici) {
            aa("tx su $n", blend(rgb(bianco), tx, s), s)
            aa("sub su $n", blend(rgb(bianco), sub, s), s)
            aa("ter su $n", blend(rgb(bianco), ter, s), s)
        }
    }

    @Test
    fun `colori di stato come testo su card e su badge tinto`() {
        for ((n, c) in semaforo + listOf("accento" to accento, "accento2" to accento2, "sciopero" to sciopero)) {
            aa("$n su card", rgb(c), card)
            aa("$n su tinta", rgb(c), blend(rgb(c), ALPHA_TINTA.toDouble(), card))
        }
    }

    @Test
    fun `badge pieno (cancellato) e bottoni pieni con testo scuro sul colore`() {
        for ((n, c) in semaforo + listOf("accento" to accento)) {
            aa("onAccento su $n", rgb(onAccento), rgb(c))
        }
    }
}
