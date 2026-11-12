package it.picone.miotreno.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Material 3 Expressive: colore dinamico dallo sfondo da Android 12, altrimenti lo schema
 * generato dal blu rail. Tipografia Atkinson sulla scala M3, molle Expressive (standard se
 * "riduci movimento" è attivo). Chiaro o scuro segue il sistema.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MioTrenoTheme(scuro: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (scuro) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        scuro -> SchemaScuro
        else -> SchemaChiaro
    }
    val view = LocalView.current
    SideEffect {
        val activity = view.context as? Activity ?: return@SideEffect
        WindowCompat.getInsetsController(activity.window, view).apply {
            isAppearanceLightStatusBars = !scuro
            isAppearanceLightNavigationBars = !scuro
        }
    }
    CompositionLocalProvider(LocalTb provides tbDa(scheme, scuro)) {
        MaterialExpressiveTheme(
            colorScheme = scheme,
            typography = TipografiaTb,
            motionScheme = if (Molla.riduci) MotionScheme.standard() else MotionScheme.expressive(),
            content = content,
        )
    }
}
