package it.picone.miotreno

import android.content.Context
import it.picone.miotreno.data.ImpostazioniStore
import it.picone.miotreno.data.JsonStore
import it.picone.miotreno.data.RisolutoreStazione
import it.picone.miotreno.data.ScioperiApi
import it.picone.miotreno.data.TrenoRepository
import it.picone.miotreno.data.ViaggiaTrenoApiHttp
import it.picone.miotreno.location.LocationProvider
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Wiring manuale. ponytail: un grafo con sei nodi non giustifica Hilt.
 */
object Deps {
    lateinit var app: Context
        private set

    fun init(context: Context) {
        if (!::app.isInitialized) app = context.applicationContext
    }

    val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    val repository: TrenoRepository by lazy {
        TrenoRepository(
            api = ViaggiaTrenoApiHttp(http),
            scioperiApi = ScioperiApi(http),
            store = JsonStore(app.filesDir),
        )
    }

    val impostazioni: ImpostazioniStore by lazy { ImpostazioniStore(app) }
    val location: LocationProvider by lazy { LocationProvider(app) }
    val stazione: RisolutoreStazione by lazy { RisolutoreStazione(repository, location, impostazioni) }
}
