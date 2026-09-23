package it.picone.miotreno

import it.picone.miotreno.domain.DettaglioTreno
import it.picone.miotreno.domain.FermataTreno
import it.picone.miotreno.domain.passaPer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PassaggioTest {
    private fun fermata(codice: String) = FermataTreno(codice, codice, null, null, passata = false)

    @Test
    fun `vero se il codice compare tra le fermate`() {
        val dettaglio = DettaglioTreno.Ok(
            fermate = listOf(fermata("S01700"), fermata("S01040"), fermata("S01031")),
            ritardoMinuti = 0, indiceCorrente = 0, indiceBusto = 2,
        )
        assertTrue(passaPer(dettaglio, "S01040"))
    }

    @Test
    fun `falso se il codice non compare tra le fermate`() {
        val dettaglio = DettaglioTreno.Ok(
            fermate = listOf(fermata("S01700"), fermata("S01031")),
            ritardoMinuti = 0, indiceCorrente = 0, indiceBusto = 1,
        )
        assertFalse(passaPer(dettaglio, "S01040"))
    }

    @Test
    fun `falso quando i dati non sono disponibili`() {
        assertFalse(passaPer(DettaglioTreno.DatiNonDisponibili, "S01040"))
    }
}
