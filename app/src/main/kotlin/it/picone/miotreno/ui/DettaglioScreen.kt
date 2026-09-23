package it.picone.miotreno.ui

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import it.picone.miotreno.domain.DettaglioTreno
import it.picone.miotreno.domain.FermataTreno
import it.picone.miotreno.domain.ProssimoTreno
import it.picone.miotreno.domain.orario
import it.picone.miotreno.domain.Semaforo
import it.picone.miotreno.domain.etichettaStato
import it.picone.miotreno.domain.semaforo
import it.picone.miotreno.ui.componenti.AnimatedCountdown
import it.picone.miotreno.ui.componenti.Binario
import it.picone.miotreno.ui.componenti.BottoneIcona
import it.picone.miotreno.ui.componenti.BottonePrimario
import it.picone.miotreno.ui.componenti.Chip
import it.picone.miotreno.ui.componenti.DeltaRitardo
import it.picone.miotreno.ui.componenti.GlassCard
import it.picone.miotreno.ui.componenti.RigaAtteso
import it.picone.miotreno.ui.componenti.Icone
import it.picone.miotreno.ui.componenti.StatoVuoto
import it.picone.miotreno.ui.componenti.StatusBadge
import it.picone.miotreno.ui.componenti.TimelineStop
import it.picone.miotreno.ui.componenti.TipoFermata
import it.picone.miotreno.ui.componenti.colore
import it.picone.miotreno.ui.componenti.comeOra
import it.picone.miotreno.ui.componenti.conferma
import it.picone.miotreno.ui.componenti.rememberHaptic
import it.picone.miotreno.ui.componenti.shimmer
import it.picone.miotreno.ui.theme.Forme
import it.picone.miotreno.ui.theme.LocalTb
import it.picone.miotreno.ui.theme.Molla
import it.picone.miotreno.ui.theme.Spazio
import it.picone.miotreno.ui.theme.Testo

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun DettaglioScreen(
    state: UiState,
    ora: Long,
    sharedScope: SharedTransitionScope,
    animatedScope: AnimatedContentScope,
    onIndietro: () -> Unit,
    onSegui: () -> Unit,
    onNotifiche: (Boolean) -> Unit,
    onChiediNotifiche: () -> Unit = {},
) {
    val tb = LocalTb.current
    val treno = state.trenoSelezionato ?: return
    val dettaglio = state.dettaglio
    val seguito = state.seguito?.numeroTreno == treno.numeroTreno
    val notificaAttiva = seguito && state.impostazioni.notifiche
    val haptic = rememberHaptic()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Spazio.pagina, end = Spazio.pagina, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(Spazio.m),
    ) {
        item(key = "barra") {
            Row(
                Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(top = Spazio.s),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BottoneIcona(Icone.Indietro, "Indietro", onIndietro)
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Chip(treno.categoria, colore = tb.accento2, pieno = true)
                        Text("${treno.numeroTreno}", style = Testo.etichettaBold, color = tb.tx)
                    }
                    Text("per ${treno.destinazione}", style = Testo.etichetta, color = tb.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        item(key = chiaveTreno(treno.numeroTreno)) {
            with(sharedScope) {
                CardIntestazione(
                    treno, ora, seguito,
                    Modifier.sharedBounds(rememberSharedContentState(chiaveTreno(treno.numeroTreno)), animatedScope),
                    atteso = state.ritardiAttesi[treno.numeroTreno]?.testo,
                    destinazioneNome = state.destinazioneNome,
                )
            }
        }

        item(key = "cta") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spazio.s)) {
                BottonePrimario(
                    if (seguito) "Seguo" else "Segui",
                    onClick = { haptic.conferma(); onSegui() },
                    icona = if (seguito) Icone.Spunta else Icone.Treno,
                    attivo = seguito,
                    modifier = Modifier.weight(1f),
                )
                BottonePrimario(
                    if (notificaAttiva) "Avviso attivo" else "Avvisami ${state.impostazioni.anticipoMinuti}′",
                    onClick = {
                        haptic.conferma()
                        // il permesso notifiche si chiede qui, la prima volta che serve davvero
                        if (!notificaAttiva) onChiediNotifiche()
                        when {
                            !seguito -> { onSegui(); if (!state.impostazioni.notifiche) onNotifiche(true) }
                            notificaAttiva -> onNotifiche(false)
                            else -> onNotifiche(true)
                        }
                    },
                    icona = Icone.Campanella,
                    attivo = notificaAttiva,
                    colore = tb.accento2,
                    modifier = Modifier.weight(1f),
                )
            }
            if (notificaAttiva) {
                val quando = treno.orarioPartenzaMs + (treno.ritardoMinuti - state.impostazioni.anticipoMinuti) * 60_000L
                Text(
                    if (state.notifichePermesse) "Ti avviso alle ${quando.comeOra()} · widget e notifica seguono questa corsa"
                    else "Notifiche bloccate dal sistema: nessun avviso, ma la corsa resta seguita qui e nel widget",
                    Modifier.fillMaxWidth().padding(top = 6.dp), style = Testo.micro,
                    color = if (state.notifichePermesse) tb.verde else tb.ambra,
                    textAlign = TextAlign.Center,
                )
            }
        }

        when {
            state.caricamentoDettaglio -> item { Caricamento() }
            dettaglio !is DettaglioTreno.Ok -> item {
                StatoVuoto(
                    Icone.Treno, "Percorso non disponibile",
                    "ViaggiaTreno non ha ancora i dati di corsa di questo treno. Di solito arrivano poco prima della partenza.",
                    colore = tb.ter,
                )
            }
            else -> {
                item(key = "avanzamento") { Avanzamento(dettaglio) }
                val fermate = dettaglio.fermate
                val codDestinazione = state.impostazioni.stazioneDestinazione
                itemsIndexed(fermate, key = { i, f -> "${f.codice}-$i" }) { i, f ->
                    val tipo = when {
                        f.codice == codDestinazione -> TipoFermata.Target
                        i == dettaglio.indiceCorrente + 1 -> TipoFermata.Prossima
                        f.passata -> TipoFermata.Passata
                        else -> TipoFermata.Futura
                    }
                    TimelineStop(
                        nome = f.nome, tipo = tipo,
                        orario = f.orario(dettaglio.ritardoMinuti),
                        primo = i == 0,
                        ultimo = i == fermate.lastIndex,
                        nota = null,
                    )
                }
                item(key = "nota") {
                    Text(
                        "aggiornamento automatico ogni 30 s",
                        Modifier.fillMaxWidth().padding(top = Spazio.l).windowInsetsPadding(WindowInsets.navigationBars),
                        style = Testo.micro, color = tb.ter, textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/**
 * La card che arriva dalla home espandendosi. Stato terminale (cancellato) = card spenta,
 * badge rosso pieno, orario barrato, nessun countdown.
 */
@Composable
private fun CardIntestazione(
    treno: ProssimoTreno, ora: Long, seguito: Boolean, modifier: Modifier, atteso: String?,
    destinazioneNome: String?,
) {
    val tb = LocalTb.current
    val semaforo = treno.semaforo()
    val cancellato = semaforo == Semaforo.Cancellato
    GlassCard(
        modifier.fillMaxWidth().let { if (cancellato) it.alpha(0.6f) else it },
        accento = seguito,
        padding = 20.dp,
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text("Partenza", style = Testo.overline, color = tb.ter)
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        treno.orarioPartenzaMs.comeOra(), style = Testo.display, color = tb.tx,
                        textDecoration = if (cancellato) TextDecoration.LineThrough else null,
                    )
                    if (!cancellato && treno.ritardoMinuti != 0) DeltaRitardo(treno.ritardoMinuti, Modifier.padding(bottom = 10.dp))
                }
            }
            if (!cancellato) AnimatedCountdown(treno.minutiAllaPartenza(ora))
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Binario(treno, grande = true)
            Text(
                if (treno.binarioConfermato) "binario confermato" else "binario previsto",
                style = Testo.etichetta, color = tb.sub,
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatusBadge(semaforo, treno.etichettaStato())
            Text(
                "a ${destinazioneNome ?: "destinazione"} · ${treno.orarioArrivoBustoMs?.comeOra() ?: "—"}",
                style = Testo.etichetta, color = tb.sub, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (atteso != null && !cancellato) RigaAtteso(atteso)
    }
}

@Composable
private fun Avanzamento(d: DettaglioTreno.Ok) {
    val tb = LocalTb.current
    val totale = d.fermate.size.coerceAtLeast(1)
    val fatte = (d.indiceCorrente + 1).coerceAtLeast(0)
    val ultima = d.fermate.getOrNull(d.indiceCorrente)
    val prossima = d.fermate.getOrNull(d.indiceCorrente + 1)
    val arrivo = d.fermate.getOrNull(d.indiceBusto)?.orario(d.ritardoMinuti)
    val destinazione = d.fermate.getOrNull(d.indiceBusto)?.nome ?: "destinazione"

    GlassCard(Modifier.fillMaxWidth(), padding = 14.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                val stato = when {
                    d.indiceBusto >= 0 && d.indiceCorrente >= d.indiceBusto -> "Arrivato a $destinazione"
                    d.indiceCorrente < 0 -> "In attesa di partenza"
                    prossima != null -> "In viaggio da ${ultima?.nome.orEmpty()} a ${prossima.nome}"
                    else -> "Percorso in aggiornamento"
                }
                AnimatedContent(
                    targetState = stato,
                    transitionSpec = { fadeIn(Molla.piatta()) togetherWith fadeOut(Molla.piatta()) },
                    label = "statoViaggio",
                ) { Text(it, style = Testo.etichettaBold, color = tb.tx, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                // L'orario della prossima fermata va proiettato: quello di tabella è già sbagliato
                // nel momento in cui il treno accumula ritardo.
                fun FermataTreno.oraPrevista(): String =
                    orario(d.ritardoMinuti).previstoMs?.comeOra().orEmpty()
                Text(
                    when {
                        d.indiceCorrente < 0 -> prossima?.let { "Prima fermata: ${it.nome} · ${it.oraPrevista()}" }
                        prossima != null -> "Prossima fermata: ${prossima.nome} · ${prossima.oraPrevista()}"
                        else -> "$fatte di $totale fermate confermate"
                    }.orEmpty(),
                    style = Testo.micro, color = tb.sub, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                val previsto = arrivo?.previstoMs?.comeOra()
                Text(
                    when {
                        previsto == null -> "—"
                        arrivo.confermato -> previsto
                        else -> "≈$previsto"
                    },
                    style = Testo.numero, color = tb.verde,
                )
                val programmata = arrivo?.programmataMs
                if (arrivo != null && arrivo.inRitardo && programmata != null) {
                    Text(
                        programmata.comeOra(), style = Testo.micro, color = tb.ter,
                        textDecoration = TextDecoration.LineThrough,
                    )
                }
                Text("arrivo", style = Testo.micro, color = tb.ter)
            }
        }
    }
}

@Composable
private fun Caricamento() {
    val tb = LocalTb.current
    Column(Modifier.fillMaxWidth().padding(top = Spazio.s)) {
        Text("Carico il percorso…", style = Testo.etichetta, color = tb.sub, modifier = Modifier.padding(bottom = Spazio.m))
        repeat(3) { i ->
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).shimmer(tb, i * 90))
                Spacer(Modifier.width(14.dp))
                Box(Modifier.weight(1f).height(16.dp).clip(Forme.chip).shimmer(tb, i * 90))
            }
        }
    }
}
