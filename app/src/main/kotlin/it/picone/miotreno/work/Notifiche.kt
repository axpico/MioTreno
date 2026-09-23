package it.picone.miotreno.work

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import it.picone.miotreno.R
import it.picone.miotreno.domain.DettaglioTreno
import it.picone.miotreno.domain.OrarioFermata
import it.picone.miotreno.domain.orarioProiettato
import it.picone.miotreno.domain.ProssimoTreno
import it.picone.miotreno.domain.Sciopero
import it.picone.miotreno.domain.StatoTreno
import it.picone.miotreno.domain.semaforo
import it.picone.miotreno.ui.MainActivity
import it.picone.miotreno.ui.componenti.colore
import it.picone.miotreno.ui.theme.ScuroTb
import it.picone.miotreno.widget.EXTRA_NUMERO_TRENO
import it.picone.miotreno.widget.EXTRA_SEGUI
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

const val CANALE_TRENI = "treni"
const val ID_NOTIFICA_TRENO = 1001

/**
 * La notifica di tracking ha un id suo.
 *
 * Con un solo id l'avviso pre-partenza e il tracking si sovrascrivevano a vicenda: ogni 15
 * minuti [NotificaWorker] ripubblicava sullo stesso slot l'avviso statico di *un altro* treno
 * (quello seguito, essendo partito, era gia fuori dal suo filtro) e la corsa in viaggio
 * spariva da sotto gli occhi.
 */
const val ID_NOTIFICA_TRACKING = 1002

private const val TAG = "Notifiche"

private val ORA = DateTimeFormatter.ofPattern("HH:mm")

private fun Long.comeOraNotifica(): String =
    ORA.format(Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()))

private fun OrarioFermata.programmaMs(): String? = programmataMs?.comeOraNotifica()

fun creaCanale(context: Context) {
    val canale = NotificationChannel(
        CANALE_TRENI,
        "Prossimo treno",
        NotificationManager.IMPORTANCE_HIGH,
    ).apply { description = "Avviso prima della partenza del treno verso la tua destinazione" }
    context.getSystemService<NotificationManager>()?.createNotificationChannel(canale)
}

/**
 * Contenuto della notifica, unico per notifica standard e Live Update: titolo, righe, intent.
 * Se il treno è soppresso o c'è uno sciopero rilevante il testo lo dice al posto del semplice
 * countdown: il conto alla rovescia da solo sarebbe fuorviante.
 */
private fun builderTreno(
    context: Context,
    treno: ProssimoTreno,
    minuti: Int,
    stazione: String,
    sciopero: Sciopero?,
    destinazioneNome: String,
): NotificationCompat.Builder {
    // Gli orari in notifica vanno proiettati come in app: annunciare l'orario di tabella
    // mentre il treno ha +12 significa dare l'ora sbagliata a chi sta correndo in stazione.
    val partenza = orarioProiettato(treno.orarioPartenzaMs, treno.ritardoMinuti)
    val arrivoOrario = orarioProiettato(treno.orarioArrivoBustoMs, treno.ritardoMinuti)
    val ora = partenza.previstoMs?.comeOraNotifica().orEmpty()
    val arrivo = arrivoOrario.previstoMs?.comeOraNotifica()
    /** " (orario 14:20)" quando il previsto si discosta dalla tabella; niente se puntuale. */
    fun OrarioFermata.scarto(): String =
        if (inRitardo) programmaMs()?.let { " (orario $it)" }.orEmpty() else ""

    val titolo = when {
        treno.stato is StatoTreno.Cancellato ->
            "${treno.categoria} ${treno.numeroTreno} cancellato"
        minuti < 0 -> "In viaggio · ${treno.categoria} ${treno.numeroTreno} per ${treno.destinazione}"
        else -> "Fra $minuti min · ${treno.categoria} ${treno.numeroTreno} per ${treno.destinazione}"
    }

    val righe = buildList {
        add(
            "$ora da $stazione · " +
                (if (treno.ritardoMinuti > 0) "+${treno.ritardoMinuti} min" else "in orario") +
                partenza.scarto(),
        )
        treno.binario?.let {
            add("Binario $it (${if (treno.binarioConfermato) "confermato" else "previsto"})")
        }
        arrivo?.let { add("Arrivo a $destinazioneNome alle $it" + arrivoOrario.scarto()) }
        when (val s = treno.stato) {
            is StatoTreno.Cancellato -> add("⚠ ${s.dettaglio}")
            is StatoTreno.Deviato -> add("⚠ Deviato: ${s.dettaglio}")
            is StatoTreno.ParzialmenteSoppresso -> add("⚠ ${s.dettaglio}")
            is StatoTreno.Alterato -> add("⚠ ${s.dettaglio}")
            StatoTreno.Regolare -> Unit
        }
        sciopero?.let { add("⚠ Sciopero ferroviario: fasce garantite 6–9 e 18–21") }
    }

    val apri = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_NUMERO_TRENO, treno.numeroTreno),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    // "Segui treno" apre il dettaglio di quel treno e lo marca come seguito: prima si
    // limitava ad aprire l'app, cioè non seguiva niente.
    val segui = PendingIntent.getActivity(
        context, 1,
        Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_NUMERO_TRENO, treno.numeroTreno)
            .putExtra(EXTRA_SEGUI, true),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    return NotificationCompat.Builder(context, CANALE_TRENI)
        .setSmallIcon(R.drawable.ic_notifica)
        .setContentTitle(titolo)
        .setContentText(righe.first())
        .setStyle(NotificationCompat.InboxStyle().also { st -> righe.forEach(st::addLine) })
        .setContentIntent(apri)
        .addAction(0, "Segui treno", segui)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
}

/** Notifica pre-partenza standard (e fallback sotto API 36). */
fun mostraNotificaTreno(
    context: Context,
    treno: ProssimoTreno,
    minuti: Int,
    stazione: String,
    sciopero: Sciopero?,
    destinazioneNome: String = "destinazione",
) {
    creaCanale(context)
    if (NotificationManagerCompat.from(context).areNotificationsEnabled().not()) {
        Log.w(TAG, "notifica treno ${treno.numeroTreno} saltata: notifiche disabilitate dal sistema")
        return
    }
    runCatching {
        NotificationManagerCompat.from(context).notify(
            ID_NOTIFICA_TRENO,
            builderTreno(context, treno, minuti, stazione, sciopero, destinazioneNome).build(),
        )
    }.onFailure { Log.w(TAG, "notify() fallita per il treno ${treno.numeroTreno}", it) }
}

// ── Live Update (API 36) ─────────────────────────────────────────────────────

/** API 36 e permesso di promozione concesso: altrimenti si resta sulla notifica ongoing normale. */
fun liveUpdateDisponibile(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA &&
        NotificationManagerCompat.from(context).canPostPromotedNotifications()

private class ProgressoTratte(val tratte: Int, val fatte: Int, val sottotitolo: String?)

/** Tratte percorse fra la stazione di partenza e la destinazione, per le due presentazioni sotto. */
private fun progressoTratte(
    dettaglio: DettaglioTreno?,
    codiciPartenza: List<String>,
    stazione: String,
    destinazioneNome: String,
): ProgressoTratte? {
    val ok = dettaglio as? DettaglioTreno.Ok ?: return null
    val inizio = ok.fermate.indexOfFirst { it.codice in codiciPartenza }.coerceAtLeast(0)
    val fine = ok.indiceBusto.takeIf { it > inizio } ?: ok.fermate.lastIndex
    val tratte = (fine - inizio).coerceAtLeast(1)
    val fatte = (ok.indiceCorrente - inizio).coerceIn(0, tratte)
    val sottotitolo = when {
        fatte >= tratte -> "Arrivato a $destinazioneNome"
        ok.indiceCorrente < inizio -> "Non ancora partito da $stazione"
        else -> ok.fermate.getOrNull(ok.indiceCorrente + 1)?.let { "Prossima fermata: ${it.nome}" }
    }
    return ProgressoTratte(tratte, fatte, sottotitolo)
}

/**
 * Live Update per la corsa seguita: un segmento per ogni tratta fra la stazione di partenza e
 * destinazione, punto sulla fermata corrente, colore del semaforo. Stesso titolo, righe e azioni
 * della notifica standard: cambia solo la presentazione. Con [dettaglio] mancante la barra è
 * indeterminata, non inventata. Richiede API 36 e permesso di promozione: altrimenti va usata
 * [mostraTrackingTreno].
 */
private fun costruisciLiveUpdate(
    context: Context,
    treno: ProssimoTreno,
    minuti: Int,
    stazione: String,
    codiciPartenza: List<String>,
    sciopero: Sciopero?,
    dettaglio: DettaglioTreno?,
    destinazioneNome: String = "destinazione",
): Notification {
    creaCanale(context)

    val colore = ScuroTb.colore(treno.semaforo()).toArgb()
    val stile = NotificationCompat.ProgressStyle().setStyledByProgress(true)
    val p = progressoTratte(dettaglio, codiciPartenza, stazione, destinazioneNome)
    if (p == null) {
        stile.setProgressIndeterminate(true)
    } else {
        stile.setProgressSegments((0 until p.tratte).map { NotificationCompat.ProgressStyle.Segment(1).setColor(colore) })
            .setProgress(p.fatte)
        // il punto marca la fermata corrente; a posizione 0 il sistema lo scarta comunque
        if (p.fatte > 0) stile.addProgressPoint(NotificationCompat.ProgressStyle.Point(p.fatte).setColor(colore))
    }

    val chip = when {
        treno.cancellato -> "canc."
        minuti > 0 -> "$minuti′"
        treno.ritardoMinuti > 0 -> "+${treno.ritardoMinuti}′"
        else -> "in orario"
    }
    val b = builderTreno(context, treno, minuti, stazione, sciopero, destinazioneNome)
        .setStyle(stile)
        .setSubText(p?.sottotitolo)
        .setShortCriticalText(chip)
        .setColor(colore)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setAutoCancel(false)
        .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        .setRequestPromotedOngoing(true)
    b.mActions.clear() // "Segui treno" non ha senso: la Live Update esiste solo per la corsa già seguita
    return b.build()
}

/**
 * Fallback di [mostraLiveUpdate] per chi non ha API 36 o non ha concesso il permesso di
 * promozione — cioè quasi tutti: una notifica ongoing normale con barra di avanzamento
 * standard, aggiornata a ogni giro di [LiveTrackingService] invece che "sparata e dimenticata"
 * come la notifica pre-partenza. Senza questa, seguire una corsa su un dispositivo comune non
 * dava alcun aggiornamento dopo la partenza.
 */
private fun costruisciTrackingTreno(
    context: Context,
    treno: ProssimoTreno,
    minuti: Int,
    stazione: String,
    codiciPartenza: List<String>,
    sciopero: Sciopero?,
    dettaglio: DettaglioTreno?,
    destinazioneNome: String = "destinazione",
): Notification {
    creaCanale(context)
    val p = progressoTratte(dettaglio, codiciPartenza, stazione, destinazioneNome)
    val b = builderTreno(context, treno, minuti, stazione, sciopero, destinazioneNome)
        .setSubText(p?.sottotitolo)
        .setColor(ScuroTb.colore(treno.semaforo()).toArgb())
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setAutoCancel(false)
        .setCategory(NotificationCompat.CATEGORY_PROGRESS)
    if (p != null) b.setProgress(p.tratte, p.fatte, false)
    b.mActions.clear()
    return b.build()
}

/**
 * La notifica di tracking da dare a `setForeground`, nella variante migliore disponibile.
 *
 * Costruisce e basta, non pubblica: la notifica del tracking **è** quella del foreground
 * service, e va aggiornata passando da `setForeground`. Con una `notify()` diretta WorkManager
 * resta convinto che valga ancora la ForegroundInfo che gli è stata data all'avvio e può
 * ripubblicare il placeholder "Aggiornamento della corsa in corso…" sopra il dato vero.
 */
fun costruisciNotificaTracking(
    context: Context,
    treno: ProssimoTreno,
    minuti: Int,
    stazione: String,
    codiciPartenza: List<String>,
    sciopero: Sciopero?,
    dettaglio: DettaglioTreno?,
    destinazioneNome: String = "destinazione",
): Notification =
    if (liveUpdateDisponibile(context)) {
        costruisciLiveUpdate(context, treno, minuti, stazione, codiciPartenza, sciopero, dettaglio, destinazioneNome)
    } else {
        costruisciTrackingTreno(context, treno, minuti, stazione, codiciPartenza, sciopero, dettaglio, destinazioneNome)
    }

fun rimuoviNotificaTreno(context: Context) =
    NotificationManagerCompat.from(context).cancel(ID_NOTIFICA_TRENO)

fun rimuoviNotificaTracking(context: Context) =
    NotificationManagerCompat.from(context).cancel(ID_NOTIFICA_TRACKING)

/**
 * Placeholder per `startForeground()`: un servizio in foreground deve mostrare una notifica
 * nell'istante in cui parte, prima che [LiveTrackingService] abbia già fatto il primo giro e
 * sappia cosa scrivere davvero — sostituita subito dopo da [mostraLiveUpdate]/[mostraTrackingTreno].
 */
fun notificaTrackingIniziale(context: Context): Notification {
    creaCanale(context)
    return NotificationCompat.Builder(context, CANALE_TRENI)
        .setSmallIcon(R.drawable.ic_notifica)
        .setContentTitle("Aggiornamento della corsa in corso…")
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        .build()
}
