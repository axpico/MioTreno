package it.picone.miotreno.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import it.picone.miotreno.ui.componenti.Icone
import it.picone.miotreno.ui.componenti.ombraMorbida
import it.picone.miotreno.ui.theme.Forme
import it.picone.miotreno.ui.theme.LocalTb
import it.picone.miotreno.ui.theme.Molla
import it.picone.miotreno.ui.theme.Testo

private data class Tab(val nome: String, val icona: ImageVector, val schermata: Schermata)

private val TAB = listOf(
    Tab("Treni", Icone.Treno, Schermata.Home),
    Tab("Statistiche", Icone.Statistiche, Schermata.Statistiche),
    Tab("Impostazioni", Icone.Impostazioni, Schermata.Impostazioni),
)

/**
 * Barra flottante a pillola con ombra tinta. Tre posti uguali, icona sopra e nome sotto: la tab
 * attiva si distingue per la capsula tinta, non allargandosi (spingeva le altre in un angolo).
 */
@Composable
internal fun BarraNav(corrente: Schermata, onVai: (Schermata) -> Unit) {
    val tb = LocalTb.current
    Row(
        Modifier
            .padding(horizontal = 24.dp, vertical = 12.dp)
            .fillMaxWidth()
            .ombraMorbida(tb, Forme.barra, 16.dp)
            .clip(Forme.barra)
            .background(tb.sf)
            .let { if (tb.scuro) it.border(1.dp, tb.bordoForte, Forme.barra) else it }
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TAB.forEach { tab ->
            val attivo = corrente == tab.schermata
            val sfondo by animateColorAsState(
                if (attivo) tb.accentoSoft else Color.Transparent,
                Molla.piatta(), label = "nav-${tab.nome}",
            )
            val colore by animateColorAsState(if (attivo) tb.accento else tb.ter, Molla.piatta(), label = "navc-${tab.nome}")
            Column(
                Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(Forme.barra)
                    .background(sfondo)
                    .selectable(selected = attivo, role = Role.Tab, onClick = { onVai(tab.schermata) })
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(tab.icona, contentDescription = null, tint = colore, modifier = Modifier.size(22.dp))
                Text(
                    tab.nome, style = if (attivo) Testo.etichettaBold else Testo.micro, color = colore, maxLines = 1,
                    softWrap = false, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}
