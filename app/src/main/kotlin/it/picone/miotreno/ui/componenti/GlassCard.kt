package it.picone.miotreno.ui.componenti

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import it.picone.miotreno.ui.theme.Forme

/**
 * La superficie base dell'app: card M3 piena, senza ombra (le superfici tonali bastano a
 * separare). [accento] = la card "tua" (treno seguito): fondo secondaryContainer. [rilievo] = "la prossima": una superficie più alta, niente colore — i due stati
 * non devono sembrare uguali.
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
    val cs = MaterialTheme.colorScheme
    val colori = CardDefaults.cardColors(
        containerColor = when {
            accento -> cs.secondaryContainer
            rilievo -> cs.surfaceContainerHigh
            else -> cs.surfaceContainer
        },
    )
    // niente bordo: il fondo tonale basta, un contorno chiaro sul grigio sembrava una cornice
    val bordo: BorderStroke? = null
    val corpo: @Composable ColumnScope.() -> Unit = { Column(Modifier.padding(padding), content = content) }
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier, shape = forma, colors = colori, border = bordo, content = corpo)
    } else {
        Card(modifier = modifier, shape = forma, colors = colori, border = bordo, content = corpo)
    }
}
