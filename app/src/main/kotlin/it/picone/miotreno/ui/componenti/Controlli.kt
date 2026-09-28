package it.picone.miotreno.ui.componenti

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import it.picone.miotreno.ui.theme.Forme
import it.picone.miotreno.ui.theme.LocalTb
import it.picone.miotreno.ui.theme.Molla
import it.picone.miotreno.ui.theme.Testo

/** Interruttore custom: pista 52×32, pomello con molla, colore accento quando acceso. */
@Composable
fun Toggle(attivo: Boolean, modifier: Modifier = Modifier) {
    val tb = LocalTb.current
    val x by animateDpAsState(if (attivo) 24.dp else 4.dp, Molla.ui(), label = "toggle")
    val pista by animateColorAsState(if (attivo) tb.accento else tb.bordoForte, Molla.piatta(), label = "pista")
    Box(
        modifier
            .width(52.dp).height(32.dp)
            .clip(CircleShape)
            .background(pista),
    ) {
        Box(
            Modifier.offset { IntOffset(x.roundToPx(), 4.dp.roundToPx()) }.size(24.dp)
                .ombraMorbida(tb, CircleShape, 2.dp).clip(CircleShape).background(Color.White),
        )
    }
}

/**
 * Bottone primario, fondo pieno accento, ombra tinta e un filo di luce in alto (rilievo).
 * [attivo] = stato "già fatto" (notifica impostata, treno seguito): fondo tinto, testo accento.
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
    val contenuto = if (attivo) colore else tb.suStato
    Row(
        modifier
            .scale(scala)
            .let { if (attivo) it else it.ombraMorbida(tb, Forme.pillola, 8.dp, colore) }
            .clip(Forme.pillola)
            .let { if (attivo) it.background(colore.copy(alpha = 0.12f)) else it.background(colore).luceSuperiore() }
            .semantics { role = Role.Button }
            .clickable(interaction, indication = null, onClick = onClick)
            .heightIn(min = 52.dp)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        icona?.let {
            Icon(it, contentDescription = null, tint = contenuto, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(testo, style = Testo.bottone, color = contenuto, maxLines = 1)
    }
}

/** Filo bianco in alto sul bottone pieno: dà rilievo senza gradienti. */
private fun Modifier.luceSuperiore(): Modifier = drawWithContent {
    drawContent()
    drawLine(Color.White.copy(alpha = 0.25f), Offset(size.height / 3, 1f), Offset(size.width - size.height / 3, 1f), 1.dp.toPx())
}

/** Bottone secondario: accento tinto, per azioni meno importanti. */
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
            .background(tb.accentoSoft)
            .semantics { role = Role.Button }
            .clickable(onClick = onClick)
            .heightIn(min = 52.dp)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        icona?.let {
            Icon(it, contentDescription = null, tint = tb.accento, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(testo, style = Testo.bottone, color = tb.accento, maxLines = 1)
    }
}

/** Icona tonda cliccabile (indietro, inverti, chiudi): 48dp di tocco, 40dp visibili. */
@Composable
fun BottoneIcona(icona: ImageVector, descrizione: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val tb = LocalTb.current
    Box(
        modifier.size(48.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(tb.sf2),
            contentAlignment = Alignment.Center,
        ) { Icon(icona, contentDescription = descrizione, tint = tb.tx, modifier = Modifier.size(20.dp)) }
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
            .padding(horizontal = 8.dp, vertical = 2.dp),
        style = Testo.etichettaBold, color = colore, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
    )
}

/** Chip filtro selezionabile (periodo, stazione): pillola piena accento quando attiva. */
@Composable
fun ChipFiltro(
    testo: String,
    selezionata: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icona: ImageVector? = null,
) {
    val tb = LocalTb.current
    val sfondo by animateColorAsState(if (selezionata) tb.accento else tb.sf, Molla.piatta(), label = "chipFiltro")
    val contenuto = if (selezionata) tb.suStato else tb.tx
    Row(
        modifier
            .heightIn(min = 40.dp)
            .clip(Forme.chip)
            .background(sfondo)
            .let { if (selezionata) it else it.border(1.dp, tb.bordoForte, Forme.chip) }
            .semantics { role = Role.Checkbox; selected = selezionata }
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icona?.let {
            Icon(it, contentDescription = null, tint = contenuto, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(testo, style = Testo.etichettaBold, color = contenuto, maxLines = 1)
    }
}

/** Titoletto di sezione. */
@Composable
fun Overline(testo: String, modifier: Modifier = Modifier) {
    Text(testo, modifier, style = Testo.overline, color = LocalTb.current.sub)
}
