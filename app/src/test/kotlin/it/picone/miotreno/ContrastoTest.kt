package it.picone.miotreno

import it.picone.miotreno.ui.componenti.ALPHA_TINTA
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * WCAG 2 contrasto ≥ 4.5:1 (AA, testo normale) per ogni coppia testo/superficie usata nell'app,
 * in entrambi i temi. Superfici e testo sono quelli dello schema dal seme (`SchemaSeme.kt`),
 * gli stati quelli fissi di `Colori.kt`; ripetuti come interi perché `Color` di Compose non gira
 * in un test JVM puro: se cambi quei file, cambia anche qui. L'accento è `primary` dello schema:
 * il suo contrasto lo garantisce Material.
 */
class ContrastoTest {
    private class Palette(
        val nome: String,
        val bg: Int,
        val sf: Int,
        val sf2: Int,
        val testo: List<Pair<String, Int>>,
        val stato: List<Pair<String, Int>>,
        val suStato: Int,
    )

    private val chiaro = Palette(
        "chiaro", bg = 0xFBF8FF, sf = 0xF4F2FF, sf2 = 0xE8E7F3,
        testo = listOf("onSurface" to 0x1A1B24, "onSurfaceVariant" to 0x444655),
        stato = listOf(
            "verde" to 0x146B3C, "ambra" to 0x7A5300,
            "arancio" to 0x9E340A, "rosso" to 0xAD2230, "sciopero" to 0x9E1F88,
        ),
        suStato = 0xFFFFFF,
    )
    private val scuro = Palette(
        "scuro", bg = 0x12131B, sf = 0x1A1B24, sf2 = 0x282933,
        testo = listOf("onSurface" to 0xE2E1EE, "onSurfaceVariant" to 0xC4C5D8),
        stato = listOf(
            "verde" to 0x3DD68C, "ambra" to 0xF5B83D,
            "arancio" to 0xFF8A4C, "rosso" to 0xFF7A7A, "sciopero" to 0xF472D0,
        ),
        suStato = 0x0B1020,
    )
    private val palette = listOf(chiaro, scuro)

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
    fun `testo su ogni superficie`() {
        for (p in palette) {
            for ((n, s) in p.superfici()) {
                for ((l, t) in p.testo) aa("${p.nome}: $l su $n", rgb(t), s)
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
