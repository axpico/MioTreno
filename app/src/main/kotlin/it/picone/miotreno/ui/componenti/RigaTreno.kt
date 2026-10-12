package it.picone.miotreno.ui.componenti

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import it.picone.miotreno.domain.ProssimoTreno
import it.picone.miotreno.domain.Semaforo
import it.picone.miotreno.domain.cambi
import it.picone.miotreno.domain.coincidenzaARischio
import it.picone.miotreno.domain.comeOra
import it.picone.miotreno.domain.etichettaBreve
import it.picone.miotreno.domain.etichettaStato
import it.picone.miotreno.domain.nota
import it.picone.miotreno.domain.orario
import it.picone.miotreno.domain.orarioProiettato
import it.picone.miotreno.domain.primoCambio
import it.picone.miotreno.domain.semaforo
import it.picone.miotreno.ui.theme.LocalTb
import it.picone.miotreno.ui.theme.Testo

/**
 * Riga del tabellone partenze: una griglia orizzontale, non una card impilata.
 * Ogni colonna (orario, categoria, destinazione, binario, countdown) è allineata
 * sullo stesso centro verticale in ogni riga: è l'allineamento a colonne, non l'altezza
 * della card, a far sembrare la lista bilanciata. Il ritardo si legge dal colore
 * dell'orario stesso, non da un badge separato che sposterebbe la riga.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RigaTreno(
    treno: ProssimoTreno,
    ora: Long,
    seguito: Boolean,
    semaforo: Semaforo,
    cancellato: Boolean,
    minuti: Int,
    onClick: () -> Unit,
    atteso: String?,
    passaPerEtichetta: String?,
    partenzaEtichetta: String?,
    divisore: Boolean,
    modifier: Modifier = Modifier,
) {
    val tb = LocalTb.current
    val coloreOrario = when {
        cancellato -> tb.tx
        semaforo == Semaforo.InOrario -> tb.tx
        else -> tb.colore(semaforo)
    }
    val nota = treno.stato.nota

    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .let { if (cancellato) it.alpha(0.55f) else it }
                .let { if (!cancellato) it.clickable(onClick = onClick) else it }
                .heightIn(min = 72.dp)
                // la corsa seguita ha una barra accento sul bordo sinistro, fuori dal contenuto:
                // così le colonne restano allineate fra tutte le righe
                .drawBehind {
                    if (seguito) {
                        drawRoundRect(
                            tb.accento, topLeft = Offset(-12.dp.toPx(), size.height * 0.25f),
                            size = Size(4.dp.toPx(), size.height * 0.5f), cornerRadius = CornerRadius(2.dp.toPx()),
                        )
                    }
                }
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OrarioConRitardo(
                orario = orarioProiettato(treno.orarioPartenzaMs, treno.ritardoMinuti),
                stile = Testo.numero,
                // min, non fisso: con testo grande "≈15:33" andava a capo a metà orario
                modifier = Modifier.widthIn(min = 72.dp),
                colore = coloreOrario,
                cancellato = cancellato,
            )

            Spacer(Modifier.width(8.dp))

            Column(Modifier.weight(1f)) {
                // con un cambio il capolinea del primo treno non è dove vai: in evidenza va il cambio
                val cambio = treno.primoCambio
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // l'icona del cambio: a colpo d'occhio, quali righe non sono dirette
                    if (cambio != null) {
                        Icon(Icone.Cambio, contentDescription = "Con cambio", tint = tb.accento2, modifier = Modifier.size(16.dp))
                    }
                    // l'icona dice già "cambio": il testo resta tutto per il nome della stazione
                    Text(
                        cambio ?: treno.destinazione, style = Testo.corpo, color = tb.tx,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                // Una sola riga secondaria per tutte le righe: con un chip "via X" a parte le righe
                // con il passaggio erano più alte delle altre e la lista perdeva il ritmo.
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = tb.ter)) { append("${treno.categoria} ${treno.numeroTreno}") }
                        // l'arrivo su ogni riga: è l'unico modo di confrontare un diretto con un viaggio con cambi
                        orarioProiettato(treno.orarioArrivoDestinazioneMs, treno.ritardoMinuti).previstoMs?.let {
                            withStyle(SpanStyle(color = tb.sub)) { append(" · arr. ${it.comeOra()}") }
                        }
                        if (treno.cambi > 1) withStyle(SpanStyle(color = tb.accento2)) { append(" · ${treno.cambi} cambi") }
                        if (treno.coincidenzaARischio) withStyle(SpanStyle(color = tb.ambra)) { append(" · coincidenza a rischio") }
                        if (seguito) withStyle(SpanStyle(color = tb.accento)) { append(" · seguita") }
                        if (passaPerEtichetta != null) withStyle(SpanStyle(color = tb.accento)) { append(" · via $passaPerEtichetta") }
                        if (semaforo == Semaforo.Cancellato || semaforo == Semaforo.Irregolare) {
                            withStyle(SpanStyle(color = tb.colore(semaforo))) {
                                append(" · " + treno.etichettaStato() + (nota?.let { " · $it" } ?: ""))
                            }
                        }
                    },
                    style = Testo.micro, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.width(8.dp))
            Binario(treno, prefisso = partenzaEtichetta?.let(::etichettaBreve))
            Spacer(Modifier.width(10.dp))

            Box(Modifier.width(48.dp), contentAlignment = Alignment.CenterEnd) {
                if (!cancellato) {
                    AnimatedCountdown(minuti, grande = false, colore = if (minuti in 0..2) tb.ambra else tb.tx)
                }
            }
        }
        if (atteso != null && !cancellato) RigaAtteso(atteso, Modifier.padding(start = 80.dp, bottom = 8.dp))
        if (divisore) Box(Modifier.fillMaxWidth().height(1.dp).background(tb.bordo))
    }
}
