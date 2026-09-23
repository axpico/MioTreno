package it.picone.miotreno.ui.componenti

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import it.picone.miotreno.domain.Semaforo
import it.picone.miotreno.ui.theme.Forme
import it.picone.miotreno.ui.theme.LocalTb
import it.picone.miotreno.ui.theme.Molla
import it.picone.miotreno.ui.theme.Testo

/**
 * Badge di stato. Il colore si anima al cambio di stato e il badge fa un piccolo
 * "pulse" (scala con overshoot): un treno che passa da in orario a ritardo deve farsi notare.
 * Cancellato è pieno, non tinto: è uno stato terminale e si legge anche senza colore.
 */
@Composable
fun StatusBadge(
    semaforo: Semaforo,
    testo: String,
    modifier: Modifier = Modifier,
    compatto: Boolean = false,
) {
    val tb = LocalTb.current
    val colore by animateColorAsState(tb.colore(semaforo), Molla.piatta(), label = "badge")
    val pieno = semaforo == Semaforo.Cancellato

    val scala = remember { Animatable(1f) }
    LaunchedEffect(semaforo) {
        scala.snapTo(1f)
        if (Molla.riduci) return@LaunchedEffect
        scala.animateTo(1.12f, Molla.stato())
        scala.animateTo(1f, Molla.stato())
    }

    val descrizione = when (semaforo) {
        Semaforo.InOrario -> "Treno in orario"
        Semaforo.RitardoLieve -> "Ritardo lieve, $testo"
        Semaforo.RitardoGrave -> "Ritardo grave, $testo"
        Semaforo.Irregolare -> "Treno irregolare, $testo"
        Semaforo.Cancellato -> "Treno cancellato"
    }

    Row(
        modifier
            .scale(scala.value)
            .clip(Forme.chip)
            .background(if (pieno) colore else colore.copy(alpha = ALPHA_TINTA))
            .padding(horizontal = if (compatto) 8.dp else 10.dp, vertical = if (compatto) 3.dp else 5.dp)
            .semantics { contentDescription = descrizione },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (pieno) {
            Icon(
                Icone.Cancellato, contentDescription = null,
                tint = tb.bg, modifier = Modifier.size(12.dp).padding(end = 0.dp),
            )
            Box(Modifier.size(4.dp))
        } else {
            Box(Modifier.size(6.dp).clip(CircleShape).background(colore))
            Box(Modifier.size(6.dp))
        }
        Text(
            testo,
            style = if (compatto) Testo.micro else Testo.etichettaBold,
            color = if (pieno) tb.bg else colore,
            maxLines = 1,
        )
    }
}
