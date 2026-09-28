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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import it.picone.miotreno.ui.theme.Forme
import it.picone.miotreno.ui.theme.LocalTb
import it.picone.miotreno.ui.theme.Molla
import it.picone.miotreno.ui.theme.TbColors

/**
 * La superficie base di tutta l'app. In chiaro: card bianca con ombra morbida tinta di navy,
 * senza bordo. In scuro l'ombra non si vede: la separazione la fa un bordo sottile.
 * [accento] = true è la card "tua" (treno seguito): fondo tinto e bordo nel colore accento.
 * [rilievo] = true è "la prossima": solo più profondità, niente colore — i due stati non
 * devono sembrare uguali.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    forma: Shape = Forme.card,
    onClick: (() -> Unit)? = null,
    accento: Boolean = false,
    rilievo: Boolean = false,
    padding: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val tb = LocalTb.current
    val interaction = remember { MutableInteractionSource() }
    val premuto by interaction.collectIsPressedAsState()
    val scala by animateFloatAsState(if (premuto) 0.98f else 1f, Molla.ui(), label = "press")

    Column(
        modifier
            .scale(scala)
            .ombraMorbida(tb, forma, if (rilievo) 16.dp else 8.dp)
            .clip(forma)
            .background(tb.sf)
            .let { if (accento) it.background(tb.accentoSoft) else it }
            .let {
                when {
                    accento -> it.border(1.dp, tb.accento.copy(alpha = 0.4f), forma)
                    tb.scuro -> it.border(1.dp, if (rilievo) tb.bordoForte else tb.bordo, forma)
                    else -> it
                }
            }
            .let { m ->
                if (onClick != null) m.clickable(interaction, indication = null, onClick = onClick) else m
            }
            .padding(padding),
        content = content,
    )
}

/** Ombra morbida tinta del colore [TbColors.ombra]; in scuro non si disegna (invisibile, e costa). */
fun Modifier.ombraMorbida(tb: TbColors, forma: Shape, elevazione: Dp = 8.dp, colore: Color = tb.ombra): Modifier =
    if (tb.scuro) {
        this
    } else {
        shadow(elevazione, forma, ambientColor = colore.copy(alpha = 0.10f), spotColor = colore.copy(alpha = 0.16f))
    }
