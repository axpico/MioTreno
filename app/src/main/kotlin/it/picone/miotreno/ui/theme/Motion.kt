package it.picone.miotreno.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Tutte le animazioni sono molle. Tre preset, non uno per schermata:
 *  - [Molla.stato]: overshoot visibile, per badge di stato e conferme (il tocco Duolingo)
 *  - [Molla.ui]: leggero rimbalzo, per card, transizioni e valori numerici
 *  - [Molla.piatta]: nessun rimbalzo, per opacità e colori (un colore che "rimbalza" è brutto)
 *
 * Con [riduci] attivo (impostazione di sistema "riduci movimento" / scala animazioni 0) i tre
 * preset collassano su una molla piatta e rapida: stessa logica di stato, nessun rimbalzo.
 * Le animazioni infinite (pulse, respiro, rotazione) controllano [riduci] e mostrano il
 * fotogramma statico equivalente.
 */
object Molla {
    /** Letta da `MainActivity.onResume` via `ValueAnimator.areAnimatorsEnabled()`. */
    var riduci by mutableStateOf(false)

    fun <T> stato(): SpringSpec<T> =
        if (riduci) ridotta() else spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium)

    fun <T> ui(): SpringSpec<T> =
        if (riduci) ridotta() else spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)

    fun <T> piatta(): SpringSpec<T> =
        if (riduci) ridotta() else spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)

    private fun <T> ridotta(): SpringSpec<T> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh)
}
