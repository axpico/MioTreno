package it.picone.miotreno.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Angoli generosi e coerenti: card 20, elementi interni 16, pillole piene. */
object Forme {
    val card = RoundedCornerShape(20.dp)
    val cardPiccola = RoundedCornerShape(16.dp)
    val chip = RoundedCornerShape(percent = 50)
    val pillola = RoundedCornerShape(16.dp)
    val barra = RoundedCornerShape(28.dp)
    val foglio = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
}

/** Griglia 8pt (4 solo per gli scarti minimi). */
object Spazio {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val pagina = 16.dp
}

val FormeTb = Shapes(
    extraSmall = Forme.cardPiccola, small = Forme.cardPiccola, medium = Forme.cardPiccola,
    large = Forme.card, extraLarge = Forme.card,
)
