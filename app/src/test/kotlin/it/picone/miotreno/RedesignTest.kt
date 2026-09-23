package it.picone.miotreno

import it.picone.miotreno.domain.DettaglioTreno
import it.picone.miotreno.domain.PeriodoFiltro
import it.picone.miotreno.domain.RitardoRecord
import it.picone.miotreno.domain.Semaforo
import it.picone.miotreno.domain.StatoTreno
import it.picone.miotreno.domain.Stazione
import it.picone.miotreno.domain.StazioneCorrente
import it.picone.miotreno.domain.StazioneVicina
import it.picone.miotreno.domain.UltimoCluster
import it.picone.miotreno.domain.calcolaStatistiche
import it.picone.miotreno.domain.filtraPerPeriodo
import it.picone.miotreno.domain.filtraPerStazione
import it.picone.miotreno.domain.progressoReale
import it.picone.miotreno.domain.scegliStazione
import it.picone.miotreno.domain.semaforoDi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class SemaforoTest {
    @Test
    fun `in orario, lieve, grave seguono la soglia dei 10 minuti`() {
        assertEquals(Semaforo.InOrario, semaforoDi(StatoTreno.Regolare, 0))
        assertEquals(Semaforo.InOrario, semaforoDi(StatoTreno.Regolare, -2))
        assertEquals(Semaforo.RitardoLieve, semaforoDi(StatoTreno.Regolare, 9))
        assertEquals(Semaforo.RitardoGrave, semaforoDi(StatoTreno.Regolare, 10))
    }

    @Test
    fun `cancellato vince sul ritardo, deviato e parziale sono irregolari`() {
        assertEquals(Semaforo.Cancellato, semaforoDi(StatoTreno.Cancellato("x"), 0))
        assertEquals(Semaforo.Irregolare, semaforoDi(StatoTreno.Deviato("x"), 30))
        assertEquals(Semaforo.Irregolare, semaforoDi(StatoTreno.ParzialmenteSoppresso("x"), 0))
    }
}

class StazioneCorrenteTest {
    private val busto = Stazione("S01031", "Busto Arsizio", 45.61, 8.85)
    private val gallarate = Stazione("S01030", "Gallarate", 45.66, 8.79)
    private val gps = listOf(StazioneVicina(gallarate, 120), StazioneVicina(busto, 300))
    private val ultima = UltimoCluster("Milano Centrale", listOf("S01700"))

    private val garibaldiSuperficie = Stazione("S01645", "Milano P. Garibaldi", 45.4845, 9.1877)
    private val garibaldiSotterranea = Stazione("S01647", "Milano P. Garibaldi Sotterranea", 45.4849, 9.1867)
    private val manualeSingolo = listOf(StazioneVicina(busto, 0))
    private val manualeGaribaldi = listOf(StazioneVicina(garibaldiSuperficie, 0), StazioneVicina(garibaldiSotterranea, 193))

    @Test
    fun `la scelta manuale batte GPS e cache`() {
        val s = scegliStazione(manualeSingolo, gps, ultima)!!
        assertEquals(StazioneCorrente.Origine.Manuale, s.origine)
        assertEquals(listOf("S01031"), s.codici)
        assertNull(s.distanzaMetri)
    }

    @Test
    fun `la scelta manuale di Garibaldi copre anche la sotterranea`() {
        val s = scegliStazione(manualeGaribaldi, gps, ultima)!!
        assertEquals(StazioneCorrente.Origine.Manuale, s.origine)
        assertEquals(listOf("S01645", "S01647"), s.codici)
    }

    @Test
    fun `senza manuale si usa il cluster GPS con la distanza della prima`() {
        val s = scegliStazione(emptyList(), gps, ultima)!!
        assertEquals(StazioneCorrente.Origine.Gps, s.origine)
        assertEquals("Gallarate", s.nome)
        assertEquals(listOf("S01030", "S01031"), s.codici)
        assertEquals(120, s.distanzaMetri)
    }

    @Test
    fun `senza GPS si ripiega sull'ultima nota, senza niente e' null`() {
        assertEquals(StazioneCorrente.Origine.UltimaNota, scegliStazione(emptyList(), emptyList(), ultima)!!.origine)
        assertNull(scegliStazione(emptyList(), emptyList(), null))
    }
}

class AndamentoStatisticheTest {
    private val zona = ZoneId.of("Europe/Rome")
    private fun record(giorno: String, ritardo: Int) = RitardoRecord(
        numeroTreno = 1, categoria = "REG", stazionePartenzaCodice = "S01645", dataRiferimento = giorno,
        orarioProgrammato = LocalDateTime.parse("${giorno}T08:00").atZone(zona).toInstant().toEpochMilli(),
        ritardoMinuti = ritardo, binarioProgrammato = null, binarioEffettivo = null, rilevatoAlle = 0,
    )

    @Test
    fun `l'andamento e' la media per giorno in ordine cronologico`() {
        val stat = calcolaStatistiche(
            listOf(record("2026-09-03", 4), record("2026-09-01", 0), record("2026-09-03", 6), record("2026-09-02", 2)),
            zona,
        )
        assertEquals(listOf(0.0, 2.0, 5.0), stat.andamento)
    }

    @Test
    fun `l'andamento tiene solo gli ultimi 14 giorni con dati`() {
        val records = (0 until 20).map { record(LocalDate.of(2026, 8, 1).plusDays(it.toLong()).toString(), it) }
        val stat = calcolaStatistiche(records, zona)
        assertEquals(14, stat.andamento.size)
        assertEquals(19.0, stat.andamento.last(), 0.0)
    }

    @Test
    fun `stazioniCoinvolte conta le stazioni distinte`() {
        val altra = record("2026-09-01", 0).copy(stazionePartenzaCodice = "S01700")
        val stat = calcolaStatistiche(listOf(record("2026-09-01", 0), record("2026-09-02", 1), altra))
        assertEquals(2, stat.stazioniCoinvolte)
    }
}

class FiltriStatisticheTest {
    private fun record(giorno: String, stazione: String = "S01645") = RitardoRecord(
        numeroTreno = 1, categoria = "REG", stazionePartenzaCodice = stazione, dataRiferimento = giorno,
        orarioProgrammato = 0, ritardoMinuti = 0, binarioProgrammato = null, binarioEffettivo = null, rilevatoAlle = 0,
    )
    private val oggi = LocalDate.of(2026, 9, 12)

    @Test
    fun `Sempre non filtra nulla`() {
        val records = listOf(record("2020-01-01"), record("2026-09-12"))
        assertEquals(records, filtraPerPeriodo(records, PeriodoFiltro.Sempre, oggi))
    }

    @Test
    fun `Ultimo mese taglia a 30 giorni fa`() {
        val dentro = record("2026-08-15")
        val fuori = record("2026-07-01")
        assertEquals(listOf(dentro), filtraPerPeriodo(listOf(dentro, fuori), PeriodoFiltro.UltimoMese, oggi))
    }

    @Test
    fun `filtraPerStazione con codice null non filtra, altrimenti confronta il codice`() {
        val a = record("2026-09-01", "S01645")
        val b = record("2026-09-01", "S01700")
        assertEquals(listOf(a, b), filtraPerStazione(listOf(a, b), null))
        assertEquals(listOf(b), filtraPerStazione(listOf(a, b), "S01700"))
    }
}

class ProgressoRealeTest {
    @Test
    fun `frazione di fermate passate su fermate fino a Busto`() {
        val dettaglio = DettaglioTreno.Ok(fermate = emptyList(), ritardoMinuti = 0, indiceCorrente = 1, indiceDestinazione = 3)
        assertEquals(0.5f, dettaglio.progressoReale())
    }

    @Test
    fun `nessuna fermata passata da' progresso zero, non null`() {
        val dettaglio = DettaglioTreno.Ok(fermate = emptyList(), ritardoMinuti = 0, indiceCorrente = -1, indiceDestinazione = 3)
        assertEquals(0f, dettaglio.progressoReale())
    }

    @Test
    fun `Busto non in lista fermate da' progresso null`() {
        val dettaglio = DettaglioTreno.Ok(fermate = emptyList(), ritardoMinuti = 0, indiceCorrente = 1, indiceDestinazione = -1)
        assertNull(dettaglio.progressoReale())
    }
}
