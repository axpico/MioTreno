package it.picone.miotreno.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import it.picone.miotreno.domain.PeriodoFiltro
import it.picone.miotreno.domain.Statistiche
import it.picone.miotreno.domain.VoceStat
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
import kotlin.math.abs

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

/** Una frase al posto di un numero da interpretare. */
private fun giudizio(quota: Double): String = when {
    quota >= 0.9 -> "Quasi sempre puntuale"
    quota >= 0.7 -> "Di solito puntuale"
    quota >= 0.5 -> "A volte in ritardo"
    else -> "Spesso in ritardo"
}

private fun Double.minuti(): String = String.format(Locale.ITALY, "%.1f′", this)

/**
 * Tendenza dall'andamento: seconda metà dei giorni contro la prima. null con troppo pochi
 * giorni, o se la differenza è sotto il mezzo minuto (rumore, non un cambiamento).
 */
internal fun tendenza(andamento: List<Double>): Double? {
    if (andamento.size < 4) return null
    val meta = andamento.size / 2
    val d = andamento.drop(meta).average() - andamento.take(meta).average()
    return d.takeIf { abs(it) >= 0.5 }
}

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
        item { Filtri(filtroPeriodo, onFiltroPeriodo, nomeStazioneFiltro, onFiltroStazione) }
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

        item { Riepilogo(stat, colore(stat.ritardoMedio)) }

        if (stat.andamento.size >= 2) item { Andamento(stat.andamento) }

        item { Distribuzione(stat, colore) }

        if (stat.perTreno.isNotEmpty()) item { ITuoiTreni(stat.perTreno, colore) }

        item {
            Text(
                "Solo su questo dispositivo. Una riga per treno e giorno, conservata 3 anni.",
                style = Testo.micro, color = tb.ter, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Periodo come menu e stazione come selettore: due chip in una riga, non cinque su due. */
@Composable
private fun Filtri(
    periodo: PeriodoFiltro,
    onPeriodo: (PeriodoFiltro) -> Unit,
    nomeStazione: String?,
    onStazione: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
        Box {
            FilterChip(
                selected = periodo != PeriodoFiltro.Sempre,
                onClick = { menu = true },
                label = { Text(periodo.etichetta, style = Testo.etichettaBold) },
                leadingIcon = { Icon(Icone.Orologio, null, Modifier.size(18.dp)) },
                trailingIcon = { Icon(Icone.Frecciagiu, null, Modifier.size(18.dp)) },
            )
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = RoundedCornerShape(16.dp)) {
                PeriodoFiltro.entries.forEach { p ->
                    DropdownMenuItem(
                        text = { Text(p.etichetta) },
                        onClick = {
                            menu = false
                            onPeriodo(p)
                        },
                        trailingIcon = if (p == periodo) ({ Icon(Icone.Spunta, null) }) else null,
                    )
                }
            }
        }
        FilterChip(
            selected = nomeStazione != null,
            onClick = onStazione,
            label = {
                Text(nomeStazione ?: "Tutte le stazioni", style = Testo.etichettaBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            leadingIcon = { Icon(Icone.Posizione, null, Modifier.size(18.dp)) },
            trailingIcon = { Icon(Icone.Frecciagiu, null, Modifier.size(18.dp)) },
        )
    }
}

/** La risposta in alto: anello ondulato Expressive con la percentuale, giudizio, i tre numeri. */
@Composable
private fun Riepilogo(stat: Statistiche, coloreRitardo: Color) {
    val tb = LocalTb.current
    val quota = stat.quotaEntro5Min.toFloat().coerceIn(0f, 1f)
    val coloreQuota = colorePuntualita(stat.quotaEntro5Min)
    val entrata = remember { Animatable(0f) }
    LaunchedEffect(quota) { entrata.animateTo(quota, Molla.ui()) }

    GlassCard(Modifier.fillMaxWidth(), rilievo = true, padding = 24.dp) {
        // anello al centro, giudizio sotto: simmetrico come il resto della pagina
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.clearAndSetSemantics { }) {
                CircularWavyProgressIndicator(
                    progress = { entrata.value },
                    modifier = Modifier.size(148.dp),
                    color = coloreQuota,
                    trackColor = coloreQuota.copy(alpha = 0.16f),
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    NumeroAnimato("${(quota * 100).toInt()}", stile = Testo.hero, colore = coloreQuota)
                    Text("%", style = Testo.numero, color = tb.sub, modifier = Modifier.padding(bottom = 4.dp))
                }
            }
            Column(
                Modifier.padding(top = Spazio.l).semantics(mergeDescendants = true) {
                    contentDescription = "Puntualità ${(quota * 100).toInt()} per cento: ${giudizio(stat.quotaEntro5Min)}"
                },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Overline("Puntualità")
                Text(giudizio(stat.quotaEntro5Min), style = Testo.sottotitolo, color = tb.tx)
                Text("treni entro 5 minuti dall'orario", style = Testo.etichetta, color = tb.sub)
            }
        }
        // i tre numeri di contorno, in fila e divisi da un filo: una riga, non tre tessere
        Row(
            Modifier.fillMaxWidth().padding(top = Spazio.xl).height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Numero("${stat.rilevazioni}", if (stat.rilevazioni == 1) "corsa" else "corse", Modifier.weight(1f))
            Filo()
            Numero("${stat.stazioniCoinvolte}", if (stat.stazioniCoinvolte == 1) "stazione" else "stazioni", Modifier.weight(1f))
            Filo()
            Numero(stat.ritardoMedio.minuti(), "ritardo medio", Modifier.weight(1f), coloreRitardo)
        }
    }
}

@Composable
private fun Numero(valore: String, etichetta: String, modifier: Modifier, colore: Color = LocalTb.current.tx) {
    Column(modifier.semantics(mergeDescendants = true) { }, horizontalAlignment = Alignment.CenterHorizontally) {
        NumeroAnimato(valore, stile = Testo.sottotitolo, colore = colore)
        Text(etichetta, style = Testo.etichetta, color = LocalTb.current.sub, textAlign = TextAlign.Center)
    }
}

@Composable
private fun Filo() {
    Box(Modifier.width(1.dp).fillMaxHeight().padding(vertical = 4.dp).background(MaterialTheme.colorScheme.outlineVariant))
}

/** Come sta andando: la linea del ritardo medio giorno per giorno, e se migliora o peggiora. */
@Composable
private fun Andamento(andamento: List<Double>) {
    val tb = LocalTb.current
    val t = tendenza(andamento)
    GlassCard(Modifier.fillMaxWidth()) {
        // tutto sull'asse: titolo, verdetto e variazione centrati, la linea sotto a tutta larghezza
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Overline("Andamento")
            Text(
                when {
                    t == null -> "Stabile"
                    t < 0 -> "In miglioramento"
                    else -> "In peggioramento"
                },
                style = Testo.sottotitolo, color = tb.tx,
            )
            if (t != null) {
                // meno ritardo è buono: verde quando scende
                val c = if (t < 0) tb.verde else tb.arancio
                Row(
                    Modifier.padding(top = 6.dp).clip(CircleShape).background(c.copy(alpha = 0.14f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(if (t < 0) Icone.Frecciagiu else Icone.Frecciasu, null, tint = c, modifier = Modifier.size(18.dp))
                    Text(abs(t).minuti(), style = Testo.numeroPiccolo, color = c)
                }
            }
        }
        Sparkline(andamento, Modifier.padding(top = Spazio.m), colore = MaterialTheme.colorScheme.primary)
        Text(
            "Ritardo medio, ultimi ${andamento.size} giorni con dati",
            style = Testo.micro, color = tb.ter, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
    }
}

/** Quando conviene evitarlo: per giorno o per fascia oraria, un grafico alla volta. */
@Composable
private fun Distribuzione(stat: Statistiche, colore: (Double) -> Color) {
    var vista by rememberSaveable { mutableIntStateOf(0) }
    val voci = if (vista == 0) stat.perGiorno else stat.perFascia
    GlassCard(Modifier.fillMaxWidth()) {
        Overline("Quando c'è più ritardo", Modifier.fillMaxWidth(), centrato = true)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = Spazio.m, bottom = Spazio.l)) {
            listOf("Giorno", "Fascia oraria").forEachIndexed { i, nome ->
                SegmentedButton(
                    selected = i == vista, onClick = { vista = i },
                    shape = SegmentedButtonDefaults.itemShape(i, 2), icon = {},
                ) { Text(nome, style = Testo.etichettaBold, maxLines = 1) }
            }
        }
        voci.filter { it.rilevazioni > 0 }.maxByOrNull { it.valore }?.takeIf { it.valore > 0 }?.let { peggiore ->
            Text(
                "Peggiore: ${peggiore.etichetta} · ${peggiore.valore.minuti()} in media",
                style = Testo.etichettaBold, color = colore(peggiore.valore), textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = Spazio.m),
            )
        }
        StatChart(voci, altezza = if (vista == 0) 104.dp else 88.dp, colore = colore, formato = { it.minuti() })
    }
}

/** I treni che prendi più spesso, dal peggiore: il numero che conta è il ritardo medio. */
@Composable
private fun ITuoiTreni(treni: List<VoceStat>, colore: (Double) -> Color) {
    val tb = LocalTb.current
    val ordinati = treni.sortedByDescending { it.valore }
    val max = ordinati.first().valore.coerceAtLeast(1.0)
    Column {
        Overline("I tuoi treni", Modifier.fillMaxWidth().padding(bottom = Spazio.s), centrato = true)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            ordinati.forEach { v ->
                val c = colore(v.valore)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = Spazio.l, vertical = Spazio.m)
                        .semantics(mergeDescendants = true) {
                            val corse = if (v.rilevazioni == 1) "corsa" else "corse"
                            contentDescription = "${v.etichetta} delle ${v.sottotitolo}: " +
                                "${v.valore.minuti().trimEnd('′')} minuti di ritardo medio su ${v.rilevazioni} $corse"
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spazio.m),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(v.etichetta, style = Testo.corpoMedio, color = tb.tx)
                        Text(
                            "${v.sottotitolo} · ${v.rilevazioni} " + if (v.rilevazioni ==
                                1
                            ) {
                                "corsa"
                            } else {
                                "corse"
                            },
                            style = Testo.etichetta, color = tb.sub,
                        )
                        // barra proporzionale al peggiore: si confrontano a colpo d'occhio
                        Box(
                            Modifier.padding(top = 6.dp).fillMaxWidth().height(6.dp).clip(CircleShape)
                                .background(c.copy(alpha = 0.14f)),
                        ) {
                            Box(
                                Modifier.fillMaxWidth((v.valore / max).toFloat().coerceIn(0.03f, 1f)).height(6.dp)
                                    .clip(CircleShape).background(c),
                            )
                        }
                    }
                    Text(v.valore.minuti(), style = Testo.numero, color = c)
                }
            }
        }
    }
}
