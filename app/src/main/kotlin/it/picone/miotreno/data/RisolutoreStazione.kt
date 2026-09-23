package it.picone.miotreno.data

import it.picone.miotreno.domain.StazioneCorrente
import it.picone.miotreno.domain.scegliStazione
import it.picone.miotreno.domain.stazioniVicine
import it.picone.miotreno.location.LocationProvider
import kotlinx.coroutines.flow.first

/**
 * Mette insieme impostazioni, GPS e cache per dire da quale stazione cercare i treni.
 * Usato da ViewModel, widget e worker: la logica sta qui una volta sola.
 */
class RisolutoreStazione(
    private val repo: TrenoRepository,
    private val location: LocationProvider,
    private val impostazioni: ImpostazioniStore,
) {
    /**
     * [usaUltimaNota] = true in background (widget, worker), dove un fix GPS spesso non arriva
     * e l'ultima stazione vera è molto più utile di un errore. In primo piano resta false:
     * l'utente vede "posizione non disponibile" e può risolvere.
     */
    suspend fun risolvi(usaUltimaNota: Boolean): StazioneCorrente? {
        val stazioni = repo.stazioni()
        val manuale = impostazioni.flow.first().stazioneManuale
            ?.let { cod -> stazioni.firstOrNull { it.codice == cod } }
        if (manuale != null) {
            val clusterManuale = stazioniVicine(stazioni, manuale.lat, manuale.lon)
            return scegliStazione(clusterManuale, emptyList(), null)
        }

        val pos = if (location.hasPermission()) location.posizione() else null
        val cluster = pos?.let { stazioniVicine(stazioni, it.latitude, it.longitude) }.orEmpty()
        if (cluster.isNotEmpty()) repo.salvaUltimoCluster(cluster.first().stazione.nome, cluster.map { it.stazione.codice })

        return scegliStazione(emptyList(), cluster, if (usaUltimaNota) repo.ultimoCluster() else null)
    }
}
