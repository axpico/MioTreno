package it.picone.miotreno.ui.componenti

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import it.picone.miotreno.ui.theme.LocalTb
import it.picone.miotreno.ui.theme.Molla

/**
 * Pull-to-refresh con indicatore proprio: un arco nel colore accento che si chiude man mano
 * che si tira, e ruota mentre carica. Niente spinner Material.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PullRefresh(
    caricamento: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val state = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = caricamento,
        onRefresh = onRefresh,
        state = state,
        modifier = modifier,
        indicator = { Arco(state, caricamento, Modifier.align(Alignment.TopCenter)) },
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Arco(state: PullToRefreshState, caricamento: Boolean, modifier: Modifier) {
    val tb = LocalTb.current
    val frazione = state.distanceFraction.coerceIn(0f, 1.3f)
    val rotazione by rememberInfiniteTransition(label = "spin").animateFloat(
        0f, 360f, infiniteRepeatable(tween(900)), label = "rot",
    )
    if (frazione <= 0f && !caricamento) return
    Box(
        modifier
            .padding(top = 12.dp)
            .graphicsLayer {
                translationY = 80.dp.toPx() * frazione.coerceAtMost(1f)
                alpha = frazione.coerceAtMost(1f)
                rotationZ = if (Molla.riduci) 0f else if (caricamento) rotazione else frazione * 200f
            }
            .size(34.dp),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = Stroke(3.dp.toPx(), cap = StrokeCap.Round)
            drawCircle(tb.sf2, style = Stroke(3.dp.toPx()))
            drawArc(
                tb.accento, startAngle = -90f,
                sweepAngle = if (caricamento) 270f else 300f * frazione.coerceAtMost(1f),
                useCenter = false, style = stroke,
                topLeft = Offset.Zero,
            )
        }
    }
}
