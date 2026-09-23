package it.picone.miotreno.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import it.picone.miotreno.domain.ElementoStazione
import it.picone.miotreno.ui.componenti.BottonePrimario
import it.picone.miotreno.ui.componenti.BottoneSecondario
import it.picone.miotreno.ui.componenti.Icone
import it.picone.miotreno.ui.componenti.Overline
import it.picone.miotreno.ui.theme.LocalTb
import it.picone.miotreno.ui.theme.Spazio
import it.picone.miotreno.ui.theme.Testo
import kotlinx.coroutines.launch

private enum class PassoOnboarding { Benvenuto, Ads, Destinazione }

/**
 * Primo avvio: nessuna destinazione ancora scelta. Tre passi in sequenza, tutti non chiudibili
 * (niente back): benvenuto → scelta ads → destinazione (riusa [SelettoreStazione] come oggi).
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
                overline = "Ultimo passo",
                mostraOpzioneVuota = false,
                ricercaLive = onCerca,
            )
        }
    }
}

@Composable
private fun BenvenutoSlide(onContinua: () -> Unit) {
    val tb = LocalTb.current
    Column(
        Modifier.fillMaxSize().padding(Spazio.pagina),
        verticalArrangement = Arrangement.Bottom,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Overline("Benvenuto")
            Text("MioTreno", style = Testo.display, color = tb.tx, modifier = Modifier.padding(top = Spazio.s))
            Text(
                "I treni per la tua destinazione, in tempo reale: ritardi, binari, avvisi sciopero " +
                    "e lo storico dei ritardi, tutto sul tuo dispositivo.",
                style = Testo.corpo, color = tb.sub, modifier = Modifier.padding(top = Spazio.l),
            )
        }
        BottonePrimario("Continua", onContinua, modifier = Modifier.fillMaxWidth(), icona = Icone.Avanti)
    }
}

@Composable
private fun SceltaAdsSlide(onScelta: (Boolean) -> Unit, onRichiediConsenso: suspend () -> Unit) {
    val tb = LocalTb.current
    var caricamento by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        Modifier.fillMaxSize().padding(Spazio.pagina),
        verticalArrangement = Arrangement.Bottom,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Overline("Sostieni l'app")
            Text(
                "Vuoi supportare MioTreno con pubblicità leggere?",
                style = Testo.titolo, color = tb.tx, modifier = Modifier.padding(top = Spazio.s),
            )
            Text(
                "L'app resta gratuita in entrambi i casi. Puoi cambiare idea in ogni momento dalle Impostazioni.",
                style = Testo.corpo, color = tb.sub, modifier = Modifier.padding(top = Spazio.l),
            )
        }
        BottonePrimario(
            "Sì, voglio le pubblicità",
            onClick = {
                if (!caricamento) {
                    caricamento = true
                    scope.launch {
                        onRichiediConsenso()
                        onScelta(true)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        BottoneSecondario(
            "No, niente pubblicità",
            onClick = { if (!caricamento) onScelta(false) },
            modifier = Modifier.fillMaxWidth().padding(top = Spazio.s),
        )
    }
}
