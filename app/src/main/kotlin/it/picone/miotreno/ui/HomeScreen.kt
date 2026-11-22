package it.picone.miotreno.ui

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import kotlinx.coroutines.launch

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

    // Due treni che si scambiano di posto (un ritardo che cresce): la riga scivola al suo posto
    // (animateItem del chiamante) e si accende un attimo, così si nota cosa è cambiato.
    var posizione by remember { mutableIntStateOf(indice) }
    val lampo = remember { Animatable(0f) }
    LaunchedEffect(indice) {
        if (indice != posizione && !Molla.riduci) {
            lampo.snapTo(1f)
            lampo.animateTo(0f, tween(900))
        }
        posizione = indice
    }
    val coloreLampo = MaterialTheme.colorScheme.primary
    Column(
        modifier.padding(top = if (compatta && !inizioGruppo) 0.dp else Spazio.l),
        verticalArrangement = Arrangement.spacedBy(Spazio.s),
    ) {
        // titoli di sezione al centro, come la barra in alto: la pagina si legge sull'asse
        if (indice == 0) Overline(if (lista.seguendo) "La tua corsa" else "Prossima partenza", Modifier.fillMaxWidth(), centrato = true)
        if (indice == 1) {
            Overline(
                if (lista.seguendo) "Altre partenze" else "Partenze successive",
                Modifier.fillMaxWidth().padding(top = Spazio.s), centrato = true,
            )
        }
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
            destinazioneUtente = state.destinazioneNome,
            modifier = (if (compatta) Modifier.gruppo(tb, inizioGruppo, fineGruppo) else Modifier.clip(Forme.card))
                .drawWithContent {
                    drawContent()
                    if (lampo.value > 0f) drawRect(coloreLampo.copy(alpha = 0.18f * lampo.value))
                },
        )
    }
}

/** Le altre partenze nascoste mentre segui una corsa: un tocco e compaiono tutte. */
@Composable
private fun MostraTutte(quante: Int, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick, shapes = ButtonDefaults.shapes(),
        modifier = Modifier.fillMaxWidth().padding(top = Spazio.m),
    ) {
        Icon(Icone.Frecciagiu, null, Modifier.size(ButtonDefaults.IconSize))
        Text(
            if (quante == 1) "Un'altra partenza" else "Altre $quante partenze",
            Modifier.padding(start = ButtonDefaults.IconSpacing), style = Testo.bottone,
        )
    }
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
    /** Torna alla stazione più vicina, dalla chip "Usa GPS" della partenza scelta a mano. */
    onUsaGps: () -> Unit = {},
    /** Sceglie la stazione di passaggio da evidenziare, dalla riga "via" della barra. */
    onScegliPassaggio: () -> Unit = {},
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
        topBar = { BarraPercorso(state, scroll, onScegliStazione, onUsaGps, onScegliPassaggio, onScegliDestinazione, onScambia) },
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

private val ALTEZZA_APERTA = 148.dp
private val ALTEZZA_CHIUSA = 64.dp
private val ALTEZZA_RIGA = 48.dp
private val ALTEZZA_VIA = 36.dp
private val TOCCO_GPS = 40.dp
private const val DURATA_FLUSSO = 700
private const val DURATA_ONDA = 1800

/** Margine a sinistra dei nomi, dove sta il tracciato pallino–linea–pallino. */
private val RIENTRO_TRACCIATO = 28.dp

/**
 * Il percorso in alto, letto nell'ordine giusto: da (sopra) → a (sotto), ognuno toccabile,
 * con l'inversione a destra. Scorrendo il blocco a due righe sfuma in una riga sola
 * "partenza → destinazione": entrambi i capi restano sempre a vista.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BarraPercorso(
    state: UiState,
    scroll: TopAppBarScrollBehavior,
    onScegliStazione: () -> Unit,
    onUsaGps: () -> Unit,
    onScegliPassaggio: () -> Unit,
    onScegliDestinazione: () -> Unit,
    onScambia: () -> Unit,
) {
    val s = state.stazione
    val haptic = rememberHaptic()
    val density = LocalDensity.current
    val partenza = s?.nome ?: if (state.caricamento) "Cerco la stazione…" else "Scegli la partenza"
    val origine = when (s?.origine) {
        StazioneCorrente.Origine.Gps -> "più vicina, ${distanzaLeggibile(s.distanzaMetri ?: 0)}"
        StazioneCorrente.Origine.Manuale -> "scelta a mano"
        StazioneCorrente.Origine.UltimaNota -> "ultima nota"
        null -> null
    }
    val destinazione = state.destinazioneNome ?: "Scegli la destinazione"

    // stesso meccanismo delle barre M3: il limite dice al nestedScroll quanto può ridursi
    val limite = with(density) { (ALTEZZA_CHIUSA - ALTEZZA_APERTA).toPx() }
    SideEffect { if (scroll.state.heightOffsetLimit != limite) scroll.state.heightOffsetLimit = limite }
    val f = scroll.state.collapsedFraction
    val altezza = ALTEZZA_APERTA + with(density) { scroll.state.heightOffset.toDp() }

    Row(
        Modifier
            .fillMaxWidth()
            .background(lerp(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surfaceContainer, f))
            .windowInsetsPadding(TopAppBarDefaults.windowInsets)
            .height(altezza)
            .clipToBounds()
            .padding(start = Spazio.pagina - 4.dp, end = Spazio.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
            if (f < 0.5f) {
                PercorsoEsteso(
                    partenza = partenza,
                    distanza = s?.takeIf { it.origine == StazioneCorrente.Origine.Gps }
                        ?.let { distanzaLeggibile(it.distanzaMetri ?: 0) },
                    mostraGps = s != null && s.origine != StazioneCorrente.Origine.Gps,
                    descrizionePartenza = "Partenza $partenza" + (origine?.let { ", $it" } ?: "") + ". Tocca per cambiarla",
                    passaggio = state.stazionePassaggioNome,
                    onScegliPassaggio = onScegliPassaggio,
                    destinazione = destinazione,
                    onScegliStazione = onScegliStazione,
                    onUsaGps = { haptic.conferma(); onUsaGps() },
                    onScegliDestinazione = onScegliDestinazione,
                    modifier = Modifier
                        .wrapContentHeight(Alignment.CenterVertically, unbounded = true)
                        .graphicsLayer { alpha = 1f - f * 2f },
                )
            } else {
                PercorsoCompatto(
                    partenza, destinazione, onScegliDestinazione,
                    Modifier.graphicsLayer { alpha = (f - 0.5f) * 2f },
                )
            }
        }
        // mezzo giro a ogni inversione, sempre nello stesso verso: si accumula, non torna indietro
        val giro = remember { Animatable(0f) }
        val scope = rememberCoroutineScope()
        FilledTonalIconButton(
            onClick = {
                if (s != null) {
                    haptic.conferma()
                    onScambia()
                    scope.launch { giro.animateTo(giro.targetValue + 180f, Molla.stato()) }
                }
            },
            modifier = if (s != null) Modifier else Modifier.alpha(0.38f),
        ) {
            Icon(Icone.Inverti, "Inverti partenza e destinazione", Modifier.graphicsLayer { rotationZ = giro.value })
        }
    }
}

/** Da / a su due righe, unite a sinistra dal tracciato: pallino vuoto, tratteggio, pallino pieno. */
@Composable
private fun PercorsoEsteso(
    partenza: String,
    distanza: String?,
    mostraGps: Boolean,
    descrizionePartenza: String,
    passaggio: String?,
    onScegliPassaggio: () -> Unit,
    destinazione: String,
    onScegliStazione: () -> Unit,
    onUsaGps: () -> Unit,
    onScegliDestinazione: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tb = LocalTb.current
    val primario = MaterialTheme.colorScheme.primary
    val gps = distanza != null
    // il tracciato è vivo: il tratteggio scorre verso la destinazione e, se la partenza viene dal
    // GPS, il pallino emette un'onda come un radar. Con "riduci movimento" resta il disegno fermo.
    val vivo = !Molla.riduci
    val tempo = rememberInfiniteTransition(label = "tracciato")
    val flusso by tempo.animateFloat(
        0f, 1f, infiniteRepeatable(tween(DURATA_FLUSSO, easing = LinearEasing)), label = "flusso",
    )
    val onda by tempo.animateFloat(
        0f, 1f, infiniteRepeatable(tween(DURATA_ONDA, easing = LinearOutSlowInEasing)), label = "onda",
    )
    Column(
        modifier.drawBehind {
            val x = RIENTRO_TRACCIATO.toPx() / 2
            val r = 5.dp.toPx()
            val sopra = ALTEZZA_RIGA.toPx() / 2
            val sotto = sopra + ALTEZZA_RIGA.toPx() + ALTEZZA_VIA.toPx()
            val passo = 6.dp.toPx()
            if (gps && vivo) {
                drawCircle(primario.copy(alpha = 0.45f * (1f - onda)), r + 12.dp.toPx() * onda, Offset(x, sopra))
            }
            // partenza scelta a mano: al posto del pallino c'è il bottone "usa GPS" (vedi sotto)
            if (!mostraGps) drawCircle(if (gps) primario else tb.ter, r, Offset(x, sopra), style = Stroke(2.dp.toPx()))
            drawLine(
                Brush.verticalGradient(listOf(tb.ter, primario), sopra, sotto),
                Offset(x, sopra + (if (mostraGps) 13.dp.toPx() else r + 4.dp.toPx())), Offset(x, sotto - r - 4.dp.toPx()), 2.dp.toPx(),
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(
                    floatArrayOf(passo / 2, passo / 2), if (vivo) -flusso * passo else 0f,
                ),
            )
            drawCircle(primario, r, Offset(x, sotto))
            // la fermata di passaggio è un pallino piccolo sul tratteggio, solo se impostata
            if (passaggio != null) drawCircle(tb.sub, 3.dp.toPx(), Offset(x, ALTEZZA_RIGA.toPx() + ALTEZZA_VIA.toPx() / 2))
        },
    ) {
        Box {
            Row(Modifier.height(ALTEZZA_RIGA), verticalAlignment = Alignment.CenterVertically) {
                RigaPercorso(partenza, descrizionePartenza, onScegliStazione, entraDaSotto = true, Modifier.weight(1f, fill = false))
                if (distanza != null) {
                    Text(distanza, Modifier.padding(start = Spazio.s), style = Testo.etichetta, color = tb.sub, maxLines = 1)
                }
            }
            if (mostraGps) {
                // sopra la riga, al posto del pallino: tocco largo 40 dp centrato sul tracciato
                Box(
                    Modifier
                        .offset(x = (RIENTRO_TRACCIATO - TOCCO_GPS) / 2)
                        .size(TOCCO_GPS, ALTEZZA_RIGA)
                        .clip(Forme.cardPiccola)
                        .semantics { role = Role.Button; contentDescription = "Usa la stazione più vicina, dal GPS" }
                        .clickable(onClick = onUsaGps),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icone.Posizione, null, Modifier.size(20.dp), tint = primario)
                }
            }
        }
        RigaVia(passaggio, onScegliPassaggio)
        RigaPercorso(
            destinazione, "Destinazione $destinazione. Tocca per cambiarla", onScegliDestinazione,
            entraDaSotto = false, Modifier.height(ALTEZZA_RIGA),
        )
    }
}

/** "via Legnano" fra partenza e destinazione; senza stazione impostata invita ad aggiungerla. */
@Composable
private fun RigaVia(passaggio: String?, onClick: () -> Unit) {
    val tb = LocalTb.current
    Text(
        if (passaggio != null) "via $passaggio" else "+ Aggiungi una stazione di passaggio",
        Modifier
            .height(ALTEZZA_VIA)
            .clip(Forme.cardPiccola)
            .semantics {
                role = Role.Button
                contentDescription = passaggio?.let { "Evidenzia i treni via $it. Tocca per cambiare" }
                    ?: "Aggiungi una stazione di passaggio da evidenziare"
            }
            .clickable(onClick = onClick)
            .wrapContentHeight(Alignment.CenterVertically)
            .padding(start = RIENTRO_TRACCIATO, end = Spazio.s),
        style = Testo.etichetta, color = if (passaggio != null) tb.sub else tb.ter,
        maxLines = 1, overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun RigaPercorso(
    nome: String,
    descrizione: String,
    onClick: () -> Unit,
    /** Verso del cambio: all'inversione partenza e destinazione si incrociano. */
    entraDaSotto: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .clip(Forme.cardPiccola)
            .semantics(mergeDescendants = true) { role = Role.Button; contentDescription = descrizione }
            .clickable(onClick = onClick)
            .padding(start = RIENTRO_TRACCIATO, end = Spazio.s, top = Spazio.s, bottom = Spazio.s),
        contentAlignment = Alignment.CenterStart,
    ) {
        val verso = if (entraDaSotto) 1 else -1
        AnimatedContent(
            nome,
            transitionSpec = {
                (slideInVertically(Molla.ui()) { it * verso } + fadeIn(Molla.piatta())) togetherWith
                    (slideOutVertically(Molla.ui()) { it * verso } + fadeOut(Molla.piatta()))
            },
            label = "nome",
        ) {
            Text(it, style = Testo.sottotitolo, color = LocalTb.current.tx, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Barra ridotta: "partenza → destinazione" su una riga; il tocco cambia la destinazione. */
@Composable
private fun PercorsoCompatto(partenza: String, destinazione: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val tb = LocalTb.current
    Row(
        modifier
            .clip(Forme.cardPiccola)
            .semantics(mergeDescendants = true) {
                role = Role.Button; contentDescription = "Da $partenza a $destinazione. Tocca per cambiare destinazione"
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = Spazio.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            partenza, Modifier.weight(1f, fill = false),
            style = Testo.sottotitolo, color = tb.tx, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Icon(Icone.Freccia, null, Modifier.padding(horizontal = 6.dp).size(18.dp), tint = tb.sub)
        Text(
            destinazione, Modifier.weight(1f, fill = false),
            style = Testo.sottotitolo, color = tb.tx, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
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
