package it.picone.miotreno.ui.componenti

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import it.picone.miotreno.ui.theme.Forme
import it.picone.miotreno.ui.theme.LocalTb
import it.picone.miotreno.ui.theme.Molla
import it.picone.miotreno.ui.theme.Testo

/** Ruolo di una fermata nella timeline. Cambio è un nodo diverso, non una fermata più grande. */
enum class TipoFermata { Passata, Prossima, Futura, Target, Cambio }

/**
 * Riga della timeline verticale (stile tracking spedizioni): linea, nodo, nome, orari.
 * La prossima fermata pulsa; le passate restano attenuate. Non chiamiamo mai "corrente" una
 * fermata solo perché è l'ultima confermata: fra due stazioni il treno è in viaggio.
 */
@Composable
fun TimelineStop(
    nome: String,
    tipo: TipoFermata,
    programmata: String?,
    effettiva: String?,
    ritardoMinuti: Int?,
    primo: Boolean,
    ultimo: Boolean,
    modifier: Modifier = Modifier,
    nota: String? = null,
) {
    val tb = LocalTb.current
    val passata = tipo == TipoFermata.Passata
    val evidenziata = tipo == TipoFermata.Prossima || tipo == TipoFermata.Target || tipo == TipoFermata.Cambio
    val coloreLinea = if (passata) tb.accento else tb.ter.copy(alpha = 0.25f)
    val descrizione = when (tipo) {
        TipoFermata.Prossima -> "Prossima fermata: $nome"
        TipoFermata.Target -> "$nome, la tua fermata"
        TipoFermata.Cambio -> "Cambio a $nome"
        TipoFermata.Passata -> "$nome, passata"
        TipoFermata.Futura -> nome
    }

    // height(IntrinsicSize.Min): senza, i segmenti con weight collassano e restano solo i nodi
    Row(
        modifier.fillMaxWidth().heightIn(min = 52.dp).height(IntrinsicSize.Min)
            .semantics(mergeDescendants = true) { contentDescription = descrizione },
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.width(20.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.width(2.dp).weight(1f).background(if (primo) Color.Transparent else coloreLinea))
            Nodo(tipo)
            Box(
                Modifier.width(2.dp).weight(1f)
                    .background(if (ultimo) Color.Transparent else if (passata) tb.accento else tb.ter.copy(alpha = 0.25f)),
            )
        }
        Row(
            Modifier.weight(1f).padding(vertical = 8.dp).let { if (passata) it.alpha(0.55f) else it },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    nome,
                    style = if (evidenziata) Testo.sottotitolo else Testo.corpoMedio,
                    color = if (passata) tb.sub else tb.tx,
                )
                val sotto = nota ?: when (tipo) {
                    TipoFermata.Prossima -> "prossima fermata"
                    TipoFermata.Target -> "la tua fermata"
                    TipoFermata.Cambio -> "cambio treno"
                    else -> null
                }
                sotto?.let {
                    Text(it, style = Testo.micro, color = if (tipo == TipoFermata.Cambio) tb.ambra else tb.accento)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(effettiva ?: programmata ?: "—", style = Testo.numeroPiccolo, color = if (passata) tb.sub else tb.tx)
                if (ritardoMinuti != null && ritardoMinuti != 0 && programmata != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(programmata, style = Testo.micro, color = tb.ter, textDecoration = TextDecoration.LineThrough)
                        DeltaRitardo(ritardoMinuti, piccolo = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun Nodo(tipo: TipoFermata) {
    val tb = LocalTb.current
    when (tipo) {
        TipoFermata.Prossima -> {
            val t = rememberInfiniteTransition(label = "pulse")
            val scala by t.animateFloat(1f, 2.2f, infiniteRepeatable(tween(1400), RepeatMode.Restart), label = "s")
            val alpha by t.animateFloat(0.6f, 0f, infiniteRepeatable(tween(1400), RepeatMode.Restart), label = "a")
            Box(contentAlignment = Alignment.Center) {
                // riduci movimento: anello fermo al posto dell'onda
                if (Molla.riduci) Box(Modifier.size(24.dp).alpha(0.3f).clip(CircleShape).background(tb.accento))
                else Box(Modifier.size(14.dp).scale(scala).alpha(alpha).clip(CircleShape).background(tb.accento))
                Box(Modifier.size(14.dp).clip(CircleShape).background(tb.accento))
            }
        }
        TipoFermata.Target -> Box(
            Modifier.size(16.dp).clip(CircleShape)
                .background(tb.bg).border(3.dp, tb.verde, CircleShape),
        )
        TipoFermata.Cambio -> Box(
            Modifier.size(18.dp).clip(Forme.chip)
                .background(tb.ambra),
            contentAlignment = Alignment.Center,
        ) { Icon(Icone.Cambio, contentDescription = null, tint = tb.bg, modifier = Modifier.size(12.dp)) }
        TipoFermata.Passata -> Box(Modifier.size(10.dp).clip(CircleShape).background(tb.accento))
        TipoFermata.Futura -> Box(
            Modifier.size(10.dp).clip(CircleShape).background(tb.bg).border(2.dp, tb.ter, CircleShape),
        )
    }
}

/** Delta stile trading: "+4" in ambra/arancio con freccia su, "−1" in verde con freccia giù. */
@Composable
fun DeltaRitardo(minuti: Int, modifier: Modifier = Modifier, piccolo: Boolean = false) {
    val tb = LocalTb.current
    val colore = when {
        minuti <= 0 -> tb.verde
        minuti < 10 -> tb.ambra
        else -> tb.arancio
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Icon(
            if (minuti > 0) Icone.Frecciasu else Icone.Frecciagiu, contentDescription = null,
            tint = colore, modifier = Modifier.size(if (piccolo) 10.dp else 14.dp),
        )
        NumeroAnimato(
            (if (minuti > 0) "+" else "") + minuti.toString(),
            stile = if (piccolo) Testo.numeroPiccolo else Testo.numero, colore = colore,
        )
        if (!piccolo) Text("min", style = Testo.micro, color = colore)
    }
}
