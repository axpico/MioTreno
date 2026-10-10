package it.picone.miotreno.ui.componenti

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import it.picone.miotreno.domain.OrarioFermata
import it.picone.miotreno.domain.comeOra
import it.picone.miotreno.ui.theme.Forme
import it.picone.miotreno.ui.theme.LocalTb
import it.picone.miotreno.ui.theme.Molla
import it.picone.miotreno.ui.theme.Testo

/** Ruolo di una fermata nella timeline. */
enum class TipoFermata { Passata, Prossima, Futura, Salita, Target }

/**
 * Riga della timeline verticale (stile tracking spedizioni): linea, nodo, nome, orari.
 * La prossima fermata pulsa; le passate restano attenuate. Non chiamiamo mai "corrente" una
 * fermata solo perché è l'ultima confermata: fra due stazioni il treno è in viaggio.
 */
@Composable
fun TimelineStop(
    nome: String,
    tipo: TipoFermata,
    orario: OrarioFermata,
    primo: Boolean,
    ultimo: Boolean,
    modifier: Modifier = Modifier,
    nota: String? = null,
) {
    val tb = LocalTb.current
    val passata = tipo == TipoFermata.Passata
    val evidenziata = tipo == TipoFermata.Prossima || tipo == TipoFermata.Salita || tipo == TipoFermata.Target
    val coloreLinea = if (passata) tb.accento else tb.ter.copy(alpha = 0.25f)
    // Il "≈" della stima non si sente: a TalkBack la differenza va detta a parole.
    val descrizione = buildString {
        append(
            when (tipo) {
                TipoFermata.Prossima -> "Prossima fermata: $nome"
                TipoFermata.Target -> "$nome, la tua fermata"
                TipoFermata.Salita -> "$nome, dove sali"
                TipoFermata.Passata -> "$nome, passata"
                TipoFermata.Futura -> nome
            },
        )
        orario.previstoMs?.let {
            append(if (orario.confermato) ", ore ${it.comeOra()}" else ", previsto per le ${it.comeOra()}")
        }
        orario.ritardoMinuti?.takeIf { orario.inRitardo }?.let {
            append(if (it > 0) ", $it minuti di ritardo" else ", ${-it} minuti di anticipo")
        }
    }

    // height(IntrinsicSize.Min): senza, i segmenti con weight collassano e restano solo i nodi
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp).height(IntrinsicSize.Min)
            .semantics(mergeDescendants = true) { contentDescription = descrizione },
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.width(24.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.width(3.dp).weight(1f).background(if (primo) Color.Transparent else coloreLinea))
            Nodo(tipo)
            Box(
                Modifier.width(3.dp).weight(1f)
                    .background(
                        if (ultimo) {
                            Color.Transparent
                        } else if (passata) {
                            tb.accento
                        } else {
                            tb.ter.copy(alpha = 0.25f)
                        },
                    ),
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
                    TipoFermata.Salita -> "sali qui"
                    else -> null
                }
                sotto?.let {
                    Text(it, style = Testo.micro, color = tb.accento)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                val previsto = orario.previstoMs?.comeOra()
                Text(
                    when {
                        previsto == null -> "—"
                        // "≈" distingue la stima dal dato: il feed conferma solo le fermate raggiunte
                        !orario.confermato -> "≈$previsto"
                        else -> previsto
                    },
                    style = Testo.numeroPiccolo,
                    color = if (passata) tb.sub else tb.tx,
                )
                val programmataMs = orario.programmataMs
                val ritardo = orario.ritardoMinuti
                if (orario.inRitardo && programmataMs != null && ritardo != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            programmataMs.comeOra(), style = Testo.micro, color = tb.ter,
                            textDecoration = TextDecoration.LineThrough,
                        )
                        DeltaRitardo(ritardo, piccolo = true)
                    }
                }
            }
        }
    }
}

/**
 * Riga della timeline che sta per un gruppo di fermate nascoste ("9 fermate prima della tua"):
 * stessa rotaia delle fermate, così la linea resta continua, e un tocco le mostra.
 */
@Composable
fun TimelineEspandi(
    testo: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** null = niente linea da quel lato (inizio o fine della timeline). */
    lineaSopra: Color?,
    lineaSotto: Color?,
) {
    val tb = LocalTb.current
    Row(
        modifier.fillMaxWidth().heightIn(min = 48.dp).height(IntrinsicSize.Min)
            .clip(Forme.cardPiccola)
            .semantics { role = Role.Button }
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(24.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.width(3.dp).weight(1f).background(lineaSopra ?: Color.Transparent))
            // tre puntini sulla rotaia: "qui ci sono altre fermate"
            repeat(3) { Box(Modifier.padding(vertical = 1.5.dp).size(4.dp).clip(CircleShape).background(tb.ter)) }
            Box(Modifier.width(3.dp).weight(1f).background(lineaSotto ?: Color.Transparent))
        }
        Text(testo, style = Testo.etichettaBold, color = tb.accento, modifier = Modifier.weight(1f))
        Icon(Icone.Avanti, contentDescription = null, tint = tb.ter, modifier = Modifier.padding(end = 4.dp).size(18.dp))
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
                if (Molla.riduci) {
                    Box(Modifier.size(24.dp).alpha(0.3f).clip(CircleShape).background(tb.accento))
                } else {
                    Box(Modifier.size(16.dp).scale(scala).alpha(alpha).clip(CircleShape).background(tb.accento))
                }
                Box(Modifier.size(16.dp).clip(CircleShape).background(tb.accento).border(3.dp, tb.sf, CircleShape))
            }
        }
        TipoFermata.Target -> Box(
            Modifier.size(20.dp).clip(CircleShape)
                .background(tb.sf).border(4.dp, tb.verde, CircleShape),
        )
        TipoFermata.Salita -> Box(
            Modifier.size(20.dp).clip(CircleShape)
                .background(tb.sf).border(4.dp, tb.accento, CircleShape),
        )
        TipoFermata.Passata -> Box(Modifier.size(12.dp).clip(CircleShape).background(tb.accento))
        TipoFermata.Futura -> Box(
            Modifier.size(12.dp).clip(CircleShape).background(tb.sf).border(2.dp, tb.ter, CircleShape),
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
