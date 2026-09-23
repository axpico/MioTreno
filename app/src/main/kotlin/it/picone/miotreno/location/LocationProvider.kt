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

/** getCurrentLocation ha un timeout interno del sistema; requestSingleUpdate no. */
private const val TIMEOUT_FIX_MS = 20_000L

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
        val provider = when {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> null
        }

        val fresca = if (provider == null) {
            null
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            suspendCancellableCoroutine<Location?> { cont ->
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                val executor = Executors.newSingleThreadExecutor()
                lm.getCurrentLocation(provider, signal, executor) {
                    if (cont.isActive) cont.resume(it)
                    executor.shutdown()
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
