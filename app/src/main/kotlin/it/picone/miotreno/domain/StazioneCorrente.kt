package it.picone.miotreno.domain

/**
 * Il gruppo di stazioni da cui cercare i treni, con la sua origine.
 *
 * Una sola struttura per app, widget e worker: prima ognuno rifaceva GPS → cluster per conto
 * suo, e l'override manuale avrebbe dovuto essere replicato in tre posti.
 */
data class StazioneCorrente(
    val nome: String,
    val codici: List<String>,
    val origine: Origine,
    /** Distanza dalla posizione, solo quando [origine] è GPS. */
    val distanzaMetri: Int? = null,
    /**
     * Nome per ciascun codice del cluster. Serve a distinguere i treni di scali gemelli
     * (superficie/sotterranea) in elenco — vedi [etichetteCluster]. Vuoto quando il cluster
     * viene dall'ultima nota salvata, che i nomi per codice non li conserva.
     */
    val nomi: Map<String, String> = emptyMap(),
) {
    enum class Origine { Gps, Manuale, UltimaNota }

    val codice: String get() = codici.first()
}

/**
 * Regola di scelta, pura e testabile: manuale batte tutto, poi il GPS, poi l'ultima nota.
 * [manuale] è il cluster (stesso raggio di [stazioniVicine]) intorno alla stazione scelta
 * dall'utente — così anche la scelta manuale copre casi come Garibaldi superficie/sotterranea,
 * non solo il singolo codice cliccato — [gps] il cluster dalla posizione, [ultima] quanto
 * salvato l'ultima volta.
 */
fun scegliStazione(
    manuale: List<StazioneVicina>,
    gps: List<StazioneVicina>,
    ultima: UltimoCluster?,
): StazioneCorrente? = when {
    manuale.isNotEmpty() -> StazioneCorrente(
        manuale.first().stazione.nome, manuale.map { it.stazione.codice },
        StazioneCorrente.Origine.Manuale,
        nomi = manuale.associate { it.stazione.codice to it.stazione.nome },
    )
    gps.isNotEmpty() -> StazioneCorrente(
        gps.first().stazione.nome, gps.map { it.stazione.codice },
        StazioneCorrente.Origine.Gps, gps.first().distanzaMetri,
        nomi = gps.associate { it.stazione.codice to it.stazione.nome },
    )
    ultima != null -> StazioneCorrente(ultima.nome, ultima.codici, StazioneCorrente.Origine.UltimaNota)
    else -> null
}
