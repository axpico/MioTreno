package it.picone.miotreno

import it.picone.miotreno.data.AndamentoTrenoDto
import it.picone.miotreno.data.PartenzaArrivoDto
import it.picone.miotreno.data.StazioneDto
import it.picone.miotreno.data.ViaggiaTrenoApi
import it.picone.miotreno.data.nomeStazione
import it.picone.miotreno.data.parseScioperiRilevanti
import it.picone.miotreno.data.rilevanteOggiODomani
import it.picone.miotreno.data.statoDa
import it.picone.miotreno.domain.RitardoRecord
import it.picone.miotreno.domain.Sciopero
import it.picone.miotreno.domain.StatoTreno
import it.picone.miotreno.domain.Stazione
import it.picone.miotreno.domain.calcolaStatistiche
import it.picone.miotreno.domain.distanzaMetri
import it.picone.miotreno.domain.stazionePiuVicina
import it.picone.miotreno.domain.stazioniVicine
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Date
import java.util.TimeZone

private val json = Json { ignoreUnknownKeys = true; isLenient = true }

/** Codice reale di Busto Arsizio FS: usato dalle fixture di dati reali, non dalla logica app. */
private const val BUSTO_ARSIZIO_FS = "S01031"

private fun fixture(nome: String): String =
    checkNotNull(object {}.javaClass.classLoader!!.getResourceAsStream(nome)) {
        "fixture mancante: $nome"
    }.bufferedReader().readText()

class ParsingTest {
    @Test
    fun `partenze reali si deserializzano nonostante i campi null`() {
        val list = json.decodeFromString<List<PartenzaArrivoDto>>(
            fixture("partenze_milano_centrale.json"),
        )
        assertTrue(list.isNotEmpty())
        assertTrue(list.all { it.numeroTreno != null })
        // l'API restituisce davvero campi null qui: il parsing non deve rompersi
        assertTrue(list.any { it.origine == null || it.binarioProgrammatoPartenzaDescrizione == null })
    }

    @Test
    fun `arrivi a Busto espongono origine e orario di arrivo`() {
        val list = json.decodeFromString<List<PartenzaArrivoDto>>(fixture("arrivi_busto.json"))
        assertTrue(list.isNotEmpty())
        assertTrue(list.all { it.codOrigine != null && it.orarioArrivo != null })
    }

    @Test
    fun `andamentoTreno elenca le fermate e include Busto Arsizio FS`() {
        val dto = json.decodeFromString<AndamentoTrenoDto>(fixture("andamento_25586.json"))
        assertTrue(dto.fermate.size > 3)
        assertTrue(dto.fermate.any { it.id == BUSTO_ARSIZIO_FS })
        // il treno passa sia da Busto Nord che da Busto FS: sono stazioni diverse
        assertTrue(dto.fermate.any { it.id == "S01137" })
    }

    @Test
    fun `il formato data di partenze e arrivi non e' epoch`() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Rome"))
        val s = ViaggiaTrenoApi.encodeQuando(Date(1_788_286_581_000L))
        // niente spazi grezzi e niente epoch: l'API risponde "Error" a quello
        assertTrue(s, "%20" in s)
        assertTrue(s, s.startsWith("Tue%20Sep"))
        assertTrue(s, "GMT%2B" in s)
    }

    @Test
    fun `i nomi maiuscoli diventano leggibili tenendo le sigle`() {
        assertEquals("Busto Arsizio FS", "BUSTO ARSIZIO FS".nomeStazione())
        assertEquals("Milano Porta Garibaldi", "MILANO PORTA GARIBALDI".nomeStazione())
    }
}

class IntersezioneTest {
    /** Il cuore dell'app: partenze ∩ arrivi = treni diretti, senza whitelist di direttrici. */
    private fun diretti(
        partenze: List<PartenzaArrivoDto>,
        arrivi: List<PartenzaArrivoDto>,
    ): List<PartenzaArrivoDto> {
        val perNumero = arrivi.filter { it.numeroTreno != null }.associateBy { it.numeroTreno!! }
        return partenze.filter { p ->
            val a = perNumero[p.numeroTreno] ?: return@filter false
            val dep = p.orarioPartenza ?: return@filter false
            val arr = a.orarioArrivo ?: return@filter false
            dep < arr
        }
    }

    @Test
    fun `l'intersezione trova i diretti reali e scarta il resto`() {
        val partenze = json.decodeFromString<List<PartenzaArrivoDto>>(
            fixture("partenze_milano_centrale.json"),
        )
        val arrivi = json.decodeFromString<List<PartenzaArrivoDto>>(fixture("arrivi_busto.json"))
        val res = diretti(partenze, arrivi)

        assertTrue("almeno un diretto atteso nelle fixture", res.isNotEmpty())
        assertTrue(res.size < partenze.size)
        val numeriArrivo = arrivi.mapNotNull { it.numeroTreno }.toSet()
        assertTrue(res.all { it.numeroTreno in numeriArrivo })
    }

    @Test
    fun `la direzione sbagliata viene scartata`() {
        val p = PartenzaArrivoDto(numeroTreno = 1, orarioPartenza = 2_000)
        val a = PartenzaArrivoDto(numeroTreno = 1, orarioArrivo = 1_000)
        assertTrue(diretti(listOf(p), listOf(a)).isEmpty())
    }
}

class StatoTrenoTest {
    @Test
    fun `treno regolare`() {
        val s = statoDa(PartenzaArrivoDto(provvedimento = 0, circolante = true, subTitle = null))
        assertEquals(StatoTreno.Regolare, s)
    }

    @Test
    fun `provvedimento 1 e' cancellazione`() {
        val s = statoDa(PartenzaArrivoDto(provvedimento = 1, subTitle = "Treno cancellato"))
        assertTrue(s is StatoTreno.Cancellato)
    }

    /**
     * Regressione: nelle risposte reali `circolante` è false per i treni non ancora partiti.
     * Se lo si usasse come segnale di cancellazione, quasi tutta la lista risulterebbe cancellata.
     */
    @Test
    fun `circolante false non significa cancellato`() {
        val s = statoDa(PartenzaArrivoDto(provvedimento = 0, circolante = false, subTitle = null))
        assertEquals(StatoTreno.Regolare, s)
    }

    @Test
    fun `provvedimento 3 e' deviazione`() {
        val s = statoDa(PartenzaArrivoDto(provvedimento = 3, circolante = true, subTitle = "via Rho"))
        assertEquals(StatoTreno.Deviato("via Rho"), s)
    }

    @Test
    fun `tipoTreno PP e' soppressione parziale`() {
        val s = statoDa(
            PartenzaArrivoDto(
                tipoTreno = "PP", circolante = true, provvedimento = 0,
                subTitle = "Treno cancellato da NOVI LIGURE a ALESSANDRIA",
            ),
        )
        assertTrue(s is StatoTreno.ParzialmenteSoppresso)
    }

    @Test
    fun `le partenze reali non producono stati assurdi`() {
        val list = json.decodeFromString<List<PartenzaArrivoDto>>(
            fixture("partenze_milano_centrale.json"),
        )
        // la stragrande maggioranza dei treni in un orario qualsiasi è regolare
        val regolari = list.count { statoDa(it) == StatoTreno.Regolare }
        assertTrue("regolari=$regolari su ${list.size}", regolari > list.size / 2)
    }
}

class StazioniTest {
    private fun stazioniLombardia(): List<Stazione> =
        json.decodeFromString<List<StazioneDto>>(fixture("elenco_stazioni_lombardia.json"))
            .mapNotNull { d ->
                val cod = d.codiceStazione ?: return@mapNotNull null
                val nome = d.localita?.nomeLungo?.trim().orEmpty()
                val lat = d.lat ?: return@mapNotNull null
                val lon = d.lon ?: return@mapNotNull null
                if (nome.isEmpty() || (lat == 0.0 && lon == 0.0)) return@mapNotNull null
                Stazione(cod, nome, lat, lon)
            }

    @Test
    fun `haversine su una distanza nota`() {
        // Milano Centrale → Busto Arsizio, circa 30 km in linea d'aria
        val d = distanzaMetri(45.4863, 9.2049, 45.6109, 8.8494)
        assertTrue("d=$d", d in 28_000.0..34_000.0)
    }

    @Test
    fun `dalle coordinate di Busto Arsizio FS la stazione piu' vicina e' S01031`() {
        val vicina = stazionePiuVicina(stazioniLombardia(), 45.616164, 8.865031)
        assertNotNull(vicina)
        assertEquals(BUSTO_ARSIZIO_FS, vicina!!.stazione.codice)
        assertTrue("distanza=${vicina.distanzaMetri}", vicina.distanzaMetri < 300)
    }

    /** Busto Arsizio Nord è un'altra stazione, a ~1,3 km da Busto Arsizio FS. */
    @Test
    fun `Busto Arsizio Nord e' una stazione distinta`() {
        val vicina = stazionePiuVicina(stazioniLombardia(), 45.606048, 8.851449)
        assertEquals("S01137", vicina!!.stazione.codice)
    }

    @Test
    fun `dalle coordinate di Milano Centrale la stazione piu' vicina e' S01700`() {
        val vicina = stazionePiuVicina(stazioniLombardia(), 45.4863, 9.2049)
        assertEquals("S01700", vicina!!.stazione.codice)
    }

    @Test
    fun `lista vuota non esplode`() {
        assertNull(stazionePiuVicina(emptyList(), 45.0, 9.0))
        assertTrue(stazioniVicine(emptyList(), 45.0, 9.0).isEmpty())
    }

    /**
     * Il caso che rende necessario il cluster: a Porta Garibaldi ci sono due codici a 193 m,
     * e le linee S passano solo dalla sotterranea. Cercarne uno solo perderebbe metà dei treni.
     */
    @Test
    fun `a Porta Garibaldi il cluster contiene superficie e sotterranea`() {
        val cluster = stazioniVicine(stazioniLombardia(), 45.4847, 9.1875)
        val codici = cluster.map { it.stazione.codice }
        assertTrue(codici.toString(), "S01645" in codici)
        assertTrue(codici.toString(), "S01647" in codici)
    }

    /** In aperta campagna il cluster non deve rastrellare mezza regione. */
    @Test
    fun `a Busto Arsizio FS il cluster non include Busto Nord`() {
        val cluster = stazioniVicine(stazioniLombardia(), 45.616164, 8.865031)
        assertEquals(BUSTO_ARSIZIO_FS, cluster.first().stazione.codice)
        assertTrue(cluster.map { it.stazione.codice }.toString(), "S01137" !in cluster.map { it.stazione.codice })
    }

    @Test
    fun `il cluster e' ordinato per distanza`() {
        val cluster = stazioniVicine(stazioniLombardia(), 45.4847, 9.1875)
        assertEquals(cluster.map { it.distanzaMetri }.sorted(), cluster.map { it.distanzaMetri })
    }
}

class ScioperiTest {
    private val scioperi by lazy { parseScioperiRilevanti(fixture("scioperi_mit.xml"), "Lombardia") }

    @Test
    fun `dal feed reale restano solo scioperi ferroviari lombardi o nazionali`() {
        assertTrue("il feed di test deve contenere almeno una voce utile", scioperi.isNotEmpty())
        assertTrue(
            scioperi.map { it.regione.lowercase() }.toString(),
            scioperi.all { it.regione.lowercase() in setOf("lombardia", "italia") },
        )
        assertTrue(
            scioperi.all {
                it.settore.contains("errovi", true) ||
                    it.settore.contains("plurisettoriale", true)
            },
        )
    }

    @Test
    fun `le date sono normalizzate in ISO e coerenti`() {
        scioperi.forEach {
            LocalDate.parse(it.dataInizio)
            LocalDate.parse(it.dataFine)
            assertTrue(it.dataFine >= it.dataInizio)
        }
    }

    @Test
    fun `la regione nazionale arriva con lo spazio iniziale e va trimmata`() {
        assertTrue(scioperi.none { it.regione != it.regione.trim() })
    }

    @Test
    fun `uno sciopero di domani viene segnalato, uno della settimana scorsa no`() {
        val oggi = LocalDate.of(2026, 9, 1)
        val domani = Sciopero("2026-09-02", "2026-09-02", "Ferroviario", "Nazionale", "Italia", "")
        val passato = Sciopero("2026-08-20", "2026-08-21", "Ferroviario", "Nazionale", "Italia", "")
        assertNotNull(listOf(passato, domani).rilevanteOggiODomani(oggi))
        assertNull(listOf(passato).rilevanteOggiODomani(oggi))
    }
}

class StatisticheTest {
    private fun record(numero: Int, giorno: String, ritardo: Int, oreDelGiorno: Int) =
        RitardoRecord(
            numeroTreno = numero,
            categoria = "S5",
            stazionePartenzaCodice = "S01645",
            dataRiferimento = giorno,
            orarioProgrammato = LocalDate.parse(giorno)
                .atTime(oreDelGiorno, 0)
                .atZone(java.time.ZoneId.systemDefault())
                .toInstant().toEpochMilli(),
            ritardoMinuti = ritardo,
            binarioProgrammato = "3",
            binarioEffettivo = null,
            rilevatoAlle = 0,
        )

    /** Regressione: le fasce coprono le 24 ore, un treno delle 22 non deve sparire. */
    @Test
    fun `nessun record cade fuori dalle fasce`() {
        val rs = listOf(record(1, "2026-09-01", 4, 22), record(2, "2026-09-01", 2, 3))
        val s = calcolaStatistiche(rs)
        assertEquals(3.0, s.perFascia.last().valore, 0.001)
        assertEquals(2, s.rilevazioni)
    }

    @Test
    fun `storico vuoto non divide per zero`() {
        val s = calcolaStatistiche(emptyList())
        assertEquals(0, s.rilevazioni)
        assertEquals(7, s.perGiorno.size)
        assertEquals(4, s.perFascia.size)
        assertTrue(s.perTreno.isEmpty())
    }

    @Test
    fun `medie per giorno fascia e treno`() {
        // 2026-08-31 è un lunedì, 2026-09-01 un martedì
        val rs = listOf(
            record(1, "2026-08-31", 4, 7),
            record(1, "2026-09-01", 6, 7),
            record(2, "2026-09-01", 0, 19),
        )
        val s = calcolaStatistiche(rs)

        assertEquals(3, s.rilevazioni)
        assertEquals(10.0 / 3, s.ritardoMedio, 0.001)
        assertEquals(2.0 / 3, s.quotaEntro5Min, 0.001)
        assertEquals(4.0, s.perGiorno[0].valore, 0.001) // lunedì
        assertEquals(3.0, s.perGiorno[1].valore, 0.001) // martedì: (6+0)/2
        assertEquals(5.0, s.perFascia[0].valore, 0.001) // 6–9: (4+6)/2
        assertEquals(0.0, s.perFascia[3].valore, 0.001) // 18–6
        assertEquals("S5 1", s.perTreno.first().etichetta)
        assertEquals(5.0, s.perTreno.first().valore, 0.001)
    }
}
