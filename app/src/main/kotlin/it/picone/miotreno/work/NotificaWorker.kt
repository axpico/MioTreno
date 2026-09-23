package it.picone.miotreno.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import it.picone.miotreno.Deps
import it.picone.miotreno.data.rilevanteOggiODomani
import it.picone.miotreno.domain.ProssimoTreno
import it.picone.miotreno.domain.trenoInEvidenza
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

private const val LAVORO_AVVISO = "avviso-treno"
private const val KEY_NUMERO = "numero"
private const val TAG = "NotificaWorker"

/**
 * Il periodico può girare solo ogni 15 minuti, che non basta per centrare "N minuti prima".
 * Quindi qui si guarda solo *quale* sia il prossimo treno utile e si schedula un one-shot
 * all'orario esatto. Niente allarmi esatti: non serve la precisione al secondo.
 */
class NotificaWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Deps.init(applicationContext)
        val imp = Deps.impostazioni.flow.first()
        val wm = WorkManager.getInstance(applicationContext)
        val codDestinazione = imp.stazioneDestinazione ?: return Result.success()
        if (!imp.notifiche) {
            wm.cancelUniqueWork(LAVORO_AVVISO)
            fermaLiveUpdate(applicationContext)
            return Result.success()
        }
        // smesso di seguire: la Live Update non ha più un soggetto
        if (Deps.impostazioni.seguito.first() == null) fermaLiveUpdate(applicationContext)

        val (treno, stazione) = prossimoTrenoUtile(codDestinazione) ?: return Result.success()
        val avvisoAlle = treno.orarioPartenzaMs + treno.ritardoMinuti * 60_000L -
            imp.anticipoMinuti * 60_000L
        val ritardoMs = avvisoAlle - System.currentTimeMillis()

        // già dentro la finestra: notifica subito, senza aspettare un altro giro.
        // Corsa seguita: tracking in foreground con avanzamento al posto della notifica statica,
        // stesso controllo del ramo scatto esatto in AvvisoTrenoWorker.
        if (ritardoMs <= 0) {
            val seguito = Deps.impostazioni.seguito.first()?.numeroTreno
            if (seguito == treno.numeroTreno) {
                avviaLiveUpdate(applicationContext)
                return Result.success()
            }
            mostraNotificaTreno(
                applicationContext, treno,
                minuti = treno.minutiAllaPartenza(System.currentTimeMillis()),
                stazione = stazione,
                sciopero = Deps.repository.scioperiInCache().rilevanteOggiODomani()
                    .takeIf { imp.avvisiSciopero },
                destinazioneNome = Deps.repository.stazioni().firstOrNull { it.codice == codDestinazione }?.nome
                    ?: "destinazione",
            )
            return Result.success()
        }

        wm.enqueueUniqueWork(
            LAVORO_AVVISO,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<AvvisoTrenoWorker>()
                .setInitialDelay(ritardoMs, TimeUnit.MILLISECONDS)
                .setInputData(Data.Builder().putInt(KEY_NUMERO, treno.numeroTreno).build())
                // scatta il più vicino possibile all'orario esatto anche sotto Doze
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build(),
        )
        return Result.success()
    }

    private suspend fun prossimoTrenoUtile(codDestinazione: String): Pair<ProssimoTreno, String>? {
        val stazione = Deps.stazione.risolvi(usaUltimaNota = true) ?: return null
        // se stai seguendo una corsa la notifica riguarda quella, non il prossimo qualsiasi
        val seguito = Deps.impostazioni.seguito.first()
        val treni = runCatching { Deps.repository.prossimiTreni(stazione.codici, codDestinazione, seguito = seguito) }
            .getOrNull()
            ?.filter { it.minutiAllaPartenza(System.currentTimeMillis()) > 0 }
            ?: return null
        val treno = trenoInEvidenza(treni, seguito) ?: return null
        return treno to stazione.nome
    }
}

/** Lo scatto effettivo all'orario calcolato: rilegge i dati, che nel frattempo cambiano. */
class AvvisoTrenoWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Deps.init(applicationContext)
        val numero = inputData.getInt(KEY_NUMERO, -1)
        val imp = Deps.impostazioni.flow.first()
        val codDestinazione = imp.stazioneDestinazione
        if (!imp.notifiche || numero < 0 || codDestinazione == null) return Result.success()
        val nomeDestinazione = Deps.repository.stazioni().firstOrNull { it.codice == codDestinazione }?.nome ?: "destinazione"

        val stazione = Deps.stazione.risolvi(usaUltimaNota = true) ?: run {
            Log.w(TAG, "avviso treno $numero saltato: stazione non risolvibile")
            return Result.success()
        }
        val seguito = Deps.impostazioni.seguito.first()?.takeIf { it.numeroTreno == numero }
        val risultato = runCatching { Deps.repository.prossimiTreni(stazione.codici, codDestinazione, seguito = seguito) }
        val treno = risultato.getOrNull()?.firstOrNull { it.numeroTreno == numero }
        if (treno == null) {
            if (risultato.isFailure) {
                Log.w(TAG, "avviso treno $numero saltato: fetch treni fallito", risultato.exceptionOrNull())
            } else {
                Log.w(TAG, "avviso treno $numero saltato: non più tra i prossimi treni (partito nel frattempo?)")
            }
            return Result.success()
        }

        val sciopero = Deps.repository.scioperiInCache()
            .rilevanteOggiODomani()
            .takeIf { imp.avvisiSciopero }

        // corsa seguita: tracking in foreground con avanzamento al posto della notifica statica
        val seguitoNumero = Deps.impostazioni.seguito.first()?.numeroTreno
        if (seguitoNumero == numero) {
            avviaLiveUpdate(applicationContext)
            return Result.success()
        }

        mostraNotificaTreno(
            applicationContext, treno,
            minuti = treno.minutiAllaPartenza(System.currentTimeMillis()),
            stazione = stazione.nome,
            sciopero = sciopero,
            destinazioneNome = nomeDestinazione,
        )
        return Result.success()
    }
}
