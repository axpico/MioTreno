package it.picone.miotreno.ui.componenti

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import it.picone.miotreno.domain.Semaforo
import it.picone.miotreno.ui.theme.Forme
import it.picone.miotreno.ui.theme.LocalTb
import it.picone.miotreno.ui.theme.Molla
import it.picone.miotreno.ui.theme.TbColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ORA = DateTimeFormatter.ofPattern("HH:mm")

fun Long.comeOra(zona: ZoneId = ZoneId.systemDefault()): String =
    ORA.format(Instant.ofEpochMilli(this).atZone(zona))

fun distanzaLeggibile(metri: Int): String =
    if (metri < 1000) "$metri m" else String.format(Locale.ITALY, "%.1f km", metri / 1000.0)

/** Alpha delle superfici "tinte" (badge, chip piene): sopra 0.12 il testo dello stesso colore scende sotto AA. */
const val ALPHA_TINTA = 0.12f

/** Colore pieno del semaforo. */
fun TbColors.colore(s: Semaforo): Color = when (s) {
    Semaforo.InOrario -> verde
    Semaforo.RitardoLieve -> ambra
    Semaforo.RitardoGrave -> arancio
    Semaforo.Irregolare -> ambra
    Semaforo.Cancellato -> rosso
}

/** Superficie piatta: fondo pieno, bordo 1dp a tinta unita. Niente vetro, niente ombra Material. */
fun Modifier.vetro(tb: TbColors, forma: Shape = Forme.card, sfondo: Color = tb.sf): Modifier =
    this.clip(forma).background(sfondo).border(1.dp, tb.bordo, forma)

/**
 * Placeholder di caricamento: base [tb.sf2] più una banda chiara che lo attraversa in diagonale.
 * [ritardoMs] sfalsa l'inizio così più box in fila non lampeggiano in sincrono, ma in onda.
 */
fun Modifier.shimmer(tb: TbColors, ritardoMs: Int = 0): Modifier = composed {
    if (Molla.riduci) return@composed background(tb.sf2)
    val transizione = rememberInfiniteTransition(label = "shimmer")
    val progresso by transizione.animateFloat(
        -0.4f, 1.4f,
        infiniteRepeatable(tween(1100, delayMillis = ritardoMs, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmer",
    )
    drawWithCache {
        val banda = size.width * 0.35f
        val centro = progresso * size.width
        val brush = Brush.linearGradient(
            colors = listOf(tb.sf2, Color.White.copy(alpha = 0.06f), tb.sf2),
            start = Offset(centro - banda, 0f),
            end = Offset(centro + banda, size.height),
        )
        onDrawBehind { drawRect(brush) }
    }
}

/** Haptic per le conferme: selezione treno, refresh, notifica, sciopero. */
@Composable
fun rememberHaptic(): HapticFeedback = LocalHapticFeedback.current

fun HapticFeedback.conferma() = performHapticFeedback(HapticFeedbackType.Confirm)
fun HapticFeedback.tocco() = performHapticFeedback(HapticFeedbackType.ContextClick)

