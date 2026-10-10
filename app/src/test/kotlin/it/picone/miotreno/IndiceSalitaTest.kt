package it.picone.miotreno

import it.picone.miotreno.domain.DettaglioTreno
import it.picone.miotreno.domain.FermataTreno
import it.picone.miotreno.domain.indiceSalita
import org.junit.Assert.assertEquals
import org.junit.Test

class IndiceSalitaTest {
    private fun fermata(codice: String) = FermataTreno(codice, codice, null, null, passata = false)

    // Treviglio → … → Garibaldi Sotterranea → … → Busto: il regionale parte molto prima di te
    private val corsa = DettaglioTreno.Ok(
        fermate = listOf("S01700", "S01701", "S01647", "S01645", "S01137").map(::fermata),
        ritardoMinuti = 4,
        indiceCorrente = 1,
        indiceDestinazione = 4,
    )

    @Test
    fun `sali alla prima fermata del tuo cluster, non al capolinea`() =
        assertEquals(2, corsa.indiceSalita(listOf("S01645", "S01647")))

    @Test
    fun `cluster non in lista vuol dire capolinea`() = assertEquals(0, corsa.indiceSalita(listOf("S99999")))

    @Test
    fun `nessun codice vuol dire capolinea`() = assertEquals(0, corsa.indiceSalita(emptyList()))
}
