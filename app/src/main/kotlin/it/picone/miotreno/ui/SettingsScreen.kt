package it.picone.miotreno.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import it.picone.miotreno.BuildConfig
import it.picone.miotreno.data.Impostazioni
import it.picone.miotreno.domain.StazioneCorrente
import it.picone.miotreno.ui.componenti.GlassCard
import it.picone.miotreno.ui.componenti.Icone
import it.picone.miotreno.ui.componenti.Overline
import it.picone.miotreno.ui.componenti.Toggle
import it.picone.miotreno.ui.componenti.conferma
import it.picone.miotreno.ui.componenti.ombraMorbida
import it.picone.miotreno.ui.componenti.rememberHaptic
import it.picone.miotreno.ui.theme.Forme
import it.picone.miotreno.ui.theme.LocalTb
import it.picone.miotreno.ui.theme.Molla
import it.picone.miotreno.ui.theme.Spazio
import it.picone.miotreno.ui.theme.Testo

private val ANTICIPI = listOf(5, 10, 15, 20, 30)

@Composable
fun SettingsScreen(
    imp: Impostazioni,
    stazione: StazioneCorrente?,
    onNotifiche: (Boolean) -> Unit,
    onAnticipo: (Int) -> Unit,
    onAvvisiSciopero: (Boolean) -> Unit,
    onScegliStazione: () -> Unit,
    onUsaGps: () -> Unit,
    destinazioneNome: String? = null,
    onScegliDestinazione: () -> Unit = {},
    onScambia: () -> Unit = {},
    regioneScioperiNome: String? = null,
    stazionePassaggioNome: String? = null,
    onScegliPassaggio: () -> Unit = {},
    onRimuoviPassaggio: () -> Unit = {},
    notifichePermesse: Boolean = true,
    onChiediNotifiche: () -> Unit = {},
    onApriImpostazioniSistema: () -> Unit = {},
    /** null sotto API 33: niente prompt nativo, solo istruzioni. */
    onAggiungiTile: (() -> Unit)? = null,
    onAdsAbilitate: (Boolean) -> Unit = {},
    mostraGestioneConsenso: Boolean = false,
    onGestisciConsenso: () -> Unit = {},
    onSupportaSviluppatore: () -> Unit = {},
) {
    val tb = LocalTb.current
    val haptic = rememberHaptic()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Spazio.pagina, end = Spazio.pagina, bottom = PADDING_BARRA),
        verticalArrangement = Arrangement.spacedBy(Spazio.xl),
    ) {
        item {
            Text(
                "Impostazioni", style = Testo.titolo, color = tb.tx,
                modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(top = Spazio.l),
            )
        }

        item {
            Sezione("Percorso") {
                RigaVoce(
                    Icone.Posizione, "Parti da", stazione?.nome ?: "—",
                    sotto = if (imp.stazioneManuale == null) "Rilevata via GPS: la più vicina" else "Scelta a mano",
                    onClick = onScegliStazione,
                )
                if (imp.stazioneManuale != null) {
                    Divisore()
                    RigaVoce(Icone.Aggiorna, "Torna al GPS", "Usa la stazione più vicina", onClick = onUsaGps)
                }
                Divisore()
                RigaVoce(Icone.Treno, "Verso", destinazioneNome ?: "—", onClick = onScegliDestinazione)
                Divisore()
                RigaVoce(
                    Icone.Cambio, "Inverti percorso", "Scambia partenza e destinazione",
                    // abilitata = false toglie già il click: qui la stazione c'è sempre
                    onClick = { haptic.conferma(); onScambia() },
                    abilitata = stazione != null,
                )
            }
        }

        item {
            Sezione("Stazione di passaggio", nota = "Evidenzia in lista i treni che fermano anche qui.") {
                RigaVoce(Icone.Cerca, "Passa per", stazionePassaggioNome ?: "Nessuna", onClick = onScegliPassaggio)
                if (stazionePassaggioNome != null) {
                    Divisore()
                    RigaVoce(Icone.Chiudi, "Rimuovi", "Disattiva l'evidenziazione", onClick = onRimuoviPassaggio)
                }
            }
        }

        item {
            Sezione("Notifiche") {
                RigaToggle(Icone.Campanella, "Avviso pre-partenza", "Prima del treno seguito, o del prossimo utile", imp.notifiche) {
                    haptic.conferma(); if (it) onChiediNotifiche(); onNotifiche(it)
                }
                if (imp.notifiche && !notifichePermesse) {
                    Text(
                        "Bloccate dal sistema: tocca per riattivarle da Impostazioni Android ↗",
                        Modifier.fillMaxWidth().clip(Forme.cardPiccola).clickable(onClick = onApriImpostazioniSistema)
                            .padding(horizontal = Spazio.l, vertical = Spazio.s),
                        style = Testo.etichetta, color = tb.ambra,
                    )
                }
                Divisore()
                Column(Modifier.padding(Spazio.l)) {
                    Text("Preavviso", style = Testo.corpoMedio, color = tb.tx)
                    Row(
                        Modifier.fillMaxWidth().padding(top = Spazio.m).clip(Forme.chip).background(tb.sf2).padding(4.dp),
                    ) {
                        ANTICIPI.forEach { m ->
                            val attivo = m == imp.anticipoMinuti
                            val sfondo by animateColorAsState(
                                if (attivo) tb.sf else Color.Transparent, Molla.piatta(), label = "anticipo$m",
                            )
                            Box(
                                Modifier
                                    .weight(1f)
                                    .heightIn(min = 40.dp)
                                    .let { if (attivo) it.ombraMorbida(tb, Forme.chip, 2.dp) else it }
                                    .clip(Forme.chip)
                                    .background(sfondo)
                                    .selectable(selected = attivo, role = Role.RadioButton) { haptic.conferma(); onAnticipo(m) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("$m′", style = Testo.numeroPiccolo, color = if (attivo) tb.accento else tb.sub)
                            }
                        }
                    }
                }
            }
        }

        item {
            Sezione("Avvisi") {
                RigaToggle(
                    Icone.Sciopero, "Scioperi",
                    "Feed MIT per ${regioneScioperiNome ?: "questa regione"} e nazionali",
                    imp.avvisiSciopero, onAvvisiSciopero,
                )
            }
        }

        item {
            Sezione(
                "Accesso rapido",
                nota = if (onAggiungiTile == null) {
                    "Apri la tendina delle impostazioni rapide, tocca la matita e trascina \"Prossimo treno\" fra i riquadri attivi."
                } else {
                    null
                },
            ) {
                RigaVoce(
                    Icone.Treno, "Prossimo treno nella tendina", "Minuti alla partenza senza aprire l'app",
                    onClick = { haptic.conferma(); onAggiungiTile?.invoke() },
                    abilitata = onAggiungiTile != null,
                )
            }
        }

        item {
            Sezione("Pubblicità e supporto") {
                RigaToggle(Icone.Chiudi, "Disabilita le pubblicità", "Niente banner né interstitial", !imp.adsAbilitate) {
                    onAdsAbilitate(!it)
                }
                if (imp.adsAbilitate && mostraGestioneConsenso) {
                    Divisore()
                    RigaVoce(Icone.Impostazioni, "Consenso privacy", "Gestisci le preferenze", onClick = onGestisciConsenso)
                }
                Divisore()
                RigaVoce(Icone.Spunta, "Supporta lo sviluppatore", "Offrimi un caffè ☕", onClick = onSupportaSviluppatore)
            }
        }

        item {
            Column(Modifier.padding(horizontal = Spazio.s)) {
                // Va detto chiaramente e dentro l'app, non solo nella scheda dello store:
                // l'app usa un feed pubblico di RFI ma non ha nulla a che vedere con loro.
                Text(
                    "App non ufficiale, non affiliata né approvata da Trenitalia, RFI o " +
                        "Ferrovie dello Stato Italiane.",
                    style = Testo.micro, color = tb.sub,
                )
                Text(
                    "Dati da ViaggiaTreno (API non ufficiale RFI). Scioperi dal feed pubblico MIT, " +
                        "aggiornato una volta al giorno. Lo storico ritardi resta solo su questo dispositivo.",
                    Modifier.padding(top = Spazio.s),
                    style = Testo.micro, color = tb.ter,
                )
                Text(
                    "MioTreno ${BuildConfig.VERSION_NAME}",
                    Modifier.padding(top = Spazio.s),
                    style = Testo.micro, color = tb.ter,
                )
            }
        }
    }
}

/** Gruppo di righe in una card: titolo sopra, nota opzionale sotto. */
@Composable
private fun Sezione(titolo: String, nota: String? = null, content: @Composable () -> Unit) {
    Column {
        Overline(titolo, Modifier.padding(start = Spazio.s, bottom = Spazio.s))
        GlassCard(Modifier.fillMaxWidth(), padding = 0.dp) { content() }
        nota?.let {
            Text(
                it, style = Testo.etichetta, color = LocalTb.current.sub,
                modifier = Modifier.padding(start = Spazio.s, end = Spazio.s, top = Spazio.s),
            )
        }
    }
}

@Composable
private fun Divisore() {
    Box(Modifier.padding(start = 68.dp).fillMaxWidth().height(1.dp).background(LocalTb.current.bordo))
}

/** Icona in una tessera tinta: il segnale visivo di ogni riga. */
@Composable
private fun TesseraIcona(icona: ImageVector) {
    val tb = LocalTb.current
    Box(
        Modifier.size(36.dp).clip(Forme.pillola).background(tb.accentoSoft),
        contentAlignment = Alignment.Center,
    ) { Icon(icona, contentDescription = null, tint = tb.accento, modifier = Modifier.size(20.dp)) }
}

/** Riga navigabile: icona, titolo, valore attuale, freccia. Il valore è ciò che conta. */
@Composable
private fun RigaVoce(
    icona: ImageVector,
    titolo: String,
    valore: String,
    sotto: String? = null,
    abilitata: Boolean = true,
    onClick: () -> Unit,
) {
    val tb = LocalTb.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .let { if (abilitata) it.clickable(onClick = onClick) else it.alpha(0.4f) }
            .semantics { role = Role.Button }
            .padding(horizontal = Spazio.l, vertical = Spazio.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spazio.l),
    ) {
        TesseraIcona(icona)
        Column(Modifier.weight(1f)) {
            Text(titolo, style = Testo.etichetta, color = tb.sub)
            Text(valore, style = Testo.corpoMedio, color = tb.tx, maxLines = 2, overflow = TextOverflow.Ellipsis)
            sotto?.let { Text(it, style = Testo.etichetta, color = tb.ter) }
        }
        Icon(Icone.Avanti, contentDescription = null, tint = tb.ter, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun RigaToggle(icona: ImageVector, nome: String, desc: String, attivo: Boolean, onChange: (Boolean) -> Unit) {
    val tb = LocalTb.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .toggleable(value = attivo, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = Spazio.l, vertical = Spazio.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spazio.l),
    ) {
        TesseraIcona(icona)
        Column(Modifier.weight(1f)) {
            Text(nome, style = Testo.corpoMedio, color = tb.tx)
            Text(desc, style = Testo.etichetta, color = tb.sub)
        }
        Toggle(attivo)
    }
}
