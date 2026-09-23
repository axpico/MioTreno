package it.picone.miotreno.ui.componenti

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import it.picone.miotreno.domain.ProssimoTreno
import it.picone.miotreno.domain.Semaforo
import it.picone.miotreno.domain.StatoTreno
import it.picone.miotreno.domain.etichettaBreve
import it.picone.miotreno.domain.etichettaStato
import it.picone.miotreno.domain.orarioProiettato
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
    /** La prima corsa è una destinazione, non una riga della lista: usa il layout hero. */
    principale: Boolean = false,
    compatta: Boolean = false,
    /** Ritardo atteso dallo storico ("di solito puntuale…"): secondario, mai al posto dello stato. */
    atteso: String? = null,
    /** Nome della stazione di passaggio confermata (es. "via Legnano"); null = non verificata/non passa. */
    passaPerEtichetta: String? = null,
    /** Quale stazione del cluster (superficie/sotterranea): vedi [etichetteCluster]. */
    partenzaEtichetta: String? = null,
    /** Percorso reale (fermate passate/prossime): solo sulla card in evidenza, null finché non arriva. */
    dettaglio: DettaglioTreno.Ok? = null,
) {
    val tb = LocalTb.current
    val semaforo = treno.semaforo()
    val cancellato = semaforo == Semaforo.Cancellato
    val minuti = treno.minutiAllaPartenza(ora)

    if (principale) {
        CardProssimaPartenza(
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
            onClick = onClick,
            modifier = modifier,
        )
        return
    }

    if (compatta) {
        RigaTreno(
            treno = treno, ora = ora, seguito = seguito, semaforo = semaforo,
            cancellato = cancellato, minuti = minuti, onClick = onClick,
            atteso = atteso, passaPerEtichetta = passaPerEtichetta,
            partenzaEtichetta = partenzaEtichetta, modifier = modifier,
        )
        return
    }

    GlassCard(
        modifier.fillMaxWidth().let { if (cancellato) it.alpha(0.55f) else it },
        onClick = if (cancellato) null else onClick,
        accento = seguito,
        rilievo = !seguito,
        padding = 16.dp,
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                // FlowRow e non Row: con via + stazione di salita insieme i chip non ci stanno
                // in larghezza e l'ultimo veniva tagliato a meta' invece di andare a capo.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(iconaCategoria(treno.categoria), contentDescription = null, tint = tb.sub, modifier = Modifier.size(16.dp))
                    Chip(treno.categoria, colore = tb.accento2, pieno = true)
                    Text("${treno.numeroTreno}", style = Testo.micro, color = tb.ter)
                    if (seguito) Chip("SEGUI", colore = tb.accento, pieno = true)
                    if (passaPerEtichetta != null) Chip("via $passaPerEtichetta", colore = tb.accento2)
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OrarioConRitardo(
                        orario = orarioProiettato(treno.orarioPartenzaMs, treno.ritardoMinuti),
                        stile = Testo.hero,
                        cancellato = cancellato,
                    )
                    Binario(
                        treno, Modifier.padding(bottom = 4.dp),
                        prefisso = partenzaEtichetta?.let(::etichettaBreve),
                    )
                }
                Text(
                    treno.destinazione,
                    style = Testo.etichetta, color = tb.sub, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (!cancellato) {
                Spacer(Modifier.width(12.dp))
                AnimatedCountdown(
                    minuti, grande = true,
                    colore = if (minuti in 0..2) tb.ambra else tb.tx,
                )
            }
        }
        if (!cancellato) BarraViaggio(treno, ora, dettaglio, Modifier.padding(top = 12.dp))
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusBadge(semaforo, treno.etichettaStato())
            val nota = when (val s = treno.stato) {
                is StatoTreno.Cancellato -> s.dettaglio
                is StatoTreno.Deviato -> s.dettaglio
                is StatoTreno.ParzialmenteSoppresso -> s.dettaglio
                is StatoTreno.Alterato -> s.dettaglio
                StatoTreno.Regolare -> null
            }
            nota?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = Testo.micro, color = tb.ter, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (atteso != null && !cancellato) RigaAtteso(atteso)
    }
}

/**
 * Riga del tabellone partenze: una griglia orizzontale, non una card impilata.
 * Ogni colonna (orario, categoria, destinazione, binario, countdown) è allineata
 * sullo stesso centro verticale in ogni riga: è l'allineamento a colonne, non l'altezza
 * della card, a far sembrare la lista bilanciata. Il ritardo si legge dal colore
 * dell'orario stesso, non da un badge separato che sposterebbe la riga.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RigaTreno(
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
    modifier: Modifier = Modifier,
) {
    val tb = LocalTb.current
    val coloreOrario = when {
        cancellato -> tb.tx
        semaforo == Semaforo.InOrario -> tb.tx
        else -> tb.colore(semaforo)
    }
    val coloreIcona = if (semaforo == Semaforo.Cancellato || semaforo == Semaforo.Irregolare) tb.colore(semaforo) else tb.sub
    val nota = when (val s = treno.stato) {
        is StatoTreno.Cancellato -> s.dettaglio
        is StatoTreno.Deviato -> s.dettaglio
        is StatoTreno.ParzialmenteSoppresso -> s.dettaglio
        is StatoTreno.Alterato -> s.dettaglio
        StatoTreno.Regolare -> null
    }?.takeIf { it.isNotBlank() }

    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .let { if (cancellato) it.alpha(0.55f) else it }
                .let { if (!cancellato) it.clickable(onClick = onClick) else it }
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .width(2.dp).height(30.dp)
                    .background(if (seguito) tb.accento else Color.Transparent, Forme.chip),
            )
            Spacer(Modifier.width(10.dp))

            OrarioConRitardo(
                orario = orarioProiettato(treno.orarioPartenzaMs, treno.ritardoMinuti),
                stile = Testo.numero,
                modifier = Modifier.width(56.dp),
                colore = coloreOrario,
                cancellato = cancellato,
            )

            Icon(
                iconaCategoria(treno.categoria), contentDescription = treno.categoria,
                tint = coloreIcona, modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    treno.destinazione, style = Testo.corpo, color = tb.tx,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${treno.numeroTreno}", style = Testo.micro, color = tb.ter)
                    if (seguito) Chip("SEGUI", colore = tb.accento, pieno = true)
                    if (passaPerEtichetta != null) Chip("via $passaPerEtichetta", colore = tb.accento2)
                    if (semaforo == Semaforo.Cancellato || semaforo == Semaforo.Irregolare) {
                        Text(
                            treno.etichettaStato() + (nota?.let { " · $it" } ?: ""),
                            style = Testo.micro, color = tb.colore(semaforo),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                }
            }

            Spacer(Modifier.width(8.dp))
            Binario(treno, prefisso = partenzaEtichetta?.let(::etichettaBreve))
            Spacer(Modifier.width(10.dp))

            Box(Modifier.width(44.dp), contentAlignment = Alignment.CenterEnd) {
                if (!cancellato) {
                    AnimatedCountdown(minuti, grande = false, colore = if (minuti in 0..2) tb.ambra else tb.tx)
                }
            }
        }
        if (atteso != null && !cancellato) RigaAtteso(atteso, Modifier.padding(start = 60.dp, bottom = 2.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(tb.bordo))
    }
}

/** Icona per categoria treno: famiglia di forme, non un codice esatto — un codice ViaggiaTreno
 * mai visto prima cade sul fallback [Icone.Treno] invece di rompersi. */
private fun iconaCategoria(categoria: String): ImageVector {
    val c = categoria.uppercase()
    return when {
        "FR" in c || "FA" in c || "ES" in c || c.startsWith("EN") -> Icone.TrenoAltaVelocita
        "IC" in c || "EC" in c || "RV" in c -> Icone.TrenoVeloce
        "REG" in c || c.startsWith("S") -> Icone.TrenoRegionale
        else -> Icone.Treno
    }
}

/**
 * La prima partenza risponde alle tre domande da banchina, nell'ordine giusto:
 * quando parte, dove va e dove aspettarla. Non è volutamente una versione ingrandita
 * delle righe successive: queste restano confrontabili a colpo d'occhio.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardProssimaPartenza(
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
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val tb = LocalTb.current
    GlassCard(
        modifier.fillMaxWidth().let { if (cancellato) it.alpha(0.55f) else it },
        onClick = if (cancellato) null else onClick,
        accento = seguito,
        rilievo = !seguito,
        padding = 20.dp,
    ) {
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(iconaCategoria(treno.categoria), contentDescription = null, tint = tb.sub, modifier = Modifier.size(18.dp))
            Text("${treno.categoria} · ${treno.numeroTreno}", style = Testo.etichettaBold, color = tb.sub)
            if (seguito) Chip("SEGUI", colore = tb.accento, pieno = true)
            if (partenzaEtichetta != null) {
                Text("da $partenzaEtichetta", style = Testo.micro, color = tb.ambra)
            }
            if (passaPerEtichetta != null) {
                Text("via $passaPerEtichetta", style = Testo.micro, color = tb.accento2)
            }
            Spacer(Modifier.weight(1f))
            StatusBadge(semaforo, treno.etichettaStato(), compatto = true)
        }

        Text(
            treno.destinazione,
            style = Testo.sottotitolo,
            color = tb.tx,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 12.dp),
        )

        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(Modifier.weight(1f)) {
                OrarioConRitardo(
                    orario = orarioProiettato(treno.orarioPartenzaMs, treno.ritardoMinuti),
                    stile = Testo.display,
                    cancellato = cancellato,
                )
                Text("PARTENZA", style = Testo.micro, color = tb.ter)
            }
            if (!cancellato) {
                AnimatedCountdown(
                    minuti = minuti,
                    grande = true,
                    colore = if (minuti in 0..2) tb.ambra else tb.tx,
                )
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .clip(Forme.cardPiccola)
                .background(tb.sf2)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("ARRIVO", style = Testo.micro, color = tb.ter)
                OrarioConRitardo(
                    orario = orarioProiettato(treno.orarioArrivoDestinazioneMs, treno.ritardoMinuti),
                    stile = Testo.numero,
                )
            }
            Box(Modifier.width(1.dp).height(30.dp).background(tb.bordoForte))
            Column(
                Modifier.weight(1f).padding(start = 12.dp),
                horizontalAlignment = Alignment.End,
            ) {
                Text("BINARIO", style = Testo.micro, color = tb.ter)
                Binario(treno, Modifier.padding(top = 3.dp), grande = true)
            }
        }

        if (!cancellato) BarraViaggio(treno, ora, dettaglio, Modifier.padding(top = 16.dp))

        val nota = when (val stato = treno.stato) {
            is StatoTreno.Cancellato -> stato.dettaglio
            is StatoTreno.Deviato -> stato.dettaglio
            is StatoTreno.ParzialmenteSoppresso -> stato.dettaglio
            is StatoTreno.Alterato -> stato.dettaglio
            StatoTreno.Regolare -> null
        }?.takeIf { it.isNotBlank() }
        nota?.let {
            Text(
                it, style = Testo.micro, color = tb.ter, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
        if (atteso != null && !cancellato) RigaAtteso(atteso)
    }
}

/**
 * Barra del viaggio, solo sulla card in evidenza. Con [dettaglio] (fermate reali da
 * `andamentoTreno`) mostra tratta e prossima fermata; senza, resta la stima sull'orario
 * mentre il dettaglio arriva o non è disponibile.
 */
@Composable
private fun BarraViaggio(treno: ProssimoTreno, ora: Long, dettaglio: DettaglioTreno.Ok?, modifier: Modifier = Modifier) {
    val tb = LocalTb.current

    // Fermate intermedie fra origine (indice 0) e destinazione (indiceDestinazione): la barra va da
    // un capo all'altro, non fino all'ultima fermata della corsa (che può proseguire
    // oltre la destinazione) — il numero di pallini deve combaciare con le fermate reali di *questo* tratto.
    val indiceDestinazione = dettaglio?.indiceDestinazione?.takeIf { it > 0 }
    val fermateIntermedie = if (indiceDestinazione != null && indiceDestinazione > 1) (1 until indiceDestinazione) else IntRange.EMPTY

    // La barra deve riempirsi sulla stessa tratta dei pallini (origine → destinazione), non sull'intera
    // corsa che può proseguire oltre la destinazione: altrimenti il riempimento resta indietro rispetto ai
    // pallini già "passati", che usano indiceDestinazione come base.
    //
    // In viaggio fra due fermate il riempimento non deve incollarsi al pallino di quella
    // precedente: si interpola sull'orologio fra l'orario reale di partenza da lì e l'orario
    // previsto di arrivo alla prossima, così la barra avanza con continuità invece di scattare
    // da un pallino all'altro solo alla conferma della fermata successiva.
    val posizioneCorrente = dettaglio?.let { posizioneInTratta(it, ora) }
    val avanzamento = if (posizioneCorrente != null && indiceDestinazione != null) {
        (posizioneCorrente / indiceDestinazione).coerceIn(0f, 1f)
    } else if (dettaglio != null) {
        ((dettaglio.indiceCorrente + 1).toFloat() / dettaglio.fermate.size.coerceAtLeast(1)).coerceIn(0f, 1f)
    } else {
        treno.avanzamento(ora)
    }

    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                dettaglio?.fermate?.firstOrNull()?.nome.orEmpty(),
                style = Testo.micro, color = tb.ter, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(8.dp))
            BoxWithConstraints(Modifier.weight(2f).height(5.dp)) {
                LinearProgressIndicator(
                    progress = { avanzamento },
                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(Forme.chip),
                    color = tb.accento, trackColor = tb.bordoForte,
                    // Material3 disegna di default un "traguardo" a fine barra: qui i pallini
                    // sopra già marcano le fermate, un secondo pallino fisso in fondo è ridondante
                    // e sembra un pallino fuori posto quando il viaggio non è ancora a destinazione.
                    drawStopIndicator = {},
                )
                for (i in fermateIntermedie) {
                    val frazione = i.toFloat() / indiceDestinazione!!
                    val passata = i <= dettaglio!!.indiceCorrente
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
                                    it.background(tb.bg).border(1.5.dp, tb.ter, CircleShape)
                                }
                            },
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Text("Arrivo", style = Testo.micro, color = tb.ter, maxLines = 1)
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

    val scala = remember { Animatable(1f) }
    LaunchedEffect(confermato) {
        if (!confermato || Molla.riduci) return@LaunchedEffect
        scala.snapTo(1f)
        scala.animateTo(1.12f, Molla.stato())
        scala.animateTo(1f, Molla.stato())
    }

    Row(
        modifier
            .scale(scala.value)
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
