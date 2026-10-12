package it.picone.miotreno.ui.componenti

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import it.picone.miotreno.domain.DettaglioTreno
import it.picone.miotreno.domain.ProssimoCambio
import it.picone.miotreno.domain.ProssimoTreno
import it.picone.miotreno.domain.Semaforo
import it.picone.miotreno.domain.StatoCorsa
import it.picone.miotreno.domain.Tono
import it.picone.miotreno.domain.coincidenzaARischio
import it.picone.miotreno.domain.comeOra
import it.picone.miotreno.domain.etichettaStato
import it.picone.miotreno.domain.indiceSalita
import it.picone.miotreno.domain.nota
import it.picone.miotreno.domain.orario
import it.picone.miotreno.domain.orarioProiettato
import it.picone.miotreno.domain.primoCambio
import it.picone.miotreno.domain.semaforo
import it.picone.miotreno.domain.testoCambi
import it.picone.miotreno.ui.theme.Forme
import it.picone.miotreno.ui.theme.LocalTb
import it.picone.miotreno.ui.theme.Testo

/**
 * La prima partenza risponde alle tre domande da banchina, nell'ordine giusto:
 * quando parte, dove va e dove aspettarla. Non è volutamente una versione ingrandita
 * delle righe successive: queste restano confrontabili a colpo d'occhio.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CardCorsa(
    treno: ProssimoTreno,
    ora: Long,
    seguito: Boolean,
    semaforo: Semaforo,
    cancellato: Boolean,
    minuti: Int,
    dettaglio: DettaglioTreno.Ok?,
    atteso: String?,
    passaPerEtichetta: String?,
    partenzaEtichetta: String?,
    codiciSalita: List<String>,
    cambio: ProssimoCambio?,
    statoCorsa: StatoCorsa?,
    onSmetti: (() -> Unit)?,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val tb = LocalTb.current
    // a bordo della corsa seguita conta l'arrivo, non più la partenza
    val aBordo = seguito && minuti < 0 && !cancellato
    GlassCard(
        modifier.fillMaxWidth().let { if (cancellato) it.alpha(0.55f) else it },
        onClick = if (cancellato) null else onClick,
        accento = seguito,
        rilievo = !seguito,
        padding = 20.dp,
    ) {
        if (statoCorsa != null) RigaStato(statoCorsa, cambio?.dettaglio?.takeIf { !cambio.persa }, Modifier.padding(bottom = 16.dp))
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(iconaCategoria(treno.categoria), contentDescription = null, tint = tb.sub, modifier = Modifier.size(18.dp))
            Text("${treno.categoria} ${treno.numeroTreno}", style = Testo.etichettaBold, color = tb.sub)
            if (seguito && statoCorsa == null) Chip("Segui", colore = tb.accento, pieno = true)
            if (partenzaEtichetta != null) {
                Chip("da $partenzaEtichetta", colore = tb.ambra, pieno = true)
            }
            if (passaPerEtichetta != null) {
                Chip("via $passaPerEtichetta", colore = tb.accento2, pieno = true)
            }
            Spacer(Modifier.weight(1f))
            StatusBadge(semaforo, treno.etichettaStato(), compatto = true)
        }

        // con un cambio il titolo è dove si cambia, come nelle righe: il capolinea del primo treno inganna
        val stazioneCambio = treno.primoCambio
        Row(
            Modifier.fillMaxWidth().padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            if (stazioneCambio != null) {
                Icon(Icone.Cambio, contentDescription = "Con cambio", tint = tb.accento2, modifier = Modifier.size(20.dp))
            }
            Text(
                stazioneCambio ?: treno.destinazione,
                style = Testo.sottotitolo,
                color = tb.tx,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // il titolo dice già dove si cambia: la riga serve solo per il rischio (il cambio live sta nella riga di stato)
        if (statoCorsa == null && (cambio != null || treno.coincidenzaARischio)) RigaCambi(treno, cambio)

        if (aBordo) {
            ColonneArrivo(treno, ora, dettaglio, codiciSalita, alCambio = cambio != null)
        } else {
            ColonnePartenza(treno, cancellato, minuti)
        }

        if (!cancellato) BarraViaggio(treno, ora, dettaglio, codiciSalita, Modifier.padding(top = 16.dp))

        val nota = treno.stato.nota
        nota?.let {
            Text(
                it, style = Testo.micro, color = tb.ter, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
        if (atteso != null && !cancellato) RigaAtteso(atteso)
        if (onSmetti != null) {
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BottoneSecondario("Dettagli", onClick = onClick, icona = Icone.Treno, modifier = Modifier.weight(1f))
                BottoneSecondario(
                    "Smetti", onClick = onSmetti, icona = Icone.Chiudi, descrizione = "Smetti di seguire questa corsa",
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * Prima della partenza: a sinistra quanto manca (il numero protagonista), a destra l'orario,
 * sotto binario e arrivo. Etichette sulla stessa riga, valori alla stessa altezza.
 */
@Composable
private fun ColonnePartenza(treno: ProssimoTreno, cancellato: Boolean, minuti: Int) {
    val tb = LocalTb.current
    Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(if (cancellato) "Stato" else "Parte tra", style = Testo.etichetta, color = tb.ter)
            when {
                cancellato -> Text("Cancellato", style = Testo.titolo, color = tb.rosso)
                minuti < 0 -> Text("In viaggio", style = Testo.titolo, color = tb.accento)
                else -> NumeroMinuti(minuti, if (minuti in 0..2) tb.ambra else tb.accento)
            }
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
            Text("Partenza", style = Testo.etichetta, color = tb.ter)
            OrarioConRitardo(
                orario = orarioProiettato(treno.orarioPartenzaMs, treno.ritardoMinuti),
                // un gradino sotto il countdown: "quanto manca" resta il numero protagonista
                stile = Testo.hero,
                cancellato = cancellato,
                allineaFine = true,
            )
        }
    }
    Row(
        Modifier.fillMaxWidth().padding(top = 16.dp).height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Tessera("Binario", Modifier.weight(1f)) { BinarioTessera(treno) }
        Tessera("Arrivo", Modifier.weight(1f)) {
            OrarioConRitardo(orario = orarioProiettato(treno.orarioArrivoDestinazioneMs, treno.ritardoMinuti), stile = Testo.numero)
        }
    }
}

/**
 * A bordo: quanto manca all'arrivo e a che ora, poi dov'è il treno adesso e quante fermate
 * restano (fino al cambio, per una tratta prima di un cambio). Il binario di partenza non serve più.
 */
@Composable
private fun ColonneArrivo(treno: ProssimoTreno, ora: Long, dettaglio: DettaglioTreno.Ok?, codiciSalita: List<String>, alCambio: Boolean) {
    val tb = LocalTb.current
    val arrivo = orarioProiettato(treno.orarioArrivoDestinazioneMs, treno.ritardoMinuti)
    val minutiArrivo = arrivo.previstoMs?.let { ((it - ora) / 60_000L).toInt().coerceAtLeast(0) }
    Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text("Arrivi tra", style = Testo.etichetta, color = tb.ter)
            if (minutiArrivo != null) NumeroMinuti(minutiArrivo, tb.verde) else Text("—", style = Testo.titolo, color = tb.sub)
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
            Text("Arrivo", style = Testo.etichetta, color = tb.ter)
            OrarioConRitardo(orario = arrivo, stile = Testo.hero, allineaFine = true)
        }
    }
    val salita = dettaglio?.indiceSalita(codiciSalita) ?: 0
    // alla stazione di cambio il treno dopo non è ancora arrivato: si dice dov'è, non la "prossima fermata"
    val daSalire = dettaglio != null && dettaglio.indiceCorrente < salita
    val prossima = dettaglio?.fermate?.getOrNull(dettaglio.indiceCorrente + 1)
    val mancanti = dettaglio?.indiceDestinazione?.takeIf {
        it >= 0
    }?.let { (it - maxOf(dettaglio.indiceCorrente, salita)).coerceAtLeast(0) }
    Row(
        Modifier.fillMaxWidth().padding(top = 16.dp).height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Tessera(if (daSalire) "Il tuo treno è a" else "Prossima fermata", Modifier.weight(1f)) {
            val fermata = if (daSalire) dettaglio?.fermate?.getOrNull(dettaglio.indiceCorrente) else prossima
            Text(fermata?.nome ?: "—", style = Testo.corpoMedio, color = tb.tx, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!daSalire && prossima != null) {
                prossima.orario(dettaglio.ritardoMinuti).previstoMs?.let { Text(it.comeOra(), style = Testo.micro, color = tb.sub) }
            }
        }
        Tessera(if (alCambio) "Fermate al cambio" else "Fermate mancanti", Modifier.weight(1f)) {
            Text(mancanti?.toString() ?: "—", style = Testo.numero, color = tb.tx)
        }
    }
}

/** "19 min": il numero grande animato e l'unità più piccola, allineati in basso. */
@Composable
private fun NumeroMinuti(minuti: Int, colore: Color) {
    val tb = LocalTb.current
    Row(verticalAlignment = Alignment.Bottom) {
        NumeroAnimato("$minuti", stile = Testo.display, colore = colore)
        Text(
            if (minuti == 1) " minuto" else " min", style = Testo.sottotitolo, color = tb.sub,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }
}

/** Cosa fare adesso, su una striscia tinta del colore del tono; sotto, il treno del cambio se c'è. */
@Composable
private fun RigaStato(stato: StatoCorsa, dettaglio: String?, modifier: Modifier = Modifier) {
    val tb = LocalTb.current
    val colore = when (stato.tono) {
        Tono.Ok -> tb.accento
        Tono.Attenzione -> tb.ambra
        Tono.Allarme -> tb.rosso
    }
    // centrata, come il titolo sotto: la card si legge dall'asse in giù
    Column(
        modifier.fillMaxWidth().clip(Forme.cardPiccola).background(colore.copy(alpha = ALPHA_TINTA))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stato.testo, style = Testo.etichettaBold, color = colore, textAlign = TextAlign.Center)
        // icona e non "→": il glifo del font sta sulla linea di base e sembra scivolato in basso
        dettaglio?.let {
            Row(
                Modifier.padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icone.Freccia, contentDescription = "poi", tint = tb.sub, modifier = Modifier.size(14.dp))
                Text(it, style = Testo.micro, color = tb.sub)
            }
        }
    }
}

/** Tessera informativa della card hero: etichetta sopra, valore sotto, stessa altezza della gemella. */
@Composable
private fun Tessera(etichetta: String, modifier: Modifier = Modifier, valore: @Composable () -> Unit) {
    val tb = LocalTb.current
    Column(
        modifier.fillMaxHeight().clip(Forme.cardPiccola).background(tb.sf2).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(etichetta, style = Testo.etichetta, color = tb.ter)
        valore()
    }
}

/**
 * Sotto la destinazione di un viaggio con cambi: quanti e dove, e se il ritardo del primo treno
 * si mangia il cambio. Per la corsa seguita, al posto del riassunto, il cambio vero in arrivo.
 */
@Composable
private fun RigaCambi(treno: ProssimoTreno, cambio: ProssimoCambio?) {
    val tb = LocalTb.current
    val allarme = treno.coincidenzaARischio || cambio?.persa == true
    // la stazione è già nel titolo della card: qui solo il resto (o la coincidenza persa)
    val testo = when {
        cambio == null -> (treno.testoCambi() ?: return) + " · coincidenza a rischio"
        cambio.persa -> cambio.testo
        else -> cambio.dettaglio ?: return
    }
    val colore = if (allarme) tb.ambra else tb.accento2
    Row(
        Modifier.padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(Icone.Cambio, contentDescription = null, tint = colore, modifier = Modifier.size(13.dp))
        Text(testo, style = Testo.micro, color = colore, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Binario nella tessera della card hero: la tessera dice già "Binario", quindi niente pillola
 * "BIN" — solo il numero grande e se è confermato o previsto.
 */
@Composable
private fun BinarioTessera(treno: ProssimoTreno) {
    val tb = LocalTb.current
    val confermato = treno.binarioConfermato
    // numero sopra, stato sotto: in riga, con testo grande, "confermato" andava a capo a metà parola
    Column(Modifier.scale(popAlConfermare(confermato))) {
        Text(treno.binario ?: "—", style = Testo.numero, color = if (confermato) tb.tx else tb.sub)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (confermato) Box(Modifier.size(6.dp).clip(CircleShape).background(tb.accento))
            Text(if (confermato) "confermato" else "previsto", style = Testo.micro, color = if (confermato) tb.accento else tb.ter)
        }
    }
}
