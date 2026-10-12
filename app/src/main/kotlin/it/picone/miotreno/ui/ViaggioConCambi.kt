package it.picone.miotreno.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import it.picone.miotreno.domain.DettaglioTreno
import it.picone.miotreno.domain.StatoCambio
import it.picone.miotreno.domain.Tratta
import it.picone.miotreno.domain.aPiediFra
import it.picone.miotreno.domain.comeOra
import it.picone.miotreno.domain.margineCambio
import it.picone.miotreno.domain.statoCambio
import it.picone.miotreno.ui.componenti.GlassCard
import it.picone.miotreno.ui.componenti.Icone
import it.picone.miotreno.ui.componenti.colore
import it.picone.miotreno.ui.theme.Forme
import it.picone.miotreno.ui.theme.LocalTb
import it.picone.miotreno.ui.theme.Spazio
import it.picone.miotreno.ui.theme.Testo

// Le parti del dettaglio che esistono solo per un viaggio con cambi: tratte, striscia, cambio.

/** Le fermate di una tratta; senza andamento, almeno da dove a dove e a che ora. */
@Composable
internal fun TrattaFermate(tratta: Tratta, dettaglio: DettaglioTreno?, notaArrivo: String?) {
    val tb = LocalTb.current
    val titolo: @Composable () -> Unit = { TitoloTratta(tratta) }
    if (dettaglio is DettaglioTreno.Ok) {
        Fermate(dettaglio, listOfNotNull(dettaglio.fermate.getOrNull(dettaglio.indicePartenza)?.codice), titolo, notaArrivo)
        return
    }
    GlassCard(Modifier.fillMaxWidth(), padding = Spazio.l) {
        titolo()
        Text(
            "Parte alle ${tratta.partenzaMs.comeOra()}, arriva alle ${tratta.arrivoMs.comeOra()}. " +
                "Percorso non ancora disponibile su ViaggiaTreno.",
            style = Testo.etichetta, color = tb.sub,
        )
    }
}

/** "REG 25308" sopra, "Garibaldi → Rho Fiera" sotto con la freccia come icona, centrata sul testo. */
@Composable
private fun TitoloTratta(tratta: Tratta) {
    val tb = LocalTb.current
    Column(Modifier.padding(start = 4.dp, bottom = Spazio.s)) {
        Text("${tratta.categoria} ${tratta.numeroTreno}", style = Testo.etichettaBold, color = tb.accento2)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                tratta.nomePartenza, style = Testo.etichetta, color = tb.sub,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
            )
            Icon(Icone.Freccia, contentDescription = "verso", tint = tb.ter, modifier = Modifier.size(14.dp))
            Text(
                tratta.nomeArrivo, style = Testo.etichetta, color = tb.sub,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}

@Composable
private fun StatoCambio.colore() = LocalTb.current.let {
    when (this) {
        StatoCambio.Ok -> it.accento2
        StatoCambio.ARischio -> it.ambra
        StatoCambio.Persa -> it.rosso
    }
}

/** Altezza dell'asse della striscia: pallini, barre e chip del cambio stanno tutti su questa riga. */
private val ASSE_STRISCIA = 22.dp

/**
 * Il viaggio in una riga, simmetrica: ogni treno ha lo stesso spazio (non lungo quanto dura,
 * che sbilanciava tutto verso il treno più lento), il nome sotto al centro del suo tratto, e il
 * cambio fra i due con la stazione sotto, colorato da quanto tempo resta con i ritardi veri.
 */
@Composable
internal fun StrisciaViaggio(treni: List<Tratta>, tutte: List<Tratta>, dettagli: List<DettaglioTreno>, modifier: Modifier = Modifier) {
    val tb = LocalTb.current
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        treni.forEachIndexed { i, t ->
            if (i > 0) {
                val prima = treni[i - 1]
                val stato = statoCambio(margineCambio(tutte, prima, t, dettagli.getOrNull(i - 1), dettagli.getOrNull(i)))
                val minuti = ((t.partenzaMs - prima.arrivoMs) / 60_000L).toInt()
                Column(Modifier.widthIn(max = 104.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.height(ASSE_STRISCIA), contentAlignment = Alignment.Center) {
                        Row(
                            Modifier.padding(horizontal = 6.dp).clip(Forme.chip).background(tb.sf2)
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(Icone.Cambio, contentDescription = "cambio", tint = stato.colore(), modifier = Modifier.size(12.dp))
                            Text("$minuti′", style = Testo.micro, color = stato.colore())
                        }
                    }
                    Text(
                        prima.nomeArrivo, style = Testo.micro, color = tb.sub, textAlign = TextAlign.Center,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth().height(ASSE_STRISCIA), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(tb.accento))
                    Box(Modifier.weight(1f).height(3.dp).background(tb.accento))
                    Box(Modifier.size(8.dp).clip(CircleShape).background(tb.accento))
                }
                Text(
                    "${t.categoria} ${t.numeroTreno}", style = Testo.micro, color = tb.ter,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/**
 * Fra due treni: dove si cambia, quanto tempo c'è e da che binario a che binario. I tratti a
 * piedi o in metro in mezzo (cambio di stazione) sono scritti qui, non sono una tratta a parte.
 */
@Composable
internal fun Cambio(arrivo: Tratta, partenza: Tratta, tutte: List<Tratta>, dArrivo: DettaglioTreno?, dPartenza: DettaglioTreno?) {
    val tb = LocalTb.current
    val minuti = ((partenza.partenzaMs - arrivo.arrivoMs) / 60_000L).toInt()
    val binArrivo = (dArrivo as? DettaglioTreno.Ok)?.let { it.fermate.getOrNull(it.indiceDestinazione)?.binario }
    val binPartenza = (dPartenza as? DettaglioTreno.Ok)?.let { it.fermate.getOrNull(it.indicePartenza)?.binario } ?: partenza.binario
    val inMezzo = aPiediFra(tutte, arrivo, partenza)
    val live = margineCambio(tutte, arrivo, partenza, dArrivo, dPartenza)
    val stato = statoCambio(live)
    // Speculare: dove arrivi a sinistra, dove riparti a destra, il cambio al centro.
    Column(
        Modifier.fillMaxWidth().clip(Forme.cardPiccola).background(tb.sf2).padding(horizontal = Spazio.l, vertical = Spazio.m),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Cambio a ${arrivo.nomeArrivo}", style = Testo.etichettaBold, color = tb.tx, textAlign = TextAlign.Center)
        // in basso: i tre valori (binario, minuti, binario) stanno sulla stessa riga
        Row(Modifier.fillMaxWidth().padding(top = Spazio.s), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text("arrivi", style = Testo.micro, color = tb.ter)
                Text(binArrivo?.let { "binario $it" } ?: "—", style = Testo.etichetta, color = tb.sub)
            }
            Column(Modifier.padding(horizontal = Spazio.l), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icone.Cambio, contentDescription = null, tint = stato.colore(), modifier = Modifier.size(16.dp))
                Text("$minuti min", style = Testo.etichetta, color = stato.colore())
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                Text("riparti", style = Testo.micro, color = tb.ter)
                Text(binPartenza?.let { "binario $it" } ?: "—", style = Testo.etichetta, color = tb.sub)
            }
        }
        // il tempo vero per cambiare, se i ritardi l'hanno spostato: è quello che decide se ce la fai
        when {
            stato == StatoCambio.Persa -> Text(
                "Coincidenza persa con i ritardi attuali", style = Testo.micro, color = tb.rosso,
                textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp),
            )
            live != null && live != minuti -> Text(
                "con i ritardi $live′", style = Testo.micro, color = stato.colore(),
                textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp),
            )
        }
        inMezzo.joinToString(" · ") { "${it.categoria.lowercase()} fino a ${it.nomeArrivo}" }.ifEmpty { null }?.let {
            Text(it, style = Testo.micro, color = tb.sub, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
