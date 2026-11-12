package it.picone.miotreno.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Angoli della scala M3 Expressive: card 24 (large increased), interni 16, pillole piene. */
object Forme {
    val card = RoundedCornerShape(24.dp)
    val cardPiccola = RoundedCornerShape(16.dp)
    val chip = RoundedCornerShape(percent = 50)
    val pillola = RoundedCornerShape(percent = 50)
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
