package it.picone.miotreno.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import it.picone.miotreno.domain.PeriodoFiltro
import it.picone.miotreno.domain.Statistiche
import it.picone.miotreno.ui.componenti.BarraOrizzontale
import it.picone.miotreno.ui.componenti.ChipFiltro
import it.picone.miotreno.ui.componenti.GlassCard
import it.picone.miotreno.ui.componenti.Icone
import it.picone.miotreno.ui.componenti.NumeroAnimato
import it.picone.miotreno.ui.componenti.Overline
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

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Spazio.pagina, end = Spazio.pagina, bottom = PADDING_BARRA),
        verticalArrangement = Arrangement.spacedBy(Spazio.l),
    ) {
        item {
            Column(Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(top = Spazio.l)) {
                Text("Ritardi", style = Testo.titolo, color = tb.tx)
                Text("Storico locale dei tuoi treni", style = Testo.etichetta, color = tb.sub)
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(PeriodoFiltro.entries) { p ->
                    ChipFiltro(p.etichetta, selezionata = p == filtroPeriodo, onClick = { onFiltroPeriodo(p) })
                }
                item {
                    ChipFiltro(
                        nomeStazioneFiltro ?: "Tutte le stazioni",
                        selezionata = nomeStazioneFiltro != null,
                        onClick = onFiltroStazione,
                        icona = Icone.Posizione,
                    )
                }
            }
        }
        if (stat == null) return@LazyColumn
        if (stat.rilevazioni == 0) {
            item {
                StatoVuoto(
                    Icone.Statistiche, "Ancora nessun dato",
                    "Ogni volta che apri l'app salvo il ritardo dei treni in lista. Dopo qualche giorno qui vedrai i pattern per giorno e fascia oraria.",
                )
            }
            return@LazyColumn
        }

        item {
            GlassCard(Modifier.fillMaxWidth(), padding = 24.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Overline("Puntualità")
                        Row(verticalAlignment = Alignment.Bottom) {
                            NumeroAnimato(
                                "${(stat.quotaEntro5Min * 100).toInt()}", stile = Testo.display,
                                colore = colorePuntualita(stat.quotaEntro5Min),
                            )
                            Text("%", style = Testo.numeroGrande, color = tb.sub, modifier = Modifier.padding(bottom = 6.dp, start = 2.dp))
                        }
                        Text("dei treni entro 5 minuti", style = Testo.etichetta, color = tb.sub)
                    }
                    Anello(stat.quotaEntro5Min.toFloat(), colorePuntualita(stat.quotaEntro5Min))
                }
                if (stat.andamento.size >= 2) {
                    Spacer(Modifier.height(Spazio.l))
                    Sparkline(stat.andamento, colore = tb.accento)
                    Text("Ritardo medio, ultimi ${stat.andamento.size} giorni", style = Testo.micro, color = tb.ter)
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spazio.m)) {
                Kpi("${stat.rilevazioni}", "Viaggi", Modifier.weight(1f))
                Kpi("${stat.stazioniCoinvolte}", "Stazioni", Modifier.weight(1f))
                Kpi(stat.ritardoMedio.minuti(), "Ritardo medio", Modifier.weight(1f), colore(stat.ritardoMedio))
            }
        }

        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Overline("Per giorno della settimana", Modifier.padding(bottom = 12.dp))
                StatChart(stat.perGiorno, colore = colore, formato = { it.minuti() })
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Overline("Per fascia oraria", Modifier.padding(bottom = 12.dp))
                StatChart(stat.perFascia, altezza = 72.dp, colore = colore, formato = { it.minuti() })
            }
        }
        if (stat.perTreno.isNotEmpty()) {
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Overline("Per treno", Modifier.padding(bottom = 12.dp))
                    val max = stat.perTreno.maxOf { it.valore }
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        stat.perTreno.forEach { BarraOrizzontale(it, max, colore(it.valore), { v -> v.minuti() }) }
                    }
                }
            }
        }
        item {
            Text(
                "Solo su questo dispositivo. Una riga per treno e giorno, conservata 3 anni.",
                style = Testo.micro, color = tb.ter,
            )
        }
    }
}

/** Tessera KPI: il valore sopra, grande; l'etichetta sotto, piccola. */
@Composable
private fun Kpi(valore: String, etichetta: String, modifier: Modifier = Modifier, colore: Color = LocalTb.current.tx) {
    GlassCard(modifier, padding = Spazio.l) {
        NumeroAnimato(valore, stile = Testo.numeroGrande, colore = colore)
        Text(etichetta, style = Testo.etichetta, color = LocalTb.current.sub, maxLines = 1)
    }
}

/** Anello di avanzamento sottile per la quota di puntualità. */
@Composable
private fun Anello(quota: Float, colore: Color) {
    val tb = LocalTb.current
    val entrata = remember { Animatable(0f) }
    LaunchedEffect(quota) { entrata.animateTo(quota.coerceIn(0f, 1f), Molla.ui()) }
    Canvas(Modifier.size(72.dp)) {
        val spessore = 8.dp.toPx()
        val stile = Stroke(spessore, cap = StrokeCap.Round)
        val inset = spessore / 2
        val area = Size(size.width - spessore, size.height - spessore)
        drawArc(tb.sf2, 0f, 360f, false, Offset(inset, inset), area, style = stile)
        drawArc(colore, -90f, 360f * entrata.value, false, Offset(inset, inset), area, style = stile)
    }
}
