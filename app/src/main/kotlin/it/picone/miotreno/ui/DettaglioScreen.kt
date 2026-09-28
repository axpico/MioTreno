package it.picone.miotreno.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import it.picone.miotreno.domain.DettaglioTreno
import it.picone.miotreno.domain.FermataTreno
import it.picone.miotreno.domain.ProssimoTreno
import it.picone.miotreno.domain.Semaforo
import it.picone.miotreno.domain.etichettaStato
import it.picone.miotreno.domain.orario
import it.picone.miotreno.domain.semaforo
import it.picone.miotreno.ui.componenti.ALPHA_TINTA
import it.picone.miotreno.ui.componenti.AnimatedCountdown
import it.picone.miotreno.ui.componenti.Binario
import it.picone.miotreno.ui.componenti.BottoneIcona
import it.picone.miotreno.ui.componenti.BottonePrimario
import it.picone.miotreno.ui.componenti.BottoneSecondario
import it.picone.miotreno.ui.componenti.Chip
import it.picone.miotreno.ui.componenti.DeltaRitardo
import it.picone.miotreno.ui.componenti.GlassCard
import it.picone.miotreno.ui.componenti.Icone
import it.picone.miotreno.ui.componenti.Overline
import it.picone.miotreno.ui.componenti.RigaAtteso
import it.picone.miotreno.ui.componenti.StatoVuoto
import it.picone.miotreno.ui.componenti.StatusBadge
import it.picone.miotreno.ui.componenti.TimelineStop
import it.picone.miotreno.ui.componenti.TipoFermata
import it.picone.miotreno.ui.componenti.colore
import it.picone.miotreno.ui.componenti.comeOra
import it.picone.miotreno.ui.componenti.conferma
import it.picone.miotreno.ui.componenti.ombraMorbida
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

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Spazio.pagina, end = Spazio.pagina, bottom = ALTEZZA_BARRA_AZIONI),
            verticalArrangement = Arrangement.spacedBy(Spazio.l),
        ) {
            item(key = "barra") {
                Row(
                    Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(top = Spazio.s),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    BottoneIcona(Icone.Indietro, "Indietro", onIndietro)
                    Column(Modifier.weight(1f)) {
                        Text("${treno.categoria} ${treno.numeroTreno}", style = Testo.sottotitolo, color = tb.tx)
                        Text(
                            "per ${treno.destinazione}", style = Testo.etichetta, color = tb.sub,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
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
                        partenzaEtichetta = state.etichettePartenza[treno.codPartenza],
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
                    item(key = "avanzamento") { Avanzamento(dettaglio, state.destinazioneNome) }
                    item(key = "fermate") { Fermate(dettaglio, state.impostazioni.stazioneDestinazione) }
                    item(key = "nota") {
                        Text(
                            "Aggiornamento automatico ogni 30 secondi",
                            Modifier.fillMaxWidth(),
                            style = Testo.micro, color = tb.ter, textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }

        BarraAzioni(
            seguito = seguito,
            notificaAttiva = notificaAttiva,
            anticipo = state.impostazioni.anticipoMinuti,
            conferma = if (notificaAttiva) {
                if (state.notifichePermesse) {
                    val quando = treno.orarioPartenzaMs + (treno.ritardoMinuti - state.impostazioni.anticipoMinuti) * 60_000L
                    "Ti avviso alle ${quando.comeOra()} · widget e notifica seguono questa corsa"
                } else {
                    "Notifiche bloccate dal sistema: la corsa resta seguita qui e nel widget"
                }
            } else {
                null
            },
            confermaOk = state.notifichePermesse,
            onSegui = { haptic.conferma(); onSegui() },
            onAvvisa = {
                haptic.conferma()
                // il permesso notifiche si chiede qui, la prima volta che serve davvero
                if (!notificaAttiva) onChiediNotifiche()
                when {
                    !seguito -> { onSegui(); if (!state.impostazioni.notifiche) onNotifiche(true) }
                    notificaAttiva -> onNotifiche(false)
                    else -> onNotifiche(true)
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/** Spazio in fondo alla lista per non finire sotto la barra azioni. */
private val ALTEZZA_BARRA_AZIONI = 160.dp

/**
 * Azioni della corsa nella zona del pollice, sempre visibili. "Segui" è l'azione primaria;
 * "Avvisami" è secondaria. Quando la corsa è seguita, il segno di spunta entra con un
 * rimbalzo: è il piccolo momento di soddisfazione dello schermo.
 */
@Composable
private fun BarraAzioni(
    seguito: Boolean,
    notificaAttiva: Boolean,
    anticipo: Int,
    conferma: String?,
    confermaOk: Boolean,
    onSegui: () -> Unit,
    onAvvisa: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tb = LocalTb.current
    val scala = remember { Animatable(1f) }
    LaunchedEffect(seguito) {
        if (!seguito || Molla.riduci) return@LaunchedEffect
        scala.snapTo(0.9f)
        scala.animateTo(1f, Molla.stato())
    }
    Column(
        modifier
            .fillMaxWidth()
            .ombraMorbida(tb, Forme.foglio, 16.dp)
            .clip(Forme.foglio)
            .background(tb.sf)
            .let { if (tb.scuro) it.border(1.dp, tb.bordo, Forme.foglio) else it }
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = Spazio.l, end = Spazio.l, top = Spazio.l, bottom = Spazio.s),
    ) {
        AnimatedVisibility(visible = conferma != null, enter = fadeIn(Molla.piatta()) + expandVertically(Molla.ui())) {
            Text(
                conferma.orEmpty(),
                Modifier.fillMaxWidth().padding(bottom = Spazio.m),
                style = Testo.etichetta, color = if (confermaOk) tb.verde else tb.ambra, textAlign = TextAlign.Center,
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spazio.s)) {
            BottonePrimario(
                if (seguito) "Seguita" else "Segui questa corsa",
                onClick = onSegui,
                icona = if (seguito) Icone.Spunta else Icone.Treno,
                attivo = seguito,
                modifier = Modifier.weight(1f).scale(scala.value),
            )
            if (notificaAttiva) {
                BottonePrimario("$anticipo′", onClick = onAvvisa, icona = Icone.Campanella, attivo = true)
            } else {
                BottoneSecondario("$anticipo′", onClick = onAvvisa, icona = Icone.Campanella)
            }
        }
    }
}

/** Le fermate in un'unica card: la linea della timeline scorre continua da una riga all'altra. */
@Composable
private fun Fermate(dettaglio: DettaglioTreno.Ok, codDestinazione: String?) {
    val fermate = dettaglio.fermate
    GlassCard(Modifier.fillMaxWidth(), padding = Spazio.l) {
        Overline("Fermate", Modifier.padding(start = 4.dp, bottom = Spazio.s))
        fermate.forEachIndexed { i, f ->
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
    partenzaEtichetta: String?,
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
            // a Garibaldi superficie e sotterranea sono due piazzali diversi: il solo
            // numero di binario non basta a sapere dove andare
            if (partenzaEtichetta != null) Chip("da $partenzaEtichetta", colore = tb.ambra)
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatusBadge(semaforo, treno.etichettaStato())
            Text(
                "a ${destinazioneNome ?: "destinazione"} · ${treno.orarioArrivoDestinazioneMs?.comeOra() ?: "—"}",
                style = Testo.etichetta, color = tb.sub, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (atteso != null && !cancellato) RigaAtteso(atteso)
    }
}

@Composable
private fun Avanzamento(d: DettaglioTreno.Ok, destinazioneNome: String?) {
    val tb = LocalTb.current
    if (d.indiceDestinazione >= 0 && d.indiceCorrente >= d.indiceDestinazione) {
        Arrivato(d.fermate[d.indiceDestinazione].nome.ifBlank { destinazioneNome.orEmpty() }, d.ritardoMinuti)
        return
    }
    val totale = d.fermate.size.coerceAtLeast(1)
    val fatte = (d.indiceCorrente + 1).coerceAtLeast(0)
    val ultima = d.fermate.getOrNull(d.indiceCorrente)
    val prossima = d.fermate.getOrNull(d.indiceCorrente + 1)
    val arrivo = d.fermate.getOrNull(d.indiceDestinazione)?.orario(d.ritardoMinuti)
    val destinazione = d.fermate.getOrNull(d.indiceDestinazione)?.nome ?: "destinazione"

    GlassCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                val stato = when {
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

/**
 * Fine viaggio: l'ultimo ricordo dell'app per questa corsa. Card verde, spunta che entra con
 * un rimbalzo, e un riepilogo onesto (in orario o quanti minuti di ritardo).
 */
@Composable
private fun Arrivato(destinazione: String, ritardo: Int) {
    val tb = LocalTb.current
    val scala = remember { Animatable(if (Molla.riduci) 1f else 0.4f) }
    LaunchedEffect(Unit) { scala.animateTo(1f, Molla.stato()) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(Forme.card)
            .background(tb.verde.copy(alpha = ALPHA_TINTA))
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spazio.l),
    ) {
        Box(
            Modifier.size(48.dp).scale(scala.value).clip(CircleShape).background(tb.verde),
            contentAlignment = Alignment.Center,
        ) { Icon(Icone.Spunta, contentDescription = null, tint = tb.suStato, modifier = Modifier.size(28.dp)) }
        Column(Modifier.weight(1f)) {
            Text("Sei arrivato a $destinazione", style = Testo.sottotitolo, color = tb.tx)
            Text(
                when {
                    ritardo <= 0 -> "In orario. Buona giornata!"
                    ritardo == 1 -> "Con 1 minuto di ritardo."
                    else -> "Con $ritardo minuti di ritardo."
                },
                style = Testo.etichetta, color = tb.verde,
            )
        }
    }
}
