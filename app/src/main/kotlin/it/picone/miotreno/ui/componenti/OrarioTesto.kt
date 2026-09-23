package it.picone.miotreno.ui.componenti

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import it.picone.miotreno.domain.OrarioFermata
import it.picone.miotreno.ui.theme.LocalTb
import it.picone.miotreno.ui.theme.Testo

/**
 * Orario in evidenza con, sotto, la programmata barrata e il delta quando c'è ritardo.
 *
 * Unico posto in cui si decide come si scrive un orario ritardato: card, hero e dettaglio
 * devono dire la stessa cosa, altrimenti la stessa corsa sembra avere due orari diversi a
 * seconda di dove la guardi.
 */
@Composable
fun OrarioConRitardo(
    orario: OrarioFermata,
    stile: TextStyle,
    modifier: Modifier = Modifier,
    colore: Color? = null,
    cancellato: Boolean = false,
) {
    val tb = LocalTb.current
    val previsto = orario.previstoMs?.comeOra()
    val programmata = orario.programmataMs
    val ritardo = orario.ritardoMinuti

    Column(modifier) {
        Text(
            when {
                previsto == null -> "—"
                // stima, non dato confermato: il feed conferma solo le fermate raggiunte
                !orario.confermato && orario.inRitardo -> "≈$previsto"
                else -> previsto
            },
            style = stile,
            color = colore ?: tb.tx,
            textDecoration = if (cancellato) TextDecoration.LineThrough else null,
        )
        if (!cancellato && orario.inRitardo && programmata != null && ritardo != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    programmata.comeOra(), style = Testo.micro, color = tb.ter,
                    textDecoration = TextDecoration.LineThrough,
                )
                DeltaRitardo(ritardo, piccolo = true)
            }
        }
    }
}
