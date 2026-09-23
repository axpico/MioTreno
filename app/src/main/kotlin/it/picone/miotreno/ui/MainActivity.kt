package it.picone.miotreno.ui

import android.Manifest
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import it.picone.miotreno.Deps
import it.picone.miotreno.data.AdsManager
import it.picone.miotreno.data.URL_BUY_ME_A_COFFEE
import it.picone.miotreno.ui.componenti.Icone
import it.picone.miotreno.ui.theme.Forme
import it.picone.miotreno.ui.theme.LocalTb
import it.picone.miotreno.ui.theme.Molla
import it.picone.miotreno.ui.theme.Testo
import it.picone.miotreno.ui.theme.MioTrenoTheme
import it.picone.miotreno.widget.EXTRA_NUMERO_TRENO
import it.picone.miotreno.widget.EXTRA_SEGUI
import it.picone.miotreno.work.pianificaLavoriPeriodici
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.coroutines.cancellation.CancellationException

/** Polling della home mentre l'app è in primo piano. */
private const val POLL_HOME_MS = 60_000L

/**
 * Larghezza massima del contenuto. Da API 36 su tablet e pieghevoli (≥600 dp) il sistema ignora
 * l'orientamento: le card a tutta larghezza si stirerebbero male, quindi il contenuto resta una
 * colonna centrata. Non è un layout tablet, è un layout telefono che regge.
 */
private val LARGHEZZA_MAX = 640.dp
private val LARGHEZZA_MAX_BARRA = 520.dp

class MainActivity : ComponentActivity() {

    /**
     * Numero treno arrivato dal widget. È uno stato dell'activity e non un parametro perché
     * `launchMode` è `singleTask`: al secondo tap sul widget non si passa da `onCreate`,
     * arriva solo `onNewIntent`.
     */
    private val dalWidget = MutableStateFlow<Int?>(null)
    private val seguiSubito = MutableStateFlow(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Deps.init(this)
        AdsManager.inizializzaSeConsentito(this)
        pianificaLavoriPeriodici(this)
        // Le barre seguono il tema di sistema; il contenuto resta edge-to-edge in entrambi i casi.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT,
            ),
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT,
            ),
        )
        leggiExtra(intent)
        setContent { MioTrenoTheme { App(dalWidget, seguiSubito) } }
    }

    override fun onResume() {
        super.onResume()
        // "riduci movimento" / scala animazioni 0: molle piatte, niente pulse
        Molla.riduci = !ValueAnimator.areAnimatorsEnabled()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        leggiExtra(intent)
    }

    private fun leggiExtra(intent: Intent?) {
        intent?.getIntExtra(EXTRA_NUMERO_TRENO, -1)
            ?.takeIf { it > 0 }
            ?.let {
                dalWidget.value = it
                seguiSubito.value = intent.getBooleanExtra(EXTRA_SEGUI, false)
            }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun App(dalWidget: MutableStateFlow<Int?>, seguiSubito: MutableStateFlow<Boolean>) {
    val vm: TrenoViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val ora by vm.ora.collectAsStateWithLifecycle()
    val tb = LocalTb.current
    val lifecycle = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        dalWidget.collect { numero ->
            if (numero != null) {
                vm.apriDaWidget(numero, segui = seguiSubito.value)
                dalWidget.value = null
                seguiSubito.value = false
            }
        }
    }

    // Polling solo con l'app visibile: repeatOnLifecycle cancella il loop in background.
    LaunchedEffect(Unit) {
        lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(POLL_HOME_MS)
                if (vm.state.value.schermata != Schermata.Dettaglio) vm.aggiorna(silenzioso = true)
            }
        }
    }

    // ── permessi: mai all'avvio, sempre dal contesto in cui servono ──────────
    val context = LocalContext.current
    val activity = context as Activity
    val apriImpostazioniApp = {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
        )
    }
    // negato due volte (o "non chiedere più"): il dialog non comparirebbe, si va in Impostazioni
    var posizioneBloccata by remember { mutableStateOf(false) }
    val permessoPosizione = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { esiti ->
        if (esiti.values.any { it }) vm.permessoConcesso()
        else posizioneBloccata =
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_COARSE_LOCATION)
    }
    val chiediPosizione = {
        if (posizioneBloccata) apriImpostazioniApp()
        else permessoPosizione.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
        )
    }
    val permessoNotifiche = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.setNotifichePermesse(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    val chiediNotifiche = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !NotificationManagerCompat.from(context).areNotificationsEnabled()
        ) permessoNotifiche.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    LaunchedEffect(Unit) {
        lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            vm.setNotifichePermesse(NotificationManagerCompat.from(context).areNotificationsEnabled())
        }
    }

    // interstitial: one-shot, consumato appena mostrato per non ripartire su rotazione/ricomposizione
    LaunchedEffect(state.mostraInterstitial) {
        if (state.mostraInterstitial) AdsManager.mostraInterstitial(activity) { vm.interstitialMostrato() }
    }

    // Utenti già installati prima di questa versione (destinazione già configurata) non passano
    // mai dall'onboarding, quindi il consenso UMP non verrebbe mai richiesto: lo richiediamo qui
    // per chiunque abbia le ads attive e non sia in onboarding (dove ci pensa già quello step).
    // Idempotente: se il consenso è già stato ottenuto o non serve, non mostra alcun form.
    LaunchedEffect(state.schermata != Schermata.Onboarding, state.impostazioni.adsAbilitate) {
        if (state.schermata != Schermata.Onboarding && state.impostazioni.adsAbilitate) {
            AdsManager.richiediConsenso(activity)
        }
    }

    // ── back predittivo: il gesto guida scala e opacità del dettaglio, poi la shared element
    //    fa il resto. Da API 36 KEYCODE_BACK/onBackPressed non arrivano più: solo questa via.
    var backProgress by remember { mutableFloatStateOf(0f) }
    PredictiveBackHandler(enabled = state.schermata != Schermata.Home && state.schermata != Schermata.Onboarding) { eventi ->
        try {
            eventi.collect { backProgress = if (Molla.riduci) 0f else it.progress }
            if (state.schermata == Schermata.Dettaglio) vm.indietro() else vm.vaiA(Schermata.Home)
        } catch (_: CancellationException) {
            // gesto annullato: si torna dov'eravamo
        } finally {
            backProgress = 0f
        }
    }

    Box(Modifier.fillMaxSize().background(tb.bg)) {
        SharedTransitionLayout(
            Modifier.align(Alignment.TopCenter).widthIn(max = LARGHEZZA_MAX).fillMaxSize()
                .graphicsLayer {
                    val s = 1f - 0.08f * backProgress
                    scaleX = s; scaleY = s; alpha = 1f - 0.25f * backProgress
                },
        ) {
            AnimatedContent(
                targetState = state.schermata,
                transitionSpec = {
                    if (Molla.riduci) fadeIn(Molla.piatta()).togetherWith(fadeOut(Molla.piatta()))
                    else (fadeIn(Molla.piatta()) + slideInVertically(Molla.ui()) { it / 16 })
                        .togetherWith(fadeOut(Molla.piatta()))
                },
                label = "schermata",
            ) { schermata ->
                when (schermata) {
                    Schermata.Onboarding -> OnboardingScreen(
                        onCerca = vm::cercaStazioni,
                        onScegli = vm::scegliDestinazione,
                        onAdsScelte = vm::setAdsAbilitate,
                        onRichiediConsenso = { AdsManager.richiediConsenso(activity) },
                    )
                    Schermata.Home -> HomeScreen(
                        state = state, ora = ora,
                        sharedScope = this@SharedTransitionLayout, animatedScope = this@AnimatedContent,
                        onApri = vm::apri,
                        onChiediPermesso = chiediPosizione,
                        permessoBloccato = posizioneBloccata,
                        onRiprova = vm::aggiorna,
                        onRefresh = { vm.aggiorna() },
                        onScegliStazione = vm::apriSelettoreStazione,
                        onScambia = vm::scambiaOrigineDestinazione,
                    )
                    Schermata.Dettaglio -> DettaglioScreen(
                        state = state, ora = ora,
                        sharedScope = this@SharedTransitionLayout, animatedScope = this@AnimatedContent,
                        onIndietro = vm::indietro,
                        onSegui = { state.trenoSelezionato?.let(vm::cambiaSeguito) },
                        onNotifiche = vm::setNotifiche,
                        onChiediNotifiche = chiediNotifiche,
                    )
                    Schermata.Statistiche -> StatisticheScreen(
                        stat = state.statistiche,
                        filtroPeriodo = state.filtroPeriodo,
                        onFiltroPeriodo = vm::impostaFiltroPeriodo,
                        nomeStazioneFiltro = state.stazioni.firstOrNull { it.codice == state.filtroStazione }?.nome,
                        onFiltroStazione = vm::apriSelettoreFiltroStazione,
                    )
                    Schermata.Impostazioni -> SettingsScreen(
                        imp = state.impostazioni,
                        stazione = state.stazione,
                        onNotifiche = vm::setNotifiche,
                        onAnticipo = vm::setAnticipo,
                        onAvvisiSciopero = vm::setAvvisiSciopero,
                        onScegliStazione = vm::apriSelettoreStazione,
                        onUsaGps = { vm.scegliStazione(null) },
                        destinazioneNome = state.destinazioneNome,
                        onScegliDestinazione = vm::apriSelettoreDestinazione,
                        onScambia = vm::scambiaOrigineDestinazione,
                        regioneScioperiNome = state.regioneScioperiNome,
                        stazionePassaggioNome = state.stazionePassaggioNome,
                        onScegliPassaggio = vm::apriSelettorePassaggio,
                        onRimuoviPassaggio = { vm.sceglipassaggio(null) },
                        notifichePermesse = state.notifichePermesse,
                        onChiediNotifiche = chiediNotifiche,
                        onApriImpostazioniSistema = apriImpostazioniApp,
                        onAggiungiTile = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) ({ proponiTile(context) }) else null,
                        onAdsAbilitate = vm::setAdsAbilitate,
                        mostraGestioneConsenso = AdsManager.richiedeGestioneConsenso(),
                        onGestisciConsenso = { AdsManager.apriGestioneConsenso(activity) },
                        onSupportaSviluppatore = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, URL_BUY_ME_A_COFFEE.toUri()))
                        },
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = state.schermata != Schermata.Dettaglio && state.schermata != Schermata.Onboarding,
            modifier = Modifier.align(Alignment.BottomCenter).widthIn(max = LARGHEZZA_MAX_BARRA),
            enter = slideInVertically(Molla.ui()) { it } + fadeIn(),
            exit = slideOutVertically(Molla.piatta()) { it } + fadeOut(),
        ) { BarraNav(state.schermata, vm::vaiA) }

        AnimatedVisibility(visible = state.selettoreStazione, enter = fadeIn(Molla.piatta()), exit = fadeOut(Molla.piatta())) {
            SelettoreStazione(
                correnteCodice = state.impostazioni.stazioneManuale,
                onScegli = vm::scegliStazione,
                onChiudi = vm::chiudiSelettoreStazione,
                ricercaLive = vm::cercaStazioni,
            )
        }

        AnimatedVisibility(visible = state.selettoreDestinazione, enter = fadeIn(Molla.piatta()), exit = fadeOut(Molla.piatta())) {
            SelettoreStazione(
                correnteCodice = state.impostazioni.stazioneDestinazione,
                onScegli = { codice -> codice?.let(vm::scegliDestinazione) },
                onChiudi = vm::chiudiSelettoreDestinazione,
                titolo = "Scegli la destinazione",
                overline = "Destinazione",
                mostraOpzioneVuota = false,
                ricercaLive = vm::cercaStazioni,
            )
        }

        AnimatedVisibility(visible = state.selettorePassaggio, enter = fadeIn(Molla.piatta()), exit = fadeOut(Molla.piatta())) {
            SelettoreStazione(
                stazioni = state.stazioni,
                correnteCodice = state.impostazioni.stazionePassaggio,
                onScegli = vm::sceglipassaggio,
                onChiudi = vm::chiudiSelettorePassaggio,
                titolo = "Stazione di passaggio",
                overline = "Passa sempre per",
                etichettaOpzioneVuota = "Nessuna",
                descrizioneOpzioneVuota = "disattiva l'evidenziazione",
                mostraIconaOpzioneVuota = false,
            )
        }

        AnimatedVisibility(visible = state.selettoreFiltroStazione, enter = fadeIn(Molla.piatta()), exit = fadeOut(Molla.piatta())) {
            SelettoreStazione(
                stazioni = state.stazioni,
                correnteCodice = state.filtroStazione,
                onScegli = vm::sceglifiltroStazione,
                onChiudi = vm::chiudiSelettoreFiltroStazione,
                titolo = "Filtra per stazione",
                overline = "Statistiche",
                etichettaOpzioneVuota = "Tutte le stazioni",
                descrizioneOpzioneVuota = "nessun filtro",
                mostraIconaOpzioneVuota = false,
            )
        }
    }
}

private data class Tab(val nome: String, val icona: ImageVector, val schermata: Schermata)

private val TAB = listOf(
    Tab("Treni", Icone.Treno, Schermata.Home),
    Tab("Statistiche", Icone.Statistiche, Schermata.Statistiche),
    Tab("Impostazioni", Icone.Impostazioni, Schermata.Impostazioni),
)

/** Barra flottante, staccata dai bordi: fondo pieno, bordo sottile a tinta unita. */
@Composable
private fun BarraNav(corrente: Schermata, onVai: (Schermata) -> Unit) {
    val tb = LocalTb.current
    Row(
        Modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 40.dp, vertical = 12.dp)
            .fillMaxWidth()
            .shadow(12.dp, Forme.barra, ambientColor = Color.Black.copy(alpha = 0.32f), spotColor = Color.Black.copy(alpha = 0.42f))
            .clip(Forme.barra)
            .background(tb.sf)
            .border(1.dp, tb.bordo, Forme.barra)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TAB.forEach { tab ->
            val attivo = corrente == tab.schermata
            val sfondo by animateColorAsState(
                if (attivo) tb.accentoSoft else Color.Transparent,
                Molla.piatta(), label = "nav-${tab.nome}",
            )
            Column(
                Modifier
                    .weight(1f)
                    .clip(Forme.pillola)
                    .background(sfondo)
                    .selectable(selected = attivo, role = Role.Tab, onClick = { onVai(tab.schermata) })
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    tab.icona, contentDescription = tab.nome,
                    tint = if (attivo) tb.accento else tb.ter,
                    modifier = Modifier.size(22.dp),
                )
                Text(tab.nome, style = Testo.micro, color = if (attivo) tb.accento else tb.ter,
                    modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}
