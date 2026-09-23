package it.picone.miotreno.ui.componenti

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import it.picone.miotreno.ui.theme.Forme
import it.picone.miotreno.ui.theme.LocalTb
import it.picone.miotreno.ui.theme.Molla
import it.picone.miotreno.ui.theme.Testo

/** Interruttore custom: pista 46×26, pomello con molla, colore accento quando acceso. */
@Composable
fun Toggle(attivo: Boolean, modifier: Modifier = Modifier) {
    val tb = LocalTb.current
    val x by animateDpAsState(if (attivo) 23.dp else 3.dp, Molla.ui(), label = "toggle")
    val pista by animateColorAsState(if (attivo) tb.accento else tb.bordoForte, Molla.piatta(), label = "pista")
    Box(
        modifier
            .width(46.dp).height(26.dp)
            .clip(CircleShape)
            .background(pista),
    ) {
        Box(
            Modifier.offset { IntOffset(x.roundToPx(), 3.dp.roundToPx()) }.size(20.dp).clip(CircleShape).background(Color.White),
        )
    }
}

/**
 * Bottone primario a pillola, fondo pieno accento. [attivo] = stato "già fatto"
 * (notifica impostata, treno seguito): resta solo il bordo, il fondo si spegne.
 */
@Composable
fun BottonePrimario(
    testo: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icona: ImageVector? = null,
    attivo: Boolean = false,
    colore: Color = LocalTb.current.accento,
) {
    val tb = LocalTb.current
    val interaction = remember { MutableInteractionSource() }
    val premuto by interaction.collectIsPressedAsState()
    val scala by animateFloatAsState(if (premuto) 0.96f else 1f, Molla.ui(), label = "press")
    Row(
        modifier
            .scale(scala)
            .clip(Forme.pillola)
            .let { if (attivo) it.background(tb.sf2).border(1.5.dp, colore, Forme.pillola) else it.background(colore) }
            .semantics { role = Role.Button }
            .clickable(interaction, indication = null, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icona?.let {
            Icon(it, contentDescription = null, tint = if (attivo) colore else tb.onAccento, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        // testo scuro sul colore pieno: bianco su accento era 3.9:1, sotto AA (vedi ContrastoTest)
        Text(testo, style = Testo.bottone, color = if (attivo) colore else tb.onAccento, maxLines = 1)
    }
}

/** Bottone secondario: solo bordo, per azioni meno importanti. */
@Composable
fun BottoneSecondario(
    testo: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icona: ImageVector? = null,
) {
    val tb = LocalTb.current
    Row(
        modifier
            .clip(Forme.pillola)
            .border(1.dp, tb.bordoForte, Forme.pillola)
            .semantics { role = Role.Button }
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icona?.let {
            Icon(it, contentDescription = null, tint = tb.sub, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(testo, style = Testo.bottone, color = tb.tx, maxLines = 1)
    }
}

/** Icona tonda cliccabile (indietro, aggiorna, chiudi): 44dp di tocco, 36dp visibili. */
@Composable
fun BottoneIcona(icona: ImageVector, descrizione: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val tb = LocalTb.current
    Box(
        modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(tb.sf2).border(1.dp, tb.bordo, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(icona, contentDescription = descrizione, tint = tb.tx, modifier = Modifier.size(18.dp)) }
    }
}

/** Chip piccola informativa (categoria treno, binario). */
@Composable
fun Chip(testo: String, modifier: Modifier = Modifier, colore: Color = LocalTb.current.sub, pieno: Boolean = false) {
    val tb = LocalTb.current
    Text(
        testo,
        modifier
            .clip(Forme.chip)
            .background(if (pieno) colore.copy(alpha = ALPHA_TINTA) else tb.sf2)
            .border(1.dp, if (pieno) Color.Transparent else tb.bordo, Forme.chip)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        style = Testo.micro, color = colore, maxLines = 1,
    )
}

/** Titoletto di sezione in maiuscoletto. */
@Composable
fun Overline(testo: String, modifier: Modifier = Modifier) {
    Text(testo.uppercase(), modifier, style = Testo.overline, color = LocalTb.current.ter)
}
