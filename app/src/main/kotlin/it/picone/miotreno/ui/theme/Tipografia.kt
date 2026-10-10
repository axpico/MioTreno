package it.picone.miotreno.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import it.picone.miotreno.R

/**
 * Atkinson Hyperlegible, disegnato dal Braille Institute perché i caratteri che si somigliano
 * (1/l/I, 0/O, 5/S, 6/8) non si confondano mai: è quello che serve a chi legge binario, orario
 * e ritardo con un'occhiata, in banchina o in corsa. Next per il testo, Mono — stessa famiglia,
 * stesso disegno — per ogni cifra: orari, countdown e ritardi stanno in colonna senza "tnum".
 * Due file variabili da ~170 KB in tutto, al posto dei 2,4 MB di Recursive.
 *
 * ponytail: il primo (′) e le frecce (↗) non sono nel font e cadono sul font di sistema,
 * glifo per glifo. Se stonano, la via è disegnarli come icone, non cambiare font.
 */
@OptIn(ExperimentalTextApi::class)
private fun atkinson(res: Int, peso: Int) = Font(
    res,
    FontWeight(peso),
    variationSettings = FontVariation.Settings(FontVariation.weight(peso)),
)

val Atkinson = FontFamily(atkinson(R.font.atkinson_next, 500), atkinson(R.font.atkinson_next, 700))
private val AtkinsonMono = FontFamily(atkinson(R.font.atkinson_mono, 500), atkinson(R.font.atkinson_mono, 700))

val Medium = FontWeight(500)
val Bold = FontWeight(700)

private fun stile(size: Int, peso: FontWeight, line: Int = size + 6, spacing: Float = 0f) = TextStyle(
    fontFamily = Atkinson, fontWeight = peso, fontSize = size.sp, lineHeight = line.sp,
    letterSpacing = spacing.sp,
)

// Mono ha già cifre larghe e regolari: niente spaziatura negativa ai corpi piccoli, solo un
// filo di stretta sui numeri grandi dove i vuoti fra le cifre si notano.
private fun numero(size: Int, line: Int = size + 4, spacing: Float = 0f) = TextStyle(
    fontFamily = AtkinsonMono, fontWeight = Bold, fontSize = size.sp, lineHeight = line.sp,
    letterSpacing = spacing.sp,
)

/**
 * Scala tipografica MioTreno. Testo: quattro corpi (13, 15, 18, 24) e due pesi (500, 700).
 * Le cifre (orari, countdown, ritardi) sono in Mono, a larghezza fissa: sono
 * valori, e i valori devono pesare più delle etichette.
 */
object Testo {
    val display = numero(48, 52, -1f)
    val hero = numero(36, 40, -0.5f)
    val numeroGrande = numero(24, 28)
    val numero = numero(18, 22)
    val numeroPiccolo = numero(13, 16)
    val titolo = stile(24, Bold, 30, -0.4f)
    val sottotitolo = stile(18, Bold, 24, -0.2f)
    val corpo = stile(15, Medium, 22)
    val corpoMedio = stile(15, Bold, 22)
    val etichetta = stile(13, Medium, 18)
    val etichettaBold = stile(13, Bold, 18)
    val micro = stile(13, Medium, 16)
    val overline = stile(13, Bold, 18)
    val bottone = stile(15, Bold, 20)
}

/** Material Typography interamente sovrascritta, così nessun testo "di default" sfugge. */
val TipografiaTb = Typography(
    displayLarge = Testo.display, displayMedium = Testo.hero, displaySmall = Testo.numeroGrande,
    headlineLarge = Testo.titolo, headlineMedium = Testo.titolo, headlineSmall = Testo.sottotitolo,
    titleLarge = Testo.titolo, titleMedium = Testo.sottotitolo, titleSmall = Testo.corpoMedio,
    bodyLarge = Testo.corpo, bodyMedium = Testo.corpo, bodySmall = Testo.etichetta,
    labelLarge = Testo.bottone, labelMedium = Testo.etichetta, labelSmall = Testo.micro,
)
