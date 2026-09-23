package it.picone.miotreno.ui

import android.content.Intent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import it.picone.miotreno.data.AdsManager
import it.picone.miotreno.data.URL_TRENI_GARANTITI
import it.picone.miotreno.domain.ProssimoTreno
import it.picone.miotreno.domain.Sciopero
import it.picone.miotreno.domain.StazioneCorrente
import it.picone.miotreno.domain.trenoInEvidenza
import it.picone.miotreno.ui.componenti.BottoneIcona
import it.picone.miotreno.ui.componenti.Chip
import it.picone.miotreno.ui.componenti.GlassCard
import it.picone.miotreno.ui.componenti.Icone
import it.picone.miotreno.ui.componenti.Overline
import it.picone.miotreno.ui.componenti.PullRefresh
import it.picone.miotreno.ui.componenti.StatoVuoto
import it.picone.miotreno.ui.componenti.TrainCard
import it.picone.miotreno.ui.componenti.comeOra
import it.picone.miotreno.ui.componenti.conferma
import it.picone.miotreno.ui.componenti.distanzaLeggibile
import it.picone.miotreno.ui.componenti.rememberHaptic
import it.picone.miotreno.ui.componenti.shimmer
import it.picone.miotreno.ui.componenti.tocco
import it.picone.miotreno.ui.theme.Forme
import it.picone.miotreno.ui.theme.LocalTb
import it.picone.miotreno.ui.theme.Molla
import it.picone.miotreno.ui.theme.Spazio
import it.picone.miotreno.ui.theme.Testo
import kotlinx.coroutines.delay

/** Spazio in fondo alla lista perché l'ultima card non finisca sotto la barra flottante. */
val PADDING_BARRA = 112.dp

fun chiaveTreno(numero: Int) = "treno-$numero"

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun HomeScreen(
    state: UiState,
    ora: Long,
    sharedScope: SharedTransitionScope,
    animatedScope: AnimatedContentScope,
    onApri: (ProssimoTreno) -> Unit,
    onChiediPermesso: () -> Unit,
    permessoBloccato: Boolean = false,
    onRiprova: () -> Unit,
    onRefresh: () -> Unit,
    onScegliStazione: () -> Unit,
    onScambia: () -> Unit,
) {
    val tb = LocalTb.current
    val haptic = rememberHaptic()

    // haptic solo sul refresh chiesto dall'utente, non sul polling silenzioso
    var tirato by remember { mutableStateOf(false) }
    LaunchedEffect(state.aggiornatoAlle) {
        if (tirato && state.aggiornatoAlle != null) { haptic.conferma(); tirato = false }
    }

    Column(Modifier.fillMaxSize()) {
        PullRefresh(
            caricamento = state.caricamento && state.treni.isNotEmpty(),
            onRefresh = { tirato = true; onRefresh() },
            modifier = Modifier.weight(1f),
        ) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = Spazio.pagina, end = Spazio.pagina, bottom = PADDING_BARRA),
                verticalArrangement = Arrangement.spacedBy(Spazio.m),
            ) {
                item(key = "header") {
                    Intestazione(state, onScegliStazione, onScambia)
                }
                state.sciopero?.let { item(key = "sciopero") { BannerSciopero(it) } }

                when {
                    state.permessoNegato && state.impostazioni.stazioneManuale == null -> item(key = "priming") {
                        PrimingPosizione(permessoBloccato, onContinua = onChiediPermesso, onScegliStazione = onScegliStazione)
                    }
                    state.errore != null && state.treni.isEmpty() -> item {
                        StatoVuoto(
                            Icone.Sciopero, "Dati non disponibili", state.errore,
                            colore = tb.arancio, azione = "Riprova", onAzione = onRiprova,
                        )
                    }
                    state.caricamento && state.treni.isEmpty() -> items(3, key = { "skel$it" }) { Scheletro(it) }
                    state.treni.isEmpty() -> item {
                        StatoVuoto(
                            Icone.Treno, "Nessun treno diretto",
                            "Da ${state.stazione?.nome.orEmpty()} non risultano treni diretti a " +
                                "${state.destinazioneNome.orEmpty()} nelle prossime ore.",
                            azione = "Aggiorna", onAzione = onRiprova,
                        )
                    }
                    else -> {
                        val evidenza = trenoInEvidenza(state.treni, state.seguito)
                        itemsIndexed(state.treni, key = { _, t -> chiaveTreno(t.numeroTreno) }) { indice, t ->
                            val seguito = state.seguito?.numeroTreno == t.numeroTreno
                            Column(verticalArrangement = Arrangement.spacedBy(Spazio.s)) {
                                if (indice == 0) Overline(if (seguito) "La corsa che segui" else "Prossima partenza")
                                if (indice == 1) Overline("Partenze successive", Modifier.padding(top = Spazio.s))
                                with(sharedScope) {
                                    TrainCard(
                                        t, ora, seguito = seguito,
                                        onClick = { haptic.tocco(); onApri(t) },
                                        principale = indice == 0,
                                        compatta = indice != 0 && t.numeroTreno != evidenza?.numeroTreno,
                                        dettaglio = state.dettagliViaggio[t.numeroTreno],
                                        atteso = state.ritardiAttesi[t.numeroTreno]?.testo,
                                        passaPerEtichetta = state.stazionePassaggioNome
                                            ?.takeIf { t.numeroTreno in state.treniConPassaggio },
                                        partenzaEtichetta = state.etichettePartenza[t.codPartenza],
                                        modifier = Modifier.animateItem(placementSpec = Molla.ui()).sharedBounds(
                                            rememberSharedContentState(chiaveTreno(t.numeroTreno)), animatedScope,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }

                if (state.treni.isNotEmpty()) item(key = "footer") {
                    Text(
                        "Dati ViaggiaTreno" + (state.aggiornatoAlle?.let { " · agg. ${it.comeOra()}" } ?: ""),
                        Modifier.fillMaxWidth().padding(top = Spazio.s),
                        style = Testo.micro, color = tb.ter,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (state.impostazioni.adsAbilitate && AdsManager.canRequestAds()) AdMobBanner()
    }
}

/** Banner fisso in fondo alla home. `AndroidView` basta: nessuna libreria Compose-Ads in più. */
@Composable
private fun AdMobBanner() {
    val context = LocalContext.current
    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = {
            AdView(context).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = AdsManager.AD_UNIT_BANNER_ID
                loadAd(AdRequest.Builder().build())
            }
        },
        onRelease = { it.destroy() },
    )
}

@Composable
private fun Intestazione(state: UiState, onScegliStazione: () -> Unit, onScambia: () -> Unit) {
    val tb = LocalTb.current
    val s = state.stazione
    Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(top = Spazio.m)) {
        Overline("Il tuo viaggio")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Verso ${state.destinazioneNome ?: "…"}", style = Testo.titolo, color = tb.tx,
                modifier = Modifier.weight(1f),
            )
            BottoneIcona(
                Icone.Cambio, "Inverti partenza e destinazione",
                onClick = { if (s != null) onScambia() },
                modifier = if (s != null) Modifier else Modifier.alpha(0.35f),
            )
        }
        Spacer(Modifier.height(Spazio.s))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(Forme.cardPiccola)
                .background(tb.sf)
                .border(1.dp, tb.bordo, Forme.cardPiccola)
                .semantics { role = Role.Button; contentDescription = "Cambia stazione" }
                .clickable(onClick = onScegliStazione)
                .padding(horizontal = Spazio.m, vertical = Spazio.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Parti da", style = Testo.micro, color = tb.ter)
                Text(
                    s?.nome ?: if (state.caricamento) "Cerco la stazione…" else "Stazione",
                    style = Testo.sottotitolo,
                    color = tb.tx, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 4.dp)) {
                    Icon(Icone.Posizione, null, tint = tb.accento2, modifier = Modifier.size(14.dp))
                    Text(
                        when (s?.origine) {
                            StazioneCorrente.Origine.Gps -> "più vicina · ${distanzaLeggibile(s.distanzaMetri ?: 0)}"
                            StazioneCorrente.Origine.Manuale -> "scelta a mano"
                            StazioneCorrente.Origine.UltimaNota -> "ultima nota"
                            null -> "in attesa del GPS"
                        },
                        style = Testo.etichetta, color = tb.sub,
                    )
                }
            }
            Icon(
                Icone.Avanti, contentDescription = null, tint = tb.accento,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * Scheletro durante il primo caricamento: nessuno spinner al centro. La prima voce
 * mima la card hero (alta), le successive mimano le righe piatte del tabellone
 * (basse) — l'altezza deve promettere quella che poi arriva davvero, altrimenti
 * la lista "salta" quando i dati arrivano.
 */
@Composable
private fun Scheletro(indice: Int = 0) {
    val tb = LocalTb.current
    val ritardo = indice * 90
    if (indice == 0) {
        GlassCard(Modifier.fillMaxWidth().height(120.dp)) {
            Box(Modifier.width(60.dp).height(12.dp).clip(Forme.chip).shimmer(tb, ritardo))
            Spacer(Modifier.height(14.dp))
            Box(Modifier.width(120.dp).height(28.dp).clip(Forme.chip).shimmer(tb, ritardo))
            Spacer(Modifier.height(14.dp))
            Box(Modifier.width(90.dp).height(18.dp).clip(Forme.chip).shimmer(tb, ritardo))
        }
    } else {
        Row(
            Modifier.fillMaxWidth().height(52.dp).padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(48.dp).height(18.dp).clip(Forme.chip).shimmer(tb, ritardo))
            Spacer(Modifier.width(18.dp))
            Box(Modifier.weight(1f).height(16.dp).clip(Forme.chip).shimmer(tb, ritardo))
            Spacer(Modifier.width(18.dp))
            Box(Modifier.width(36.dp).height(16.dp).clip(Forme.chip).shimmer(tb, ritardo))
        }
    }
}

/** Banner sciopero: colore e trama a righe propri, distinti dal semaforo dei ritardi. */
@Composable
private fun BannerSciopero(sciopero: Sciopero) {
    val tb = LocalTb.current
    val context = LocalContext.current
    val haptic = rememberHaptic()
    val quando = if (sciopero.dataInizio <= java.time.LocalDate.now().toString()) "oggi" else "domani"

    var visibile by remember { mutableStateOf(false) }
    LaunchedEffect(sciopero) {
        delay(150)
        visibile = true
        haptic.conferma()
    }
    AnimatedVisibility(visible = visibile, enter = fadeIn(Molla.piatta()) + slideInVertically(Molla.ui()) { -it / 3 }) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(Forme.card)
                .background(tb.sciopero.copy(alpha = 0.12f))
                .drawBehind {
                    // righe diagonali: un pattern, non solo un colore
                    val passo = 14.dp.toPx()
                    var x = -size.height
                    while (x < size.width) {
                        drawLine(tb.sciopero.copy(alpha = 0.07f), Offset(x, size.height), Offset(x + size.height, 0f), 6.dp.toPx())
                        x += passo * 2
                    }
                }
                .border(1.dp, tb.sciopero.copy(alpha = 0.45f), Forme.card)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(Icone.Sciopero, contentDescription = "Sciopero", tint = tb.sciopero, modifier = Modifier.size(28.dp))
            Column(Modifier.weight(1f)) {
                Text("Sciopero ferroviario $quando", style = Testo.sottotitolo, color = tb.tx)
                Text(
                    sciopero.modalita.ifBlank { "Settore ferroviario, ${sciopero.rilevanza}." }.frase(),
                    style = Testo.etichetta, color = tb.sub, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "Treni garantiti ↗",
                    Modifier.padding(top = 6.dp).clip(Forme.chip)
                        .semantics { role = Role.Button }
                        .clickable { context.startActivity(Intent(Intent.ACTION_VIEW, URL_TRENI_GARANTITI.toUri())) }
                        .padding(vertical = 4.dp),
                    style = Testo.etichettaBold, color = tb.sciopero,
                )
            }
        }
    }
}

/** Il feed MIT urla in maiuscolo: prima lettera maiuscola, il resto minuscolo. */
private fun String.frase(): String = lowercase().replaceFirstChar { it.uppercase() }
