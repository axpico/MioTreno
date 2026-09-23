package it.picone.miotreno.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

const val LAVORO_SCIOPERI = "sync-scioperi"
const val LAVORO_NOTIFICA = "check-notifica"
const val LAVORO_NOTIFICA_SUBITO = "check-notifica-subito"
const val LAVORO_WIDGET = "refresh-widget"

private val CON_RETE = Constraints.Builder()
    .setRequiredNetworkType(NetworkType.CONNECTED)
    .build()

/** Un solo punto di registrazione dei lavori periodici, chiamato all'avvio dell'app. */
fun pianificaLavoriPeriodici(context: Context) {
    val wm = WorkManager.getInstance(context)

    wm.enqueueUniquePeriodicWork(
        LAVORO_SCIOPERI,
        ExistingPeriodicWorkPolicy.KEEP,
        PeriodicWorkRequestBuilder<ScioperiSyncWorker>(1, TimeUnit.DAYS)
            .setConstraints(CON_RETE)
            .build(),
    )

    // 15 min è il minimo consentito da WorkManager per il lavoro periodico:
    // il worker guarda avanti e schedula lui stesso il one-shot all'orario giusto.
    wm.enqueueUniquePeriodicWork(
        LAVORO_NOTIFICA,
        ExistingPeriodicWorkPolicy.KEEP,
        PeriodicWorkRequestBuilder<NotificaWorker>(15, TimeUnit.MINUTES)
            .setConstraints(CON_RETE)
            .build(),
    )

    wm.enqueueUniquePeriodicWork(
        LAVORO_WIDGET,
        ExistingPeriodicWorkPolicy.KEEP,
        PeriodicWorkRequestBuilder<WidgetRefreshWorker>(15, TimeUnit.MINUTES)
            .setConstraints(CON_RETE)
            .build(),
    )
}

/**
 * Ricalcola subito la notifica pre-partenza (cambio corsa seguita, anticipo, stazione).
 * Senza questo, dopo "Segui" l'avviso poteva arrivare fino a 15 minuti dopo il dovuto —
 * o non arrivare affatto, se la partenza cadeva prima del giro successivo del periodico.
 */
fun ricalcolaNotifica(context: Context) {
    WorkManager.getInstance(context).enqueueUniqueWork(
        LAVORO_NOTIFICA_SUBITO,
        ExistingWorkPolicy.REPLACE,
        OneTimeWorkRequestBuilder<NotificaWorker>().setConstraints(CON_RETE).build(),
    )
}
