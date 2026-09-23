package it.picone.miotreno.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import it.picone.miotreno.domain.ElementoStazione
import it.picone.miotreno.ui.componenti.Icone
import it.picone.miotreno.ui.componenti.Overline
import it.picone.miotreno.ui.componenti.conferma
import it.picone.miotreno.ui.componenti.rememberHaptic
import it.picone.miotreno.ui.theme.Forme
import it.picone.miotreno.ui.theme.LocalTb
import it.picone.miotreno.ui.theme.Molla
import it.picone.miotreno.ui.theme.Spazio
import it.picone.miotreno.ui.theme.Testo
import kotlinx.coroutines.delay

/**
 * Foglio di scelta manuale della stazione: ricerca per nome, "Usa GPS" in testa.
 *
 * Due modalità di ricerca: filtro in-memory su [stazioni] (elenco già scaricato, es. la cache
 * regionale) quando [ricercaLive] è null, oppure ricerca live (debounced) su [ricercaLive]
 * quando presente — usata per la ricerca nazionale di partenza/destinazione.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelettoreStazione(
    stazioni: List<ElementoStazione> = emptyList(),
    correnteCodice: String?,
    onScegli: (String?) -> Unit,
    onChiudi: () -> Unit,
    titolo: String = "Scegli la stazione",
    overline: String = "Stazione di partenza",
    etichettaOpzioneVuota: String = "Usa la posizione GPS",
    descrizioneOpzioneVuota: String = "stazione più vicina, aggiornata automaticamente",
    mostraIconaOpzioneVuota: Boolean = true,
    mostraOpzioneVuota: Boolean = true,
    ricercaLive: (suspend (String) -> List<ElementoStazione>)? = null,
) {
    val tb = LocalTb.current
    val haptic = rememberHaptic()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    var risultatiLive by remember { mutableStateOf<List<ElementoStazione>>(emptyList()) }
    var cercando by remember { mutableStateOf(false) }

    if (ricercaLive != null) {
        LaunchedEffect(query) {
            val q = query.trim()
            if (q.isEmpty()) {
                risultatiLive = emptyList(); cercando = false
                return@LaunchedEffect
            }
            cercando = true
            delay(300)
            risultatiLive = runCatching { ricercaLive(q) }.getOrDefault(emptyList())
            cercando = false
        }
    }
    val filtrate = when {
        ricercaLive != null -> risultatiLive
        query.isBlank() -> stazioni
        else -> stazioni.filter { it.nome.contains(query.trim(), ignoreCase = true) }
    }

    ModalBottomSheet(
        onDismissRequest = onChiudi,
        sheetState = sheet,
        containerColor = tb.sf,
        contentColor = tb.tx,
        shape = Forme.card,
        dragHandle = {
            Box(Modifier.padding(top = 12.dp, bottom = 4.dp).width(36.dp).height(4.dp).clip(Forme.chip).background(tb.bordoForte))
        },
    ) {
        Column(Modifier.fillMaxHeight(0.9f).padding(horizontal = Spazio.pagina)) {
            Overline(overline)
            Text(titolo, style = Testo.titolo, color = tb.tx, modifier = Modifier.padding(bottom = 12.dp))

            Row(
                Modifier.fillMaxWidth().clip(Forme.pillola).background(tb.sf2).border(1.dp, tb.bordo, Forme.pillola)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icone.Cerca, contentDescription = null, tint = tb.ter, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                BasicTextField(
                    value = query, onValueChange = { query = it },
                    textStyle = Testo.corpo.copy(color = tb.tx), cursorBrush = SolidColor(tb.accento),
                    singleLine = true, modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        if (query.isEmpty()) Text("Cerca per nome", style = Testo.corpo, color = tb.ter)
                        inner()
                    },
                )
                if (query.isNotEmpty()) {
                    Icon(
                        Icone.Chiudi, contentDescription = "Cancella", tint = tb.sub,
                        modifier = Modifier.size(18.dp).clickable { query = "" },
                    )
                }
            }

            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (mostraOpzioneVuota) {
                    item(key = "gps") {
                        Riga(
                            nome = etichettaOpzioneVuota, sotto = descrizioneOpzioneVuota,
                            icona = mostraIconaOpzioneVuota, selezionata = correnteCodice == null,
                        ) { haptic.conferma(); onScegli(null) }
                    }
                }
                if (ricercaLive != null) {
                    if (query.isBlank()) {
                        item {
                            Text(
                                "Cerca una stazione per nome.", style = Testo.etichetta,
                                color = tb.sub, modifier = Modifier.padding(12.dp),
                            )
                        }
                    } else if (cercando) {
                        item {
                            Text("Cerco…", style = Testo.etichetta, color = tb.sub, modifier = Modifier.padding(12.dp))
                        }
                    } else if (filtrate.isEmpty()) {
                        item { NessunaStazioneTrovata(tb) }
                    }
                } else {
                    if (stazioni.isEmpty()) {
                        item {
                            Text("Carico l'elenco…", style = Testo.etichetta, color = tb.sub, modifier = Modifier.padding(12.dp))
                        }
                    }
                    if (stazioni.isNotEmpty() && filtrate.isEmpty()) item { NessunaStazioneTrovata(tb) }
                }
                items(filtrate, key = { it.codice }) { s ->
                    Riga(s.nome, "", icona = false, selezionata = s.codice == correnteCodice) { haptic.conferma(); onScegli(s.codice) }
                }
            }
        }
    }
}

@Composable
private fun NessunaStazioneTrovata(tb: it.picone.miotreno.ui.theme.TbColors) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 36.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icone.Cerca, contentDescription = null, tint = tb.ter, modifier = Modifier.size(28.dp))
        Text("Nessuna stazione trovata", style = Testo.sottotitolo, color = tb.tx, modifier = Modifier.padding(top = 10.dp))
        Text("Prova con un altro nome.", style = Testo.etichetta, color = tb.sub)
    }
}

@Composable
private fun Riga(nome: String, sotto: String, icona: Boolean, selezionata: Boolean, onClick: () -> Unit) {
    val tb = LocalTb.current
    val sfondo by animateColorAsState(
        if (selezionata) tb.accentoSoft else androidx.compose.ui.graphics.Color.Transparent,
        Molla.piatta(), label = "sfondoRiga",
    )
    Row(
        Modifier.fillMaxWidth().clip(Forme.cardPiccola)
            .background(sfondo)
            .semantics {
                role = Role.RadioButton
                contentDescription = if (selezionata) "$nome, selezionata" else nome
            }
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icona) Icon(Icone.Posizione, contentDescription = null, tint = tb.accento2, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Text(nome, style = Testo.corpoMedio, color = tb.tx)
            if (sotto.isNotBlank()) Text(sotto, style = Testo.micro, color = tb.ter)
        }
        if (selezionata) {
            Icon(Icone.Spunta, contentDescription = null, tint = tb.accento, modifier = Modifier.size(20.dp))
        }
    }
}
