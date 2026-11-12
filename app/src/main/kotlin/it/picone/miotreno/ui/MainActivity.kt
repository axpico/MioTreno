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
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import it.picone.miotreno.Deps
import it.picone.miotreno.ui.componenti.Icone
import it.picone.miotreno.ui.theme.MioTrenoTheme
import it.picone.miotreno.ui.theme.Molla
import it.picone.miotreno.work.EXTRA_NUMERO_TRENO
import it.picone.miotreno.work.EXTRA_SEGUI
import it.picone.miotreno.work.pianificaLavoriPeriodici
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

/** Polling della home mentre l'app è in primo piano. */
private const val POLL_HOME_MS = 60_000L

/** Oltre questa età, al ritorno in primo piano la home si aggiorna subito. */
private const val SOGLIA_STALE_MS = 30_000L

/**
 * Larghezza massima del contenuto. Da API 36 su tablet e pieghevoli (≥600 dp) il sistema ignora
 * l'orientamento: le card a tutta larghezza si stirerebbero male, quindi il contenuto resta una
 * colonna centrata. Non è un layout tablet, è un layout telefono che regge.
 */
private val LARGHEZZA_MAX = 640.dp

class MainActivity : ComponentActivity() {
    /**
     * Numero treno arrivato da notifica o tile. È uno stato dell'activity e non un parametro perché
     * `launchMode` è `singleTask`: al secondo tap non si passa da `onCreate`,
     * arriva solo `onNewIntent`.
     */
    private val daIntent = MutableStateFlow<Int?>(null)
    private val seguiSubito = MutableStateFlow(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        Deps.init(this)
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
        setContent { MioTrenoTheme { App(daIntent, seguiSubito) } }
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
                daIntent.value = it
                seguiSubito.value = intent.getBooleanExtra(EXTRA_SEGUI, false)
            }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun App(daIntent: MutableStateFlow<Int?>, seguiSubito: MutableStateFlow<Boolean>) {
    val vm: TrenoViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val ora by vm.ora.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        daIntent.collect { numero ->
            if (numero != null) {
                vm.apriDaIntent(numero, segui = seguiSubito.value)
                daIntent.value = null
                seguiSubito.value = false
            }
        }
    }

    AggiornamentoAutomatico(vm)
    val permessi = rememberPermessi(vm)

    // primo avvio: solo la ricerca della destinazione, nessuna barra
    if (state.schermata == Schermata.SceltaDestinazione) {
        val activity = LocalActivity.current
        SelettoreStazione(
            correnteCodice = null,
            onScegli = { codice -> codice?.let(vm::scegliDestinazione) },
            // chiuderla esce dall'app invece di lasciare uno sfondo vuoto
            onChiudi = { activity?.finish() },
            titolo = "Dove vuoi arrivare?",
            mostraOpzioneVuota = false,
            ricercaLive = vm::cercaStazioni,
        )
        return
    }

    BackHandler(enabled = state.schermata != Schermata.Home && state.trenoSelezionato == null) {
        vm.vaiA(Schermata.Home)
    }

    val larghezza = LocalConfiguration.current.screenWidthDp
    // da 840 dp il dettaglio sta accanto alla lista invece che in un foglio sopra
    val paneLaterale = larghezza >= 840
    val effetti = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    NavigationSuiteScaffold(
        layoutType = if (larghezza >= 600) NavigationSuiteType.NavigationRail else NavigationSuiteType.NavigationBar,
        navigationSuiteItems = {
            SEZIONI.forEach { sezione ->
                item(
                    selected = state.schermata == sezione.schermata,
                    onClick = { vm.vaiA(sezione.schermata) },
                    icon = { Icon(sezione.icona(), contentDescription = null) },
                    label = { Text(sezione.nome) },
                )
            }
        },
    ) {
        Row(Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = state.schermata,
                transitionSpec = { fadeIn(effetti) togetherWith fadeOut(effetti) },
                label = "schermata",
                modifier = Modifier.weight(1f).fillMaxHeight(),
            ) { schermata ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    Box(Modifier.widthIn(max = LARGHEZZA_MAX)) {
                        SchermataCorrente(schermata, state, ora, vm, permessi)
                    }
                }
            }
            if (paneLaterale && state.trenoSelezionato != null) {
                BackHandler { vm.indietro() }
                VerticalDivider()
                Surface(Modifier.width(420.dp).fillMaxHeight(), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Dettaglio(state, ora, vm, permessi, Modifier.windowInsetsPadding(WindowInsets.safeDrawing))
                }
            }
        }
    }

    if (!paneLaterale && state.trenoSelezionato != null) {
        ModalBottomSheet(onDismissRequest = vm::indietro, sheetState = rememberModalBottomSheetState()) {
            Dettaglio(state, ora, vm, permessi)
        }
    }

    SelettoriStazione(state, vm)
}

private data class Sezione(val nome: String, val icona: @Composable () -> ImageVector, val schermata: Schermata)

private val SEZIONI = listOf(
    Sezione("Treni", { Icone.Treno }, Schermata.Home),
    Sezione("Statistiche", { Icone.Statistiche }, Schermata.Statistiche),
    Sezione("Impostazioni", { Icone.Impostazioni }, Schermata.Impostazioni),
)

@Composable
private fun Dettaglio(state: UiState, ora: Long, vm: TrenoViewModel, permessi: Permessi, modifier: Modifier = Modifier) {
    DettaglioScreen(
        state = state, ora = ora,
        onSegui = { state.trenoSelezionato?.let(vm::cambiaSeguito) },
        onNotifiche = vm::setNotifiche,
        onChiediNotifiche = permessi.chiediNotifiche,
        modifier = modifier,
    )
}

/**
 * Polling della lista solo con l'app visibile (repeatOnLifecycle cancella il loop in background),
 * e subito al ritorno se i dati sono vecchi.
 */
@Composable
private fun AggiornamentoAutomatico(vm: TrenoViewModel) {
    val lifecycle = LocalLifecycleOwner.current
    LaunchedEffect(Unit) {
        lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            // al ritorno dal background la lista può essere vecchia di minuti: subito, non fra 60 s.
            // Al primo avvio aggiornatoAlle è null e ci pensa già l'init del ViewModel.
            val ultimo = vm.state.value.aggiornatoAlle
            if (ultimo != null && System.currentTimeMillis() - ultimo > SOGLIA_STALE_MS &&
                vm.state.value.trenoSelezionato == null
            ) {
                vm.aggiorna(silenzioso = true)
            }
            while (true) {
                delay(POLL_HOME_MS)
                if (vm.state.value.trenoSelezionato == null) vm.aggiorna(silenzioso = true)
            }
        }
    }
}

/** Permessi: mai all'avvio, sempre dal contesto in cui servono. */
@Composable
private fun rememberPermessi(vm: TrenoViewModel): Permessi {
    val lifecycle = LocalLifecycleOwner.current
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
        if (esiti.values.any { it }) {
            vm.permessoConcesso()
        } else {
            posizioneBloccata =
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }
    val chiediPosizione = {
        if (posizioneBloccata) {
            apriImpostazioniApp()
        } else {
            permessoPosizione.launch(
                // solo COARSE: per capire a quale scalo sei vicino non serve la precisione GPS,
                // e una permission in meno e' una permission in meno da giustificare e da esporre
                arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION),
            )
        }
    }
    val permessoNotifiche = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.setNotifichePermesse(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    val chiediNotifiche = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !NotificationManagerCompat.from(context).areNotificationsEnabled()
        ) {
            permessoNotifiche.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    LaunchedEffect(Unit) {
        lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            vm.setNotifichePermesse(NotificationManagerCompat.from(context).areNotificationsEnabled())
        }
    }
    return Permessi(chiediPosizione, posizioneBloccata, chiediNotifiche, apriImpostazioniApp)
}

/** Le richieste di permesso, preparate in [App] dove servono i launcher: le schermate le chiamano soltanto. */
private class Permessi(
    val chiediPosizione: () -> Unit,
    val posizioneBloccata: Boolean,
    val chiediNotifiche: () -> Unit,
    val apriImpostazioniApp: () -> Unit,
)

/** Quale schermata mostrare e con quali azioni: solo instradamento, la logica sta nel ViewModel. */
@Composable
private fun SchermataCorrente(
    schermata: Schermata,
    state: UiState,
    ora: Long,
    vm: TrenoViewModel,
    permessi: Permessi,
) {
    val context = LocalContext.current
    when (schermata) {
        Schermata.SceltaDestinazione -> Unit
        Schermata.Home -> HomeScreen(
            state = state, ora = ora,
            onApri = vm::apri,
            onSmetti = { vm.cambiaSeguito(it) },
            onChiediPermesso = permessi.chiediPosizione,
            permessoBloccato = permessi.posizioneBloccata,
            onRiprova = vm::aggiorna,
            onRefresh = { vm.aggiorna() },
            onScegliStazione = vm::apriSelettoreStazione,
            onScambia = vm::scambiaOrigineDestinazione,
            onScegliDestinazione = vm::apriSelettoreDestinazione,
            onAttivaCambi = { vm.setConCambi(true) },
        )
        Schermata.Statistiche -> StatisticheScreen(
            stat = state.statistiche,
            filtroPeriodo = state.filtroPeriodo,
            onFiltroPeriodo = vm::impostaFiltroPeriodo,
            nomeStazioneFiltro = state.stazioniStorico.firstOrNull { it.codice == state.filtroStazione }?.nome,
            onFiltroStazione = vm::apriSelettoreFiltroStazione,
        )
        Schermata.Impostazioni -> SettingsScreen(
            imp = state.impostazioni,
            stazione = state.stazione,
            onNotifiche = vm::setNotifiche,
            onAnticipo = vm::setAnticipo,
            onConCambi = vm::setConCambi,
            onAvvisoCambio = vm::setAvvisoCambio,
            onAvvisiSciopero = vm::setAvvisiSciopero,
            onScegliStazione = vm::apriSelettoreStazione,
            onUsaGps = { vm.scegliStazione(null) },
            destinazioneNome = state.destinazioneNome,
            onScegliDestinazione = vm::apriSelettoreDestinazione,
            onScambia = vm::scambiaOrigineDestinazione,
            regioneScioperiNome = state.regioneScioperiNome,
            stazionePassaggioNome = state.stazionePassaggioNome,
            onScegliPassaggio = vm::apriSelettorePassaggio,
            notifichePermesse = state.notifichePermesse,
            onChiediNotifiche = permessi.chiediNotifiche,
            onApriImpostazioniSistema = permessi.apriImpostazioniApp,
            onAggiungiTile = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                { proponiTile(context) { vm.setTileAggiunto(true) } }
            } else {
                null
            },
        )
    }
}

/** I quattro selettori di stazione, SearchBar a tutto schermo sopra il resto quando aperti. */
@Composable
private fun SelettoriStazione(state: UiState, vm: TrenoViewModel) {
    if (state.selettoreStazione) {
        SelettoreStazione(
            correnteCodice = state.impostazioni.stazioneManuale,
            onScegli = vm::scegliStazione,
            onChiudi = vm::chiudiSelettoreStazione,
            ricercaLive = vm::cercaStazioni,
            recenti = state.stazioniRecenti,
        )
    }

    if (state.selettoreDestinazione) {
        SelettoreStazione(
            correnteCodice = state.impostazioni.stazioneDestinazione,
            onScegli = { codice -> codice?.let(vm::scegliDestinazione) },
            onChiudi = vm::chiudiSelettoreDestinazione,
            titolo = "Cerca la destinazione",
            mostraOpzioneVuota = false,
            ricercaLive = vm::cercaStazioni,
            recenti = state.stazioniRecenti,
        )
    }

    if (state.selettorePassaggio) {
        SelettoreStazione(
            stazioni = state.stazioni,
            correnteCodice = state.impostazioni.stazionePassaggio,
            onScegli = vm::scegliPassaggio,
            onChiudi = vm::chiudiSelettorePassaggio,
            titolo = "Evidenzia i treni che passano per…",
            etichettaOpzioneVuota = "Nessuna stazione",
            descrizioneOpzioneVuota = "non evidenziare treni per una stazione",
            mostraIconaOpzioneVuota = false,
        )
    }

    if (state.selettoreFiltroStazione) {
        SelettoreStazione(
            stazioni = state.stazioniStorico,
            correnteCodice = state.filtroStazione,
            onScegli = vm::scegliFiltroStazione,
            onChiudi = vm::chiudiSelettoreFiltroStazione,
            titolo = "Filtra le statistiche per stazione",
            etichettaOpzioneVuota = "Tutte le stazioni",
            descrizioneOpzioneVuota = "nessun filtro",
            mostraIconaOpzioneVuota = false,
        )
    }
}
