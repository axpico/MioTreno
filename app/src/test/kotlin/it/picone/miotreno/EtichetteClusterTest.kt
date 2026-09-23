package it.picone.miotreno

import it.picone.miotreno.domain.etichetteCluster
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EtichetteClusterTest {
    // Il caso reale che ha originato la richiesta: cercando "Garibaldi" compaiono due
    // stazioni e la lista treni non diceva quale fosse quale.
    @Test
    fun `garibaldi distingue la sotterranea e lascia nuda la superficie`() {
        val e = etichetteCluster(
            mapOf(
                "S01645" to "Milano Porta Garibaldi",
                "S01647" to "Milano Porta Garibaldi Sotterranea",
            ),
        )

        assertEquals("Sotterranea", e["S01647"])
        assertNull("la superficie non va etichettata con parole inventate", e["S01645"])
    }

    @Test
    fun `stazione sola non produce etichette`() {
        assertTrue(etichetteCluster(mapOf("S01700" to "Milano Centrale")).isEmpty())
    }

    @Test
    fun `cluster vuoto non produce etichette`() {
        assertTrue(etichetteCluster(emptyMap()).isEmpty())
    }

    @Test
    fun `due stazioni entrambe con resto ricevono entrambe l'etichetta`() {
        val e = etichetteCluster(
            mapOf("A" to "Torino Porta Nuova", "B" to "Torino Porta Susa"),
        )

        assertEquals("Nuova", e["A"])
        assertEquals("Susa", e["B"])
    }

    @Test
    fun `nomi senza prefisso comune restano interi`() {
        val e = etichetteCluster(mapOf("A" to "Roma Termini", "B" to "Ostiense"))

        assertEquals("Roma Termini", e["A"])
        assertEquals("Ostiense", e["B"])
    }

    @Test
    fun `il confronto del prefisso ignora le maiuscole`() {
        val e = etichetteCluster(mapOf("A" to "Milano porta Garibaldi", "B" to "Milano Porta Garibaldi Sotterranea"))

        assertEquals("Sotterranea", e["B"])
    }
}
