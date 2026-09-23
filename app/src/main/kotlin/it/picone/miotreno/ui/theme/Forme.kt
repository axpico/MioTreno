package it.picone.miotreno.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Angoli quasi vivi: un tabellone partenze non ha bordi morbidi. La gerarchia viene dalla
 * tipografia e dai bordi sottili, non da raggi generosi — coerente con [GlassCard] che non
 * proietta più un'ombra decorativa.
 */
object Forme {
    val card = RoundedCornerShape(4.dp)
    val cardPiccola = RoundedCornerShape(3.dp)
    val chip = RoundedCornerShape(2.dp)
    val pillola = RoundedCornerShape(4.dp)
    val barra = RoundedCornerShape(4.dp)
}

/** Griglia 4dp. */
object Spazio {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 20.dp
    val xxl = 28.dp
    val pagina = 20.dp
}

val FormeTb = Shapes(
    extraSmall = Forme.chip, small = Forme.chip, medium = Forme.cardPiccola,
    large = Forme.card, extraLarge = Forme.card,
)
