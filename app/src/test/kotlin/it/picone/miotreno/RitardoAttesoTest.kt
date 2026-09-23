package it.picone.miotreno

import it.picone.miotreno.domain.RitardoRecord
import it.picone.miotreno.domain.mediana
import it.picone.miotreno.domain.ritardoAtteso
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class RitardoAttesoTest {
    // 2026-09-07 è lunedì
    private val oggi = LocalDate.of(2026, 9, 7)

    private fun rec(numero: Int, giorno: LocalDate, ritardo: Int) = RitardoRecord(
        numeroTreno = numero, categoria = "REG", stazionePartenzaCodice = "S01700",
        dataRiferimento = giorno.toString(), orarioProgrammato = 0L, ritardoMinuti = ritardo,
        binarioProgrammato = null, binarioEffettivo = null, rilevatoAlle = 0L,
    )

    /** Un record per ogni giorno feriale delle ultime [settimane] settimane, escluso oggi. */
    private fun feriali(numero: Int, settimane: Int, ritardo: (Int) -> Int) =
        (1..settimane * 7).map { oggi.minusDays(it.toLong()) }
            .filter { it.dayOfWeek.value <= 5 }
            .mapIndexed { i, d -> rec(numero, d, ritardo(i)) }

    @Test
    fun `mediana pari e dispari`() {
        assertEquals(3, mediana(listOf(1, 3, 40)))
        assertEquals(2, mediana(listOf(1, 3)))
    }

    @Test
    fun `sotto cinque osservazioni non dice niente`() {
        val storico = feriali(2600, 1) { 8 }.take(4)
        assertNull(ritardoAtteso(storico, 2600, oggi))
    }

    @Test
    fun `mediana robusta a un outlier e testo con settimane`() {
        val storico = feriali(2600, 2) { i -> if (i == 0) 45 else 7 }
        val a = ritardoAtteso(storico, 2600, oggi)!!
        assertEquals(7, a.medianaMinuti)
        assertEquals("spesso ~7 min di ritardo (ultime 4 settimane)", a.testo)
    }

    @Test
    fun `puntuale fino a due minuti`() {
        val a = ritardoAtteso(feriali(2600, 2) { 2 }, 2600, oggi)!!
        assertEquals("di solito puntuale (ultime 4 settimane)", a.testo)
    }

    @Test
    fun `ignora oggi, i weekend, gli altri treni e le date oltre quattro settimane`() {
        val storico = feriali(2600, 4) { 10 } +
            listOf(rec(2600, oggi, 0)) +
            (1..8).map { rec(2600, oggi.minusDays(it.toLong()).let { d -> if (d.dayOfWeek.value <= 5) d.minusDays(2) else d }, 0) }
                .filter { LocalDate.parse(it.dataRiferimento).dayOfWeek.value > 5 } +
            feriali(2601, 4) { 0 } +
            (1..10).map { rec(2600, oggi.minusWeeks(5).minusDays(it.toLong()), 0) }
        val a = ritardoAtteso(storico, 2600, oggi)!!
        assertEquals(10, a.medianaMinuti)
        assertEquals(20, a.osservazioni)
    }

    @Test
    fun `di sabato conta solo i sabati`() {
        val sabato = LocalDate.of(2026, 9, 12)
        val sabati = (1..4).map { rec(2600, sabato.minusWeeks(it.toLong()), 3) }
        val storico = sabati + feriali(2600, 4) { 20 }
        assertNull(ritardoAtteso(storico, 2600, sabato)) // 4 sabati < 5, i feriali non contano
        val a = ritardoAtteso(storico + rec(2600, sabato.minusWeeks(4).minusDays(1), 3), 2600, sabato)
        assertNull(a) // il quinto sarebbe un venerdì
    }
}
