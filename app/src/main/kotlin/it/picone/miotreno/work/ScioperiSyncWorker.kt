package it.picone.miotreno.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import it.picone.miotreno.Deps

/**
 * Gli scioperi ferroviari sono comunicati con almeno dieci giorni di preavviso:
 * una sincronizzazione al giorno è sufficiente.
 */
class ScioperiSyncWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Deps.init(applicationContext)
        return runCatching { Deps.repository.aggiornaScioperi() }
            .fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
    }
}
