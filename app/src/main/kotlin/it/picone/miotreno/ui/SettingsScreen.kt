package it.picone.miotreno.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import it.picone.miotreno.data.Impostazioni
import it.picone.miotreno.domain.StazioneCorrente
import it.picone.miotreno.ui.componenti.BottoneIcona
import it.picone.miotreno.ui.componenti.BottonePrimario
import it.picone.miotreno.ui.componenti.BottoneSecondario
import it.picone.miotreno.ui.componenti.Chip
import it.picone.miotreno.ui.componenti.GlassCard
import it.picone.miotreno.ui.componenti.Icone
import it.picone.miotreno.ui.componenti.Overline
import it.picone.miotreno.ui.componenti.Toggle
import it.picone.miotreno.ui.componenti.conferma
import it.picone.miotreno.ui.componenti.rememberHaptic
import it.picone.miotreno.ui.theme.Forme
import it.picone.miotreno.ui.theme.LocalTb
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
        verticalArrangement = Arrangement.spacedBy(Spazio.l),
    ) {
        item {
            Column(Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(top = Spazio.m)) {
                Overline("Preferenze")
                Text("Impostazioni", style = Testo.titolo, color = tb.tx)
            }
        }

        item {
            Column {
                Row(
                    Modifier.padding(bottom = 8.dp, start = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Overline("Percorso", Modifier.weight(1f))
                    BottoneIcona(
                        Icone.Cambio, "Inverti partenza e destinazione",
                        onClick = { if (stazione != null) onScambia() },
                        modifier = if (stazione != null) Modifier else Modifier.alpha(0.35f),
                    )
                }
                GlassCard(Modifier.fillMaxWidth()) {
                    Column {
                        Overline("Parti da")
                        Text(stazione?.nome ?: "—", style = Testo.sottotitolo, color = tb.tx, modifier = Modifier.padding(top = 2.dp))
                        Text(
                            when (imp.stazioneManuale) {
                                null -> "Rilevata via GPS: la più vicina."
                                else -> "Scelta a mano. Il GPS è ignorato finché non torni ad usarlo."
                            },
                            style = Testo.etichetta, color = tb.sub, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(Spazio.s)) {
                            BottonePrimario("Scegli stazione", onScegliStazione, icona = Icone.Cerca, attivo = imp.stazioneManuale != null)
                            if (imp.stazioneManuale != null) BottoneSecondario("Usa GPS", onUsaGps, icona = Icone.Posizione)
                        }

                        Box(Modifier.fillMaxWidth().padding(vertical = Spazio.l).height(1.dp).background(tb.bordo))

                        Overline("Verso")
                        Text(
                            destinazioneNome ?: "—", style = Testo.sottotitolo, color = tb.tx,
                            modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
                        )
                        BottonePrimario("Cambia destinazione", onScegliDestinazione, icona = Icone.Cerca, attivo = true)
                    }
                }
            }
        }

        item {
            Sezione("Stazione di passaggio") {
                Text(stazionePassaggioNome ?: "Nessuna", style = Testo.sottotitolo, color = tb.tx)
                Text(
                    "Evidenzia in lista i treni diretti a ${destinazioneNome ?: "destinazione"} che fermano anche qui.",
                    style = Testo.etichetta, color = tb.sub, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spazio.s)) {
                    BottonePrimario("Scegli stazione", onScegliPassaggio, icona = Icone.Cerca, attivo = stazionePassaggioNome != null)
                    if (stazionePassaggioNome != null) BottoneSecondario("Nessuna", onRimuoviPassaggio, icona = Icone.Chiudi)
                }
            }
        }

        item {
            Sezione("Notifiche") {
                RigaToggle("Avviso pre-partenza", "Notifica prima del treno seguito, o del prossimo utile.", imp.notifiche) {
                    haptic.conferma(); if (it) onChiediNotifiche(); onNotifiche(it)
                }
                if (imp.notifiche && !notifichePermesse) Text(
                    "Bloccate dal sistema: nessun avviso finché non le riattivi da Impostazioni Android ↗",
                    Modifier.padding(top = 8.dp).clip(Forme.chip).clickable(onClick = onApriImpostazioniSistema).padding(4.dp),
                    style = Testo.micro, color = tb.ambra,
                )
                Text("Preavviso", style = Testo.etichettaBold, color = tb.tx, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(Spazio.s)) {
                    ANTICIPI.forEach { m ->
                        val attivo = m == imp.anticipoMinuti
                        Box(
                            Modifier
                                .clip(Forme.pillola)
                                .background(if (attivo) tb.accento else tb.sf2)
                                .border(1.dp, if (attivo) tb.accento else tb.bordo, Forme.pillola)
                                .clickable { haptic.conferma(); onAnticipo(m) }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        ) { Text("$m′", style = Testo.numeroPiccolo, color = if (attivo) tb.bg else tb.sub) }
                    }
                }
            }
        }

        item {
            Sezione("Avvisi") {
                RigaToggle(
                    "Scioperi",
                    "Banner e nota in notifica se il feed MIT segnala uno sciopero ferroviario in " +
                        "${regioneScioperiNome ?: "questa regione"} o nazionale.",
                    imp.avvisiSciopero, onAvvisiSciopero,
                )
            }
        }

        item {
            Sezione("Accesso rapido") {
                Text("Prossimo treno nella tendina", style = Testo.corpoMedio, color = tb.tx)
                Text(
                    "Un riquadro nelle impostazioni rapide con il prossimo treno e i minuti alla partenza, senza aprire l'app.",
                    style = Testo.etichetta, color = tb.sub, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
                )
                if (onAggiungiTile != null) {
                    BottonePrimario("Aggiungi alla tendina", { haptic.conferma(); onAggiungiTile() }, icona = Icone.Treno)
                } else {
                    Text(
                        "Apri la tendina delle impostazioni rapide, tocca la matita (modifica) e trascina \"Prossimo treno\" fra i riquadri attivi.",
                        style = Testo.etichetta, color = tb.sub,
                    )
                }
            }
        }

        item {
            Sezione("Pubblicità e supporto") {
                RigaToggle(
                    "Disabilita tutte le pubblicità", "Niente banner né interstitial, in nessuna schermata.",
                    !imp.adsAbilitate,
                ) { onAdsAbilitate(!it) }
                if (imp.adsAbilitate && mostraGestioneConsenso) Text(
                    "Gestisci consenso privacy",
                    Modifier.padding(top = 14.dp).clickable(onClick = onGestisciConsenso),
                    style = Testo.etichettaBold, color = tb.accento,
                )
                Text(
                    "Supporta lo sviluppatore ☕",
                    Modifier.padding(top = 14.dp).clickable(onClick = onSupportaSviluppatore),
                    style = Testo.etichettaBold, color = tb.accento,
                )
            }
        }

        item {
            Text(
                "Dati da ViaggiaTreno (API non ufficiale RFI). Scioperi dal feed pubblico MIT, aggiornato una volta al giorno. " +
                    "Lo storico ritardi resta solo su questo dispositivo.",
                style = Testo.micro, color = tb.ter,
            )
        }
    }
}

@Composable
private fun Sezione(titolo: String, etichetta: String? = null, content: @Composable () -> Unit) {
    Column {
        Row(Modifier.padding(bottom = 8.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Overline(titolo)
            etichetta?.let { Chip(it, colore = LocalTb.current.ter) }
        }
        GlassCard(Modifier.fillMaxWidth()) { content() }
    }
}

@Composable
private fun RigaToggle(nome: String, desc: String, attivo: Boolean, onChange: (Boolean) -> Unit) {
    val tb = LocalTb.current
    Row(
        Modifier.fillMaxWidth().toggleable(value = attivo, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(nome, style = Testo.corpoMedio, color = tb.tx)
            Text(desc, style = Testo.etichetta, color = tb.sub, modifier = Modifier.padding(top = 2.dp))
        }
        Toggle(attivo)
    }
}
