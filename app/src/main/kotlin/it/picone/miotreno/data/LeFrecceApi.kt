package it.picone.miotreno.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date

/**
 * Soluzioni di viaggio con cambi, dal backend del sito LeFrecce (Trenitalia).
 *
 * ViaggiaTreno aveva un `soluzioniViaggioNew`, ma risponde 404: questo è l'unico endpoint che
 * dà viaggi completi da A a B. Come ViaggiaTreno è non ufficiale e senza SLA, quindi chi lo
 * usa deve sopravvivere al suo fallimento (vedi il ripiego in [TrenoRepository]).
 */
interface LeFrecceApi {
    suspend fun soluzioni(codPartenza: String, codArrivo: String, quando: Date): List<SoluzioneDto>
}

/**
 * Id LeFrecce di una stazione ViaggiaTreno: "83" + le cifre del codice su 7 posizioni
 * (`S01700` → `830001700`). Vale per le stazioni principali; qualcuna ha un id diverso
 * (Garibaldi Passante), e allora LeFrecce non risponde e si ripiega su ViaggiaTreno.
 */
fun idLeFrecce(codStazione: String): String = "83" + codStazione.filter { it.isDigit() }.padStart(7, '0')

private val ORA_LOCALE = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS")
private val ROMA = ZoneId.of("Europe/Rome")

/** Quante soluzioni chiedere: comprende i diretti, che poi si scartano (li dà ViaggiaTreno). */
private const val LIMITE_SOLUZIONI = 10

class LeFrecceApiHttp(
    private val client: OkHttpClient,
    private val base: String = "https://www.lefrecce.it/Channels.Website.BFF.WEB/website",
) : LeFrecceApi {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override suspend fun soluzioni(codPartenza: String, codArrivo: String, quando: Date): List<SoluzioneDto> {
        val corpo = buildJsonObject {
            put("departureLocationId", idLeFrecce(codPartenza).toLong())
            put("arrivalLocationId", idLeFrecce(codArrivo).toLong())
            put("departureTime", ORA_LOCALE.format(Instant.ofEpochMilli(quando.time).atZone(ROMA)))
            put("adults", 1)
            put("children", 0)
            putJsonObject("criteria") {
                put("frecceOnly", false)
                put("regionalOnly", false)
                put("noChanges", false)
                put("order", "DEPARTURE_DATE")
                put("limit", LIMITE_SOLUZIONI)
                put("offset", 0)
            }
            putJsonObject("advancedSearchRequest") { put("bestFare", false) }
        }.toString()
        val testo = withContext(Dispatchers.IO) {
            val req = Request.Builder()
                .url("$base/ticket/solutions")
                .header("User-Agent", USER_AGENT)
                .post(corpo.toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) throw ApiException("HTTP ${res.code} su LeFrecce")
                res.body?.string().orEmpty()
            }
        }
        return json.decodeFromString<RispostaSoluzioniDto>(testo).solutions.mapNotNull { it.solution }
    }
}

@Serializable
data class RispostaSoluzioniDto(val solutions: List<VoceSoluzioneDto> = emptyList())

@Serializable
data class VoceSoluzioneDto(val solution: SoluzioneDto? = null)

@Serializable
data class SoluzioneDto(
    val departureTime: String? = null,
    val arrivalTime: String? = null,
    val nodes: List<NodoDto> = emptyList(),
)

/** Una tratta. `bdoOrigin` è il codice ViaggiaTreno dell'origine del *treno*, non di dove si sale. */
@Serializable
data class NodoDto(
    val origin: String? = null,
    val bdoOrigin: String? = null,
    val destination: String? = null,
    val departureTime: String? = null,
    val arrivalTime: String? = null,
    val train: TrenoLfDto? = null,
)

@Serializable
data class TrenoLfDto(
    /** "UB" = a piedi o mezzo urbano, senza numero. */
    val acronym: String? = null,
    val name: String? = null,
    val trainCategory: String? = null,
)
