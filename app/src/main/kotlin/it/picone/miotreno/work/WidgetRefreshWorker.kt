package it.picone.miotreno.work

import android.content.Context
import it.picone.miotreno.widget.aggiornaWidget
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class WidgetRefreshWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        runCatching { aggiornaWidget(applicationContext) }
        return Result.success()
    }
}
