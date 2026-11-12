package it.picone.miotreno.ui

import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import it.picone.miotreno.R
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.hypot

/** Una volta per processo: tornando all'app dal multitasking l'intro non si ripete. */
private var introMostrata = false

/**
 * Seguito animato dello splash di sistema. Il primo fotogramma è identico a quello
 * (stessi colori di res, cerchio di 160 dp, icona di 240 dp al centro), così il passaggio
 * non si vede; poi le rotaie si allungano verso chi guarda, sale il nome, e il cerchio
 * si allarga fino a coprire lo schermo e svanisce sull'app. Circa un secondo, mai di più:
 * l'app intanto carica sotto. Con "riduci movimento" non parte.
 */
@Composable
fun IntroAvvio() {
    var visibile by remember { mutableStateOf(!introMostrata && ValueAnimator.areAnimatorsEnabled()) }
    if (!visibile) return

    val sfondo = colorResource(R.color.mt_bg)
    val cerchio = colorResource(R.color.splash_cerchio)
    val treno = colorResource(R.color.splash_treno)

    val rotaie = remember { Animatable(0f) }
    val nome = remember { Animatable(0f) }
    val apertura = remember { Animatable(0f) }
    val dissolvenza = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        introMostrata = true
        coroutineScope {
            launch { rotaie.animateTo(1f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 220f)) }
            launch {
                delay(120)
                nome.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow))
            }
        }
        delay(150)
        launch { dissolvenza.animateTo(0f, tween(260, delayMillis = 220)) }
        apertura.animateTo(1f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 260f))
        visibile = false
    }

    BoxWithConstraints(
        Modifier.fillMaxSize().clearAndSetSemantics { }.graphicsLayer { alpha = dissolvenza.value }.background(sfondo),
        contentAlignment = Alignment.Center,
    ) {
        // quanto deve crescere il cerchio (160 dp) per coprire lo schermo fino agli angoli
        val copertura = hypot(maxWidth.value, maxHeight.value) / 160f

        // Il binario in prospettiva: parte stretto sotto il treno e si apre verso chi guarda,
        // con le traversine sempre più larghe e distanti. Si srotola dall'alto verso il basso.
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2
            val inizio = size.height / 2 + 72.dp.toPx()
            val altezza = size.height - inizio
            val stretta = 26.dp.toPx()
            val larga = size.width * 0.46f

            // prospettiva vera: rotaie dritte che convergono, larghezza lineare lungo il binario
            fun mezzo(t: Float) = stretta + (larga - stretta) * t
            fun y(t: Float) = inizio + altezza * t
            val fino = rotaie.value
            val colore = treno.copy(alpha = 0.5f)
            // traversine: più larghe, spesse e distanti man mano che si avvicinano a chi guarda
            var t = 0.05f
            while (t <= fino) {
                val m = mezzo(t) * 1.3f
                drawLine(colore.copy(alpha = 0.28f), Offset(cx - m, y(t)), Offset(cx + m, y(t)), (2f + 9f * t).dp.toPx(), StrokeCap.Round)
                t += 0.05f + 0.22f * t
            }
            if (fino > 0f) {
                listOf(-1f, 1f).forEach { lato ->
                    drawLine(
                        colore, Offset(cx + lato * mezzo(0f), y(0f)), Offset(cx + lato * mezzo(fino), y(fino)),
                        strokeWidth = (3f + 5f * fino).dp.toPx(), cap = StrokeCap.Round,
                    )
                }
            }
        }

        Text(
            "MioTreno",
            style = MaterialTheme.typography.displaySmall,
            color = treno,
            modifier = Modifier
                .offset(y = (-140).dp)
                .graphicsLayer {
                    alpha = nome.value * (1f - apertura.value)
                    translationY = (1f - nome.value) * 24.dp.toPx()
                },
        )

        Box(
            Modifier
                .size(160.dp)
                .graphicsLayer {
                    val s = 1f + (copertura - 1f) * apertura.value
                    scaleX = s
                    scaleY = s
                }
                .clip(CircleShape)
                .background(cerchio),
        )
        Image(
            painterResource(R.drawable.splash_icona),
            contentDescription = null,
            modifier = Modifier
                .size(240.dp)
                .graphicsLayer {
                    // il treno parte in avanti verso chi guarda mentre il cerchio si apre
                    val s = 1f + 0.35f * apertura.value
                    scaleX = s
                    scaleY = s
                    alpha = 1f - apertura.value
                },
        )
    }
}
