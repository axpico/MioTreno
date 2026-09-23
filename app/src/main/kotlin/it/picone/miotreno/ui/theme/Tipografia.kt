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
 * Recursive variabile: un solo file, due mood via l'asse MONO invece di due font separati.
 * MONO=0 per il corpo del testo (prosa leggibile, mano tecnica ma non da "board"), MONO=1 per
 * ogni cifra — orari, countdown, ritardi — così tabellone e testo restano nella stessa famiglia
 * senza il pairing "un font per i numeri, uno per il resto". Niente CASL (niente morbidezza
 * casual): direzione "Departure Board", non editoriale.
 */
@OptIn(ExperimentalTextApi::class)
private fun recursive(peso: Int, mono: Float = 0f) = Font(
    R.font.recursive,
    FontWeight(peso),
    variationSettings = FontVariation.Settings(
        FontVariation.weight(peso),
        FontVariation.Setting("MONO", mono),
        FontVariation.Setting("CASL", 0f),
    ),
)

val Recursive = FontFamily(recursive(400), recursive(500), recursive(700))
private val RecursiveMono = FontFamily(recursive(500, mono = 1f), recursive(700, mono = 1f))

val Regular = FontWeight(400)
val Medium = FontWeight(500)
val Bold = FontWeight(700)

/** Cifre tabulari: in lista gli orari devono allinearsi in colonna. */
private const val TABULARI = "tnum"

private fun stile(size: Int, peso: FontWeight, line: Int = size + 6, spacing: Float = 0f) = TextStyle(
    fontFamily = Recursive, fontWeight = peso, fontSize = size.sp, lineHeight = line.sp,
    letterSpacing = spacing.sp,
)

private fun numero(size: Int, line: Int = size + 4, spacing: Float = -0.5f) = TextStyle(
    fontFamily = RecursiveMono, fontWeight = Bold, fontSize = size.sp, lineHeight = line.sp,
    letterSpacing = spacing.sp, fontFeatureSettings = TABULARI,
)

/** Scala tipografica MioTreno: tre pesi, cifre sempre tabulari. */
object Testo {
    val display = numero(44, 48, -1.5f)
    val hero = numero(34, 38, -1f)
    val numeroGrande = numero(26, 30)
    val numero = numero(18, 22)
    val numeroPiccolo = numero(13, 16, 0f)
    val titolo = stile(22, Bold, 26, -0.3f)
    val sottotitolo = stile(16, Medium, 20)
    val corpo = stile(15, Regular, 21)
    val corpoMedio = stile(15, Medium, 21)
    val etichetta = stile(12, Medium, 16)
    val etichettaBold = stile(12, Bold, 16)
    val micro = stile(11, Medium, 14, 0.2f)
    val overline = stile(11, Bold, 14, 1.2f)
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
