package it.picone.miotreno.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

const val SETTIMANE_STORICO = 4L

/** Sotto questa soglia il numero non è affidabile e non si mostra nulla. */
const val MIN_OSSERVAZIONI = 5

/** Fino a qui il treno è "di solito puntuale": 1–2 minuti sono rumore di rilevazione. */
private const val SOGLIA_PUNTUALE = 2

/**
 * Ritardo "atteso" di un treno, dallo storico locale. Informazione secondaria e puramente
 * descrittiva: lo stato in tempo reale resta quello primario, e niente streak o punteggi.
 */
data class RitardoAtteso(val medianaMinuti: Int, val osservazioni: Int) {
    val testo: String
        get() = (if (medianaMinuti <= SOGLIA_PUNTUALE) "di solito puntuale" else "spesso ~$medianaMinuti min di ritardo") +
            " (ultime $SETTIMANE_STORICO settimane)"
}

/**
 * Mediana del ritardo dello stesso numero di treno, nelle ultime [SETTIMANE_STORICO] settimane,
 * sullo stesso tipo di giorno (feriale / sabato / domenica). Oggi è escluso: quel record è la
 * rilevazione in corso, già mostrata come stato realtime.
 *
 * ponytail: il numero di treno fissa già l'orario programmato, quindi "fascia oraria" e
 * "numero" coincidono; il filtro per tipo di giorno è quello che separa davvero le corse.
 * Mediana e non media: un solo +40 su venti corse non deve diventare "spesso in ritardo".
 */
fun ritardoAtteso(storico: List<RitardoRecord>, numeroTreno: Int, giorno: LocalDate): RitardoAtteso? {
    val da = giorno.minusWeeks(SETTIMANE_STORICO)
    val tipo = tipoGiorno(giorno)
    val ritardi = storico.asSequence()
        .filter { it.numeroTreno == numeroTreno }
        .mapNotNull { r -> runCatching { LocalDate.parse(r.dataRiferimento) }.getOrNull()?.let { it to r.ritardoMinuti } }
        .filter { (d, _) -> d >= da && d < giorno && tipoGiorno(d) == tipo }
        .map { it.second }
        .toList()
    if (ritardi.size < MIN_OSSERVAZIONI) return null
    return RitardoAtteso(mediana(ritardi), ritardi.size)
}

/** Per ogni treno in lista, il suo atteso (se ci sono abbastanza dati). */
fun ritardiAttesi(
    storico: List<RitardoRecord>,
    treni: List<ProssimoTreno>,
    zona: ZoneId = ZoneId.systemDefault(),
): Map<Int, RitardoAtteso> = treni.mapNotNull { t ->
    val giorno = Instant.ofEpochMilli(t.orarioPartenzaMs).atZone(zona).toLocalDate()
    ritardoAtteso(storico, t.numeroTreno, giorno)?.let { t.numeroTreno to it }
}.toMap()

private fun tipoGiorno(d: LocalDate): Int = when (d.dayOfWeek) {
    DayOfWeek.SATURDAY -> 1
    DayOfWeek.SUNDAY -> 2
    else -> 0
}

internal fun mediana(v: List<Int>): Int {
    val s = v.sorted()
    val n = s.size
    return if (n % 2 == 1) s[n / 2] else (s[n / 2 - 1] + s[n / 2]) / 2
}
