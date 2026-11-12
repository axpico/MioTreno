package it.picone.miotreno.ui.componenti

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import it.picone.miotreno.R

/**
 * Icone dell'app. Le generiche sono Material Symbols Rounded scaricati come vettori (niente
 * material-icons-extended, ~10 MB per una ventina di icone); quelle dei tipi di treno sono
 * disegnate a mano su griglia 24, perché Symbols non distingue regionale e alta velocità.
 */
object Icone {
    private fun icona(nome: String, disegna: PathBuilder.() -> Unit, riempi: Boolean = false) =
        ImageVector.Builder(nome, 24.dp, 24.dp, 24f, 24f).apply {
            path(
                fill = if (riempi) SolidColor(Color.White) else null,
                stroke = if (riempi) null else SolidColor(Color.White),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = disegna,
            )
        }.build()

    // Icone generiche: Material Symbols Rounded, come drawable vettoriali (res/drawable/sym_*).
    val Treno: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.sym_train)
    val Statistiche: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.sym_bar_chart)
    val Impostazioni: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.sym_settings)
    val Posizione: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.sym_location_on)
    val Campanella: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.sym_notifications)
    val Indietro: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.sym_arrow_back)
    val Avanti: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.sym_chevron_right)
    val Freccia: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.sym_arrow_forward)
    val Cerca: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.sym_search)
    val Chiudi: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.sym_close)
    val Spunta: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.sym_check)
    val Cambio: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.sym_swap_horiz)
    val Sciopero: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.sym_campaign)
    val Cancellato: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.sym_cancel)
    val Aggiorna: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.sym_refresh)
    val Orologio: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.sym_schedule)
    val Frecciagiu: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.sym_arrow_drop_down)
    val Frecciasu: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.sym_arrow_drop_up)

    /** Regionale: musetto squadrato, due finestrini — il treno "lento" della flotta. */
    val TrenoRegionale: ImageVector by lazy {
        icona("treno_regionale", {
            moveTo(6f, 5.5f); lineTo(16f, 5.5f)
            curveTo(17.5f, 5.5f, 18.5f, 6.5f, 18.5f, 8f); lineTo(18.5f, 15f)
            curveTo(18.5f, 16.5f, 17.5f, 17.5f, 16f, 17.5f); lineTo(6f, 17.5f)
            curveTo(4.5f, 17.5f, 3.5f, 16.5f, 3.5f, 15f); lineTo(3.5f, 8f)
            curveTo(3.5f, 6.5f, 4.5f, 5.5f, 6f, 5.5f); close()
            moveTo(6.5f, 10.5f); lineTo(6.5f, 13.5f); moveTo(15.5f, 10.5f); lineTo(15.5f, 13.5f)
            moveTo(6f, 17.5f); lineTo(4.5f, 21f); moveTo(16f, 17.5f); lineTo(17.5f, 21f)
        })
    }

    /** Veloce (RV/IC/EC): musetto smussato, un solo finestrino lungo. */
    val TrenoVeloce: ImageVector by lazy {
        icona("treno_veloce", {
            moveTo(5f, 6.5f); lineTo(15f, 6.5f)
            curveTo(17f, 6.5f, 19f, 8.5f, 19f, 11f)
            curveTo(19f, 13.5f, 17f, 15.5f, 15f, 15.5f); lineTo(5f, 15.5f)
            curveTo(4f, 15.5f, 3.5f, 15f, 3.5f, 14f); lineTo(3.5f, 8f)
            curveTo(3.5f, 7f, 4f, 6.5f, 5f, 6.5f); close()
            moveTo(6.5f, 10.5f); lineTo(14f, 10.5f)
            moveTo(5f, 15.5f); lineTo(4f, 19.5f); moveTo(15.5f, 15.5f); lineTo(17.5f, 19.5f)
        })
    }

    /** Alta velocità (FR/FA/ES): muso a punta, freccia — il treno "veloce" della flotta. */
    val TrenoAltaVelocita: ImageVector by lazy {
        icona("treno_av", {
            moveTo(3.5f, 15f); lineTo(3.5f, 9f)
            curveTo(3.5f, 7.5f, 4.5f, 6.5f, 6f, 6.5f); lineTo(13.5f, 6.5f)
            lineTo(20.5f, 10.5f); lineTo(13.5f, 14.5f)
            lineTo(6f, 14.5f)
            curveTo(4.5f, 14.5f, 3.5f, 13.5f, 3.5f, 12f); close()
            moveTo(6.5f, 10.5f); lineTo(11.5f, 10.5f)
            moveTo(5f, 15.5f); lineTo(4f, 19.5f); moveTo(14f, 15.5f); lineTo(15.5f, 19.5f)
        })
    }
}
