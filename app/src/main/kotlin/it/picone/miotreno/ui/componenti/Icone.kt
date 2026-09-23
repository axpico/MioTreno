package it.picone.miotreno.ui.componenti

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Set di icone line-style, tratto 1.8, angoli arrotondati. Disegnate a mano su griglia 24:
 * ponytail: dieci icone non giustificano material-icons-extended (~10 MB).
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

    val Treno: ImageVector by lazy {
        icona("treno", {
            moveTo(7f, 3.5f); lineTo(17f, 3.5f)
            curveTo(18.5f, 3.5f, 19.5f, 4.5f, 19.5f, 6f); lineTo(19.5f, 15f)
            curveTo(19.5f, 16.5f, 18.5f, 17.5f, 17f, 17.5f); lineTo(7f, 17.5f)
            curveTo(5.5f, 17.5f, 4.5f, 16.5f, 4.5f, 15f); lineTo(4.5f, 6f)
            curveTo(4.5f, 4.5f, 5.5f, 3.5f, 7f, 3.5f); close()
            moveTo(4.5f, 10.5f); lineTo(19.5f, 10.5f)
            moveTo(8f, 14f); lineTo(8.01f, 14f)
            moveTo(16f, 14f); lineTo(16.01f, 14f)
            moveTo(7f, 17.5f); lineTo(5f, 21f)
            moveTo(17f, 17.5f); lineTo(19f, 21f)
        })
    }
    val Statistiche: ImageVector by lazy {
        icona("statistiche", {
            moveTo(4f, 20f); lineTo(20f, 20f)
            moveTo(7f, 16f); lineTo(7f, 11f)
            moveTo(12f, 16f); lineTo(12f, 5f)
            moveTo(17f, 16f); lineTo(17f, 8f)
        })
    }
    val Impostazioni: ImageVector by lazy {
        icona("impostazioni", {
            moveTo(4f, 7f); lineTo(20f, 7f)
            moveTo(4f, 12f); lineTo(20f, 12f)
            moveTo(4f, 17f); lineTo(20f, 17f)
            moveTo(15f, 7f); arcTo(1.6f, 1.6f, 0f, true, true, 15.01f, 7f)
            moveTo(9f, 12f); arcTo(1.6f, 1.6f, 0f, true, true, 9.01f, 12f)
            moveTo(13f, 17f); arcTo(1.6f, 1.6f, 0f, true, true, 13.01f, 17f)
        })
    }
    val Posizione: ImageVector by lazy {
        icona("posizione", {
            moveTo(12f, 21f); curveTo(12f, 21f, 5f, 14.5f, 5f, 10f)
            arcTo(7f, 7f, 0f, true, true, 19f, 10f)
            curveTo(19f, 14.5f, 12f, 21f, 12f, 21f); close()
            moveTo(12f, 10f); arcTo(2.5f, 2.5f, 0f, true, true, 12.01f, 10f)
        })
    }
    val Campanella: ImageVector by lazy {
        icona("campanella", {
            moveTo(6f, 16f); lineTo(6f, 10.5f)
            arcTo(6f, 6f, 0f, true, true, 18f, 10.5f); lineTo(18f, 16f)
            lineTo(20f, 18f); lineTo(4f, 18f); close()
            moveTo(10f, 21f); lineTo(14f, 21f)
        })
    }
    val Indietro: ImageVector by lazy {
        icona("indietro", { moveTo(15f, 5f); lineTo(8f, 12f); lineTo(15f, 19f) })
    }
    val Avanti: ImageVector by lazy {
        icona("avanti", { moveTo(9f, 5f); lineTo(16f, 12f); lineTo(9f, 19f) })
    }
    val Cerca: ImageVector by lazy {
        icona("cerca", {
            moveTo(10.5f, 4f); arcTo(6.5f, 6.5f, 0f, true, true, 10.49f, 4f); close()
            moveTo(15.5f, 15.5f); lineTo(20f, 20f)
        })
    }
    val Chiudi: ImageVector by lazy {
        icona("chiudi", { moveTo(6f, 6f); lineTo(18f, 18f); moveTo(18f, 6f); lineTo(6f, 18f) })
    }
    val Spunta: ImageVector by lazy {
        icona("spunta", { moveTo(5f, 12.5f); lineTo(10f, 17.5f); lineTo(19f, 7f) })
    }
    val Cambio: ImageVector by lazy {
        icona("cambio", {
            moveTo(4f, 8f); lineTo(17f, 8f); moveTo(14f, 5f); lineTo(17f, 8f); lineTo(14f, 11f)
            moveTo(20f, 16f); lineTo(7f, 16f); moveTo(10f, 13f); lineTo(7f, 16f); lineTo(10f, 19f)
        })
    }
    val Sciopero: ImageVector by lazy {
        icona("sciopero", {
            moveTo(12f, 3.5f); lineTo(21f, 19.5f); lineTo(3f, 19.5f); close()
            moveTo(12f, 10f); lineTo(12f, 14f); moveTo(12f, 16.8f); lineTo(12.01f, 16.8f)
        })
    }
    val Cancellato: ImageVector by lazy {
        icona("cancellato", {
            moveTo(12f, 2.5f); lineTo(21.5f, 12f); lineTo(12f, 21.5f); lineTo(2.5f, 12f); close()
            moveTo(9f, 9f); lineTo(15f, 15f); moveTo(15f, 9f); lineTo(9f, 15f)
        })
    }
    val Aggiorna: ImageVector by lazy {
        icona("aggiorna", {
            moveTo(20f, 11f); arcTo(8f, 8f, 0f, true, true, 17.5f, 5.5f)
            moveTo(20f, 4f); lineTo(20f, 8.5f); lineTo(15.5f, 8.5f)
        })
    }
    val Frecciagiu: ImageVector by lazy {
        icona("giu", { moveTo(12f, 5f); lineTo(12f, 19f); moveTo(6f, 13f); lineTo(12f, 19f); lineTo(18f, 13f) })
    }
    val Frecciasu: ImageVector by lazy {
        icona("su", { moveTo(12f, 19f); lineTo(12f, 5f); moveTo(6f, 11f); lineTo(12f, 5f); lineTo(18f, 11f) })
    }

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
