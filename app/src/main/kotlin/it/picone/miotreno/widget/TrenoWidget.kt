package it.picone.miotreno.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import it.picone.miotreno.Deps
import it.picone.miotreno.R
import it.picone.miotreno.data.rilevanteOggiODomani
import it.picone.miotreno.domain.DettaglioTreno
import it.picone.miotreno.domain.ProssimoTreno
import it.picone.miotreno.domain.Semaforo
import it.picone.miotreno.domain.cambi
import it.picone.miotreno.domain.comeOra
import it.picone.miotreno.domain.comeSeguito
import it.picone.miotreno.domain.etichettaStato
import it.picone.miotreno.domain.indiceSalita
import it.picone.miotreno.domain.orarioProiettato
import it.picone.miotreno.domain.progressoReale
import it.picone.miotreno.domain.semaforo
import it.picone.miotreno.ui.MainActivity
import it.picone.miotreno.ui.componenti.ALPHA_TINTA
import it.picone.miotreno.ui.componenti.colore
import it.picone.miotreno.ui.theme.ChiaroTb
import it.picone.miotreno.ui.theme.ScuroTb
import it.picone.miotreno.ui.theme.TbColors
import it.picone.miotreno.work.controllaSeguito
import kotlinx.coroutines.flow.first

/** Extra dell'Intent: quale treno aprire quando si tocca una riga del widget. */
const val EXTRA_NUMERO_TRENO = "numero_treno"

/** Chi apre l'app con questo flag vuole anche seguire quel treno, non solo vederlo. */
const val EXTRA_SEGUI = "segui_treno"

/**
 * Orari proiettati col ritardo corrente.
 *
 * Nel widget il ritardo si vede solo dal colore della barretta: stampare l'orario di tabella
 * accanto a una barretta arancione lascia leggere "parte alle 8:12" a chi invece partirà alle
 * 8:24. Lo spazio non basta per due orari, quindi si mostra quello che conta.
 */
private fun ProssimoTreno.oraPartenza(): String =
    orarioProiettato(orarioPartenzaMs, ritardoMinuti).previstoMs?.comeOra() ?: "—"

private fun ProssimoTreno.oraArrivo(): String =
    orarioProiettato(orarioArrivoDestinazioneMs, ritardoMinuti).previstoMs?.comeOra() ?: "—"

/**
 * Soglie di larghezza fra i tre formati: 2×2 compatto, 3×2 con avanzamento, 4×2 lista.
 * Con SizeMode.Exact la dimensione vera arriva da LocalSize e decide quanto contenuto ci sta.
 */
private val LARGHEZZA_MEDIO = 200.dp
private val LARGHEZZA_LISTA = 300.dp

private fun righeExtra(altezza: Dp, base: Dp, perRiga: Dp, max: Int): Int =
    (((altezza - base) / perRiga).toInt()).coerceIn(0, max)

sealed interface EsitoWidget {
    data class Dati(
        val stazione: String,
        val destinazione: String,
        val treni: List<ProssimoTreno>,
        val aggiornatoAlle: Long,
        val sciopero: Boolean,
        val seguito: Int? = null,
        /** Corsa seguita non trovata qui: si è ripiegato sul prossimo treno, e va detto. */
        val seguitoNonDisponibile: Int? = null,
        /** Posizione reale della corsa seguita da andamentoTreno; null → fallback a [ProssimoTreno.avanzamento]. */
        val avanzamentoReale: Float? = null,
        /** Fermate alla destinazione per la corsa seguita già a bordo: come chip e app. Null prima. */
        val fermateMancanti: Int? = null,
        /** Corsa seguita con cambi: il prossimo cambio o la coincidenza persa. */
        val rigaCambio: String? = null,
    ) : EsitoWidget

    data object PermessoMancante : EsitoWidget
    data object PosizioneNonDisponibile : EsitoWidget
    data object ErroreRete : EsitoWidget
    data object NessunTreno : EsitoWidget

    /** Nessuna destinazione ancora scelta: l'app non ha completato l'onboarding. */
    data object DestinazioneNonConfigurata : EsitoWidget
}

/**
 * Override "corsa seguita" nello stato Glance del widget (numero, 0 = nessuna, e quando).
 *
 * Una sessione Glance resta viva 45 s dopo il primo render e `update()` la *ricompone* con il
 * nuovo stato, senza rieseguire il caricamento fatto prima di `provideContent`. Quindi il
 * cambio di corsa seguita, che deve vedersi subito, passa da qui: la composizione riordina i
 * treni già caricati. I dati veri arrivano dalle sessioni nuove (worker da 15 min, ⟳, apertura
 * dell'app). L'override vale solo se scritto dopo il caricamento della sessione.
 */
private val SEGUITO = intPreferencesKey("seguito")
private val SEGUITO_ALLE = longPreferencesKey("seguito_alle")

/** Ridisegna il widget; con [seguito] (numero o 0) applica subito il cambio di corsa seguita. */
suspend fun aggiornaWidget(context: Context, seguito: Int? = null) {
    if (seguito != null) {
        val ids = runCatching { GlanceAppWidgetManager(context).getGlanceIds(TrenoWidget::class.java) }.getOrDefault(emptyList())
        ids.forEach { id ->
            updateAppWidgetState(context, id) { it[SEGUITO] = seguito; it[SEGUITO_ALLE] = System.currentTimeMillis() }
        }
    }
    TrenoWidget().updateAll(context)
}

class TrenoWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Deps.init(context)
        val iniziale = carica()
        provideContent {
            val prefs = currentState<Preferences>()
            val esito = remember(prefs) {
                val alle = prefs[SEGUITO_ALLE] ?: 0L
                if (iniziale is EsitoWidget.Dati && alle > iniziale.aggiornatoAlle) {
                    iniziale.conSeguito(prefs[SEGUITO]?.takeIf { it > 0 })
                } else {
                    iniziale
                }
            }
            GlanceTheme {
                val size = LocalSize.current
                when {
                    size.width < LARGHEZZA_MEDIO -> Compatto(esito, size.height)
                    size.width < LARGHEZZA_LISTA -> Medio(esito, size.height)
                    else -> Lista(esito, size.height)
                }
            }
        }
    }

    private suspend fun carica(): EsitoWidget {
        val repo = Deps.repository
        val imp = Deps.impostazioni.flow.first()
        val codDestinazione = imp.stazioneDestinazione ?: return EsitoWidget.DestinazioneNonConfigurata
        if (imp.stazioneManuale == null && !Deps.location.hasPermission()) return EsitoWidget.PermessoMancante
        val stazione = Deps.stazione.risolvi(usaUltimaNota = true) ?: return EsitoWidget.PosizioneNonDisponibile

        val seguito = Deps.impostazioni.seguito.first()
        val treni = runCatching { repo.prossimiTreni(stazione.codici, codDestinazione, seguito = seguito) }
            .getOrElse { return EsitoWidget.ErroreRete }
        if (treni.isEmpty()) return EsitoWidget.NessunTreno

        val trenoSeguito = treni.firstOrNull { it.numeroTreno == seguito?.numeroTreno }
        // "segui" dal widget salva solo numero/data/arrivo: senza lo snapshot completo il
        // tracking perde la corsa appena esce dal tabellone partenze
        trenoSeguito?.let { Deps.impostazioni.aggiornaSeguito(it) }
        // stesso controllo di app e tracking: per un viaggio con cambi, la tratta in corso
        val esitoSeguito = trenoSeguito?.let { t ->
            val s = Deps.impostazioni.seguito.first() ?: return@let null
            runCatching { controllaSeguito(Deps.app, s, t, codDestinazione) }.getOrNull()
        }
        val dettaglio = esitoSeguito?.dettaglio as? DettaglioTreno.Ok
        val salita = dettaglio?.indiceSalita(stazione.codici)
        val fermateMancanti = dettaglio?.takeIf { salita != null && it.indiceCorrente >= salita && it.indiceDestinazione > salita }
            ?.let { (it.indiceDestinazione - it.indiceCorrente).coerceAtLeast(0) }

        val nomeDestinazione = repo.nomeStazione(codDestinazione) ?: "destinazione"
        return EsitoWidget.Dati(
            stazione = stazione.nome,
            destinazione = nomeDestinazione,
            treni = treni,
            aggiornatoAlle = System.currentTimeMillis(),
            sciopero = imp.avvisiSciopero && repo.scioperiInCache().rilevanteOggiODomani() != null,
            avanzamentoReale = dettaglio?.progressoReale(stazione.codici),
            fermateMancanti = fermateMancanti,
            rigaCambio = esitoSeguito?.rigaCambio,
        ).conSeguito(seguito?.numeroTreno)
    }
}

/**
 * La corsa seguita va in testa anche se cancellata: è l'informazione più importante.
 * Altrimenti in testa va il primo non cancellato; i cancellati restano in coda, visibili.
 */
fun EsitoWidget.Dati.conSeguito(seguito: Int?): EsitoWidget.Dati {
    val scelto = treni.firstOrNull { it.numeroTreno == seguito }
    val testa = scelto ?: treni.firstOrNull { !it.cancellato } ?: treni.first()
    return copy(
        treni = listOf(testa) + (treni - testa),
        seguito = scelto?.numeroTreno,
        seguitoNonDisponibile = seguito?.takeIf { scelto == null },
    )
}

class TrenoWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TrenoWidget()
}

/** Colore giorno/notte: il widget segue il tema del launcher, non quello dell'app. */
private fun col(sel: (TbColors) -> Color): ColorProvider = ColorProvider(day = sel(ChiaroTb), night = sel(ScuroTb))
private val apriApp = actionStartActivity(MainActivity::class.java)

private fun apriTreno(context: Context, numeroTreno: Int) = actionStartActivity(
    Intent(context, MainActivity::class.java)
        .setAction(Intent.ACTION_VIEW)
        .putExtra(EXTRA_NUMERO_TRENO, numeroTreno),
)

private fun sfondo(accento: Boolean) = GlanceModifier.fillMaxSize()
    .background(ImageProvider(if (accento) R.drawable.widget_bg_accento else R.drawable.widget_bg))

/** Spazio stretto nel widget: etichetta troncata per il layout compatto. */
private fun String.etichettaWidget(max: Int = 14): String {
    val u = uppercase()
    return if (u.length <= max) u else u.take(max - 1) + "…"
}

private fun stile(colore: ColorProvider, size: Int, bold: Boolean = false) = TextStyle(
    color = colore, fontSize = size.sp,
    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
)

@Composable
private fun Vuoto(esito: EsitoWidget) {
    val permesso = esito is EsitoWidget.PermessoMancante
    val erroreRete = esito is EsitoWidget.ErroreRete
    val nonConfigurata = esito is EsitoWidget.DestinazioneNonConfigurata
    val titolo = when (esito) {
        is EsitoWidget.PermessoMancante -> "Attiva la posizione"
        is EsitoWidget.PosizioneNonDisponibile -> "Posizione non disponibile"
        is EsitoWidget.ErroreRete -> "Dati non disponibili"
        is EsitoWidget.DestinazioneNonConfigurata -> "Apri l'app per iniziare"
        else -> "Nessun treno diretto"
    }
    val sottotitolo = when {
        permesso || nonConfigurata -> "tocca per aprire l'app"
        erroreRete -> "tocca per riprovare"
        else -> "tocca per aprire l'app"
    }
    Column(
        sfondo(false).padding(14.dp).let {
            when {
                permesso || nonConfigurata -> it.clickable(apriApp)
                erroreRete -> it.clickable(actionRunCallback<AggiornaWidgetAction>())
                else -> it
            }
        },
        verticalAlignment = Alignment.Vertical.CenterVertically,
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
    ) {
        Text(titolo, style = stile(col { it.tx }, 12, bold = permesso || erroreRete))
        Text(sottotitolo, style = stile(col { it.sub }, 11))
    }
}

/** Badge di stato del widget: pieno per cancellato, tinto per il resto. Statico (RemoteViews). */
@Composable
private fun BadgeStato(t: ProssimoTreno, piccolo: Boolean = false) {
    val s = t.semaforo()
    val pieno = s == Semaforo.Cancellato
    Text(
        t.etichettaStato(),
        maxLines = 1,
        modifier = GlanceModifier
            .background(col { if (pieno) it.colore(s) else it.colore(s).copy(alpha = ALPHA_TINTA) })
            .cornerRadius(12.dp)
            .padding(horizontal = if (piccolo) 6.dp else 8.dp, vertical = if (piccolo) 1.dp else 3.dp),
        style = stile(col { if (pieno) it.suStato else it.colore(s) }, if (piccolo) 11 else 12, bold = true),
    )
}

@Composable
private fun Pillola(testo: String, colore: (TbColors) -> Color = { it.sub }) {
    Text(
        testo, maxLines = 1,
        modifier = GlanceModifier.background(col { colore(it).copy(alpha = ALPHA_TINTA) }).cornerRadius(12.dp)
            .padding(horizontal = 7.dp, vertical = 2.dp),
        style = stile(col(colore), 11, bold = true),
    )
}

/** Toggle segui/smetti in-widget: pieno se è la corsa seguita. Non apre l'app. */
@Composable
private fun PillolaSegui(t: ProssimoTreno, seguito: Boolean) {
    if (t.cancellato) return
    val s = t.comeSeguito()
    Text(
        if (seguito) "● seguo" else "segui",
        maxLines = 1,
        modifier = GlanceModifier
            .background(col { if (seguito) it.accento else it.accento.copy(alpha = 0.16f) })
            .cornerRadius(12.dp)
            .clickable(
                actionRunCallback<SeguiTrenoAction>(
                    actionParametersOf(PARAM_NUMERO_TRENO to t.numeroTreno, PARAM_DATA to s.data, PARAM_ARRIVO to s.arrivoDestinazioneMs),
                ),
            )
            .padding(horizontal = 8.dp, vertical = 3.dp),
        style = stile(col { if (seguito) it.onAccento else it.accento }, 11, bold = true),
    )
}

@Composable
private fun Intestazione(esito: EsitoWidget.Dati, testo: String) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
        Text(
            if (esito.seguito != null) "● SEGUI · $testo" else "→ $testo",
            maxLines = 1, modifier = GlanceModifier.defaultWeight(),
            style = stile(col { it.accento }, 11, bold = true),
        )
        if (esito.sciopero) Text("⚠ sciopero", style = stile(col { it.sciopero }, 11, bold = true))
    }
}

/** 2×2: orario grande, categoria e binario, badge di stato, countdown. */
@Composable
private fun Compatto(esito: EsitoWidget, altezza: Dp) {
    if (esito !is EsitoWidget.Dati) {
        Vuoto(esito)
        return
    }
    val t = esito.treni.first()
    val minuti = t.minutiAllaPartenza(System.currentTimeMillis())
    val context = LocalContext.current
    val extra = righeExtra(altezza, base = 160.dp, perRiga = 21.dp, max = 3)
    Column(sfondo(esito.seguito != null).padding(15.dp).clickable(apriTreno(context, t.numeroTreno))) {
        Intestazione(esito, esito.destinazione.etichettaWidget())
        val aBordo = esito.aBordo(t)
        Text(
            when {
                aBordo -> "ARRIVO"
                esito.seguito == t.numeroTreno -> "LA TUA CORSA"
                else -> "PROSSIMA PARTENZA"
            },
            modifier = GlanceModifier.padding(top = 8.dp),
            style = stile(col { it.ter }, 11, bold = true),
        )
        Text(
            if (aBordo) t.oraArrivo() else t.oraPartenza(),
            modifier = GlanceModifier.padding(top = 2.dp),
            style = TextStyle(
                color = col { it.tx }, fontSize = 30.sp, fontWeight = FontWeight.Bold,
                textDecoration = if (t.cancellato) TextDecoration.LineThrough else TextDecoration.None,
            ),
        )
        Row(GlanceModifier.padding(top = 6.dp)) {
            Pillola(t.categoria) { it.accento2 }
            Spacer(GlanceModifier.width(5.dp))
            Pillola("Bin ${t.binario ?: "—"}") { if (t.binarioConfermato) it.accento else it.sub }
        }
        Row(GlanceModifier.padding(top = 8.dp), verticalAlignment = Alignment.Vertical.CenterVertically) {
            BadgeStato(t)
            Spacer(GlanceModifier.width(6.dp))
            PillolaSegui(t, esito.seguito == t.numeroTreno)
        }
        Column(GlanceModifier.padding(top = 8.dp)) {
            if (!t.cancellato) {
                Text(
                    when {
                        minuti > 0 -> "tra $minuti min"
                        aBordo && esito.fermateMancanti != null -> fermate(esito.fermateMancanti)
                        else -> "in viaggio"
                    },
                    style = stile(col { it.tx }, 12, bold = true),
                )
            }
            esito.seguitoNonDisponibile?.let {
                Text("treno $it non disponibile", maxLines = 1, style = stile(col { it.ambra }, 11, bold = true))
            }
            if (altezza >= 160.dp && !aBordo) Text("a ${esito.destinazione} ${t.oraArrivo()}", style = stile(col { it.sub }, 11))
            esito.rigaCambio?.takeIf { esito.seguito == t.numeroTreno }?.let {
                Text(it, maxLines = 2, style = stile(col { c -> c.accento2 }, 11, bold = true))
            }
            esito.treni.drop(1).take(extra).forEach {
                Box(GlanceModifier.padding(top = 5.dp)) {
                    Text(
                        "${it.oraPartenza()} · Bin ${it.binario ?: "—"}" + if (it.cancellato) " · canc." else "",
                        maxLines = 1, style = stile(col { c -> if (it.cancellato) c.rosso else c.sub }, 11, bold = true),
                    )
                }
            }
        }
        Spacer(GlanceModifier.defaultWeight())
    }
}

/** 3×2: orario, treno, binario, badge e la barra di avanzamento verso la destinazione. */
@Composable
private fun Medio(esito: EsitoWidget, altezza: Dp) {
    if (esito !is EsitoWidget.Dati) {
        Vuoto(esito)
        return
    }
    val t = esito.treni.first()
    val context = LocalContext.current
    val extra = righeExtra(altezza, base = 136.dp, perRiga = 30.dp, max = 3)
    Column(
        sfondo(esito.seguito != null).padding(horizontal = 16.dp, vertical = 14.dp)
            .clickable(apriTreno(context, t.numeroTreno)),
    ) {
        Intestazione(esito, esito.destinazione.etichettaWidget(max = 24))
        Spacer(GlanceModifier.height(8.dp))
        val aBordo = esito.aBordo(t)
        Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
            Column {
                if (aBordo) Text("ARRIVO", style = stile(col { it.ter }, 11, bold = true))
                Text(
                    if (aBordo) t.oraArrivo() else t.oraPartenza(),
                    style = TextStyle(
                        color = col { it.tx }, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                        textDecoration = if (t.cancellato) TextDecoration.LineThrough else TextDecoration.None,
                    ),
                )
                BadgeStato(t, piccolo = true)
            }
            Spacer(GlanceModifier.width(12.dp))
            Column(GlanceModifier.defaultWeight()) {
                Text(t.rigaWidget(), maxLines = 1, style = stile(col { it.tx }, 11, bold = true))
                Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                    Text("da ${esito.stazione}", maxLines = 1, modifier = GlanceModifier.defaultWeight(), style = stile(col { it.sub }, 11))
                    Spacer(GlanceModifier.width(6.dp))
                    PillolaSegui(t, esito.seguito == t.numeroTreno)
                }
            }
            Spacer(GlanceModifier.width(8.dp))
            Column(
                GlanceModifier.background(col { if (t.binarioConfermato) it.accento.copy(alpha = 0.2f) else it.sf2 })
                    .cornerRadius(12.dp).padding(horizontal = 10.dp, vertical = 5.dp),
                horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
            ) {
                Text("BIN", style = stile(col { if (t.binarioConfermato) it.accento else it.ter }, 11, bold = true))
                Text(t.binario ?: "—", style = stile(col { it.tx }, 15, bold = true))
            }
        }
        Spacer(GlanceModifier.height(10.dp))
        Avanzamento(
            t, esito.stazione, esito.destinazione, esito.avanzamentoReale, esito.fermateMancanti.takeIf { aBordo },
            rigaCambio = esito.rigaCambio.takeIf { esito.seguito == t.numeroTreno },
        )
        Column(GlanceModifier.padding(top = 8.dp)) {
            esito.seguitoNonDisponibile?.let {
                Text("treno $it non disponibile da qui", maxLines = 1, style = stile(col { it.ambra }, 11, bold = true))
            }
            esito.treni.drop(1).take(extra).forEach { RigaCompatta(it, context) }
        }
        Spacer(GlanceModifier.defaultWeight())
    }
}

@Composable
private fun Avanzamento(
    t: ProssimoTreno,
    stazione: String,
    destinazione: String,
    avanzamentoReale: Float? = null,
    fermateMancanti: Int? = null,
    rigaCambio: String? = null,
) {
    val ora = System.currentTimeMillis()
    val minuti = t.minutiAllaPartenza(ora)
    val arrivo = t.oraArrivo()
    Column(GlanceModifier.fillMaxWidth()) {
        LinearProgressIndicator(
            progress = avanzamentoReale ?: t.avanzamento(ora),
            modifier = GlanceModifier.fillMaxWidth().height(5.dp),
            color = col { it.accento },
            backgroundColor = col { it.sf2 },
        )
        // lo stato prima dei nomi: se l'altezza non basta, a sparire è la riga meno utile
        Text(
            when {
                t.cancellato -> "treno cancellato"
                minuti > 0 -> "parte tra $minuti min · a $destinazione $arrivo"
                fermateMancanti != null -> "${fermate(fermateMancanti)} · arrivo $arrivo"
                else -> "in viaggio · a $destinazione $arrivo"
            },
            maxLines = 1, modifier = GlanceModifier.padding(top = 4.dp),
            style = stile(col { if (t.cancellato) it.rosso else it.sub }, 11),
        )
        // nomi interi e non sigle: "MPG → BA" andava decifrato
        Text(
            rigaCambio ?: "$stazione → $destinazione", maxLines = 1, modifier = GlanceModifier.padding(top = 4.dp),
            style = stile(col { if (rigaCambio != null) it.accento2 else it.ter }, 11),
        )
    }
}

/** "RE 2416 · Milano Centrale · 1 cambio": i cambi si dicono anche nel poco spazio del widget. */
private fun ProssimoTreno.rigaWidget(): String =
    "$categoria $numeroTreno · $destinazione" + when (cambi) {
        0 -> ""
        1 -> " · ⇄ 1 cambio"
        else -> " · ⇄ $cambi cambi"
    }

/** Corsa seguita già partita: il widget smette di contare la partenza e conta l'arrivo. */
private fun EsitoWidget.Dati.aBordo(t: ProssimoTreno): Boolean =
    seguito == t.numeroTreno && t.minutiAllaPartenza(System.currentTimeMillis()) < 0

private fun fermate(n: Int) = if (n == 1) "1 fermata alla tua" else "$n fermate alla tua"

/** 4×2: i prossimi treni in lista con badge, aggiornamento manuale e avviso sciopero. */
@Composable
private fun Lista(esito: EsitoWidget, altezza: Dp) {
    if (esito !is EsitoWidget.Dati) {
        Vuoto(esito)
        return
    }
    val context = LocalContext.current
    val righe = righeExtra(altezza, base = 84.dp, perRiga = 42.dp, max = 8).coerceAtLeast(1)
    Column(sfondo(false).padding(horizontal = 15.dp, vertical = 13.dp)) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
            Box(
                GlanceModifier.size(22.dp).background(ImageProvider(R.drawable.widget_logo)),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    ImageProvider(R.drawable.ic_notifica), contentDescription = null,
                    modifier = GlanceModifier.size(15.dp), colorFilter = ColorFilter.tint(ColorProvider(Color.White)),
                )
            }
            Spacer(GlanceModifier.width(7.dp))
            Box(GlanceModifier.defaultWeight()) { Intestazione(esito, esito.destinazione.etichettaWidget(max = 24)) }
            Spacer(GlanceModifier.width(6.dp))
            // area di tocco da 44dp attorno al cerchio visibile da 26: 22dp era mezzo dito
            Box(
                GlanceModifier.size(44.dp).clickable(actionRunCallback<AggiornaWidgetAction>()),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    GlanceModifier.size(26.dp).background(col { it.sf2 }).cornerRadius(13.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("⟳", style = stile(col { it.accento }, 13)) }
            }
        }
        Spacer(GlanceModifier.height(8.dp))
        Column {
            esito.treni.take(righe).forEachIndexed { i, t ->
                RigaTreno(t, context, esito.destinazione, evidenziata = i == 0 && esito.seguito != null)
            }
        }
        Spacer(GlanceModifier.defaultWeight())
        Text(
            esito.seguitoNonDisponibile?.let { "treno $it non disponibile da qui" }
                ?: "${esito.stazione} · agg. ${esito.aggiornatoAlle.comeOra()}",
            maxLines = 1,
            style = stile(col { if (esito.seguitoNonDisponibile != null) it.ambra else it.ter }, 11),
        )
    }
}

@Composable
private fun RigaCompatta(t: ProssimoTreno, context: Context) {
    Row(
        GlanceModifier.fillMaxWidth().clickable(apriTreno(context, t.numeroTreno)).padding(vertical = 3.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Text(t.oraPartenza(), modifier = GlanceModifier.width(44.dp), style = stile(col { it.tx }, 12, bold = true))
        Text(
            t.rigaWidget(), maxLines = 1,
            modifier = GlanceModifier.defaultWeight(), style = stile(col { it.sub }, 11),
        )
        BadgeStato(t, piccolo = true)
    }
}

@Composable
private fun RigaTreno(t: ProssimoTreno, context: Context, destinazione: String, evidenziata: Boolean) {
    val minuti = t.minutiAllaPartenza(System.currentTimeMillis())
    Row(
        GlanceModifier.fillMaxWidth()
            .background(col { if (evidenziata) it.accentoSoft else Color.Transparent })
            .cornerRadius(12.dp)
            .clickable(apriTreno(context, t.numeroTreno))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Column(GlanceModifier.width(46.dp)) {
            Text(
                t.oraPartenza(),
                style = TextStyle(
                    color = col { it.tx }, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    textDecoration = if (t.cancellato) TextDecoration.LineThrough else TextDecoration.None,
                ),
            )
            if (!t.cancellato) {
                Text(
                    when { minuti > 0 -> "tra $minuti′"; else -> "in viaggio" },
                    maxLines = 1, style = stile(col { it.ter }, 11),
                )
            }
        }
        Box(GlanceModifier.width(3.dp).height(30.dp).cornerRadius(12.dp).background(col { it.colore(t.semaforo()) })) {}
        Spacer(GlanceModifier.width(10.dp))
        Column(GlanceModifier.defaultWeight()) {
            Text(t.rigaWidget(), maxLines = 1, style = stile(col { it.tx }, 11, bold = true))
            Text("a $destinazione ${t.oraArrivo()}", maxLines = 1, style = stile(col { it.sub }, 11))
        }
        // in orario lo dice già la barretta verde: il badge serve solo quando c'è qualcosa da dire
        if (t.semaforo() != Semaforo.InOrario) {
            BadgeStato(t, piccolo = true)
            Spacer(GlanceModifier.width(6.dp))
        }
        PillolaSegui(t, evidenziata)
        Spacer(GlanceModifier.width(6.dp))
        Text(
            "Bin ${t.binario ?: "—"}",
            modifier = GlanceModifier.background(col { if (t.binarioConfermato) it.accento.copy(alpha = 0.2f) else it.sf2 })
                .cornerRadius(12.dp).padding(horizontal = 8.dp, vertical = 3.dp),
            style = stile(col { it.tx }, 12, bold = true),
        )
    }
}
