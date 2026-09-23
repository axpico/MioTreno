package it.picone.miotreno.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

data class Statistiche(
    val rilevazioni: Int,
    val stazioniCoinvolte: Int,
    val ritardoMedio: Double,
    val quotaEntro5Min: Double,
    val perGiorno: List<VoceStat>, // Lun..Dom, sempre 7 voci
    val perFascia: List<VoceStat>, // 6-9, 9-13, 13-18, 18-21
    // top 4 per numero di rilevazioni
    val perTreno: List<VoceStat>,
    /** Ritardo medio per giorno di calendario, ultimi [GIORNI_TREND] giorni con dati, cronologico. */
    val andamento: List<Double>,
)

enum class PeriodoFiltro(val etichetta: String, val giorni: Int?) {
    Sempre("Sempre", null),
    UltimoMese("Ultimo mese", 30),
    UltimiTreMesi("Ultimi 3 mesi", 90),
    AnnoCorrente("Anno corrente", 365),
}

fun filtraPerPeriodo(records: List<RitardoRecord>, periodo: PeriodoFiltro, oggi: LocalDate = LocalDate.now()): List<RitardoRecord> {
    val giorni = periodo.giorni ?: return records
    val limite = oggi.minusDays(giorni.toLong()).toString()
    return records.filter { it.dataRiferimento >= limite }
}

fun filtraPerStazione(records: List<RitardoRecord>, codice: String?): List<RitardoRecord> =
    if (codice == null) records else records.filter { it.stazionePartenzaCodice == codice }

data class VoceStat(val etichetta: String, val sottotitolo: String, val valore: Double, val rilevazioni: Int = 0)

private val GIORNI = listOf("Lun", "Mar", "Mer", "Gio", "Ven", "Sab", "Dom")
const val GIORNI_TREND = 14

/**
 * Le fasce devono coprire le 24 ore: l'ultima è "tutto il resto", altrimenti i treni serali
 * dopo le 21 e quelli notturni sparirebbero in silenzio dalla ripartizione.
 */
private val FASCE = listOf("6–9" to 6..8, "9–13" to 9..12, "13–18" to 13..17, "18–6" to 18..23)

/** Aggregazioni sullo storico locale. Niente DB: sono qualche migliaio di record al massimo. */
fun calcolaStatistiche(records: List<RitardoRecord>, zona: ZoneId = ZoneId.systemDefault()): Statistiche {
    if (records.isEmpty()) {
        return Statistiche(
            rilevazioni = 0, stazioniCoinvolte = 0, ritardoMedio = 0.0, quotaEntro5Min = 0.0,
            perGiorno = GIORNI.map { VoceStat(it, "", 0.0) },
            perFascia = FASCE.map { VoceStat(it.first, "", 0.0) },
            perTreno = emptyList(),
            andamento = emptyList(),
        )
    }

    fun oraDi(r: RitardoRecord): LocalTime =
        java.time.Instant.ofEpochMilli(r.orarioProgrammato).atZone(zona).toLocalTime()

    val perGiornoMap = records.groupBy { LocalDate.parse(it.dataRiferimento).dayOfWeek.value }
    val perFasciaMap = records.groupBy { r ->
        FASCE.indexOfFirst { oraDi(r).hour in it.second }.takeIf { it >= 0 } ?: FASCE.lastIndex
    }

    return Statistiche(
        rilevazioni = records.size,
        stazioniCoinvolte = records.map { it.stazionePartenzaCodice }.distinct().size,
        ritardoMedio = records.map { it.ritardoMinuti }.average(),
        quotaEntro5Min = records.count { it.ritardoMinuti <= 5 } / records.size.toDouble(),
        perGiorno = GIORNI.mapIndexed { i, nome ->
            VoceStat(nome, "", perGiornoMap[i + 1]?.map { it.ritardoMinuti }?.average() ?: 0.0, perGiornoMap[i + 1]?.size ?: 0)
        },
        perFascia = FASCE.mapIndexed { i, (nome, _) ->
            VoceStat(nome, "", perFasciaMap[i]?.map { it.ritardoMinuti }?.average() ?: 0.0, perFasciaMap[i]?.size ?: 0)
        },
        perTreno = records.groupBy { it.numeroTreno }
            .entries
            .sortedByDescending { it.value.size }
            .take(4)
            .map { (numero, rs) ->
                val r = rs.first()
                VoceStat(
                    etichetta = "${r.categoria} $numero",
                    sottotitolo = String.format(
                        "%tR",
                        java.util.Date(r.orarioProgrammato),
                    ),
                    valore = rs.map { it.ritardoMinuti }.average(),
                    rilevazioni = rs.size,
                )
            },
        andamento = records.groupBy { it.dataRiferimento }
            .toSortedMap()
            .values.toList()
            .takeLast(GIORNI_TREND)
            .map { rs -> rs.map { it.ritardoMinuti }.average() },
    )
}
