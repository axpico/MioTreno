package it.picone.miotreno.data

import it.picone.miotreno.domain.Sciopero
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.LocalDate
import java.time.format.DateTimeFormatter

const val URL_TRENI_GARANTITI =
    "https://www.trenitalia.com/it/informazioni/treni-garantiti-incasodisciopero.html"

const val URL_BUY_ME_A_COFFEE = "https://buymeacoffee.com/axpico"

/**
 * Feed RSS ufficiale MIT degli scioperi programmati.
 * I campi non sono strutturati: sono etichette dentro <title> e <description>.
 */
class ScioperiApi(
    private val client: OkHttpClient,
    private val url: String = "https://scioperi.mit.gov.it/mit2/public/scioperi/rss",
) {
    suspend fun scaricaRilevanti(regioneNome: String): List<Sciopero> = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        val xml = client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) throw ApiException("HTTP ${res.code} sul feed scioperi")
            res.body?.string().orEmpty()
        }
        parseScioperiRilevanti(xml, regioneNome)
    }
}

/** Nome regione ViaggiaTreno (id) → nome usato dal feed scioperi MIT. */
val NOMI_REGIONI = mapOf(
    1 to "Lombardia", 2 to "Liguria", 3 to "Piemonte", 4 to "Valle d'Aosta",
    5 to "Lazio", 6 to "Umbria", 7 to "Molise", 8 to "Emilia Romagna",
    9 to "Trentino Alto Adige", 10 to "Friuli Venezia Giulia", 11 to "Marche",
    12 to "Veneto", 13 to "Toscana", 14 to "Sicilia", 15 to "Basilicata",
    16 to "Puglia", 17 to "Calabria", 18 to "Campania", 19 to "Abruzzo", 20 to "Sardegna",
)

private val ITEM = Regex("<item>(.*?)</item>", RegexOption.DOT_MATCHES_ALL)
private val TITLE = Regex("<title>(.*?)</title>", RegexOption.DOT_MATCHES_ALL)
private val DESCR = Regex("<description>(.*?)</description>", RegexOption.DOT_MATCHES_ALL)
private val DATA_IT = DateTimeFormatter.ofPattern("dd/MM/yyyy")

/**
 * Estrae dal feed solo gli scioperi che riguardano i treni nella regione di destinazione
 * dell'utente: settore ferroviario (anche plurisettoriale che cita il ferroviario) e regione
 * [regioneNome] o nazionale. Attenzione: per gli scioperi nazionali la regione arriva
 * come " Italia", con lo spazio iniziale.
 */
fun parseScioperiRilevanti(xml: String, regioneNome: String): List<Sciopero> {
    val regioniRilevanti = setOf(regioneNome.lowercase(), "italia")
    return ITEM.findAll(xml).mapNotNull { m ->
        val item = m.groupValues[1]
        val title = TITLE.find(item)?.groupValues?.get(1)?.let(::unescape).orEmpty()
        val descr = DESCR.find(item)?.groupValues?.get(1)?.let(::unescape).orEmpty()
        val testo = "$title\n${descr.replace("<br/>", "\n").replace("<br>", "\n")}"

        val settore = campo(testo, "Settore")
        val regione = campo(testo, "Regione")
        val modalita = campo(testo, "modalità")
        val inizio = dataIso(campo(testo, "Data inizio")) ?: return@mapNotNull null
        val fine = dataIso(campo(testo, "Data fine")) ?: inizio

        val ferroviario = settore.equals("Ferroviario", true) ||
            (settore.contains("plurisettoriale", true) && testo.contains("ferroviario", true))
        if (!ferroviario) return@mapNotNull null
        if (regione.lowercase() !in regioniRilevanti) return@mapNotNull null

        Sciopero(
            dataInizio = inizio,
            dataFine = fine,
            settore = settore,
            rilevanza = campo(testo, "Rilevanza"),
            regione = regione,
            modalita = modalita,
        )
    }.distinctBy { it.dataInizio + it.dataFine + it.regione }.toList()
}

/** Legge "Etichetta: valore" fermandosi al separatore " - " o a fine riga. */
private fun campo(testo: String, etichetta: String): String {
    val i = testo.indexOf("$etichetta:", ignoreCase = true, startIndex = 0)
    if (i < 0) return ""
    val resto = testo.substring(i + etichetta.length + 1)
    val fine = listOf(resto.indexOf('\n'), resto.indexOf(" - "))
        .filter { it >= 0 }
        .minOrNull() ?: resto.length
    return resto.substring(0, fine).trim()
}

private fun dataIso(it: String): String? =
    runCatching { LocalDate.parse(it.trim(), DATA_IT).toString() }.getOrNull()

private fun unescape(s: String): String = s
    .removePrefix("<![CDATA[").removeSuffix("]]>")
    .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
    .replace("&quot;", "\"").replace("&#39;", "'")

/** Sciopero attivo oggi o domani, se ce n'è uno. */
fun Iterable<Sciopero>.rilevanteOggiODomani(oggi: LocalDate = LocalDate.now()): Sciopero? =
    firstOrNull { s ->
        val i = LocalDate.parse(s.dataInizio)
        val f = LocalDate.parse(s.dataFine)
        !oggi.isAfter(f) && !oggi.plusDays(1).isBefore(i)
    }
