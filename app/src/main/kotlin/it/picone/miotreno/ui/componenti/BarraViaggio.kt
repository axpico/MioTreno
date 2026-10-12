package it.picone.miotreno.ui.componenti

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import it.picone.miotreno.domain.DettaglioTreno
import it.picone.miotreno.domain.ProssimoTreno
import it.picone.miotreno.domain.indiceSalita
import it.picone.miotreno.ui.theme.Forme
import it.picone.miotreno.ui.theme.LocalTb
import it.picone.miotreno.ui.theme.Testo

/**
 * Barra del viaggio, solo sulla card in evidenza. Con [dettaglio] (fermate reali da
 * `andamentoTreno`) copre il *tuo* tratto, da dove sali ([codiciSalita]) alla destinazione:
 * finché il treno non è arrivato da te resta vuota e dice dov'è. Senza dettaglio resta la
 * stima sull'orario mentre i dati arrivano o non sono disponibili.
 */
@Composable
internal fun BarraViaggio(
    treno: ProssimoTreno,
    ora: Long,
    dettaglio: DettaglioTreno.Ok?,
    codiciSalita: List<String>,
    modifier: Modifier = Modifier,
) {
    val tb = LocalTb.current
    val salita = dettaglio?.indiceSalita(codiciSalita) ?: 0
    // La barra va da dove sali alla destinazione, non fino all'ultima fermata della corsa (che
    // può proseguire oltre): il numero di pallini deve combaciare con le fermate di *questo* tratto.
    val arrivo = dettaglio?.indiceDestinazione?.takeIf { it > salita }
    val tratte = arrivo?.let { it - salita }
    val fermateIntermedie = if (arrivo != null) (salita + 1 until arrivo) else IntRange.EMPTY

    // In viaggio fra due fermate il riempimento non si incolla al pallino precedente: si
    // interpola sull'orologio (vedi [posizioneInTratta]) e avanza con continuità.
    val avanzamento = when {
        dettaglio != null && tratte != null ->
            ((posizioneInTratta(dettaglio, ora) - salita) / tratte).coerceIn(0f, 1f)
        dettaglio != null ->
            ((dettaglio.indiceCorrente + 1).toFloat() / dettaglio.fermate.size.coerceAtLeast(1)).coerceIn(0f, 1f)
        else -> treno.avanzamento(ora)
    }

    Column(modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(6.dp)) {
            LinearProgressIndicator(
                progress = { avanzamento },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(Forme.chip),
                color = tb.accento, trackColor = tb.sf2,
                // Material3 disegna di default un "traguardo" a fine barra: qui i pallini
                // sopra già marcano le fermate, un secondo pallino fisso in fondo è ridondante.
                drawStopIndicator = {},
            )
            for (i in fermateIntermedie) {
                val frazione = (i - salita).toFloat() / tratte!!
                val passata = i <= dettaglio.indiceCorrente
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = maxWidth * frazione - 3.dp)
                        .size(6.dp)
                        .clip(CircleShape)
                        .let {
                            if (passata) {
                                it.background(tb.accento)
                            } else {
                                it.background(tb.sf).border(1.5.dp, tb.ter, CircleShape)
                            }
                        },
                )
            }
        }
        // Capolinea sotto la barra, uno per lato, con lo stesso peso: la barra resta a piena
        // larghezza invece di rimpicciolirsi quando il nome dell'origine è lungo.
        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            val corrente = dettaglio?.fermate?.getOrNull(dettaglio.indiceCorrente)
            Text(
                when {
                    dettaglio == null -> "Partenza"
                    // il treno non è ancora da te: dove si trova è l'informazione utile
                    corrente != null && dettaglio.indiceCorrente < salita -> "Il treno è a ${corrente.nome}"
                    else -> dettaglio.fermate.getOrNull(salita)?.nome ?: "Partenza"
                },
                style = Testo.micro, color = tb.ter, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                arrivo?.let { dettaglio.fermate.getOrNull(it)?.nome } ?: "Arrivo",
                style = Testo.micro, color = tb.ter, maxLines = 1, overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End, modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Posizione continua del treno fra le fermate, come indice frazionario: 2.4 vuol dire "fra la
 * terza e la quarta fermata, al 40% della tratta". Serve a non far scattare la barra da un
 * pallino all'altro solo quando arriva la conferma della fermata successiva.
 */
private fun posizioneInTratta(dettaglio: DettaglioTreno.Ok, ora: Long): Float {
    val corrente = dettaglio.indiceCorrente
    if (corrente < 0) return 0f
    val prossima = dettaglio.fermate.getOrNull(corrente + 1) ?: return corrente.toFloat()
    val partenza = dettaglio.fermate[corrente].effettivaMs ?: dettaglio.fermate[corrente].programmataMs
    val arrivo = prossima.programmataMs
    if (partenza == null || arrivo == null || arrivo <= partenza) return corrente.toFloat()
    val frazione = ((ora - partenza).toFloat() / (arrivo - partenza)).coerceIn(0f, 1f)
    return corrente + frazione
}
