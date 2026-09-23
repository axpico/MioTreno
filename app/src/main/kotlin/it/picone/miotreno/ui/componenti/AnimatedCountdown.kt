package it.picone.miotreno.ui.componenti

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import it.picone.miotreno.ui.theme.LocalTb
import it.picone.miotreno.ui.theme.Molla
import it.picone.miotreno.ui.theme.Testo

/**
 * Numero che scatta quando cambia: ogni cifra scorre dal basso (valore che sale) o
 * dall'alto (valore che scende) con un taglio netto, senza rimbalzo — il "click" meccanico
 * di un tabellone a palette, non lo scivolamento morbido di una UI.
 */
@Composable
fun NumeroAnimato(
    valore: String,
    modifier: Modifier = Modifier,
    stile: TextStyle = Testo.hero,
    colore: Color = LocalTb.current.tx,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        valore.forEachIndexed { i, c ->
            AnimatedContent(
                targetState = c,
                transitionSpec = {
                    val giu = targetState > initialState
                    if (Molla.riduci) {
                        fadeIn(Molla.piatta()).togetherWith(fadeOut(Molla.piatta()))
                    } else {
                        (slideInVertically(Molla.piatta()) { if (giu) it else -it } + fadeIn(Molla.piatta()))
                            .togetherWith(slideOutVertically(Molla.piatta()) { if (giu) -it else it } + fadeOut(Molla.piatta()))
                    }
                },
                label = "cifra$i",
            ) { Text(it.toString(), style = stile, color = colore) }
        }
    }
}

/**
 * Countdown ai minuti alla partenza: numero grande + unità, semantica unica per TalkBack.
 * Sotto zero il treno è in viaggio: il numero negativo sembrerebbe un errore.
 */
@Composable
fun AnimatedCountdown(
    minuti: Int,
    modifier: Modifier = Modifier,
    grande: Boolean = true,
    colore: Color = LocalTb.current.tx,
) {
    val tb = LocalTb.current
    val partito = minuti < 0
    val descrizione = if (partito) "Treno partito" else "Parte tra $minuti minuti"
    Column(
        modifier.semantics(mergeDescendants = true) { contentDescription = descrizione },
        horizontalAlignment = Alignment.End,
    ) {
        if (partito) {
            Text("in viaggio", style = if (grande) Testo.numero else Testo.numeroPiccolo, color = tb.accento2)
        } else {
            NumeroAnimato(
                minuti.toString(),
                stile = if (grande) Testo.hero else Testo.numeroGrande,
                colore = colore,
            )
            Text(
                if (minuti == 1) "minuto" else "min",
                style = Testo.micro, color = tb.ter, textAlign = TextAlign.End,
            )
        }
    }
}
