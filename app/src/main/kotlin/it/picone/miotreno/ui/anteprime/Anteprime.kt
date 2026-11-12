package it.picone.miotreno.ui.anteprime

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import it.picone.miotreno.domain.Semaforo
import it.picone.miotreno.ui.componenti.BottonePrimario
import it.picone.miotreno.ui.componenti.BottoneSecondario
import it.picone.miotreno.ui.componenti.Chip
import it.picone.miotreno.ui.componenti.GlassCard
import it.picone.miotreno.ui.componenti.Icone
import it.picone.miotreno.ui.componenti.StatoVuoto
import it.picone.miotreno.ui.componenti.StatusBadge
import it.picone.miotreno.ui.theme.LocalTb
import it.picone.miotreno.ui.theme.MioTrenoTheme
import it.picone.miotreno.ui.theme.Testo

/** Anteprime dei componenti base: chiaro/scuro e testo grande (1,5×). Solo per Android Studio. */
@Composable
private fun Catalogo() {
    MioTrenoTheme {
        Surface {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusBadge(Semaforo.InOrario, "in orario")
                    StatusBadge(Semaforo.RitardoLieve, "+4 min")
                    StatusBadge(Semaforo.Cancellato, "Cancellato")
                }
                GlassCard(accento = true) {
                    Text("REG 2634 · Milano Centrale", style = Testo.sottotitolo, color = LocalTb.current.tx)
                    Chip("via Rho Fiera", colore = LocalTb.current.accento, pieno = true, modifier = Modifier.padding(top = 8.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BottonePrimario("Segui corsa", onClick = {}, icona = Icone.Treno, modifier = Modifier.weight(1f))
                    BottoneSecondario("15′ prima", onClick = {}, icona = Icone.Campanella, modifier = Modifier.weight(1f))
                }
                StatoVuoto(Icone.Treno, "Nessun treno", "Da Busto Arsizio non risultano treni nelle prossime ore.")
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun CatalogoTemi() = Catalogo()

@Preview(fontScale = 1.5f, name = "Testo grande")
@Composable
private fun CatalogoTestoGrande() = Catalogo()
