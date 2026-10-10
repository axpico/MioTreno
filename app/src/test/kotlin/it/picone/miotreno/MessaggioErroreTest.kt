package it.picone.miotreno

import it.picone.miotreno.data.ApiException
import it.picone.miotreno.ui.messaggioErrore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.net.UnknownHostException

class MessaggioErroreTest {
    @Test
    fun `rete assente diventa un messaggio sulla connessione`() {
        val m = messaggioErrore(UnknownHostException("Unable to resolve host www.viaggiatreno.it"))
        assertEquals("Nessuna connessione. Controlla la rete e riprova.", m)
    }

    @Test
    fun `errore HTTP non mostra url ne codice`() {
        val m = messaggioErrore(ApiException("HTTP 503 su http://www.viaggiatreno.it/x"))
        assertFalse("HTTP" in m || "http" in m)
    }

    @Test
    fun `i nostri error() restano leggibili cosi come sono`() {
        val testo = "Posizione non disponibile: attiva il GPS o scegli la stazione a mano."
        assertEquals(testo, messaggioErrore(IllegalStateException(testo)))
    }

    @Test
    fun `la cancellazione non mostra il suo messaggio tecnico`() {
        val m = messaggioErrore(kotlinx.coroutines.CancellationException("Job was cancelled"))
        assertFalse("Job" in m)
    }
}
