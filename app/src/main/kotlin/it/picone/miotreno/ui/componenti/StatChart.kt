package it.picone.miotreno.ui.componenti

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import it.picone.miotreno.domain.VoceStat
import it.picone.miotreno.ui.theme.LocalTb
import it.picone.miotreno.ui.theme.Molla
import it.picone.miotreno.ui.theme.Testo

/** Barre sottili stile fintech: nessun 3D, nessuna ombra, colore per soglia, molla in entrata. */
@Composable
fun StatChart(
    voci: List<VoceStat>,
    modifier: Modifier = Modifier,
    altezza: Dp = 88.dp,
    colore: (Double) -> Color,
    formato: (Double) -> String,
) {
    val tb = LocalTb.current
    val max = voci.maxOfOrNull { it.valore }?.coerceAtLeast(1.0) ?: 1.0
    val entrata = remember { Animatable(0f) }
    LaunchedEffect(voci) { entrata.snapTo(0f); entrata.animateTo(1f, Molla.ui()) }

    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            voci.forEach { v ->
                Text(
                    if (v.rilevazioni > 0) formato(v.valore) else "–",
                    Modifier.weight(1f), style = Testo.micro, color = tb.sub, textAlign = TextAlign.Center,
                )
            }
        }
        Canvas(Modifier.fillMaxWidth().height(altezza).padding(top = 6.dp, bottom = 6.dp)) {
            val n = voci.size.coerceAtLeast(1)
            val gap = 6.dp.toPx()
            val larghezza = (size.width - gap * (n - 1)) / n
            val spessore = (larghezza * 0.45f).coerceAtMost(22.dp.toPx())
            voci.forEachIndexed { i, v ->
                val x = i * (larghezza + gap) + (larghezza - spessore) / 2
                val h = (size.height * (v.valore / max).toFloat() * entrata.value).coerceAtLeast(3.dp.toPx())
                drawRoundRect(tb.sf2.copy(alpha = 0.7f), Offset(x, 0f), Size(spessore, size.height), CornerRadius(spessore / 2))
                if (v.rilevazioni == 0) return@forEachIndexed
                drawRoundRect(
                    Brush.verticalGradient(listOf(colore(v.valore), colore(v.valore).copy(alpha = 0.55f))),
                    Offset(x, size.height - h), Size(spessore, h), CornerRadius(spessore / 2),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            voci.forEach { v ->
                Text(v.etichetta, Modifier.weight(1f), style = Testo.micro, color = tb.ter, textAlign = TextAlign.Center)
            }
        }
    }
}

/** Sparkline: linea sottile con area sfumata sotto, per l'andamento nel tempo. */
@Composable
fun Sparkline(valori: List<Double>, modifier: Modifier = Modifier, colore: Color = LocalTb.current.accento) {
    val entrata = remember { Animatable(0f) }
    LaunchedEffect(valori) { entrata.snapTo(0f); entrata.animateTo(1f, Molla.piatta()) }
    Canvas(modifier.fillMaxWidth().height(64.dp)) {
        if (valori.size < 2) return@Canvas
        val max = valori.max().coerceAtLeast(1.0)
        val passo = size.width / (valori.size - 1)
        val punti = valori.mapIndexed { i, v ->
            Offset(i * passo, size.height - (size.height * 0.85f * (v / max).toFloat()) - 4.dp.toPx())
        }
        val linea = Path().apply {
            moveTo(punti[0].x, punti[0].y)
            for (i in 1 until punti.size) {
                val p0 = punti[i - 1]; val p1 = punti[i]
                val cx = (p0.x + p1.x) / 2
                cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
            }
        }
        val area = Path().apply {
            addPath(linea); lineTo(size.width, size.height); lineTo(0f, size.height); close()
        }
        clipRect(right = size.width * entrata.value) {
            drawPath(area, Brush.verticalGradient(listOf(colore.copy(alpha = 0.28f), Color.Transparent)))
            drawPath(linea, colore, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}

/** Riga barra orizzontale per classifiche (per treno). */
@Composable
fun BarraOrizzontale(v: VoceStat, max: Double, colore: Color, formato: (Double) -> String, modifier: Modifier = Modifier) {
    val tb = LocalTb.current
    val entrata = remember { Animatable(0f) }
    LaunchedEffect(v) { entrata.snapTo(0f); entrata.animateTo(1f, Molla.ui()) }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.fillMaxWidth(0.3f)) {
            Text(v.etichetta, style = Testo.etichettaBold, color = tb.tx)
            Text(v.sottotitolo, style = Testo.micro, color = tb.ter)
        }
        Canvas(Modifier.weight(1f).height(8.dp)) {
            drawRoundRect(tb.sf2, cornerRadius = CornerRadius(4.dp.toPx()))
            val w = size.width * (v.valore / max.coerceAtLeast(1.0)).toFloat() * entrata.value
            drawRoundRect(colore, size = Size(w.coerceAtLeast(3.dp.toPx()), size.height), cornerRadius = CornerRadius(4.dp.toPx()))
        }
        Text(formato(v.valore), style = Testo.numeroPiccolo, color = colore, textAlign = TextAlign.End)
    }
}
