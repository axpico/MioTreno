package it.picone.miotreno.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import it.picone.miotreno.ui.componenti.BottonePrimario
import it.picone.miotreno.ui.componenti.Chip
import it.picone.miotreno.ui.componenti.GlassCard
import it.picone.miotreno.ui.componenti.Icone
import it.picone.miotreno.ui.componenti.Overline
import it.picone.miotreno.ui.theme.Forme
import it.picone.miotreno.ui.theme.LocalTb
import it.picone.miotreno.ui.theme.Spazio
import it.picone.miotreno.ui.theme.Testo

/**
 * Priming del permesso posizione: spiega il perché e mostra com'è la home con la stazione già
 * rilevata, *prima* del dialog di sistema, che parte solo da "Attiva la posizione".
 * [bloccato] = negato in modo permanente: il dialog non comparirebbe più, si apre Impostazioni.
 */
@Composable
fun PrimingPosizione(bloccato: Boolean, onContinua: () -> Unit, onScegliStazione: () -> Unit) {
    val tb = LocalTb.current
    Column(Modifier.fillMaxWidth().padding(top = Spazio.s)) {
        Text("La stazione giusta, da sola", style = Testo.titolo, color = tb.tx)
        Text(
            "MioTreno usa la posizione per trovarti automaticamente la stazione più vicina, " +
                "senza che tu debba cercarla ogni volta. Viene letta solo mentre usi l'app o aggiorni il widget.",
            style = Testo.corpo, color = tb.sub, modifier = Modifier.padding(top = 6.dp, bottom = Spazio.l),
        )
        Mockup()
        Spacer(Modifier.height(Spazio.l))
        BottonePrimario(
            if (bloccato) "Apri le impostazioni" else "Attiva la posizione",
            onContinua, icona = Icone.Posizione, modifier = Modifier.fillMaxWidth(),
        )
        if (bloccato) {
            Text(
                "Il permesso è stato negato in modo permanente: puoi riattivarlo da Impostazioni › App › MioTreno.",
                style = Testo.micro, color = tb.ter, modifier = Modifier.padding(top = 8.dp),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Text(
                "Preferisco scegliere la stazione a mano",
                Modifier.clip(Forme.pillola).semantics { role = Role.Button }.clickable(onClick = onScegliStazione).padding(12.dp),
                style = Testo.etichettaBold, color = tb.accento,
            )
        }
    }
}

/** Anteprima statica della home con stazione rilevata: nessun dato vero, è un esempio. */
@Composable
private fun Mockup() {
    val tb = LocalTb.current
    GlassCard(Modifier.fillMaxWidth(), padding = 14.dp) {
        Overline("Esempio")
        Spacer(Modifier.height(6.dp))
        Text("Milano Centrale", style = Testo.sottotitolo, color = tb.tx)
        Row(
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(top = 2.dp),
        ) {
            Icon(Icone.Posizione, null, tint = tb.accento2, modifier = Modifier.size(12.dp))
            Text("più vicina · 350 m", style = Testo.etichetta, color = tb.sub)
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth().clip(Forme.cardPiccola).background(tb.sf2).border(1.dp, tb.bordo, Forme.cardPiccola).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Chip("REG", colore = tb.accento2, pieno = true)
                    Text("2603", style = Testo.micro, color = tb.ter)
                }
                Text("08:12", style = Testo.numeroGrande, color = tb.tx, modifier = Modifier.padding(top = 4.dp))
                Text("Roma Termini · arrivo 08:41", style = Testo.etichetta, color = tb.sub)
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text("12", style = Testo.numeroGrande, color = tb.tx)
                Text("min", style = Testo.micro, color = tb.ter)
            }
            Box(Modifier.width(8.dp))
            Box(Modifier.size(8.dp).clip(Forme.chip).background(tb.verde))
        }
    }
}
