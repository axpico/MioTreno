package it.picone.miotreno.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
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
import androidx.glance.currentState
import androidx.glance.background
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
import it.picone.miotreno.domain.orarioProiettato
import it.picone.miotreno.domain.comeSeguito
import it.picone.miotreno.domain.etichettaStato
import it.picone.miotreno.domain.progressoReale
import it.picone.miotreno.domain.semaforo
import it.picone.miotreno.ui.MainActivity
import it.picone.miotreno.ui.componenti.colore
import it.picone.miotreno.ui.theme.ScuroTb
import it.picone.miotreno.ui.theme.TbColors
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Extra dell'Intent: quale treno aprire quando si tocca una riga del widget. */
const val EXTRA_NUMERO_TRENO = "numero_treno"

/** Chi apre l'app con questo flag vuole anche seguire quel treno, non solo vederlo. */
const val EXTRA_SEGUI = "segui_treno"

private val ORA = DateTimeFormatter.ofPattern("HH:mm")
private fun Long.ora(): String = ORA.format(Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()))

/**
 * Orari proiettati col ritardo corrente.
 *
 * Nel widget il ritardo si vede solo dal colore della barretta: stampare l'orario di tabella
 * accanto a una barretta arancione lascia leggere "parte alle 8:12" a chi invece partirà alle
 * 8:24. Lo spazio non basta per due orari, quindi si mostra quello che conta.
 */
private fun ProssimoTreno.oraPartenza(): String =
    orarioProiettato(orarioPartenzaMs, ritardoMinuti).previstoMs?.ora() ?: "—"

private fun ProssimoTreno.oraArrivo(): String =
    orarioProiettato(orarioArrivoDestinazioneMs, ritardoMinuti).previstoMs?.ora() ?: "—"

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
                if (iniziale is EsitoWidget.Dati && alle > iniziale.aggiornatoAlle) iniziale.conSeguito(prefs[SEGUITO]?.takeIf { it > 0 })
                else iniziale
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
        val avanzamentoReale = trenoSeguito?.let {
            runCatching { repo.dettaglio(it, codDestinazione) }.getOrNull()
                ?.let { d -> (d as? DettaglioTreno.Ok)?.progressoReale() }
        }

        val nomeDestinazione = repo.stazioni().firstOrNull { it.codice == codDestinazione }?.nome ?: "destinazione"
        return EsitoWidget.Dati(
            stazione = stazione.nome,
            destinazione = nomeDestinazione,
            treni = treni,
            aggiornatoAlle = System.currentTimeMillis(),
            sciopero = imp.avvisiSciopero && repo.scioperiInCache().rilevanteOggiODomani() != null,
            avanzamentoReale = avanzamentoReale,
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

private val tb: TbColors = ScuroTb
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

private fun stile(colore: Color, size: Int, bold: Boolean = false) = TextStyle(
    color = ColorProvider(colore), fontSize = size.sp,
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
        Text(titolo, style = stile(tb.tx, 12, bold = permesso || erroreRete))
        Text(sottotitolo, style = stile(tb.sub, 10))
    }
}

/** Badge di stato del widget: pieno per cancellato, tinto per il resto. Statico (RemoteViews). */
@Composable
private fun BadgeStato(t: ProssimoTreno, piccolo: Boolean = false) {
    val s = t.semaforo()
    val c = tb.colore(s)
    val pieno = s == Semaforo.Cancellato
    Text(
        t.etichettaStato(),
        maxLines = 1,
        modifier = GlanceModifier
            .background(if (pieno) c else c.copy(alpha = 0.18f))
            .cornerRadius(2.dp)
            .padding(horizontal = if (piccolo) 6.dp else 8.dp, vertical = if (piccolo) 1.dp else 3.dp),
        style = stile(if (pieno) tb.bg else c, if (piccolo) 9 else 10, bold = true),
    )
}

@Composable
private fun Pillola(testo: String, colore: Color = tb.sub) {
    Text(
        testo, maxLines = 1,
        modifier = GlanceModifier.background(colore.copy(alpha = 0.16f)).cornerRadius(2.dp)
            .padding(horizontal = 7.dp, vertical = 2.dp),
        style = stile(colore, 10, bold = true),
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
            .background(if (seguito) tb.accento else tb.accento.copy(alpha = 0.16f))
            .cornerRadius(3.dp)
            .clickable(
                actionRunCallback<SeguiTrenoAction>(
                    actionParametersOf(PARAM_NUMERO_TRENO to t.numeroTreno, PARAM_DATA to s.data, PARAM_ARRIVO to s.arrivoDestinazioneMs),
                ),
            )
            .padding(horizontal = 8.dp, vertical = 3.dp),
        style = stile(if (seguito) tb.onAccento else tb.accento, 9, bold = true),
    )
}

@Composable
private fun Intestazione(esito: EsitoWidget.Dati, testo: String) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
        Text(
            if (esito.seguito != null) "● SEGUI · $testo" else "→ $testo",
            maxLines = 1, modifier = GlanceModifier.defaultWeight(),
            style = stile(tb.accento, 9, bold = true),
        )
        if (esito.sciopero) Text("⚠ sciopero", style = stile(tb.sciopero, 9, bold = true))
    }
}

/** 2×2: orario grande, categoria e binario, badge di stato, countdown. */
@Composable
private fun Compatto(esito: EsitoWidget, altezza: Dp) {
    if (esito !is EsitoWidget.Dati) { Vuoto(esito); return }
    val t = esito.treni.first()
    val minuti = t.minutiAllaPartenza(System.currentTimeMillis())
    val context = LocalContext.current
    val extra = righeExtra(altezza, base = 160.dp, perRiga = 21.dp, max = 3)
    Column(sfondo(esito.seguito != null).padding(15.dp).clickable(apriTreno(context, t.numeroTreno))) {
        Intestazione(esito, esito.destinazione.etichettaWidget())
        Text(
            if (esito.seguito == t.numeroTreno) "LA TUA CORSA" else "PROSSIMA PARTENZA",
            modifier = GlanceModifier.padding(top = 8.dp),
            style = stile(tb.ter, 8, bold = true),
        )
        Text(
            t.oraPartenza(),
            modifier = GlanceModifier.padding(top = 2.dp),
            style = TextStyle(
                color = ColorProvider(tb.tx), fontSize = 30.sp, fontWeight = FontWeight.Bold,
                textDecoration = if (t.cancellato) TextDecoration.LineThrough else TextDecoration.None,
            ),
        )
        Row(GlanceModifier.padding(top = 6.dp)) {
            Pillola(t.categoria, tb.accento2)
            Spacer(GlanceModifier.width(5.dp))
            Pillola("Bin ${t.binario ?: "—"}", if (t.binarioConfermato) tb.accento else tb.sub)
        }
        Row(GlanceModifier.padding(top = 8.dp), verticalAlignment = Alignment.Vertical.CenterVertically) {
            BadgeStato(t)
            Spacer(GlanceModifier.width(6.dp))
            PillolaSegui(t, esito.seguito == t.numeroTreno)
        }
        Column(GlanceModifier.padding(top = 8.dp)) {
            if (!t.cancellato) Text(
                if (minuti > 0) "tra $minuti min" else "in viaggio",
                style = stile(tb.tx, 11, bold = true),
            )
            esito.seguitoNonDisponibile?.let { Text("treno $it non disponibile", maxLines = 1, style = stile(tb.ambra, 9, bold = true)) }
            if (altezza >= 160.dp) Text("a ${esito.destinazione} ${t.oraArrivo()}", style = stile(tb.sub, 10))
            esito.treni.drop(1).take(extra).forEach {
                Box(GlanceModifier.padding(top = 5.dp)) {
                    Text(
                        "${it.oraPartenza()} · Bin ${it.binario ?: "—"}" + if (it.cancellato) " · canc." else "",
                        maxLines = 1, style = stile(if (it.cancellato) tb.rosso else tb.sub, 11, bold = true),
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
    if (esito !is EsitoWidget.Dati) { Vuoto(esito); return }
    val t = esito.treni.first()
    val context = LocalContext.current
    val extra = righeExtra(altezza, base = 136.dp, perRiga = 30.dp, max = 3)
    Column(
        sfondo(esito.seguito != null).padding(horizontal = 16.dp, vertical = 14.dp)
            .clickable(apriTreno(context, t.numeroTreno)),
    ) {
        Intestazione(esito, esito.destinazione.etichettaWidget(max = 24))
        Spacer(GlanceModifier.height(8.dp))
        Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
            Column {
                Text(
                    t.oraPartenza(),
                    style = TextStyle(
                        color = ColorProvider(tb.tx), fontSize = 24.sp, fontWeight = FontWeight.Bold,
                        textDecoration = if (t.cancellato) TextDecoration.LineThrough else TextDecoration.None,
                    ),
                )
                BadgeStato(t, piccolo = true)
            }
            Spacer(GlanceModifier.width(12.dp))
            Column(GlanceModifier.defaultWeight()) {
                Text("${t.categoria} ${t.numeroTreno} · ${t.destinazione}", maxLines = 1, style = stile(tb.tx, 11, bold = true))
                Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                    Text("da ${esito.stazione}", maxLines = 1, modifier = GlanceModifier.defaultWeight(), style = stile(tb.sub, 10))
                    Spacer(GlanceModifier.width(6.dp))
                    PillolaSegui(t, esito.seguito == t.numeroTreno)
                }
            }
            Spacer(GlanceModifier.width(8.dp))
            Column(
                GlanceModifier.background(if (t.binarioConfermato) tb.accento.copy(alpha = 0.2f) else tb.sf2)
                    .cornerRadius(2.dp).padding(horizontal = 10.dp, vertical = 5.dp),
                horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
            ) {
                Text("BIN", style = stile(if (t.binarioConfermato) tb.accento else tb.ter, 8, bold = true))
                Text(t.binario ?: "—", style = stile(tb.tx, 15, bold = true))
            }
        }
        Spacer(GlanceModifier.height(10.dp))
        Avanzamento(t, esito.stazione, esito.destinazione, esito.avanzamentoReale)
        Column(GlanceModifier.padding(top = 8.dp)) {
            esito.seguitoNonDisponibile?.let {
                Text("treno $it non disponibile da qui", maxLines = 1, style = stile(tb.ambra, 10, bold = true))
            }
            esito.treni.drop(1).take(extra).forEach { RigaCompatta(it, context) }
        }
        Spacer(GlanceModifier.defaultWeight())
    }
}

@Composable
private fun Avanzamento(t: ProssimoTreno, stazione: String, destinazione: String, avanzamentoReale: Float? = null) {
    val ora = System.currentTimeMillis()
    val minuti = t.minutiAllaPartenza(ora)
    val arrivo = t.oraArrivo()
    Column(GlanceModifier.fillMaxWidth()) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
            Text(sigla(stazione), style = stile(tb.ter, 8, bold = true))
            Spacer(GlanceModifier.width(6.dp))
            LinearProgressIndicator(
                progress = avanzamentoReale ?: t.avanzamento(ora),
                modifier = GlanceModifier.defaultWeight().height(5.dp),
                color = ColorProvider(tb.accento),
                backgroundColor = ColorProvider(tb.sf2),
            )
            Spacer(GlanceModifier.width(6.dp))
            Text(sigla(destinazione), style = stile(tb.verde, 8, bold = true))
        }
        Text(
            when {
                t.cancellato -> "treno cancellato"
                minuti > 0 -> "parte tra $minuti min · a $destinazione $arrivo"
                else -> "in viaggio · a $destinazione $arrivo"
            },
            maxLines = 1, modifier = GlanceModifier.padding(top = 4.dp),
            style = stile(if (t.cancellato) tb.rosso else tb.sub, 9),
        )
    }
}

private fun sigla(nome: String): String =
    nome.split(" ").filter { it.isNotBlank() }.take(3).joinToString("") { it.first().uppercase() }

/** 4×2: i prossimi treni in lista con badge, aggiornamento manuale e avviso sciopero. */
@Composable
private fun Lista(esito: EsitoWidget, altezza: Dp) {
    if (esito !is EsitoWidget.Dati) { Vuoto(esito); return }
    val context = LocalContext.current
    val righe = righeExtra(altezza, base = 78.dp, perRiga = 44.dp, max = 8).coerceAtLeast(1)
    Column(sfondo(false).padding(horizontal = 15.dp, vertical = 13.dp)) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
            Box(
                GlanceModifier.size(20.dp).background(ImageProvider(R.drawable.widget_logo)),
                contentAlignment = Alignment.Center,
            ) { Text("T", style = stile(Color.White, 10, bold = true)) }
            Spacer(GlanceModifier.width(7.dp))
            Box(GlanceModifier.defaultWeight()) { Intestazione(esito, esito.destinazione.etichettaWidget(max = 24)) }
            Spacer(GlanceModifier.width(6.dp))
            Box(
                GlanceModifier.size(22.dp).background(tb.sf2).cornerRadius(11.dp)
                    .clickable(actionRunCallback<AggiornaWidgetAction>()),
                contentAlignment = Alignment.Center,
            ) { Text("⟳", style = stile(tb.accento, 11)) }
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
                ?: "${esito.stazione} · agg. ${esito.aggiornatoAlle.ora()}",
            maxLines = 1,
            style = stile(if (esito.seguitoNonDisponibile != null) tb.ambra else tb.ter, 9),
        )
    }
}

@Composable
private fun RigaCompatta(t: ProssimoTreno, context: Context) {
    Row(
        GlanceModifier.fillMaxWidth().clickable(apriTreno(context, t.numeroTreno)).padding(vertical = 3.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Text(t.oraPartenza(), modifier = GlanceModifier.width(44.dp), style = stile(tb.tx, 12, bold = true))
        Text("${t.categoria} ${t.numeroTreno} · ${t.destinazione}", maxLines = 1,
            modifier = GlanceModifier.defaultWeight(), style = stile(tb.sub, 11))
        BadgeStato(t, piccolo = true)
    }
}

@Composable
private fun RigaTreno(t: ProssimoTreno, context: Context, destinazione: String, evidenziata: Boolean) {
    val minuti = t.minutiAllaPartenza(System.currentTimeMillis())
    Row(
        GlanceModifier.fillMaxWidth()
            .background(if (evidenziata) tb.accento.copy(alpha = 0.16f) else Color.Transparent)
            .cornerRadius(3.dp)
            .clickable(apriTreno(context, t.numeroTreno))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Column(GlanceModifier.width(46.dp)) {
            Text(
                t.oraPartenza(),
                style = TextStyle(
                    color = ColorProvider(tb.tx), fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    textDecoration = if (t.cancellato) TextDecoration.LineThrough else TextDecoration.None,
                ),
            )
            if (!t.cancellato) Text(
                when { minuti > 0 -> "tra ${minuti}′"; else -> "in viaggio" },
                maxLines = 1, style = stile(tb.ter, 8),
            )
        }
        Box(GlanceModifier.width(3.dp).height(30.dp).cornerRadius(2.dp).background(tb.colore(t.semaforo()))) {}
        Spacer(GlanceModifier.width(10.dp))
        Column(GlanceModifier.defaultWeight()) {
            Text("${t.categoria} ${t.numeroTreno} · ${t.destinazione}", maxLines = 1, style = stile(tb.tx, 11, bold = true))
            Text("a $destinazione ${t.oraArrivo()}", maxLines = 1, style = stile(tb.sub, 9))
        }
        BadgeStato(t, piccolo = true)
        Spacer(GlanceModifier.width(6.dp))
        PillolaSegui(t, evidenziata)
        Spacer(GlanceModifier.width(6.dp))
        Text(
            t.binario ?: "—",
            modifier = GlanceModifier.background(if (t.binarioConfermato) tb.accento.copy(alpha = 0.2f) else tb.sf2)
                .cornerRadius(2.dp).padding(horizontal = 8.dp, vertical = 3.dp),
            style = stile(tb.tx, 12, bold = true),
        )
    }
}
