package it.picone.miotreno.ui.componenti

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import it.picone.miotreno.ui.theme.LocalTb
import it.picone.miotreno.ui.theme.Testo

/**
 * Stato vuoto / errore / permesso: icona in un disco piatto, titolo, testo, e un'azione
 * opzionale. Un solo componente per tutti i casi.
 */
@Composable
fun StatoVuoto(
    icona: ImageVector,
    titolo: String,
    testo: String,
    modifier: Modifier = Modifier,
    colore: Color = LocalTb.current.accento,
    azione: String? = null,
    onAzione: (() -> Unit)? = null,
) {
    val tb = LocalTb.current
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(tb.sf2)
                .border(1.dp, tb.bordo, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(icona, contentDescription = null, tint = colore, modifier = Modifier.size(40.dp)) }
        Spacer(Modifier.height(20.dp))
        Text(titolo, style = Testo.titolo, color = tb.tx, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(testo, style = Testo.corpo, color = tb.sub, textAlign = TextAlign.Center)
        if (azione != null && onAzione != null) {
            Spacer(Modifier.height(24.dp))
            BottonePrimario(azione, onAzione, colore = colore)
        }
    }
}
