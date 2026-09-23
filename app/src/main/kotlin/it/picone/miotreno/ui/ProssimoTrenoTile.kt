package it.picone.miotreno.ui

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import androidx.core.content.getSystemService
import it.picone.miotreno.Deps
import it.picone.miotreno.R
import it.picone.miotreno.domain.ProssimoTreno
import it.picone.miotreno.widget.EXTRA_NUMERO_TRENO
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val ETICHETTA_TILE = "Prossimo treno"

/** Finché la tendina è aperta il countdown si aggiorna da solo. */
private const val PASSO_TILE_MS = 30_000L

/**
 * Tile nelle impostazioni rapide: prossimo treno utile (o quello seguito) e minuti alla partenza.
 * Tap: apre l'app, sul dettaglio se c'è una corsa seguita.
 */
class ProssimoTrenoTile : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var seguito: Int? = null

    override fun onStartListening() {
        Deps.init(this)
        job?.cancel()
        job = scope.launch {
            while (true) {
                aggiorna()
                delay(PASSO_TILE_MS)
            }
        }
    }

    override fun onStopListening() {
        job?.cancel()
        job = null
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun aggiorna() {
        val tile = qsTile ?: return
        val esito = runCatching { withContext(Dispatchers.IO) { prossimo() } }.getOrElse { EsitoTile.ErroreRete }
        seguito = (esito as? EsitoTile.Dati)?.seguito
        tile.label = ETICHETTA_TILE
        tile.state = if (esito is EsitoTile.Dati) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.subtitle = when (esito) {
            is EsitoTile.Dati -> esito.testo
            EsitoTile.PosizioneNonDisponibile -> "posizione non disponibile"
            EsitoTile.ErroreRete -> "dati non disponibili"
            EsitoTile.NessunTreno -> "nessun treno"
            EsitoTile.NonConfigurato -> "apri l'app per iniziare"
        }
        tile.updateTile()
    }

    private sealed interface EsitoTile {
        data class Dati(val testo: String, val seguito: Int?) : EsitoTile
        data object PosizioneNonDisponibile : EsitoTile
        data object ErroreRete : EsitoTile
        data object NessunTreno : EsitoTile
        data object NonConfigurato : EsitoTile
    }

    private suspend fun prossimo(): EsitoTile {
        val codDestinazione = Deps.impostazioni.flow.first().stazioneDestinazione ?: return EsitoTile.NonConfigurato
        val stazione = Deps.stazione.risolvi(usaUltimaNota = true) ?: return EsitoTile.PosizioneNonDisponibile
        val seguito = Deps.impostazioni.seguito.first()
        val treni = runCatching { Deps.repository.prossimiTreni(stazione.codici, codDestinazione, seguito = seguito) }
            .getOrElse { return EsitoTile.ErroreRete }
        val t: ProssimoTreno = treni.firstOrNull { it.numeroTreno == seguito?.numeroTreno } ?: treni.firstOrNull { !it.cancellato } ?: return EsitoTile.NessunTreno
        val minuti = t.minutiAllaPartenza(System.currentTimeMillis())
        val quando = when {
            t.cancellato -> "cancellato"
            minuti > 0 -> "$minuti min"
            else -> "in viaggio"
        }
        return EsitoTile.Dati("${t.categoria} ${t.numeroTreno} · $quando", seguito?.numeroTreno?.takeIf { it == t.numeroTreno })
    }

    override fun onClick() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .also { i -> seguito?.let { i.putExtra(EXTRA_NUMERO_TRENO, it) } }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 2, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}

/** Prompt nativo "Aggiungi tile" (API 33+): chiamato solo da Impostazioni, mai all'avvio. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
fun proponiTile(context: Context) {
    context.getSystemService<android.app.StatusBarManager>()?.requestAddTileService(
        ComponentName(context, ProssimoTrenoTile::class.java),
        ETICHETTA_TILE,
        Icon.createWithResource(context, R.drawable.ic_notifica),
        context.mainExecutor,
    ) { }
}
