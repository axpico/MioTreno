package it.picone.miotreno.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import it.picone.miotreno.domain.ElementoStazione
import it.picone.miotreno.ui.componenti.BottonePrimario
import it.picone.miotreno.ui.componenti.Icone
import it.picone.miotreno.ui.componenti.ombraMorbida
import it.picone.miotreno.ui.theme.Forme
import it.picone.miotreno.ui.theme.LocalTb
import it.picone.miotreno.ui.theme.Molla
import it.picone.miotreno.ui.theme.Spazio
import it.picone.miotreno.ui.theme.Testo
import kotlinx.coroutines.launch

private enum class PassoOnboarding { Benvenuto, Ads, Destinazione }

/**
 * Primo avvio: nessuna destinazione ancora scelta. Tre passi in sequenza, tutti non chiudibili
 * (niente back): benvenuto → scelta ads → destinazione (riusa [SelettoreStazione]).
 */
@Composable
fun OnboardingScreen(
    onCerca: suspend (String) -> List<ElementoStazione>,
    onScegli: (String) -> Unit,
    onAdsScelte: (abilitate: Boolean) -> Unit,
    onRichiediConsenso: suspend () -> Unit,
) {
    val tb = LocalTb.current
    var passo by remember { mutableStateOf(PassoOnboarding.Benvenuto) }

    Box(Modifier.fillMaxSize().background(tb.bg)) {
        when (passo) {
            PassoOnboarding.Benvenuto -> BenvenutoSlide(onContinua = { passo = PassoOnboarding.Ads })
            PassoOnboarding.Ads -> SceltaAdsSlide(
                onScelta = { abilitaAds ->
                    onAdsScelte(abilitaAds)
                    passo = PassoOnboarding.Destinazione
                },
                onRichiediConsenso = onRichiediConsenso,
            )
            PassoOnboarding.Destinazione -> SelettoreStazione(
                correnteCodice = null,
                onScegli = { codice -> codice?.let(onScegli) },
                onChiudi = {},
                titolo = "Dove vuoi arrivare?",
                overline = "Ultimo passo · 3 di 3",
                mostraOpzioneVuota = false,
                ricercaLive = onCerca,
            )
        }
    }
}

/** Impaginazione comune: indicatore passi in alto, illustrazione, testo, CTA nella zona del pollice. */
@Composable
private fun Passo(
    indice: Int,
    icona: ImageVector,
    titolo: String,
    testo: String,
    contenuto: @Composable () -> Unit = {},
    cta: @Composable () -> Unit,
) {
    val tb = LocalTb.current
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 24.dp, vertical = Spazio.l)) {
        IndicatorePassi(indice, totale = 3)
        Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center) {
            Illustrazione(icona)
            Text(titolo, style = Testo.titolo, color = tb.tx, modifier = Modifier.padding(top = 32.dp))
            Text(testo, style = Testo.corpo, color = tb.sub, modifier = Modifier.padding(top = Spazio.s))
            contenuto()
        }
        cta()
    }
}

@Composable
private fun IndicatorePassi(attivo: Int, totale: Int) {
    val tb = LocalTb.current
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(totale) { i ->
            val larghezza by animateDpAsState(if (i == attivo) 24.dp else 8.dp, Molla.ui(), label = "passo$i")
            Box(
                Modifier.height(8.dp).width(larghezza).clip(CircleShape)
                    .background(if (i <= attivo) tb.accento else tb.bordoForte),
            )
        }
    }
}

/** Illustrazione senza asset: icona dentro due dischi tinti dell'accento. */
@Composable
private fun Illustrazione(icona: ImageVector) {
    val tb = LocalTb.current
    Box(
        Modifier.size(128.dp).clip(CircleShape).background(tb.accentoSoft),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(80.dp).ombraMorbida(tb, CircleShape, 12.dp, tb.accento).clip(CircleShape).background(tb.accento),
            contentAlignment = Alignment.Center,
        ) { Icon(icona, contentDescription = null, tint = tb.suStato, modifier = Modifier.size(40.dp)) }
    }
}

@Composable
private fun BenvenutoSlide(onContinua: () -> Unit) {
    Passo(
        indice = 0,
        icona = Icone.Treno,
        titolo = "Benvenuto su MioTreno",
        testo = "I treni per la tua destinazione, in tempo reale: ritardi, binari, avvisi sciopero " +
            "e lo storico dei ritardi, tutto sul tuo dispositivo.",
        cta = { BottonePrimario("Iniziamo", onContinua, modifier = Modifier.fillMaxWidth(), icona = Icone.Avanti) },
    )
}

@Composable
private fun SceltaAdsSlide(onScelta: (Boolean) -> Unit, onRichiediConsenso: suspend () -> Unit) {
    var caricamento by remember { mutableStateOf(false) }
    var conAds by remember { mutableStateOf<Boolean?>(null) }
    val scope = rememberCoroutineScope()

    Passo(
        indice = 1,
        icona = Icone.Spunta,
        titolo = "Vuoi supportare MioTreno?",
        testo = "L'app resta gratuita in entrambi i casi. Puoi cambiare idea in ogni momento dalle Impostazioni.",
        contenuto = {
            Column(Modifier.padding(top = Spazio.xl), verticalArrangement = Arrangement.spacedBy(Spazio.m)) {
                OpzioneAds("Sì, con pubblicità leggere", "Un banner in fondo alla lista treni.", conAds == true) { conAds = true }
                OpzioneAds("No, niente pubblicità", "Nessun banner né interstitial.", conAds == false) { conAds = false }
            }
        },
        cta = {
            BottonePrimario(
                "Continua",
                onClick = {
                    val scelta = conAds ?: return@BottonePrimario
                    if (caricamento) return@BottonePrimario
                    if (!scelta) {
                        onScelta(false)
                        return@BottonePrimario
                    }
                    caricamento = true
                    scope.launch {
                        onRichiediConsenso()
                        onScelta(true)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                icona = Icone.Avanti,
                colore = if (conAds == null) LocalTb.current.ter else LocalTb.current.accento,
            )
        },
    )
}

/** Scelta come card selezionabile: radio a sinistra, bordo accento quando scelta. */
@Composable
private fun OpzioneAds(titolo: String, sotto: String, selezionata: Boolean, onClick: () -> Unit) {
    val tb = LocalTb.current
    val bordo by animateColorAsState(if (selezionata) tb.accento else tb.bordoForte, Molla.piatta(), label = "bordoAds")
    Row(
        Modifier
            .fillMaxWidth()
            .clip(Forme.card)
            .background(if (selezionata) tb.accentoSoft else tb.sf)
            .border(if (selezionata) 2.dp else 1.dp, bordo, Forme.card)
            .selectable(selected = selezionata, role = Role.RadioButton, onClick = onClick)
            .padding(Spazio.l),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spazio.m),
    ) {
        Box(
            Modifier.size(24.dp).clip(CircleShape).border(2.dp, bordo, CircleShape),
            contentAlignment = Alignment.Center,
        ) { if (selezionata) Box(Modifier.size(12.dp).clip(CircleShape).background(tb.accento)) }
        Column(Modifier.weight(1f)) {
            Text(titolo, style = Testo.corpoMedio, color = tb.tx)
            Text(sotto, style = Testo.etichetta, color = tb.sub)
        }
    }
}
