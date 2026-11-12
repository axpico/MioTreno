package it.picone.miotreno.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Le molle dell'app, con i valori di MotionScheme.expressive() di Material 3 Expressive: così le
 * animazioni scritte a mano si muovono come i componenti M3 (barre, fogli, bottoni). Tre preset:
 *  - [Molla.stato]: fast spatial (0.6 / 800), con rimbalzo, per badge di stato e conferme
 *  - [Molla.ui]: default spatial (0.8 / 380), per card, transizioni e valori numerici
 *  - [Molla.piatta]: default effects (1.0 / 1600), senza rimbalzo, per opacità e colori
 *
 * Qui e non `MaterialTheme.motionScheme` perché servono anche fuori da un contesto @Composable.
 * Con [riduci] attivo (impostazione di sistema "riduci movimento" / scala animazioni 0) i tre
 * preset collassano su una molla piatta e rapida: stessa logica di stato, nessun rimbalzo.
 * Le animazioni infinite (pulse, respiro, rotazione) controllano [riduci] e mostrano il
 * fotogramma statico equivalente.
 */
object Molla {
    /** Letta da `MainActivity.onResume` via `ValueAnimator.areAnimatorsEnabled()`. */
    var riduci by mutableStateOf(false)

    fun <T> stato(): SpringSpec<T> =
        if (riduci) ridotta() else spring(dampingRatio = 0.6f, stiffness = 800f)

    fun <T> ui(): SpringSpec<T> =
        if (riduci) ridotta() else spring(dampingRatio = 0.8f, stiffness = 380f)

    fun <T> piatta(): SpringSpec<T> =
        if (riduci) ridotta() else spring(dampingRatio = 1f, stiffness = 1600f)

    private fun <T> ridotta(): SpringSpec<T> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh)
}
