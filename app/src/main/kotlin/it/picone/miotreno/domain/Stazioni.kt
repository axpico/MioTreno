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

/**
 * Etichette che distinguono fra loro le stazioni di uno stesso cluster.
 *
 * Nel cluster i treni arrivano da codici diversi dello stesso scalo e la lista li mescola:
 * a Milano Porta Garibaldi le linee S passano dal Passante (sotterranea) e le altre dalla
 * superficie, ma in elenco sembrano la stessa cosa. Qui si ricava la parte di nome che
 * davvero cambia — "Sotterranea" — togliendo il prefisso comune a tutto il cluster.
 *
 * Chi non ha resto non riceve etichetta: a Garibaldi la superficie si chiama esattamente
 * come lo scalo, e marcarla "Superficie" sarebbe una parola inventata da noi, non un dato.
 * L'assenza di chip è già il segnale: chip = sotterranea, niente chip = superficie.
 */
fun etichetteCluster(nomiPerCodice: Map<String, String>): Map<String, String> {
    if (nomiPerCodice.size < 2) return emptyMap()
    val parole = nomiPerCodice.values.map { it.trim().split(" ").filter(String::isNotEmpty) }
    val comuni = parole.minOf { it.size }.let { max ->
        (0 until max).takeWhile { i -> parole.all { it[i].equals(parole[0][i], ignoreCase = true) } }.count()
    }
    return nomiPerCodice.mapNotNull { (codice, nome) ->
        val resto = nome.trim().split(" ").filter(String::isNotEmpty).drop(comuni).joinToString(" ")
        if (resto.isEmpty()) null else codice to resto
    }.toMap()
}

/**
 * Etichetta di [etichetteCluster] ridotta a misura di badge.
 *
 * "Sotterranea" per esteso non sta accanto al numero di binario senza mandare la riga a capo,
 * e una riga che va a capo in mezzo a una lista si legge come un errore di layout. Taglio
 * secco a quattro lettere: "SOTT" resta leggibile e non inventa un'abbreviazione diversa
 * per ogni stazione.
 */
fun etichettaBreve(etichetta: String, max: Int = 5): String =
    etichetta.trim().uppercase().let { if (it.length <= max) it else it.take(4) }
