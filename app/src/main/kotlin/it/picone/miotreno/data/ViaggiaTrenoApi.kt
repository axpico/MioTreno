package it.picone.miotreno.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Lombardia. Serve per elencoStazioni. */
const val REGIONE_LOMBARDIA = 1

/** User-Agent condiviso da tutte le chiamate HTTP dell'app (ViaggiaTreno e feed scioperi). */
const val USER_AGENT = "MioTreno/1.0"

/** Risultato di andamentoTreno: il 204 dell'API è uno stato normale, non un errore. */
sealed interface Andamento {
    data class Ok(val treno: AndamentoTrenoDto) : Andamento
    data object DatiNonDisponibili : Andamento
}

/**
 * I quattro endpoint ViaggiaTreno che servono all'app. Interfaccia separata dall'unico
 * adapter reale (OkHttp, [ViaggiaTrenoApiHttp]) così i test possono sostituirla con un
 * fake in memoria invece di rifare via HTTP la logica che [TrenoRepository] orchestra.
 */
interface ViaggiaTrenoApi {
    suspend fun partenze(codStazione: String, quando: Date = Date()): List<PartenzaArrivoDto>
    suspend fun arrivi(codStazione: String, quando: Date = Date()): List<PartenzaArrivoDto>
    suspend fun andamentoTreno(codOrigine: String, numeroTreno: Int, partenzaMs: Long): Andamento
    suspend fun elencoStazioni(idRegione: Int = REGIONE_LOMBARDIA): List<StazioneDto>
    /** Ricerca stazioni per prefisso nome, su tutto il territorio nazionale (niente coordinate). */
    suspend fun cercaStazione(prefisso: String): List<RicercaStazioneDto>
    /** Id regione ViaggiaTreno a cui appartiene la stazione. */
    suspend fun regione(codStazione: String): Int?
    /** Dati completi (incluse coordinate) di una singola stazione, nota la sua regione. */
    suspend fun dettaglioStazione(codStazione: String, idRegione: Int): StazioneDto?

    companion object {
        /**
         * partenze/arrivi rifiutano l'epoch in ms (rispondono "Error"): vogliono la stringa
         * prodotta da Date.toString() in JS, es. "Tue Sep 01 2026 20:46:53 GMT+0200".
         */
        fun encodeQuando(quando: Date): String {
            val fmt = SimpleDateFormat("EEE MMM dd yyyy HH:mm:ss 'GMT'Z", Locale.US)
            return URLEncoder.encode(fmt.format(quando), "UTF-8").replace("+", "%20")
        }
    }
}

class ViaggiaTrenoApiHttp(
    private val client: OkHttpClient,
    private val base: String = "http://www.viaggiatreno.it/infomobilita/resteasy/viaggiatreno",
) : ViaggiaTrenoApi {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override suspend fun partenze(codStazione: String, quando: Date): List<PartenzaArrivoDto> =
        getList("$base/partenze/$codStazione/${ViaggiaTrenoApi.encodeQuando(quando)}")

    override suspend fun arrivi(codStazione: String, quando: Date): List<PartenzaArrivoDto> =
        getList("$base/arrivi/$codStazione/${ViaggiaTrenoApi.encodeQuando(quando)}")

    override suspend fun andamentoTreno(codOrigine: String, numeroTreno: Int, partenzaMs: Long): Andamento {
        val body = get("$base/andamentoTreno/$codOrigine/$numeroTreno/$partenzaMs")
        if (body.isNullOrBlank()) return Andamento.DatiNonDisponibili
        return runCatching { Andamento.Ok(json.decodeFromString<AndamentoTrenoDto>(body)) }
            .getOrElse { Andamento.DatiNonDisponibili }
    }

    override suspend fun elencoStazioni(idRegione: Int): List<StazioneDto> =
        getList("$base/elencoStazioni/$idRegione")

    override suspend fun cercaStazione(prefisso: String): List<RicercaStazioneDto> =
        getList("$base/cercaStazione/${URLEncoder.encode(prefisso, "UTF-8")}")

    /** Risposta testo semplice: un intero nudo (id regione), non JSON. */
    override suspend fun regione(codStazione: String): Int? =
        get("$base/regione/$codStazione")?.trim()?.toIntOrNull()

    override suspend fun dettaglioStazione(codStazione: String, idRegione: Int): StazioneDto? {
        val body = get("$base/dettaglioStazione/$codStazione/$idRegione") ?: return null
        return runCatching { json.decodeFromString<StazioneDto>(body) }.getOrNull()
    }

    private suspend inline fun <reified T> getList(url: String): List<T> {
        val body = get(url) ?: return emptyList()
        if (body.isBlank() || !body.trimStart().startsWith("[")) return emptyList()
        return runCatching { json.decodeFromString<List<T>>(body) }.getOrDefault(emptyList())
    }

    /** null = 204/corpo vuoto. Le altre risposte non-2xx alzano [ApiException]. */
    private suspend fun get(url: String): String? = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) throw ApiException("HTTP ${res.code} su $url")
            res.body?.string()?.takeIf { it.isNotBlank() }
        }
    }
}

class ApiException(message: String) : RuntimeException(message)
