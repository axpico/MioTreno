package it.picone.miotreno.work

import android.app.Notification
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import it.picone.miotreno.Deps
import it.picone.miotreno.data.Impostazioni
import it.picone.miotreno.data.rilevanteOggiODomani
import it.picone.miotreno.domain.DettaglioTreno
import it.picone.miotreno.domain.ProssimoTreno
import it.picone.miotreno.domain.StazioneCorrente
import it.picone.miotreno.domain.TrenoSeguito
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
    else ->
        PASSO_MIN_MS +
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
    val corsa = when (val r = corsaDaSeguire(ctx)) {
        is Ricerca.Trovata -> r.corsa
        is Ricerca.Niente -> return r.giro
    }
    val imp = corsa.imp
    // tratta in corso, cambio, coincidenza persa: vedi controllaSeguito
    val esito = controllaSeguito(ctx, corsa.seguito, corsa.dalFeed, corsa.codDestinazione)
    val dettaglio = esito.dettaglio

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
    val treno = (dettaglio as? DettaglioTreno.Ok)?.let { esito.treno.copy(ritardoMinuti = it.ritardoMinuti) } ?: esito.treno

    val adesso = System.currentTimeMillis()
    val minuti = treno.minutiAllaPartenza(adesso)
    // non ancora nella finestra di preavviso: ci pensa AvvisoTrenoWorker a riaccenderlo. Solo
    // per il primo treno: alla stazione di cambio si aspetta il secondo con il tracking acceso
    if (esito.primaTratta && minuti > imp.anticipoMinuti) {
        rimuoviNotificaTracking(ctx)
        return Giro.Finito
    }

    val sciopero = Deps.repository.scioperiInCache().rilevanteOggiODomani().takeIf { imp.avvisiSciopero }
    // un viaggio con cambi mostra la tratta in corso: da dove sali a dove scendi
    val nomeDestinazione = if (corsa.dalFeed.tratte.isEmpty()) {
        Deps.repository.nomeStazione(corsa.codDestinazione) ?: "destinazione"
    } else {
        treno.destinazione
    }
    pubblica(
        costruisciNotificaTracking(
            ctx, treno, minuti, esito.nomeSalita ?: corsa.stazione.nome, corsa.stazione.codici, sciopero, dettaglio,
            destinazioneNome = nomeDestinazione,
            rigaCambio = esito.cambio?.testo,
        ),
    )

    if (esito.arrivato) Deps.impostazioni.smettiDiSeguire()
    // ultimo fotogramma: resta visibile, non si continua a interrogare un treno finito
    if (esito.arrivato || treno.cancellato) return Giro.Finito
    return Giro.Ancora(prossimoPasso(treno, minuti, adesso))
}

/** Tutto quello che serve a un giro di tracking, trovato da [corsaDaSeguire]. */
private class CorsaDaSeguire(
    val imp: Impostazioni,
    val seguito: TrenoSeguito,
    val codDestinazione: String,
    val stazione: StazioneCorrente,
    val dalFeed: ProssimoTreno,
)

private sealed interface Ricerca {
    class Trovata(val corsa: CorsaDaSeguire) : Ricerca

    /** Niente da tracciare in questo giro: [giro] dice se smettere o riprovare. */
    class Niente(val giro: Giro) : Ricerca
}

/**
 * La corsa seguita, ancora valida e con lo snapshot rinfrescato dal feed. Senza notifiche, senza
 * corsa seguita o senza destinazione il tracking finisce; senza stazione o feed si riprova.
 */
private suspend fun corsaDaSeguire(ctx: Context): Ricerca {
    val imp = Deps.impostazioni.flow.first()
    val seguito = Deps.impostazioni.seguito.first()
    val codDestinazione = imp.stazioneDestinazione
    if (!imp.notifiche || seguito == null || codDestinazione == null) {
        Log.d(TAG, "tracking chiuso: notifiche=${imp.notifiche} seguito=${seguito != null}")
        rimuoviNotificaTracking(ctx)
        return Ricerca.Niente(Giro.Finito)
    }
    val stazione = Deps.stazione.risolvi(usaUltimaNota = true) ?: run {
        Log.w(TAG, "tracking: stazione non risolvibile, riprovo al giro successivo")
        return Ricerca.Niente(Giro.Ancora(PASSO_MIN_MS))
    }
    val dalFeed = runCatching { Deps.repository.prossimiTreni(stazione.codici, codDestinazione, seguito = seguito) }
        .getOrNull()?.firstOrNull { it.numeroTreno == seguito.numeroTreno } ?: run {
        Log.w(TAG, "tracking: snapshot mancante per ${seguito.numeroTreno}, riprovo al giro successivo")
        return Ricerca.Niente(Giro.Ancora(PASSO_MIN_MS))
    }
    // "segui" dal widget salva solo numero/data/arrivo: qui lo snapshot si completa, finché la
    // corsa è ancora nel feed, o dopo la partenza il fallback non avrebbe di che ripartire
    Deps.impostazioni.aggiornaSeguito(dalFeed)
    val aggiornato = Deps.impostazioni.seguito.first() ?: seguito
    return Ricerca.Trovata(CorsaDaSeguire(imp, aggiornato, codDestinazione, stazione, dalFeed))
}

/** Fra quanto ripassare: si guarda all'evento più vicino, prima la partenza, poi l'arrivo. */
internal fun prossimoPasso(treno: ProssimoTreno, minutiAllaPartenza: Int, adesso: Long): Long {
    val minutiAllArrivo = treno.orarioArrivoDestinazioneMs
        ?.let { ((it + treno.ritardoMinuti * 60_000L - adesso) / 60_000L).toInt() }
        ?: Int.MAX_VALUE
    return passoLive(minOf(if (minutiAllaPartenza < 0) Int.MAX_VALUE else minutiAllaPartenza, minutiAllArrivo))
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
