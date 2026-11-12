package it.picone.miotreno.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import it.picone.miotreno.domain.ElementoStazione
import it.picone.miotreno.ui.componenti.Icone
import it.picone.miotreno.ui.componenti.conferma
import it.picone.miotreno.ui.componenti.rememberHaptic
import kotlinx.coroutines.delay
import kotlin.coroutines.cancellation.CancellationException

/**
 * Scelta di una stazione: SearchBar M3 aperta a tutto schermo. Recenti a ricerca vuota,
 * risultati sotto mentre si scrive, opzione "vuota" (GPS / nessuna / tutte) in testa.
 *
 * Due modalità di ricerca: filtro in-memory su [stazioni] quando [ricercaLive] è null, oppure
 * ricerca live (debounced) su [ricercaLive] — la ricerca nazionale di partenza/destinazione.
 * Chiudere la barra (freccia o indietro) chiama [onChiudi].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelettoreStazione(
    stazioni: List<ElementoStazione> = emptyList(),
    correnteCodice: String?,
    onScegli: (String?) -> Unit,
    onChiudi: () -> Unit,
    titolo: String = "Cerca la stazione di partenza",
    etichettaOpzioneVuota: String = "Usa la posizione GPS",
    descrizioneOpzioneVuota: String = "stazione più vicina, aggiornata automaticamente",
    mostraIconaOpzioneVuota: Boolean = true,
    mostraOpzioneVuota: Boolean = true,
    ricercaLive: (suspend (String) -> List<ElementoStazione>)? = null,
    /** Mostrate a ricerca vuota: una ricerca non parte mai da una schermata bianca. */
    recenti: List<ElementoStazione> = emptyList(),
) {
    val haptic = rememberHaptic()
    var query by rememberSaveable { mutableStateOf("") }
    var risultatiLive by remember { mutableStateOf<List<ElementoStazione>>(emptyList()) }
    var cercando by remember { mutableStateOf(false) }
    var erroreRicerca by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    // primo uso (nessuna stazione recente): l'unica cosa da fare è scrivere
    if (ricercaLive != null && recenti.isEmpty()) {
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    }

    if (ricercaLive != null) {
        LaunchedEffect(query) {
            val q = query.trim()
            if (q.isEmpty()) {
                risultatiLive = emptyList(); cercando = false; erroreRicerca = false
                return@LaunchedEffect
            }
            cercando = true
            delay(300)
            // un errore di rete non è "nessuna stazione trovata": va detto diversamente
            val esito = runCatching { ricercaLive(q) }
            (esito.exceptionOrNull() as? CancellationException)?.let { throw it }
            erroreRicerca = esito.isFailure
            risultatiLive = esito.getOrDefault(emptyList())
            cercando = false
        }
    }
    val filtrate = when {
        ricercaLive != null -> risultatiLive
        query.isBlank() -> stazioni
        else -> stazioni.filter { it.nome.contains(query.trim(), ignoreCase = true) }
    }
    val scegli = { codice: String? -> haptic.conferma(); onScegli(codice) }

    SearchBar(
        inputField = {
            SearchBarDefaults.InputField(
                query = query,
                onQueryChange = { query = it },
                onSearch = { filtrate.singleOrNull()?.let { scegli(it.codice) } },
                expanded = true,
                onExpandedChange = { if (!it) onChiudi() },
                placeholder = { Text(titolo) },
                leadingIcon = {
                    IconButton(onClick = onChiudi) { Icon(Icone.Indietro, contentDescription = "Indietro") }
                },
                trailingIcon = if (query.isNotEmpty()) {
                    { IconButton(onClick = { query = "" }) { Icon(Icone.Chiudi, contentDescription = "Cancella") } }
                } else {
                    null
                },
                modifier = Modifier.focusRequester(focus),
            )
        },
        expanded = true,
        onExpandedChange = { if (!it) onChiudi() },
    ) {
        LazyColumn {
            if (mostraOpzioneVuota) {
                item(key = "vuota") {
                    Riga(
                        etichettaOpzioneVuota, descrizioneOpzioneVuota,
                        icona = if (mostraIconaOpzioneVuota) Icone.Posizione else null,
                        selezionata = correnteCodice == null,
                    ) { scegli(null) }
                }
            }
            when {
                ricercaLive != null && query.isBlank() && recenti.isEmpty() -> item {
                    Nota("Scrivi il nome di una stazione: le ultime scelte compariranno qui.")
                }
                ricercaLive != null && query.isBlank() -> {
                    item(key = "recenti") { Intestazione("Recenti") }
                    items(recenti, key = { "r-${it.codice}" }) { s ->
                        Riga(s.nome, null, Icone.Orologio, s.codice == correnteCodice) { scegli(s.codice) }
                    }
                }
                ricercaLive != null && cercando -> item { Nota("Cerco…") }
                ricercaLive != null && erroreRicerca -> item {
                    Nota("Ricerca non riuscita: controlla la connessione e riprova.")
                }
                ricercaLive == null && stazioni.isEmpty() -> item { Nota("Carico l'elenco…") }
                filtrate.isEmpty() && query.isNotBlank() -> item { Nota("Nessuna stazione trovata: prova con un altro nome.") }
            }
            items(filtrate, key = { it.codice }) { s ->
                Riga(s.nome, null, null, s.codice == correnteCodice) { scegli(s.codice) }
            }
        }
    }
}

@Composable
private fun Intestazione(testo: String) {
    Text(
        testo, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun Nota(testo: String) {
    Text(
        testo, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(16.dp),
    )
}

@Composable
private fun Riga(
    nome: String,
    sotto: String?,
    icona: ImageVector?,
    selezionata: Boolean,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(nome) },
        supportingContent = sotto?.let { { Text(it) } },
        leadingContent = icona?.let { { Icon(it, contentDescription = null) } },
        trailingContent = if (selezionata) {
            { Icon(Icone.Spunta, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
        } else {
            null
        },
        colors = ListItemDefaults.colors(
            containerColor = if (selezionata) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        ),
        modifier = Modifier
            .semantics {
                role = Role.RadioButton
                selected = selezionata
            }
            .clickable(onClick = onClick),
    )
}
