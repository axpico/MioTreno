package it.picone.miotreno.ui.componenti

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import it.picone.miotreno.ui.theme.Forme
import it.picone.miotreno.ui.theme.LocalTb
import it.picone.miotreno.ui.theme.Testo

/** Interruttore M3; il tocco lo gestisce la riga che lo contiene. */
@Composable
fun Toggle(attivo: Boolean, modifier: Modifier = Modifier) {
    Switch(checked = attivo, onCheckedChange = null, modifier = modifier)
}

/**
 * Bottone primario M3 (pieno). [attivo] = stato "già fatto" (treno seguito, avviso impostato):
 * tonale, così non sembra un'altra azione da fare.
 */
@Composable
fun BottonePrimario(
    testo: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icona: ImageVector? = null,
    attivo: Boolean = false,
    colore: Color = MaterialTheme.colorScheme.primary,
    /** Per TalkBack, quando il testo visibile da solo non basta (es. "10′ prima"). */
    descrizione: String? = null,
) {
    val m = modifier.heightIn(min = 56.dp).semantics { descrizione?.let { contentDescription = it } }
    if (attivo) {
        FilledTonalButton(onClick = onClick, modifier = m, shapes = ButtonDefaults.shapes()) { Contenuto(testo, icona) }
    } else {
        Button(
            onClick = onClick, modifier = m, shapes = ButtonDefaults.shapes(),
            colors = ButtonDefaults.buttonColors(containerColor = colore),
        ) { Contenuto(testo, icona) }
    }
}

/** Bottone secondario: tonale. */
@Composable
fun BottoneSecondario(
    testo: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icona: ImageVector? = null,
    descrizione: String? = null,
) {
    FilledTonalButton(
        onClick = onClick, shapes = ButtonDefaults.shapes(),
        modifier = modifier.heightIn(min = 56.dp).semantics { descrizione?.let { contentDescription = it } },
    ) { Contenuto(testo, icona) }
}

@Composable
private fun Contenuto(testo: String, icona: ImageVector?) {
    icona?.let {
        Icon(it, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
    }
    Text(testo, style = Testo.bottone, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** Azione di ripiego: "preferisco fare a mano", "riprova". */
@Composable
fun BottoneTesto(testo: String, onClick: () -> Unit, modifier: Modifier = Modifier, colore: Color = MaterialTheme.colorScheme.primary) {
    TextButton(onClick = onClick, modifier = modifier, shapes = ButtonDefaults.shapes()) {
        Text(testo, style = Testo.etichettaBold, color = colore)
    }
}

/** Area tattile del bottone a icona: chi gli fa da contrappeso (titoli centrati) usa la stessa. */
val DIMENSIONE_BOTTONE_ICONA = 48.dp

/** Bottone a icona tonale (inverti, chiudi). */
@Composable
fun BottoneIcona(icona: ImageVector, descrizione: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilledTonalIconButton(onClick = onClick, modifier = modifier) { Icon(icona, contentDescription = descrizione) }
}

/** Chip piccola informativa (categoria, stazione, "via …"): testo nel colore, fondo tinto. */
@Composable
fun Chip(testo: String, modifier: Modifier = Modifier, colore: Color = LocalTb.current.sub, pieno: Boolean = false) {
    val tb = LocalTb.current
    Text(
        testo,
        modifier
            .let { if (pieno) it.sfondoChip(colore.copy(alpha = ALPHA_TINTA)) else it.sfondoChip(tb.sf2) }
            .padding(horizontal = 8.dp, vertical = 2.dp),
        style = Testo.etichettaBold, color = colore, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
    )
}

private fun Modifier.sfondoChip(c: Color) = background(c, Forme.chip)

/** Chip filtro selezionabile (periodo, stazione): FilterChip M3. */
@Composable
fun ChipFiltro(
    testo: String,
    selezionata: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icona: ImageVector? = null,
) {
    FilterChip(
        selected = selezionata,
        onClick = onClick,
        label = { Text(testo, style = Testo.etichettaBold, maxLines = 1) },
        leadingIcon = icona?.let { { Icon(it, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) } },
        modifier = modifier,
    )
}

/** Titoletto di sezione, nel colore primario come le intestazioni M3. */
@Composable
fun Overline(testo: String, modifier: Modifier = Modifier) {
    Text(testo, modifier, style = Testo.overline, color = MaterialTheme.colorScheme.primary)
}
