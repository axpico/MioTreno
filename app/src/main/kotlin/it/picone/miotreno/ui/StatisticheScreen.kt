package it.picone.miotreno.ui

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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import it.picone.miotreno.domain.PeriodoFiltro
import it.picone.miotreno.domain.Statistiche
import it.picone.miotreno.ui.componenti.BarraOrizzontale
import it.picone.miotreno.ui.componenti.GlassCard
import it.picone.miotreno.ui.componenti.Icone
import it.picone.miotreno.ui.componenti.NumeroAnimato
import it.picone.miotreno.ui.componenti.Overline
import it.picone.miotreno.ui.componenti.Sparkline
import it.picone.miotreno.ui.componenti.StatChart
import it.picone.miotreno.ui.componenti.StatoVuoto
import it.picone.miotreno.ui.theme.LocalTb
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
            Column(Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(top = Spazio.m)) {
                Overline("Storico locale")
                Text("Ritardi", style = Testo.titolo, color = tb.tx)
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(PeriodoFiltro.entries) { p ->
                    FilterChip(selected = p == filtroPeriodo, onClick = { onFiltroPeriodo(p) }, label = { Text(p.etichetta) })
                }
                item {
                    FilterChip(
                        selected = nomeStazioneFiltro != null,
                        onClick = onFiltroStazione,
                        label = { Text(nomeStazioneFiltro ?: "Tutte le stazioni") },
                        leadingIcon = { Icon(Icone.Posizione, contentDescription = null, modifier = Modifier.height(16.dp)) },
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
            GlassCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Overline("Viaggi")
                        NumeroAnimato("${stat.rilevazioni}", stile = Testo.numeroGrande, colore = tb.tx)
                    }
                    Column(Modifier.weight(1f)) {
                        Overline("Stazioni")
                        NumeroAnimato("${stat.stazioniCoinvolte}", stile = Testo.numeroGrande, colore = tb.tx)
                    }
                }
            }
        }

        item {
            GlassCard(Modifier.fillMaxWidth(), padding = 20.dp) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Overline("Puntualità")
                        Row(verticalAlignment = Alignment.Bottom) {
                            NumeroAnimato("${(stat.quotaEntro5Min * 100).toInt()}", stile = Testo.display, colore = colorePuntualita(stat.quotaEntro5Min))
                            Text("%", style = Testo.numeroGrande, color = tb.sub, modifier = Modifier.padding(bottom = 6.dp, start = 2.dp))
                        }
                        Text("entro 5 minuti · ${stat.rilevazioni} rilevazioni", style = Testo.etichetta, color = tb.sub)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Overline("Ritardo medio")
                        Text(stat.ritardoMedio.minuti(), style = Testo.numeroGrande, color = colore(stat.ritardoMedio))
                    }
                }
                if (stat.andamento.size >= 2) {
                    Spacer(Modifier.height(12.dp))
                    Sparkline(stat.andamento, colore = tb.accento)
                    Text("ritardo medio, ultimi ${stat.andamento.size} giorni", style = Testo.micro, color = tb.ter)
                }
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
        if (stat.perTreno.isNotEmpty()) item {
            GlassCard(Modifier.fillMaxWidth()) {
                Overline("Per treno", Modifier.padding(bottom = 12.dp))
                val max = stat.perTreno.maxOf { it.valore }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    stat.perTreno.forEach { BarraOrizzontale(it, max, colore(it.valore), { v -> v.minuti() }) }
                }
            }
        }
        item {
            Text("Solo su questo dispositivo. Una riga per treno e giorno, conservata 3 anni.",
                style = Testo.micro, color = tb.ter)
        }
    }
}
