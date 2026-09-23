package it.picone.miotreno.work

import android.app.Notification
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import android.content.Context
import it.picone.miotreno.Deps
import it.picone.miotreno.data.rilevanteOggiODomani
import it.picone.miotreno.domain.DettaglioTreno
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

const val LAVORO_LIVE = "live-update"
private const val TAG = "LiveTrackingWorker"

/** Passo minimo/massimo fra un aggiornamento e l'altro mentre la corsa seguita è in viaggio. */
private const val PASSO_MIN_MS = 30_000L
private const val PASSO_MAX_MS = 5 * 60_000L

/** Entro quanti minuti da un evento (partenza o arrivo) si aggiorna al passo minimo. */
private const val FINESTRA_STRETTA_MIN = 5

/**
 * Ogni quanto ricontrollare, in base a quanto manca all'evento più vicino.
 *
 * Aggiornare ogni 30 s per tutto il viaggio è sprecato: a mezz'ora dall'arrivo il ritardo
 * cambia di rado e la batteria la paga l'utente. Sotto i cinque minuti, invece, è proprio
 * quando serve sapere il binario e il minuto esatto.
 */
internal fun passoLive(minutiAllEvento: Int): Long = when {
    minutiAllEvento <= FINESTRA_STRETTA_MIN -> PASSO_MIN_MS
    minutiAllEvento >= 30 -> PASSO_MAX_MS
    // fra 5 e 30 minuti: interpolazione lineare fra i due estremi
    else -> PASSO_MIN_MS +
        (PASSO_MAX_MS - PASSO_MIN_MS) * (minutiAllEvento - FINESTRA_STRETTA_MIN) / (30 - FINESTRA_STRETTA_MIN)
}

/**
 * Tiene aggiornata la notifica di tracking della corsa seguita finché non arriva a destinazione
 * (o l'utente smette di seguirla): un unico worker "a lunga esecuzione" che ricontrolla treno e
 * andamento a passo variabile (vedi [passoLive]) e ripubblica, invece di una catena di one-shot.
 *
 * `setForeground` promuove il worker a foreground service dall'interno di `doWork` — è la via
 * ufficiale di WorkManager per farlo, e l'unica che non incappa nel divieto Android 12+ di
 * avviare un foreground service da un contesto in background: un `Service` avviato "a mano" con
 * `startForegroundService` da un worker schedulato lancerebbe `ForegroundServiceStartNotAllowedException`
 * proprio quando l'app non è in primo piano, cioè sempre nel caso che ci interessa.
 * Prima di questo, sotto Doze la catena di one-shot poteva slittare di minuti o ore: per un
 * treno in viaggio "aggiornato con ritardo" equivaleva a "non aggiornato".
 */
class LiveTrackingWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Deps.init(applicationContext)
        setForeground(info(notificaTrackingIniziale(applicationContext)))
        while (true) {
            val esito = runCatching { unGiro(applicationContext, ::pubblica) }
                .getOrElse { e -> Log.w(TAG, "giro di tracking fallito, riprovo", e); Giro.Ancora(PASSO_MAX_MS) }
            if (esito !is Giro.Ancora) break
            delay(esito.fraMs)
        }
        return Result.success()
    }

    /**
     * Aggiorna la notifica **passando da setForeground**, non da notify().
     *
     * È la stessa notifica del foreground service: aggiornandola per conto proprio, la
     * ForegroundInfo che WorkManager tiene in cache resterebbe quella dell'avvio — il
     * placeholder — e il dispatcher potrebbe ripubblicarla sopra il contenuto aggiornato.
     */
    private suspend fun pubblica(notification: Notification) = setForeground(info(notification))

    private fun info(notification: Notification): ForegroundInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(ID_NOTIFICA_TRACKING, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(ID_NOTIFICA_TRACKING, notification)
        }
}

/** Esito di un giro: o si continua fra [Giro.Ancora.fraMs], o il tracking è finito. */
private sealed interface Giro {
    data class Ancora(val fraMs: Long) : Giro
    data object Finito : Giro
}

/**
 * Un giro di tracking: ricontrolla la corsa, aggiorna la notifica, dice quando ripassare.
 */
private suspend fun unGiro(ctx: Context, pubblica: suspend (Notification) -> Unit): Giro {
    val imp = Deps.impostazioni.flow.first()
    val seguito = Deps.impostazioni.seguito.first()
    val codDestinazione = imp.stazioneDestinazione
    if (!imp.notifiche || seguito == null || codDestinazione == null) {
        Log.d(TAG, "tracking chiuso: notifiche=${imp.notifiche} seguito=${seguito != null}")
        rimuoviNotificaTracking(ctx)
        return Giro.Finito
    }

    val stazione = Deps.stazione.risolvi(usaUltimaNota = true) ?: run {
        Log.w(TAG, "tracking: stazione non risolvibile, riprovo al giro successivo")
        return Giro.Ancora(PASSO_MIN_MS)
    }
    val dalFeed = runCatching { Deps.repository.prossimiTreni(stazione.codici, codDestinazione, seguito = seguito) }
        .getOrNull()?.firstOrNull { it.numeroTreno == seguito.numeroTreno } ?: run {
        Log.w(TAG, "tracking: snapshot mancante per ${seguito.numeroTreno}, riprovo al giro successivo")
        return Giro.Ancora(PASSO_MIN_MS)
    }

    val dettaglio = runCatching { Deps.repository.dettaglio(dalFeed, codDestinazione) }.getOrNull()
    val ok = dettaglio as? DettaglioTreno.Ok

    /*
     * Qui stava il motivo per cui la notifica non si aggiornava mai.
     *
     * Appena la corsa parte, `partenze` smette di elencarla e prossimiTreni() ripiega sullo
     * snapshot salvato in DataStore, che nessuno riscrive: ogni giro riceveva un oggetto
     * identico al precedente e ricostruiva una notifica byte per byte uguale. Il worker
     * lavorava, la rete lavorava, il testo non cambiava di una virgola.
     *
     * Il ritardo vero, dopo la partenza, lo dà solo andamentoTreno — che era già stato
     * scaricato qui sopra e buttato via.
     */
    val treno = if (ok != null) dalFeed.copy(ritardoMinuti = ok.ritardoMinuti) else dalFeed

    val adesso = System.currentTimeMillis()
    val minuti = treno.minutiAllaPartenza(adesso)
    // non ancora nella finestra di preavviso: ci pensa AvvisoTrenoWorker a riaccenderlo
    if (minuti > imp.anticipoMinuti) {
        rimuoviNotificaTracking(ctx)
        return Giro.Finito
    }

    val sciopero = Deps.repository.scioperiInCache().rilevanteOggiODomani().takeIf { imp.avvisiSciopero }
    val nomeDestinazione = Deps.repository.stazioni().firstOrNull { it.codice == codDestinazione }?.nome ?: "destinazione"
    pubblica(
        costruisciNotificaTracking(
            ctx, treno, minuti, stazione.nome, stazione.codici, sciopero, dettaglio,
            destinazioneNome = nomeDestinazione,
        ),
    )

    val arrivato = ok != null && ok.indiceBusto >= 0 && ok.indiceCorrente >= ok.indiceBusto
    if (arrivato) Deps.impostazioni.smettiDiSeguire()
    // ultimo fotogramma: resta visibile, non si continua a interrogare un treno finito
    if (arrivato || treno.cancellato) return Giro.Finito

    // si guarda all'evento più vicino: prima della partenza è la partenza, dopo è l'arrivo
    val minutiAllArrivo = treno.orarioArrivoBustoMs
        ?.let { ((it + treno.ritardoMinuti * 60_000L - adesso) / 60_000L).toInt() }
        ?: Int.MAX_VALUE
    return Giro.Ancora(passoLive(minOf(if (minuti < 0) Int.MAX_VALUE else minuti, minutiAllArrivo)))
}

/** Avvia (o lascia proseguire, se già in corso) il tracking della corsa seguita. */
fun avviaLiveUpdate(context: Context) {
    WorkManager.getInstance(context).enqueueUniqueWork(
        LAVORO_LIVE,
        ExistingWorkPolicy.KEEP,
        OneTimeWorkRequestBuilder<LiveTrackingWorker>().build(),
    )
}

fun fermaLiveUpdate(context: Context) {
    WorkManager.getInstance(context).cancelUniqueWork(LAVORO_LIVE)
    rimuoviNotificaTracking(context)
}
