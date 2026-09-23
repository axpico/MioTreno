package it.picone.miotreno.data

import kotlinx.serialization.Serializable

/**
 * DTO grezzi di ViaggiaTreno. Ogni campo è nullable per scelta: l'API non è documentata,
 * cambia forma tra endpoint e restituisce null in posti non ovvi.
 */
@Serializable
data class PartenzaArrivoDto(
    val numeroTreno: Int? = null,
    val categoria: String? = null,
    val categoriaDescrizione: String? = null,
    val compNumeroTreno: String? = null,
    val origine: String? = null,
    val codOrigine: String? = null,
    val destinazione: String? = null,
    val codDestinazione: String? = null,
    val orarioPartenza: Long? = null,
    val orarioArrivo: Long? = null,
    val dataPartenzaTreno: Long? = null,
    val ritardo: Int? = null,
    val circolante: Boolean? = null,
    val nonPartito: Boolean? = null,
    val inStazione: Boolean? = null,
    val provvedimento: Int? = null,
    val subTitle: String? = null,
    val tipoTreno: String? = null,
    val binarioProgrammatoPartenzaDescrizione: String? = null,
    val binarioEffettivoPartenzaDescrizione: String? = null,
    val binarioProgrammatoArrivoDescrizione: String? = null,
    val binarioEffettivoArrivoDescrizione: String? = null,
)

@Serializable
data class AndamentoTrenoDto(
    val numeroTreno: Int? = null,
    val compNumeroTreno: String? = null,
    val categoria: String? = null,
    val origine: String? = null,
    val destinazione: String? = null,
    val idOrigine: String? = null,
    val ritardo: Int? = null,
    val tipoTreno: String? = null,
    val provvedimento: Int? = null,
    val subTitle: String? = null,
    val fermate: List<FermataDto> = emptyList(),
)

@Serializable
data class FermataDto(
    val id: String? = null,
    val stazione: String? = null,
    val progressivo: Int? = null,
    val programmata: Long? = null,
    val effettiva: Long? = null,
    val partenza_teorica: Long? = null,
    val arrivo_teorico: Long? = null,
    val partenzaReale: Long? = null,
    val arrivoReale: Long? = null,
    val ritardo: Int? = null,
    val ritardoPartenza: Int? = null,
    val ritardoArrivo: Int? = null,
    val binarioProgrammatoPartenzaDescrizione: String? = null,
    val binarioEffettivoPartenzaDescrizione: String? = null,
)

@Serializable
data class StazioneDto(
    val codiceStazione: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val localita: LocalitaDto? = null,
)

@Serializable
data class LocalitaDto(
    val nomeLungo: String? = null,
    val nomeBreve: String? = null,
)

/** Riga di risultato di cercaStazione: ricerca nazionale, senza coordinate. */
@Serializable
data class RicercaStazioneDto(
    val nomeLungo: String? = null,
    val id: String? = null,
)
