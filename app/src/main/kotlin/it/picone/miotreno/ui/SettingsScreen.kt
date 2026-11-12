package it.picone.miotreno.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import it.picone.miotreno.BuildConfig
import it.picone.miotreno.R
import it.picone.miotreno.data.Impostazioni
import it.picone.miotreno.domain.StazioneCorrente
import it.picone.miotreno.ui.componenti.Icone
import it.picone.miotreno.ui.componenti.Overline
import it.picone.miotreno.ui.componenti.SchermataConBarra
import it.picone.miotreno.ui.componenti.Toggle
import it.picone.miotreno.ui.componenti.conferma
import it.picone.miotreno.ui.componenti.rememberHaptic
import it.picone.miotreno.ui.theme.Forme
import it.picone.miotreno.ui.theme.LocalTb
import it.picone.miotreno.ui.theme.Spazio
import it.picone.miotreno.ui.theme.Testo

private val ANTICIPI = listOf(5, 10, 15, 20, 30)

/** Avviso prima di arrivare alla stazione di cambio: già a bordo, bastano pochi minuti. 0 = spento. */
private val AVVISI_CAMBIO = listOf(0, 3, 5, 10)

/**
 * Impostazioni. Ogni riga dice una cosa sola e ogni azione è un bottone vero: le righe che
 * aprono una scelta hanno la freccia, gli interruttori sono interruttori, le azioni immediate
 * (inverti, aggiungi il riquadro) sono bottoni con il verbo scritto sopra.
 */
@Composable
fun SettingsScreen(
    imp: Impostazioni,
    stazione: StazioneCorrente?,
    onNotifiche: (Boolean) -> Unit,
    onAnticipo: (Int) -> Unit,
    onConCambi: (Boolean) -> Unit,
    onAvvisoCambio: (Int) -> Unit,
    onAvvisiSciopero: (Boolean) -> Unit,
    onScegliStazione: () -> Unit,
    onUsaGps: () -> Unit,
    destinazioneNome: String? = null,
    onScegliDestinazione: () -> Unit = {},
    onScambia: () -> Unit = {},
    regioneScioperiNome: String? = null,
    stazionePassaggioNome: String? = null,
    onScegliPassaggio: () -> Unit = {},
    notifichePermesse: Boolean = true,
    onChiediNotifiche: () -> Unit = {},
    onApriImpostazioniSistema: () -> Unit = {},
    /** null sotto API 33: niente prompt nativo, solo istruzioni. */
    onAggiungiTile: (() -> Unit)? = null,
) {
    val haptic = rememberHaptic()
    val manuale = imp.stazioneManuale != null

    SchermataConBarra("Impostazioni", spaziatura = Spazio.xl) {
        item {
            Sezione("Il tuo viaggio") {
                // Partenza: la stazione, e sotto come la si sceglie. GPS o a mano è una scelta
                // fra due, quindi due bottoni segmentati — non tre righe che dicono la stessa cosa.
                Column(Modifier.tessera()) {
                    RigaVoce(
                        Icone.Posizione, "Partenza", stazione?.nome ?: "Cerco la stazione…",
                        sotto = if (manuale) "Scelta a mano" else "La più vicina a te, dal GPS",
                        onClick = onScegliStazione,
                        tessera = false,
                    )
                    SingleChoiceSegmentedButtonRow(
                        Modifier.fillMaxWidth().padding(start = Spazio.l, end = Spazio.l, bottom = Spazio.l),
                    ) {
                        SegmentedButton(
                            selected = !manuale,
                            onClick = {
                                if (manuale) {
                                    haptic.conferma()
                                    onUsaGps()
                                }
                            },
                            shape = SegmentedButtonDefaults.itemShape(0, 2),
                            icon = { Icon(Icone.Posizione, null, Modifier.size(18.dp)) },
                        ) { Text("Più vicina", style = Testo.etichettaBold) }
                        SegmentedButton(
                            selected = manuale,
                            onClick = onScegliStazione,
                            shape = SegmentedButtonDefaults.itemShape(1, 2),
                            icon = { Icon(Icone.Cerca, null, Modifier.size(18.dp)) },
                        ) { Text("Scegli tu", style = Testo.etichettaBold) }
                    }
                }
                RigaVoce(Icone.Treno, "Destinazione", destinazioneNome ?: "Scegli la destinazione", onClick = onScegliDestinazione)
                Box(Modifier.tessera().padding(Spazio.m)) {
                    OutlinedButton(
                        onClick = { haptic.conferma(); onScambia() },
                        enabled = stazione != null,
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icone.Cambio, null, Modifier.size(ButtonDefaults.IconSize))
                        Text("Inverti per il ritorno", Modifier.padding(start = ButtonDefaults.IconSpacing), style = Testo.bottone)
                    }
                }
            }
        }

        item {
            Sezione("Treni in lista") {
                RigaToggle(
                    Icone.Cambio, "Anche con cambi",
                    "Treni con 1 o 2 cambi accanto ai diretti, se arrivano prima",
                    imp.conCambi,
                ) { haptic.conferma(); onConCambi(it) }
                RigaVoce(
                    Icone.Cerca, "Evidenzia chi passa per", stazionePassaggioNome ?: "Nessuna stazione",
                    sotto = if (stazionePassaggioNome == null) "Ad esempio la stazione di un cambio abituale" else null,
                    onClick = onScegliPassaggio,
                )
            }
        }

        item {
            Sezione("Avvisi") {
                RigaToggle(Icone.Campanella, "Avviso prima della partenza", "Per il treno che segui, o il prossimo utile", imp.notifiche) {
                    haptic.conferma()
                    if (it) onChiediNotifiche()
                    onNotifiche(it)
                }
                if (imp.notifiche && !notifichePermesse) {
                    Row(
                        Modifier.tessera().padding(start = Spazio.l, end = Spazio.s, top = Spazio.s, bottom = Spazio.s),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Le notifiche sono bloccate dal sistema",
                            Modifier.weight(1f), style = Testo.etichetta, color = LocalTb.current.ambra,
                        )
                        TextButton(onClick = onApriImpostazioniSistema) { Text("Sblocca", style = Testo.etichettaBold) }
                    }
                }
                // i tempi contano solo con l'avviso acceso: spento, spariscono invece di restare lì inutili
                AnimatedVisibility(visible = imp.notifiche) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        RigaBlocco("Quanto prima della partenza") {
                            Segmenti(ANTICIPI, imp.anticipoMinuti, { "$it′" }) { haptic.conferma(); onAnticipo(it) }
                        }
                        if (imp.conCambi) {
                            RigaBlocco("Prima di un cambio", "Quando sei a bordo e devi scendere per cambiare") {
                                Segmenti(AVVISI_CAMBIO, imp.avvisoCambioMinuti, { if (it == 0) "Mai" else "$it′" }) {
                                    haptic.conferma(); onAvvisoCambio(it)
                                }
                            }
                        }
                    }
                }
                RigaToggle(
                    Icone.Sciopero, "Scioperi",
                    "In ${regioneScioperiNome ?: "questa regione"} e nazionali, dal feed del MIT",
                    imp.avvisiSciopero, onAvvisiSciopero,
                )
            }
        }

        item {
            Sezione("Accesso rapido", nota = ISTRUZIONI_TILE.takeIf { onAggiungiTile == null }) {
                Riga(
                    Modifier.tessera(), Icone.Treno, "Prossimo treno nella tendina",
                    sotto = "I minuti alla partenza senza aprire l'app",
                ) {
                    run {
                        if (imp.tileAggiunto) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icone.Spunta, null, tint = LocalTb.current.verde, modifier = Modifier.size(18.dp))
                                Text("Aggiunto", style = Testo.etichettaBold, color = LocalTb.current.verde)
                            }
                        } else if (onAggiungiTile != null) {
                            FilledTonalButton(onClick = { haptic.conferma(); onAggiungiTile() }, shapes = ButtonDefaults.shapes()) {
                                Text("Aggiungi", style = Testo.etichettaBold)
                            }
                        }
                    }
                }
            }
        }

        item { Informazioni() }
    }
}

/** In fondo: chi siamo, da dove vengono i dati, versione. Una card, non tre righe di testo grigio. */
@Composable
private fun Informazioni() {
    val tb = LocalTb.current
    Column(
        Modifier.fillMaxWidth().clip(Forme.card).background(MaterialTheme.colorScheme.surfaceContainer).padding(Spazio.l),
        verticalArrangement = Arrangement.spacedBy(Spazio.m),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spazio.m)) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(colorResource(R.color.splash_cerchio)),
                contentAlignment = Alignment.Center,
            ) { Image(painterResource(R.drawable.splash_icona), null, Modifier.size(72.dp)) }
            Column {
                Text("MioTreno", style = Testo.sottotitolo, color = tb.tx)
                Text("Versione ${BuildConfig.VERSION_NAME}", style = Testo.etichetta, color = tb.sub)
            }
        }
        // Va detto chiaramente e dentro l'app: usa un feed pubblico di RFI ma non ha nulla a che vedere con loro.
        Text(
            "App non ufficiale, non affiliata né approvata da Trenitalia, RFI o Ferrovie dello Stato Italiane.",
            style = Testo.etichetta, color = tb.sub,
        )
        Text(
            "Orari da ViaggiaTreno, cambi da LeFrecce, scioperi dal MIT. Lo storico dei ritardi resta solo su questo telefono.",
            style = Testo.micro, color = tb.ter,
        )
    }
}

/**
 * Gruppo di righe come lista segmentata M3 Expressive: ogni riga è una tessera, 2 dp di stacco,
 * angoli grandi agli estremi del gruppo e piccoli all'interno. Titolo sopra, nota sotto.
 */
@Composable
private fun Sezione(titolo: String, nota: String? = null, content: @Composable () -> Unit) {
    Column {
        Overline(titolo, Modifier.fillMaxWidth().padding(bottom = Spazio.s), centrato = true)
        Column(Modifier.fillMaxWidth().clip(Forme.card), verticalArrangement = Arrangement.spacedBy(2.dp)) { content() }
        nota?.let {
            Text(
                it, style = Testo.etichetta, color = LocalTb.current.sub, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(start = Spazio.s, end = Spazio.s, top = Spazio.s),
            )
        }
    }
}

/** Una tessera del gruppo: angoli piccoli, quelli grandi li dà il ritaglio della [Sezione]. */
@Composable
private fun Modifier.tessera(): Modifier =
    fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)

/** Icona in un cerchio tonale: il segnale visivo di ogni riga. */
@Composable
private fun TesseraIcona(icona: ImageVector) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) { Icon(icona, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(20.dp)) }
}

/**
 * Riga compatta: icona, testi, controllo a destra, tutto centrato in verticale. Al posto di
 * ListItem, che con tre righe di testo impone 88 dp e lasciava un vuoto sotto.
 */
@Composable
private fun Riga(
    modifier: Modifier,
    icona: ImageVector,
    titolo: String,
    sopra: String? = null,
    sotto: String? = null,
    destra: @Composable () -> Unit = {},
) {
    val tb = LocalTb.current
    Row(
        modifier.heightIn(min = 64.dp).padding(horizontal = Spazio.l, vertical = Spazio.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spazio.l),
    ) {
        TesseraIcona(icona)
        Column(Modifier.weight(1f)) {
            sopra?.let { Text(it, style = Testo.etichetta, color = tb.sub) }
            Text(titolo, style = Testo.corpoMedio, color = tb.tx, maxLines = 2, overflow = TextOverflow.Ellipsis)
            sotto?.let { Text(it, style = Testo.etichetta, color = tb.sub) }
        }
        destra()
    }
}

/** Riga che apre una scelta: cosa (sopra, piccolo), valore attuale (grande), freccia. */
@Composable
private fun RigaVoce(
    icona: ImageVector,
    titolo: String,
    valore: String,
    sotto: String? = null,
    tessera: Boolean = true,
    onClick: () -> Unit,
) {
    Riga(
        (if (tessera) Modifier.tessera() else Modifier.fillMaxWidth())
            .clickable(onClick = onClick)
            .semantics { role = Role.Button },
        icona, valore, sopra = titolo, sotto = sotto,
    ) { Icon(Icone.Avanti, contentDescription = null, tint = LocalTb.current.sub, modifier = Modifier.size(20.dp)) }
}

@Composable
private fun RigaToggle(icona: ImageVector, nome: String, desc: String, attivo: Boolean, onChange: (Boolean) -> Unit) {
    Riga(
        Modifier.tessera().toggleable(value = attivo, role = Role.Switch, onValueChange = onChange),
        icona, nome, sotto = desc,
    ) { Toggle(attivo) }
}

/** Riga libera (titolo + controllo sotto) nello stesso stile delle altre. */
@Composable
private fun RigaBlocco(titolo: String, sotto: String? = null, content: @Composable () -> Unit) {
    Column(Modifier.tessera().padding(Spazio.l)) {
        Text(titolo, style = Testo.corpoMedio, color = MaterialTheme.colorScheme.onSurface)
        sotto?.let { Text(it, style = Testo.etichetta, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        content()
    }
}

/** Scelta fra pochi valori: bottoni segmentati M3. */
@Composable
private fun Segmenti(valori: List<Int>, scelto: Int, etichetta: (Int) -> String, onScelta: (Int) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = Spazio.m)) {
        valori.forEachIndexed { i, m ->
            SegmentedButton(
                selected = m == scelto,
                onClick = { onScelta(m) },
                shape = SegmentedButtonDefaults.itemShape(i, valori.size),
                icon = {},
            ) {
                val t = etichetta(m)
                Text(t, style = if (t.first().isDigit()) Testo.numeroPiccolo else Testo.etichettaBold)
            }
        }
    }
}
