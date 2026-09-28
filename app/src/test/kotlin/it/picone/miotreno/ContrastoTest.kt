package it.picone.miotreno

import it.picone.miotreno.ui.componenti.ALPHA_TINTA
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * WCAG 2 contrasto ≥ 4.5:1 (AA, testo normale) per ogni coppia testo/superficie usata nell'app,
 * in entrambi i temi. I colori sono ripetuti qui come interi perché `Color` di Compose non gira
 * in un test JVM puro: se cambi `Colori.kt`, cambia anche qui.
 */
class ContrastoTest {
    private class Palette(
        val nome: String,
        val bg: Int,
        val sf: Int,
        val sf2: Int,
        val testo: Int,
        val stato: List<Pair<String, Int>>,
        val suStato: Int,
    )

    private val chiaro = Palette(
        "chiaro", bg = 0xF5F7FB, sf = 0xFFFFFF, sf2 = 0xEEF2F8, testo = 0x0E1526,
        stato = listOf(
            "accento" to 0x2448E0, "accento2" to 0x1F3DB8, "verde" to 0x146B3C, "ambra" to 0x7A5300,
            "arancio" to 0xA6380C, "rosso" to 0xB82530, "sciopero" to 0x9E1F88,
        ),
        suStato = 0xFFFFFF,
    )
    private val scuro = Palette(
        "scuro", bg = 0x0B1020, sf = 0x151B2E, sf2 = 0x1D2540, testo = 0xEEF2FF,
        stato = listOf(
            "accento" to 0x7B96FF, "accento2" to 0x9DB0FF, "verde" to 0x3DD68C, "ambra" to 0xF5B83D,
            "arancio" to 0xFF8A4C, "rosso" to 0xFF6B6B, "sciopero" to 0xF472D0,
        ),
        suStato = 0x0B1020,
    )
    private val palette = listOf(chiaro, scuro)

    /** Opacità di tx, sub, ter in `Colori.kt`. */
    private val livelli = listOf("tx" to 1.0, "sub" to 0xB8 / 255.0, "ter" to 0x99 / 255.0)

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

    private fun Palette.superfici() = listOf("bg" to rgb(bg), "sf" to rgb(sf), "sf2" to rgb(sf2))

    @Test
    fun `testo a tre livelli su ogni superficie`() {
        for (p in palette) {
            for ((n, s) in p.superfici()) {
                for ((l, a) in livelli) aa("${p.nome}: $l su $n", blend(rgb(p.testo), a, s), s)
            }
        }
    }

    @Test
    fun `colori di stato come testo su superficie e su badge tinto`() {
        for (p in palette) {
            for ((n, c) in p.stato) {
                for ((sn, s) in p.superfici()) {
                    aa("${p.nome}: $n su $sn", rgb(c), s)
                    aa("${p.nome}: $n su tinta/$sn", rgb(c), blend(rgb(c), ALPHA_TINTA.toDouble(), s))
                }
            }
        }
    }

    @Test
    fun `testo su colore pieno (bottone primario, badge cancellato)`() {
        for (p in palette) {
            for ((n, c) in p.stato) aa("${p.nome}: suStato su $n", rgb(p.suStato), rgb(c))
        }
    }
}
