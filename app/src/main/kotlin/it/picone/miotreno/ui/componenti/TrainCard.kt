package it.picone.miotreno.ui.componenti

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import it.picone.miotreno.domain.DettaglioTreno
import it.picone.miotreno.domain.ProssimoCambio
import it.picone.miotreno.domain.ProssimoTreno
import it.picone.miotreno.domain.Semaforo
import it.picone.miotreno.domain.StatoCorsa
import it.picone.miotreno.domain.semaforo
import it.picone.miotreno.ui.theme.Forme
import it.picone.miotreno.ui.theme.LocalTb
import it.picone.miotreno.ui.theme.Molla
import it.picone.miotreno.ui.theme.Testo

/**
 * La card treno. Gerarchia Revolut: a sinistra chi è (categoria, numero, destinazione) e
 * quando (orario grande, binario), a destra il countdown come numero protagonista.
 * Il badge di stato sta sotto. Un treno cancellato è una card spenta: opaca al 55%,
 * orario barrato, badge rosso pieno, non cliccabile.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TrainCard(
    treno: ProssimoTreno,
    ora: Long,
    seguito: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Riga del tabellone; altrimenti la card grande (prossima partenza o corsa seguita). */
    compatta: Boolean = false,
    /** Ritardo atteso dallo storico ("di solito puntuale…"): secondario, mai al posto dello stato. */
    atteso: String? = null,
    /** Nome della stazione di passaggio confermata (es. "via Legnano"); null = non verificata/non passa. */
    passaPerEtichetta: String? = null,
    /** Quale stazione del cluster (superficie/sotterranea): vedi [etichetteCluster]. */
    partenzaEtichetta: String? = null,
    /** Percorso reale (fermate passate/prossime): solo sulla card in evidenza, null finché non arriva. */
    dettaglio: DettaglioTreno.Ok? = null,
    /** Riga compatta: linea sotto, se non è l'ultima del gruppo. */
    divisore: Boolean = true,
    /** Codici del cluster dove sali: la barra viaggio parte da lì, non dal capolinea. */
    codiciSalita: List<String> = emptyList(),
    /** Corsa seguita con cambi: prossimo cambio o coincidenza persa (vedi `controllaSeguito`). */
    cambio: ProssimoCambio? = null,
    /** Corsa seguita: cosa fare adesso, in cima alla card (vedi [it.picone.miotreno.domain.statoCorsa]). */
    statoCorsa: StatoCorsa? = null,
    /** Corsa seguita: azioni rapide "Dettagli" e "Smetti di seguire" in fondo alla card. */
    onSmetti: (() -> Unit)? = null,
) {
    val tb = LocalTb.current
    val semaforo = treno.semaforo()
    val cancellato = semaforo == Semaforo.Cancellato
    val minuti = treno.minutiAllaPartenza(ora)

    if (compatta) {
        RigaTreno(
            treno = treno, ora = ora, seguito = seguito, semaforo = semaforo,
            cancellato = cancellato, minuti = minuti, onClick = onClick,
            atteso = atteso, passaPerEtichetta = passaPerEtichetta,
            partenzaEtichetta = partenzaEtichetta, divisore = divisore, modifier = modifier,
        )
        return
    }

    // Una sola card grande, per la prossima partenza come per la corsa seguita: la terza
    // variante, sbilanciata a sinistra, diceva le stesse cose in un'altra lingua visiva.
    CardCorsa(
        treno = treno,
        ora = ora,
        seguito = seguito,
        semaforo = semaforo,
        cancellato = cancellato,
        minuti = minuti,
        dettaglio = dettaglio,
        atteso = atteso,
        passaPerEtichetta = passaPerEtichetta,
        partenzaEtichetta = partenzaEtichetta,
        codiciSalita = codiciSalita,
        cambio = cambio,
        statoCorsa = statoCorsa,
        onSmetti = onSmetti,
        onClick = onClick,
        modifier = modifier,
    )
}

/** Icona per categoria treno: famiglia di forme, non un codice esatto — un codice ViaggiaTreno
 * mai visto prima cade sul fallback [Icone.Treno] invece di rompersi. */
internal fun iconaCategoria(categoria: String): ImageVector {
    val c = categoria.uppercase()
    return when {
        "FR" in c || "FA" in c || "ES" in c || c.startsWith("EN") -> Icone.TrenoAltaVelocita
        "IC" in c || "EC" in c || "RV" in c -> Icone.TrenoVeloce
        "REG" in c || c.startsWith("S") -> Icone.TrenoRegionale
        else -> Icone.Treno
    }
}

/** Storico locale: testo terziario con icona piccola, staccato dal badge realtime. */
@Composable
fun RigaAtteso(testo: String, modifier: Modifier = Modifier) {
    val tb = LocalTb.current
    Row(
        modifier.padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(Icone.Statistiche, contentDescription = "Storico", tint = tb.ter, modifier = Modifier.size(11.dp))
        Text(testo, style = Testo.micro, color = tb.ter, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Il pop del binario che diventa definitivo: un piccolo evento per chi è in stazione. */
@Composable
internal fun popAlConfermare(confermato: Boolean): Float {
    val scala = remember { Animatable(1f) }
    LaunchedEffect(confermato) {
        if (!confermato || Molla.riduci) return@LaunchedEffect
        scala.snapTo(1f)
        scala.animateTo(1.12f, Molla.stato())
        scala.animateTo(1f, Molla.stato())
    }
    return scala.value
}

/**
 * Binario: numero in una pillola; confermato = accento con pallino, previsto = solo bordo.
 * Il passaggio da previsto a confermato è un piccolo evento per chi è in stazione: un pop
 * (stessa molla del badge di stato) segna il momento in cui il binario diventa definitivo.
 */
@Composable
fun Binario(
    treno: ProssimoTreno,
    modifier: Modifier = Modifier,
    grande: Boolean = false,
    /** Sostituisce "BIN": dove salire quando lo scalo ha piu' piazzali (vedi [etichetteCluster]). */
    prefisso: String? = null,
) {
    val tb = LocalTb.current
    val confermato = treno.binarioConfermato
    val sfondo by animateColorAsState(if (confermato) tb.accentoSoft else Color.Transparent, Molla.piatta(), label = "binSfondo")
    val bordo by animateColorAsState(if (confermato) tb.accento.copy(alpha = 0.5f) else tb.bordoForte, Molla.piatta(), label = "binBordo")
    val testo by animateColorAsState(if (confermato) tb.accento else tb.ter, Molla.piatta(), label = "binTesto")
    val numero by animateColorAsState(if (confermato) tb.tx else tb.sub, Molla.piatta(), label = "binNumero")

    val scala = popAlConfermare(confermato)

    Row(
        modifier
            .scale(scala)
            .clip(Forme.chip)
            .background(sfondo)
            .border(1.dp, bordo, Forme.chip)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(if (prefisso != null) "$prefisso ·" else "BIN", style = Testo.micro, color = testo)
        Text(
            treno.binario ?: "—",
            style = if (grande) Testo.numero else Testo.numeroPiccolo,
            color = numero,
        )
        if (confermato) Box(Modifier.size(5.dp).clip(CircleShape).background(tb.accento))
    }
}
