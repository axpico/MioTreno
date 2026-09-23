package it.picone.miotreno.domain

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private const val RAGGIO_TERRA_M = 6_371_000.0

/** Distanza haversine in metri fra due coordinate. */
fun distanzaMetri(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
    return 2 * RAGGIO_TERRA_M * asin(min(1.0, sqrt(a)))
}

/** Stazione più vicina alle coordinate date, o null se la lista è vuota. */
fun stazionePiuVicina(stazioni: List<Stazione>, lat: Double, lon: Double): StazioneVicina? =
    stazioni
        .map { it to distanzaMetri(lat, lon, it.lat, it.lon) }
        .minByOrNull { it.second }
        ?.let { (s, d) -> StazioneVicina(s, d.toInt()) }

/** Raggio entro cui due codici stazione sono di fatto lo stesso posto. */
const val RAGGIO_CLUSTER_M = 400.0

/**
 * Tutte le stazioni a portata di mano, non solo la più vicina.
 *
 * Serve perché lo stesso scalo può avere più codici ViaggiaTreno a poche centinaia di metri
 * l'uno dall'altro — Milano Porta Garibaldi (S01645) e Porta Garibaldi Sotterranea (S01647)
 * distano 193 m ma servono treni diversi: le linee S passano dal Passante, cioè dalla
 * sotterranea. Prendere solo la più vicina significherebbe, a seconda di dove ti trovi sul
 * piazzale, perdere in silenzio metà dei treni utili.
 *
 * Ordinate per distanza: la prima è quella da mostrare come "stazione più vicina".
 */
fun stazioniVicine(
    stazioni: List<Stazione>,
    lat: Double,
    lon: Double,
    raggioMetri: Double = RAGGIO_CLUSTER_M,
): List<StazioneVicina> {
    val perDistanza = stazioni
        .map { StazioneVicina(it, distanzaMetri(lat, lon, it.lat, it.lon).toInt()) }
        .sortedBy { it.distanzaMetri }
    val prima = perDistanza.firstOrNull() ?: return emptyList()
    return perDistanza.takeWhile { it.distanzaMetri - prima.distanzaMetri <= raggioMetri }
}
