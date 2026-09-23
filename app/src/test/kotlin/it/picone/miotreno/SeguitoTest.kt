package it.picone.miotreno

import it.picone.miotreno.domain.ProssimoTreno
import it.picone.miotreno.domain.StatoTreno
import it.picone.miotreno.domain.TrenoSeguito
import it.picone.miotreno.domain.comeSeguito
import it.picone.miotreno.domain.seguitoAttivo
import it.picone.miotreno.domain.trenoAncoraUtile
import it.picone.miotreno.domain.trenoInEvidenza
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

private val ZONA: ZoneId = ZoneId.of("Europe/Rome")

private fun ms(giorno: String, ora: String): Long =
    LocalDateTime.parse("${giorno}T$ora").atZone(ZONA).toInstant().toEpochMilli()

private fun treno(
    partenza: Long,
    arrivo: Long?,
    ritardo: Int = 0,
    numero: Int = 24576,
    stato: StatoTreno = StatoTreno.Regolare,
) = ProssimoTreno(
    numeroTreno = numero,
    categoria = "REG",
    destinazione = "Varese",
    codOrigine = "S01645",
    dataPartenzaTrenoMs = partenza,
    orarioPartenzaMs = partenza,
    orarioArrivoBustoMs = arrivo,
    ritardoMinuti = ritardo,
    binario = "2",
    binarioConfermato = true,
    stato = stato,
)

class AvanzamentoTest {

    private val partenza = ms("2026-09-01", "22:00:00")
    private val arrivo = ms("2026-09-01", "22:40:00")

    @Test
    fun `prima della partenza la barra e' vuota`() {
        val t = treno(partenza, arrivo)
        assertEquals(0f, t.avanzamento(ms("2026-09-01", "21:30:00")), 0.001f)
    }

    @Test
    fun `a meta' viaggio la barra e' a meta'`() {
        val t = treno(partenza, arrivo)
        assertEquals(0.5f, t.avanzamento(ms("2026-09-01", "22:20:00")), 0.001f)
    }

    @Test
    fun `dopo l'arrivo la barra e' piena e non oltre`() {
        val t = treno(partenza, arrivo)
        assertEquals(1f, t.avanzamento(ms("2026-09-01", "23:30:00")), 0.001f)
    }

    /**
     * Il bug che questa funzione corregge: interpolando sugli orari programmati nudi, un treno
     * in ritardo risultava avanti quanto uno in orario. Alle 22:20 un treno con +10 non è ancora
     * partito da 10 minuti: deve stare indietro.
     */
    @Test
    fun `il ritardo sposta la barra indietro, non avanti`() {
        val ora = ms("2026-09-01", "22:20:00")
        val inOrario = treno(partenza, arrivo, ritardo = 0).avanzamento(ora)
        val inRitardo = treno(partenza, arrivo, ritardo = 10).avanzamento(ora)

        assertTrue("inOrario=$inOrario inRitardo=$inRitardo", inRitardo < inOrario)
        // +10 su un viaggio di 40 minuti: alle 22:20 ne sono passati 10 dei 40 → 25%
        assertEquals(0.25f, inRitardo, 0.001f)
    }

    @Test
    fun `senza orario di arrivo non si divide per zero`() {
        assertEquals(0f, treno(partenza, null).avanzamento(partenza + 60_000), 0.001f)
    }

    @Test
    fun `arrivo non successivo alla partenza non esplode`() {
        assertEquals(0f, treno(partenza, partenza).avanzamento(partenza + 60_000), 0.001f)
    }
}

/**
 * Il motivo per cui la barra di avanzamento non si riempiva mai: la lista scartava ogni treno
 * già partito, quindi ogni treno mostrato doveva ancora partire e l'avanzamento era 0 sempre.
 */
class TrenoAncoraUtileTest {

    private val partenza = ms("2026-09-01", "22:00:00")
    private val arrivo = ms("2026-09-01", "22:40:00")

    @Test
    fun `un treno non ancora partito resta in lista`() {
        assertTrue(trenoAncoraUtile(partenza, arrivo, ms("2026-09-01", "21:50:00"), seguito = false))
    }

    @Test
    fun `un treno gia' partito sparisce, se non lo stai seguendo`() {
        assertTrue(!trenoAncoraUtile(partenza, arrivo, ms("2026-09-01", "22:10:00"), seguito = false))
    }

    @Test
    fun `la corsa seguita resta finche' non arriva a Busto`() {
        assertTrue(trenoAncoraUtile(partenza, arrivo, ms("2026-09-01", "22:10:00"), seguito = true))
        assertTrue(trenoAncoraUtile(partenza, arrivo, ms("2026-09-01", "22:39:00"), seguito = true))
    }

    @Test
    fun `anche la corsa seguita sparisce dopo Busto`() {
        assertTrue(!trenoAncoraUtile(partenza, arrivo, ms("2026-09-01", "22:41:00"), seguito = true))
    }

    /** Con il treno tenuto in lista dopo la partenza, la barra ha finalmente valori veri. */
    @Test
    fun `un treno seguito in viaggio produce un avanzamento non nullo`() {
        val ora = ms("2026-09-01", "22:20:00")
        assertTrue(trenoAncoraUtile(partenza, arrivo, ora, seguito = true))
        assertEquals(0.5f, treno(partenza, arrivo).avanzamento(ora), 0.001f)
    }
}

class SeguitoAttivoTest {

    private val arrivo = ms("2026-09-01", "22:40:00")
    private val seguito = TrenoSeguito(24576, "2026-09-01", arrivo)

    @Test
    fun `durante il viaggio e' attivo`() {
        assertNotNull(seguitoAttivo(seguito, ms("2026-09-01", "22:20:00"), ZONA))
    }

    @Test
    fun `dopo l'orario previsto resta attivo finche il tracking non conferma Busto`() {
        assertNotNull(seguitoAttivo(seguito, ms("2026-09-01", "22:50:00"), ZONA))
    }

    @Test
    fun `subito dopo l'arrivo resta attivo in attesa della conferma`() {
        assertNotNull(seguitoAttivo(seguito, ms("2026-09-01", "22:42:00"), ZONA))
    }

    @Test
    fun `la selezione attraversa mezzanotte se la corsa e ancora in viaggio`() {
        assertNotNull(seguitoAttivo(seguito, ms("2026-09-02", "07:00:00"), ZONA))
    }

    @Test
    fun `niente selezione, niente risultato`() {
        assertNull(seguitoAttivo(null, ms("2026-09-01", "22:20:00"), ZONA))
    }

    @Test
    fun `una data illeggibile non fa esplodere il tracking`() {
        val rotto = TrenoSeguito(1, "non-una-data", arrivo)
        assertNotNull(seguitoAttivo(rotto, ms("2026-09-01", "22:20:00"), ZONA))
    }

    /**
     * Il bug che questo test previene: senza scadenza, un treno seguito ieri (o prima) e mai
     * "smesso di seguire" a mano resta marcato come corsa di oggi per sempre. Con un servizio
     * regolare (stesso numero ogni giorno) la prossima corsa che passa con quel numero viene
     * scambiata per quella seguita.
     */
    @Test
    fun `una selezione dimenticata da due o piu' giorni scade`() {
        assertNull(seguitoAttivo(seguito, ms("2026-09-03", "07:00:00"), ZONA))
        assertNull(seguitoAttivo(seguito, ms("2026-09-10", "07:00:00"), ZONA))
    }

    @Test
    fun `comeSeguito prende data e arrivo dal treno`() {
        val t = treno(ms("2026-09-01", "22:00:00"), arrivo)
        val s = t.comeSeguito(ZONA)
        assertEquals(24576, s.numeroTreno)
        assertEquals(LocalDate.of(2026, 9, 1).toString(), s.data)
        assertEquals(arrivo, s.arrivoBustoMs)
    }
}

class TrenoInEvidenzaTest {

    private val ora = ms("2026-09-01", "08:00:00")
    private fun corsa(numero: Int, cancellato: Boolean = false) = treno(
        partenza = ora,
        arrivo = ora + 30 * 60_000L,
        numero = numero,
        stato = if (cancellato) StatoTreno.Cancellato("soppresso") else StatoTreno.Regolare,
    )

    private fun seguito(numero: Int) =
        TrenoSeguito(numeroTreno = numero, data = "2026-09-01", arrivoBustoMs = ora)

    @Test
    fun `la corsa seguita vince anche se non e' la prima`() {
        val treni = listOf(corsa(1), corsa(2), corsa(3))

        val evidenza = trenoInEvidenza(treni, seguito(3))

        assertEquals(3, evidenza?.numeroTreno)
    }

    @Test
    fun `senza selezione tocca al primo`() {
        val treni = listOf(corsa(1), corsa(2))

        val evidenza = trenoInEvidenza(treni, null)

        assertEquals(1, evidenza?.numeroTreno)
    }

    @Test
    fun `un primo treno cancellato non va in evidenza`() {
        val treni = listOf(corsa(1, cancellato = true), corsa(2))

        val evidenza = trenoInEvidenza(treni, null)

        assertEquals(2, evidenza?.numeroTreno)
    }

    @Test
    fun `nemmeno se e' quello seguito`() {
        val treni = listOf(corsa(1), corsa(2, cancellato = true))

        val evidenza = trenoInEvidenza(treni, seguito(2))

        assertEquals(1, evidenza?.numeroTreno)
    }

    @Test
    fun `se sono tutti cancellati non c'e' evidenza`() {
        val treni = listOf(corsa(1, cancellato = true), corsa(2, cancellato = true))

        assertNull(trenoInEvidenza(treni, null))
    }

    @Test
    fun `lista vuota, nessuna evidenza`() {
        assertNull(trenoInEvidenza(emptyList(), seguito(1)))
    }
}
