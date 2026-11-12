package it.picone.miotreno.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executors
import kotlin.coroutines.resume

private const val TTL_POSIZIONE_MS = 2 * 60_000L

/** Oltre non si aspetta il fix: meglio l'ultima posizione nota che uno schermo vuoto. */
private const val TIMEOUT_FIX_MS = 10_000L

/**
 * Wrapper su LocationManager.
 * ponytail: niente play-services-location. getCurrentLocation basta per una posizione
 * una tantum; aggiungi FusedLocation solo se serve il tracking continuo.
 */
class LocationProvider(private val context: Context) {
    private var ultima: Location? = null
    private var ultimaAlle = 0L

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    suspend fun posizione(): Location? {
        if (!hasPermission()) return null
        ultima?.let { if (System.currentTimeMillis() - ultimaAlle < TTL_POSIZIONE_MS) return it }

        val lm = context.getSystemService<LocationManager>() ?: return null
        // Con il solo permesso COARSE il GPS dà comunque una posizione sfocata, ma al chiuso
        // impiega 30 s e più per il fix: per trovare la stazione bastano fused o rete, istantanei.
        val provider = listOfNotNull(
            LocationManager.FUSED_PROVIDER.takeIf { Build.VERSION.SDK_INT >= Build.VERSION_CODES.S },
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER,
        ).firstOrNull { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }

        // un'ultima posizione di pochi minuti fa va benissimo: niente attesa
        val recente = listOfNotNull(provider, LocationManager.PASSIVE_PROVIDER)
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
            ?.takeIf { System.currentTimeMillis() - it.time < TTL_POSIZIONE_MS }
        if (recente != null) {
            ultima = recente
            ultimaAlle = System.currentTimeMillis()
            return recente
        }

        val fresca = if (provider == null) {
            null
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            withTimeoutOrNull(TIMEOUT_FIX_MS) {
                suspendCancellableCoroutine<Location?> { cont ->
                    val signal = CancellationSignal()
                    cont.invokeOnCancellation { signal.cancel() }
                    val executor = Executors.newSingleThreadExecutor()
                    lm.getCurrentLocation(provider, signal, executor) {
                        if (cont.isActive) cont.resume(it)
                        executor.shutdown()
                    }
                }
            }
        } else {
            // ponytail: getCurrentLocation esiste solo da API 30; sotto, unico modo per
            // ottenere un fix attivo (non solo l'ultimo noto, spesso assente/vecchio su
            // dispositivi senza altre app che chiedono la posizione) è requestSingleUpdate.
            withTimeoutOrNull(TIMEOUT_FIX_MS) {
                suspendCancellableCoroutine<Location?> { cont ->
                    val listener = object : LocationListener {
                        override fun onLocationChanged(location: Location) {
                            if (cont.isActive) cont.resume(location)
                            lm.removeUpdates(this)
                        }
                    }
                    cont.invokeOnCancellation { lm.removeUpdates(listener) }
                    @Suppress("DEPRECATION") // unica via per un fix attivo sotto API 30, vedi sopra
                    lm.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                }
            }
        }

        val loc = fresca
            ?: provider?.let { lm.getLastKnownLocation(it) }
            ?: lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)

        if (loc != null) {
            ultima = loc
            ultimaAlle = System.currentTimeMillis()
        }
        return loc ?: ultima
    }
}
