package it.picone.miotreno.ui.theme

import androidx.compose.foundation.LocalIndication
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import android.app.Activity

/**
 * Material 3 usato come infrastruttura, non come estetica: ColorScheme, Typography e Shapes
 * sono tutti sovrascritti. Nessun colore o forma di default può arrivare all'utente.
 *
 * Solo tema scuro, sempre: un tabellone partenze non ha una modalità chiara, e leggere
 * [isSystemInDarkTheme] qui significherebbe reintrodurre un secondo tema che nessuno schermo
 * dell'app è disegnato per reggere.
 */
@Composable
fun MioTrenoTheme(content: @Composable () -> Unit) {
    val tb = ScuroTb
    val scheme = darkColorScheme(
        primary = tb.accento, onPrimary = tb.onAccento,
        primaryContainer = tb.accentoSoft, onPrimaryContainer = tb.tx,
        secondary = tb.accento2, onSecondary = tb.onAccento,
        secondaryContainer = tb.accentoSoft, onSecondaryContainer = tb.tx,
        tertiary = tb.verde, onTertiary = tb.onAccento,
        background = tb.bg, onBackground = tb.tx,
        surface = tb.sf, onSurface = tb.tx,
        surfaceVariant = tb.sf2, onSurfaceVariant = tb.sub,
        surfaceContainer = tb.sf, surfaceContainerHigh = tb.sf2, surfaceContainerHighest = tb.sf2,
        surfaceContainerLow = tb.sf, surfaceContainerLowest = tb.bg,
        outline = tb.bordoForte, outlineVariant = tb.bordo,
        error = tb.rosso, onError = tb.onAccento,
        scrim = Color.Black.copy(alpha = 0.7f),
    )
    CompositionLocalProvider(
        LocalTb provides tb,
        LocalIndication provides ripple(color = tb.accento),
    ) {
        val view = LocalView.current
        SideEffect {
            val activity = view.context as? Activity ?: return@SideEffect
            WindowCompat.getInsetsController(activity.window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
        MaterialTheme(colorScheme = scheme, typography = TipografiaTb, shapes = FormeTb, content = content)
    }
}
