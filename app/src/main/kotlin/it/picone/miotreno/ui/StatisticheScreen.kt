package it.picone.miotreno.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import it.picone.miotreno.domain.PeriodoFiltro
import it.picone.miotreno.domain.Statistiche
import it.picone.miotreno.ui.componenti.BarraOrizzontale
import it.picone.miotreno.ui.componenti.ChipFiltro
import it.picone.miotreno.ui.componenti.GlassCard
import it.picone.miotreno.ui.componenti.Icone
import it.picone.miotreno.ui.componenti.NumeroAnimato
import it.picone.miotreno.ui.componenti.Overline
import it.picone.miotreno.ui.componenti.SchermataConBarra
import it.picone.miotreno.ui.componenti.Sparkline
import it.picone.miotreno.ui.componenti.StatChart
import it.picone.miotreno.ui.componenti.StatoVuoto
import it.picone.miotreno.ui.theme.LocalTb
import it.picone.miotreno.ui.theme.Molla
import it.picone.miotreno.ui.theme.Spazio
import it.picone.miotreno.ui.theme.Testo
import java.util.Locale

private const val SOGLIA_AMBRA = 5.0
private const val SOGLIA_ARANCIO = 10.0

/** Puntualità: verde da 80%, ambra da 60%, sotto arancio. */
@Composable
private fun colorePuntualita(quota: Double): Color = LocalTb.current.let {
    when {
        quota >= 0.8 -> it.verde
        quota >= 0.6 -> it.ambra
        else -> it.arancio
    }
}

private fun Double.minuti(): String = String.format(Locale.ITALY, "%.1f′", this)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticheScreen(
    stat: Statistiche?,
    filtroPeriodo: PeriodoFiltro = PeriodoFiltro.Sempre,
    onFiltroPeriodo: (PeriodoFiltro) -> Unit = {},
    nomeStazioneFiltro: String? = null,
    onFiltroStazione: () -> Unit = {},
) {
    val tb = LocalTb.current
    val colore: (Double) -> Color = { v ->
        when {
            v >= SOGLIA_ARANCIO -> tb.arancio
            v >= SOGLIA_AMBRA -> tb.ambra
            else -> tb.verde
        }
    }

    SchermataConBarra("Ritardi", "Storico locale dei tuoi treni") {
        item {
            // vanno a capo invece di scorrere: in riga "Ultimo anno" e la stazione restavano fuori schermo
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PeriodoFiltro.entries.forEach { p ->
                    ChipFiltro(p.etichetta, selezionata = p == filtroPeriodo, onClick = { onFiltroPeriodo(p) })
                }
                ChipFiltro(
                    nomeStazioneFiltro ?: "Tutte le stazioni",
                    selezionata = nomeStazioneFiltro != null,
                    onClick = onFiltroStazione,
                    icona = Icone.Posizione,
                )
            }
        }
        if (stat == null) return@SchermataConBarra
        if (stat.rilevazioni == 0) {
            item {
                StatoVuoto(
                    Icone.Statistiche, "Ancora nessun dato",
                    "Ogni volta che apri l'app salvo il ritardo dei treni in lista. Dopo qualche giorno qui vedrai i pattern per giorno e fascia oraria.",
                )
            }
            return@SchermataConBarra
        }

        item {
            GlassCard(Modifier.fillMaxWidth(), rilievo = true, padding = 24.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spazio.xl)) {
                    // la percentuale dentro l'anello: un solo oggetto da leggere, non due affiancati
                    Box(contentAlignment = Alignment.Center) {
                        Anello(stat.quotaEntro5Min.toFloat(), colorePuntualita(stat.quotaEntro5Min))
                        Row(verticalAlignment = Alignment.Bottom) {
                            NumeroAnimato(
                                "${(stat.quotaEntro5Min * 100).toInt()}", stile = Testo.hero,
                                colore = colorePuntualita(stat.quotaEntro5Min),
                            )
                            Text("%", style = Testo.numero, color = tb.sub, modifier = Modifier.padding(bottom = 4.dp))
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Overline("Puntualità")
                        Text(giudizio(stat.quotaEntro5Min), style = Testo.sottotitolo, color = tb.tx)
                        Text("treni entro 5 minuti dall'orario", style = Testo.etichetta, color = tb.sub)
                    }
                }
                if (stat.andamento.size >= 2) {
                    Spacer(Modifier.height(Spazio.l))
                    Sparkline(stat.andamento, colore = tb.accento)
                    Text("Ritardo medio, ultimi ${stat.andamento.size} giorni con dati", style = Testo.micro, color = tb.ter)
                }
            }
        }

        item {
            // altezza comune: con testo grande un'etichetta va su due righe e le tessere restano pari
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spazio.m)) {
                Kpi("${stat.rilevazioni}", "Rilevati", Modifier.weight(1f))
                Kpi("${stat.stazioniCoinvolte}", "Stazioni", Modifier.weight(1f))
                Kpi(stat.ritardoMedio.minuti(), "Ritardo medio", Modifier.weight(1f), colore(stat.ritardoMedio))
            }
        }

        item {
            // un grafico alla volta: tre carte impilate erano un muro da scorrere
            val viste = listOfNotNull("Giorno", "Fascia oraria", "Treno".takeIf { stat.perTreno.isNotEmpty() })
            var vista by rememberSaveable { mutableIntStateOf(0) }
            val scelta = vista.coerceAtMost(viste.lastIndex)
            Column(verticalArrangement = Arrangement.spacedBy(Spazio.m)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    viste.forEachIndexed { i, nome ->
                        SegmentedButton(
                            selected = i == scelta, onClick = { vista = i },
                            shape = SegmentedButtonDefaults.itemShape(i, viste.size), icon = {},
                        ) { Text(nome, style = Testo.etichettaBold, maxLines = 1) }
                    }
                }
                GlassCard(Modifier.fillMaxWidth()) {
                    val voci = when (scelta) {
                        0 -> stat.perGiorno
                        1 -> stat.perFascia
                        else -> stat.perTreno
                    }
                    // la domanda vera dietro il grafico: quando conviene evitarlo
                    voci.filter { it.rilevazioni > 0 || scelta == 2 }.maxByOrNull { it.valore }
                        ?.takeIf { it.valore > 0 }
                        ?.let { peggiore ->
                            Text(
                                "Peggiore: ${peggiore.etichetta} · ${peggiore.valore.minuti()} in media",
                                style = Testo.etichettaBold, color = colore(peggiore.valore),
                                modifier = Modifier.padding(bottom = Spazio.m),
                            )
                        }
                    when (scelta) {
                        0 -> StatChart(stat.perGiorno, colore = colore, formato = { it.minuti() })
                        1 -> StatChart(stat.perFascia, altezza = 72.dp, colore = colore, formato = { it.minuti() })
                        else -> {
                            val max = stat.perTreno.maxOf { it.valore }
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                stat.perTreno.forEach { BarraOrizzontale(it, max, colore(it.valore), { v -> v.minuti() }) }
                            }
                        }
                    }
                }
            }
        }
        item {
            Text(
                "Solo su questo dispositivo. Una riga per treno e giorno, conservata 3 anni.",
                style = Testo.micro, color = tb.ter, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Una frase al posto di un numero da interpretare. */
private fun giudizio(quota: Double): String = when {
    quota >= 0.9 -> "Quasi sempre puntuale"
    quota >= 0.75 -> "Di solito puntuale"
    quota >= 0.5 -> "Spesso in ritardo"
    else -> "Quasi sempre in ritardo"
}

/** Tessera KPI: il valore sopra, grande; l'etichetta sotto, piccola. */
@Composable
private fun Kpi(valore: String, etichetta: String, modifier: Modifier = Modifier, colore: Color = LocalTb.current.tx) {
    GlassCard(modifier.fillMaxHeight(), padding = Spazio.l) {
        // centrate: tre tessere uguali affiancate si leggono come una riga sola
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            // Next e non Mono: in una tessera non serve l'incolonnamento, e la virgola mono spaziava "1 , 2"
            NumeroAnimato(valore, stile = Testo.titolo, colore = colore)
            Text(etichetta, style = Testo.etichetta, color = LocalTb.current.sub, maxLines = 2, textAlign = TextAlign.Center)
        }
    }
}

/** Anello di avanzamento sottile per la quota di puntualità. */
@Composable
private fun Anello(quota: Float, colore: Color) {
    val tb = LocalTb.current
    val entrata = remember { Animatable(0f) }
    LaunchedEffect(quota) { entrata.animateTo(quota.coerceIn(0f, 1f), Molla.ui()) }
    // decorativo: la percentuale è già scritta accanto
    Canvas(Modifier.size(120.dp).clearAndSetSemantics { }) {
        val spessore = 12.dp.toPx()
        val stile = Stroke(spessore, cap = StrokeCap.Round)
        val inset = spessore / 2
        val area = Size(size.width - spessore, size.height - spessore)
        drawArc(tb.sf2, 0f, 360f, false, Offset(inset, inset), area, style = stile)
        drawArc(colore, -90f, 360f * entrata.value, false, Offset(inset, inset), area, style = stile)
    }
}
