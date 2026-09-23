package it.picone.miotreno.ui.componenti

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import it.picone.miotreno.ui.theme.Forme
import it.picone.miotreno.ui.theme.LocalTb
import it.picone.miotreno.ui.theme.Molla

/**
 * La superficie base di tutta l'app. Piatta: nessuna ombra decorativa, la separazione dal fondo
 * viene dal bordo sottile e dal colore di superficie, come un pannello reale, non un'elevazione
 * simulata.
 * [accento] = true dà il fondo e il bordo nel colore dell'accento: è la card "tua"
 * (treno seguito, CTA attiva). [rilievo] = true dà solo il bordo più marcato, senza il colore
 * dell'accento: per "questa è la prossima", non "questa è quella che segui" — i due stati non
 * devono sembrare uguali.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    forma: Shape = Forme.card,
    onClick: (() -> Unit)? = null,
    accento: Boolean = false,
    rilievo: Boolean = false,
    padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val tb = LocalTb.current
    val interaction = remember { MutableInteractionSource() }
    val premuto by interaction.collectIsPressedAsState()
    val scala by animateFloatAsState(if (premuto) 0.975f else 1f, Molla.ui(), label = "press")

    Column(
        modifier
            .scale(scala)
            .clip(forma)
            .background(if (accento) tb.sf2 else tb.sf)
            .border(1.dp, if (accento) tb.accento else if (rilievo) tb.bordoForte else tb.bordo, forma)
            .let { m ->
                if (onClick != null) m.clickable(interaction, indication = null, onClick = onClick) else m
            }
            .padding(padding),
        content = content,
    )
}
