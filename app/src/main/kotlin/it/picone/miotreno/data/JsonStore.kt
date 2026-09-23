package it.picone.miotreno.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Persistenza su file JSON in filesDir.
 * ponytail: nessun DB. Lo storico cresce di ~6 righe al giorno; passa a Room solo se
 * i record superano le decine di migliaia.
 */
class JsonStore(private val dir: File) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val lock = Mutex()

    suspend inline fun <reified T> leggi(nome: String, default: T): T =
        leggiRaw(nome)?.let { runCatching { jsonFormat.decodeFromString<T>(it) }.getOrNull() } ?: default

    suspend inline fun <reified T> scrivi(nome: String, valore: T) =
        scriviRaw(nome, jsonFormat.encodeToString(valore))

    suspend fun leggiRaw(nome: String): String? = withContext(Dispatchers.IO) {
        lock.withLock { File(dir, nome).takeIf { it.exists() }?.readText() }
    }

    suspend fun scriviRaw(nome: String, contenuto: String) = withContext(Dispatchers.IO) {
        lock.withLock {
            dir.mkdirs()
            val tmp = File(dir, "$nome.tmp")
            tmp.writeText(contenuto)
            tmp.renameTo(File(dir, nome))
            Unit
        }
    }

    @PublishedApi
    internal val jsonFormat: Json get() = json
}
