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

/** Passo fra un aggiornamento e l'altro mentre la corsa seguita è in viaggio. */
private const val PASSO_LIVE_MS = 2 * 60_000L

/**
 * Tiene aggiornata la notifica di tracking della corsa seguita finché non arriva a destinazione
 * (o l'utente smette di seguirla): un unico worker "a lunga esecuzione" che ricontrolla treno e
 * andamento ogni [PASSO_LIVE_MS] e ripubblica, invece di una catena di one-shot.
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
            val continua = runCatching { unGiro(applicationContext) }
                .getOrElse { e -> Log.w(TAG, "giro di tracking fallito, riprovo", e); true }
            if (!continua) break
            delay(PASSO_LIVE_MS)
        }
        return Result.success()
    }

    private fun info(notification: Notification): ForegroundInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(ID_NOTIFICA_TRENO, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(ID_NOTIFICA_TRENO, notification)
        }
}

/**
 * Un giro di tracking. Ritorna `true` se va ripetuto dopo [PASSO_LIVE_MS], `false` se ha finito
 * (arrivo confermato, cancellato, o non c'è più nulla da seguire).
 */
private suspend fun unGiro(ctx: Context): Boolean {
    val imp = Deps.impostazioni.flow.first()
    val seguito = Deps.impostazioni.seguito.first()
    val codDestinazione = imp.stazioneDestinazione
    if (!imp.notifiche || seguito == null || codDestinazione == null) {
        Log.d(TAG, "tracking chiuso: notifiche=${imp.notifiche} seguito=${seguito != null}")
        rimuoviNotificaTreno(ctx)
        return false
    }

    val stazione = Deps.stazione.risolvi(usaUltimaNota = true) ?: run {
        Log.w(TAG, "tracking: stazione non risolvibile, riprovo al giro successivo")
        return true
    }
    val treno = runCatching { Deps.repository.prossimiTreni(stazione.codici, codDestinazione, seguito = seguito) }
        .getOrNull()?.firstOrNull { it.numeroTreno == seguito.numeroTreno } ?: run {
        Log.w(TAG, "tracking: snapshot mancante per ${seguito.numeroTreno}, riprovo al giro successivo")
        return true
    }

    val adesso = System.currentTimeMillis()
    // non ancora nella finestra di preavviso: ci pensa AvvisoTrenoWorker a riaccenderlo
    if (treno.minutiAllaPartenza(adesso) > imp.anticipoMinuti) {
        rimuoviNotificaTreno(ctx)
        return false
    }

    val dettaglio = runCatching { Deps.repository.dettaglio(treno, codDestinazione) }.getOrNull()
    val sciopero = Deps.repository.scioperiInCache().rilevanteOggiODomani().takeIf { imp.avvisiSciopero }
    val nomeDestinazione = Deps.repository.stazioni().firstOrNull { it.codice == codDestinazione }?.nome ?: "destinazione"
    val minuti = treno.minutiAllaPartenza(adesso)
    if (liveUpdateDisponibile(ctx)) {
        mostraLiveUpdate(ctx, treno, minuti, stazione.nome, stazione.codici, sciopero, dettaglio, destinazioneNome = nomeDestinazione)
    } else {
        mostraTrackingTreno(ctx, treno, minuti, stazione.nome, stazione.codici, sciopero, dettaglio, destinazioneNome = nomeDestinazione)
    }

    val ok = dettaglio as? DettaglioTreno.Ok
    val arrivato = ok != null && ok.indiceBusto >= 0 && ok.indiceCorrente >= ok.indiceBusto
    if (arrivato) Deps.impostazioni.smettiDiSeguire()
    // ultimo fotogramma: resta visibile, non si continua a interrogare un treno finito
    return !(arrivato || treno.cancellato)
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
    rimuoviNotificaTreno(context)
}
