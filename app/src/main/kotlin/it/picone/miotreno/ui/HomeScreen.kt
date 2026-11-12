package it.picone.miotreno.ui

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import it.picone.miotreno.data.URL_TRENI_GARANTITI
import it.picone.miotreno.domain.ProssimoTreno
import it.picone.miotreno.domain.Sciopero
import it.picone.miotreno.domain.StazioneCorrente
import it.picone.miotreno.domain.TrenoSeguito
import it.picone.miotreno.domain.comeOra
import it.picone.miotreno.domain.statoCorsa
import it.picone.miotreno.domain.trenoInEvidenza
import it.picone.miotreno.ui.componenti.BottoneIcona
import it.picone.miotreno.ui.componenti.GlassCard
import it.picone.miotreno.ui.componenti.Icone
import it.picone.miotreno.ui.componenti.Overline
import it.picone.miotreno.ui.componenti.PullRefresh
import it.picone.miotreno.ui.componenti.StatoVuoto
import it.picone.miotreno.ui.componenti.TrainCard
import it.picone.miotreno.ui.componenti.conferma
import it.picone.miotreno.ui.componenti.distanzaLeggibile
import it.picone.miotreno.ui.componenti.rememberHaptic
import it.picone.miotreno.ui.componenti.shimmer
import it.picone.miotreno.ui.componenti.tocco
import it.picone.miotreno.ui.theme.Forme
import it.picone.miotreno.ui.theme.LocalTb
import it.picone.miotreno.ui.theme.Molla
import it.picone.miotreno.ui.theme.Spazio
import it.picone.miotreno.ui.theme.TbColors
import it.picone.miotreno.ui.theme.Testo
import kotlinx.coroutines.delay

fun chiaveTreno(numero: Int) = "treno-$numero"

/** Altre partenze visibili mentre segui una corsa, prima di "Mostra tutte". */
internal const val ALTRE_VISIBILI = 3

/**
 * Come mostrare i treni in home. Con una corsa seguita, quella va in cima anche se parte dopo (è
 * la sola che interessa) e delle altre se ne vedono [ALTRE_VISIBILI], il resto a richiesta.
 * Le righe compatte consecutive formano un'unica card: il bordo arrotondato sta solo sopra la
 * prima e sotto l'ultima del gruppo.
 */
internal data class ListaHome(
    val visibili: List<ProssimoTreno>,
    val compatte: List<Boolean>,
    val nascoste: Int,
    val seguita: Int?,
) {
    val seguendo: Boolean get() = seguita != null
    fun inizioGruppo(i: Int) = compatte[i] && !compatte.getOrElse(i - 1) { false }
    fun fineGruppo(i: Int) = compatte[i] && !compatte.getOrElse(i + 1) { false }
}

internal fun listaHome(treni: List<ProssimoTreno>, seguito: TrenoSeguito?, mostraTutte: Boolean): ListaHome {
    val seguita = treni.firstOrNull { it.numeroTreno == seguito?.numeroTreno }
    val ordinati = if (seguita != null) listOf(seguita) + treni.filter { it !== seguita } else treni
    val evidenza = seguita ?: trenoInEvidenza(treni, seguito)
    val nascoste = if (seguita != null && !mostraTutte) (ordinati.size - 1 - ALTRE_VISIBILI).coerceAtLeast(0) else 0
    val visibili = ordinati.dropLast(nascoste)
    return ListaHome(
        visibili = visibili,
        compatte = visibili.mapIndexed { i, t -> i != 0 && t.numeroTreno != evidenza?.numeroTreno },
        nascoste = nascoste,
        seguita = seguita?.numeroTreno,
    )
}

/** Un treno della lista: titolo di sezione se è il primo del suo gruppo, poi la card o la riga. */
@Composable
private fun VoceTreno(
    t: ProssimoTreno,
    indice: Int,
    lista: ListaHome,
    state: UiState,
    ora: Long,
    onApri: (ProssimoTreno) -> Unit,
    onSmetti: (ProssimoTreno) -> Unit,
    modifier: Modifier,
    /** La transizione condivisa con il dettaglio: la fa il chiamante, che ha gli scope. */
) {
    val tb = LocalTb.current
    val haptic = rememberHaptic()
    val seguito = t.numeroTreno == lista.seguita
    val compatta = lista.compatte[indice]
    val inizioGruppo = lista.inizioGruppo(indice)
    val fineGruppo = lista.fineGruppo(indice)
    Column(
        modifier.padding(top = if (compatta && !inizioGruppo) 0.dp else Spazio.l),
        verticalArrangement = Arrangement.spacedBy(Spazio.s),
    ) {
        if (indice == 0) Overline(if (lista.seguendo) "La tua corsa" else "Prossima partenza")
        if (indice == 1) Overline(if (lista.seguendo) "Altre partenze" else "Partenze successive", Modifier.padding(top = Spazio.s))
        TrainCard(
            t, ora, seguito = seguito,
            onClick = { haptic.tocco(); onApri(t) },
            compatta = compatta,
            dettaglio = state.dettagliViaggio[t.numeroTreno],
            atteso = state.ritardiAttesi[t.numeroTreno]?.testo,
            passaPerEtichetta = state.stazionePassaggioNome?.takeIf { t.numeroTreno in state.treniConPassaggio },
            partenzaEtichetta = state.etichettePartenza[t.codPartenza]?.takeIf { it.isNotBlank() },
            codiciSalita = state.stazione?.codici.orEmpty(),
            cambio = state.cambioSeguito.takeIf { seguito },
            statoCorsa = if (seguito) {
                statoCorsa(state.trattaSeguita ?: t, state.cambioSeguito, state.destinazioneNome ?: "destinazione", ora)
            } else {
                null
            },
            onSmetti = if (seguito) ({ haptic.conferma(); onSmetti(t) }) else null,
            modifier = (if (compatta) Modifier.gruppo(tb, inizioGruppo, fineGruppo) else Modifier),
        )
    }
}

/** Le altre partenze nascoste mentre segui una corsa: un tocco e compaiono tutte. */
@Composable
private fun MostraTutte(quante: Int, onClick: () -> Unit) {
    val tb = LocalTb.current
    Text(
        "Mostra tutte ($quante)",
        Modifier.fillMaxWidth().padding(top = Spazio.s).clip(Forme.chip)
            .semantics { role = Role.Button }
            .clickable(onClick = onClick).padding(vertical = Spazio.s),
        style = Testo.etichettaBold, color = tb.accento, textAlign = TextAlign.Center,
    )
}

@Composable
fun HomeScreen(
    state: UiState,
    ora: Long,
    onApri: (ProssimoTreno) -> Unit,
    /** Smetti di seguire, dall'azione rapida sulla card della corsa seguita. */
    onSmetti: (ProssimoTreno) -> Unit = {},
    onChiediPermesso: () -> Unit,
    permessoBloccato: Boolean = false,
    onRiprova: () -> Unit,
    onRefresh: () -> Unit,
    onScegliStazione: () -> Unit,
    onScambia: () -> Unit,
    onScegliDestinazione: () -> Unit,
    onAttivaCambi: () -> Unit = {},
) {
    val tb = LocalTb.current
    val haptic = rememberHaptic()

    // haptic solo sul refresh chiesto dall'utente, non sul polling silenzioso
    var tirato by remember { mutableStateOf(false) }
    LaunchedEffect(state.aggiornatoAlle) {
        if (tirato && state.aggiornatoAlle != null) {
            haptic.conferma()
            tirato = false
        }
    }

    // mentre segui una corsa le altre partenze sono contorno: ne bastano poche, il resto a richiesta
    var mostraTutte by rememberSaveable { mutableStateOf(false) }

    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scroll.nestedScrollConnection),
        topBar = { BarraPercorso(state, scroll, onScegliStazione, onScegliDestinazione, onScambia) },
        containerColor = MaterialTheme.colorScheme.surface,
        // la barra di navigazione sotto gestisce già il suo inset
        contentWindowInsets = WindowInsets(0),
    ) { interno ->
        PullRefresh(
            caricamento = state.caricamento && state.treni.isNotEmpty(),
            onRefresh = { tirato = true; onRefresh() },
            modifier = Modifier.padding(interno).fillMaxSize(),
        ) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = Spazio.pagina, end = Spazio.pagina, bottom = Spazio.xl),
            ) {
                state.sciopero?.let { item(key = "sciopero") { BannerSciopero(it, Modifier.padding(top = Spazio.s)) } }

                when {
                    state.permessoNegato && state.impostazioni.stazioneManuale == null -> item(key = "posizione") {
                        // il dialog di sistema parte da solo; resta solo se negato, con le vie d'uscita
                        LaunchedEffect(Unit) { if (!permessoBloccato) onChiediPermesso() }
                        StatoVuoto(
                            Icone.Posizione, "Posizione non disponibile",
                            if (permessoBloccato) {
                                "Permesso negato: riattivalo da Impostazioni › App › MioTreno."
                            } else {
                                "Serve per trovare la stazione più vicina."
                            },
                            azione = if (permessoBloccato) "Apri le impostazioni" else "Attiva la posizione",
                            onAzione = onChiediPermesso,
                            azioneSecondaria = "Scegli la stazione a mano",
                            onAzioneSecondaria = onScegliStazione,
                        )
                    }
                    state.errore != null && state.treni.isEmpty() -> item {
                        StatoVuoto(
                            Icone.Sciopero, "Dati non disponibili", state.errore,
                            colore = tb.arancio, azione = "Riprova", onAzione = onRiprova,
                            azioneSecondaria = "Scegli la stazione a mano"
                                .takeIf { state.impostazioni.stazioneManuale == null },
                            onAzioneSecondaria = onScegliStazione,
                        )
                    }
                    state.caricamento && state.treni.isEmpty() -> items(3, key = {
                        "skel$it"
                    }) { Scheletro(Modifier.padding(top = Spazio.l), indice = it) }
                    state.treni.isEmpty() -> item {
                        StatoVuoto(
                            Icone.Treno, if (state.impostazioni.conCambi) "Nessun treno" else "Nessun treno diretto",
                            "Da ${state.stazione?.nome.orEmpty()} non risultano treni " +
                                (if (state.impostazioni.conCambi) "" else "diretti ") +
                                "per ${state.destinazioneNome.orEmpty()} nelle prossime ore. " +
                                if (state.impostazioni.conCambi) "Prova un'altra destinazione." else "Con un cambio potresti arrivarci.",
                            // senza diretti, la prima cosa da provare sono i cambi: un tocco, non un giro in Impostazioni
                            azione = if (state.impostazioni.conCambi) "Cambia destinazione" else "Mostra anche soluzioni con cambi",
                            onAzione = if (state.impostazioni.conCambi) onScegliDestinazione else onAttivaCambi,
                        )
                    }
                    else -> {
                        val lista = listaHome(state.treni, state.seguito, mostraTutte)
                        itemsIndexed(lista.visibili, key = { _, t -> chiaveTreno(t.numeroTreno) }) { indice, t ->
                            VoceTreno(
                                t, indice, lista, state, ora,
                                onApri = onApri, onSmetti = onSmetti,
                                modifier = Modifier.animateItem(placementSpec = Molla.ui()),
                            )
                        }
                        if (lista.nascoste > 0) {
                            item(key = "mostra-tutte") { MostraTutte(lista.nascoste) { haptic.tocco(); mostraTutte = true } }
                        }
                    }
                }

                if (state.treni.isNotEmpty()) {
                    item(key = "footer") {
                        val passaggio = state.stazionePassaggioNome
                        if (passaggio != null && state.passaggioVerificato && state.treniConPassaggio.isEmpty()) {
                            // senza questa riga l'evidenziazione sembra rotta: nessun "via" e nessun perché
                            Text(
                                "Nessun treno in lista passa per $passaggio",
                                Modifier.fillMaxWidth().padding(top = Spazio.l),
                                style = Testo.etichetta, color = tb.sub, textAlign = TextAlign.Center,
                            )
                        }
                        if (state.errore != null) {
                            // refresh fallito con una lista già a video: la lista resta, ma va detto
                            Text(
                                "Aggiornamento non riuscito · Riprova",
                                Modifier.fillMaxWidth().padding(top = Spazio.l).clip(Forme.chip)
                                    .semantics { role = Role.Button }
                                    .clickable(onClick = onRiprova).padding(vertical = Spazio.s),
                                style = Testo.etichettaBold, color = tb.ambra, textAlign = TextAlign.Center,
                            )
                        }
                        Text(
                            "Dati ViaggiaTreno" + (state.aggiornatoAlle?.let { " · agg. ${it.comeOra()}" } ?: ""),
                            Modifier.fillMaxWidth().padding(top = if (state.errore != null) 0.dp else Spazio.l),
                            style = Testo.micro, color = tb.ter, textAlign = TextAlign.Center,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Lista segmentata M3 Expressive: ogni riga è una tessera, 2 dp di stacco fra l'una e l'altra,
 * angoli grandi agli estremi del gruppo e piccoli all'interno.
 */
private fun Modifier.gruppo(tb: TbColors, inizio: Boolean, fine: Boolean): Modifier {
    val grande = 24.dp
    val piccolo = 6.dp
    val forma = RoundedCornerShape(
        topStart = if (inizio) grande else piccolo, topEnd = if (inizio) grande else piccolo,
        bottomStart = if (fine) grande else piccolo, bottomEnd = if (fine) grande else piccolo,
    )
    return padding(bottom = if (fine) 0.dp else 2.dp).clip(forma).background(tb.sf2).padding(horizontal = Spazio.l)
}

/**
 * Il percorso nella barra in alto: destinazione come titolo, partenza come sottotitolo, inverti
 * a destra. Titolo e sottotitolo si toccano per cambiare stazione; la barra si riduce scorrendo.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BarraPercorso(
    state: UiState,
    scroll: TopAppBarScrollBehavior,
    onScegliStazione: () -> Unit,
    onScegliDestinazione: () -> Unit,
    onScambia: () -> Unit,
) {
    val s = state.stazione
    val partenza = s?.nome ?: if (state.caricamento) "Cerco la stazione…" else "Scegli la partenza"
    val origine = when (s?.origine) {
        StazioneCorrente.Origine.Gps -> "più vicina, ${distanzaLeggibile(s.distanzaMetri ?: 0)}"
        StazioneCorrente.Origine.Manuale -> "scelta a mano"
        StazioneCorrente.Origine.UltimaNota -> "ultima nota"
        null -> null
    }
    val destinazione = state.destinazioneNome ?: "Scegli la destinazione"
    LargeFlexibleTopAppBar(
        title = {
            Row(
                Modifier
                    .clip(Forme.cardPiccola)
                    .semantics(mergeDescendants = true) {
                        role = Role.Button; contentDescription = "Cambia destinazione, ora $destinazione"
                    }
                    .clickable(onClick = onScegliDestinazione),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(destinazione, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Icon(Icone.Frecciagiu, contentDescription = null, modifier = Modifier.padding(start = 4.dp).size(20.dp))
            }
        },
        subtitle = {
            Row(
                Modifier
                    .clip(Forme.cardPiccola)
                    .semantics(mergeDescendants = true) {
                        role = Role.Button; contentDescription =
                            "Cambia stazione di partenza, ora $partenza"
                    }
                    .clickable(onClick = onScegliStazione),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icone.Posizione, contentDescription = null, modifier = Modifier.padding(end = 4.dp).size(16.dp))
                Text(
                    "da $partenza" + (origine?.let { " · $it" } ?: ""),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        },
        actions = {
            BottoneIcona(
                Icone.Cambio, "Inverti partenza e destinazione",
                onClick = { if (s != null) onScambia() },
                modifier = Modifier.padding(end = Spazio.s).let { if (s != null) it else it.alpha(0.38f) },
            )
        },
        scrollBehavior = scroll,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    )
}

/**
 * Scheletro durante il primo caricamento: nessuno spinner al centro. La prima voce
 * mima la card hero (alta), le successive mimano le righe piatte del tabellone
 * (basse) — l'altezza deve promettere quella che poi arriva davvero, altrimenti
 * la lista "salta" quando i dati arrivano.
 */
@Composable
private fun Scheletro(modifier: Modifier = Modifier, indice: Int = 0) {
    val tb = LocalTb.current
    val ritardo = indice * 90
    if (indice == 0) {
        GlassCard(modifier.fillMaxWidth().height(248.dp)) {
            Box(Modifier.width(60.dp).height(12.dp).clip(Forme.chip).shimmer(tb, ritardo))
            Spacer(Modifier.height(14.dp))
            Box(Modifier.width(120.dp).height(28.dp).clip(Forme.chip).shimmer(tb, ritardo))
            Spacer(Modifier.height(14.dp))
            Box(Modifier.width(90.dp).height(18.dp).clip(Forme.chip).shimmer(tb, ritardo))
        }
    } else {
        Row(
            modifier.fillMaxWidth().clip(Forme.card).background(tb.sf).height(64.dp).padding(horizontal = 16.dp, vertical = 20.dp),
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
private fun BannerSciopero(sciopero: Sciopero, modifier: Modifier = Modifier) {
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
    AnimatedVisibility(
        visible = visibile, modifier = modifier,
        enter = fadeIn(Molla.piatta()) + slideInVertically(Molla.ui()) {
            -it / 3
        },
    ) {
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
                .border(1.dp, tb.sciopero.copy(alpha = 0.3f), Forme.card)
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
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
