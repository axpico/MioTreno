package it.picone.miotreno.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Palette MioTreno — "Departure Board": nero vero, superfici piatte, ambra come unico accento.
 * Solo tema scuro, per scelta: un tabellone partenze non ha una modalità chiara.
 *
 * Tre famiglie di colore con ruoli separati:
 *  - superfici e testo: nero vero e bianco caldo (come la vernice di un tabellone a palette)
 *    a opacità decrescente.
 *  - accento: un solo ambra pieno per tutto ciò che è interattivo o "tuo" (treno seguito).
 *    [accento2] è lo stesso tono più chiaro, per una seconda gerarchia — mai un'altra tinta.
 *    Coincide deliberatamente con [ambra] del semaforo: l'ambra è *il* colore di un tabellone
 *    partenze reale, quindi qui si legge come "transito", non come coincidenza scomoda.
 *  - semaforo: verde / ambra / arancio / rosso solo per lo stato del treno (Duolingo)
 * Lo sciopero ha un colore suo, fucsia: non è un ritardo, è un'altra categoria di informazione.
 *
 * Niente gradienti, vetro o glow: superfici piatte, bordi sottili come dividers, nessuna ombra
 * decorativa. La gerarchia viene dalla tipografia e dai bordi, non dall'elevazione.
 *
 * Contrasto: ogni coppia testo/superficie usata nell'app è verificata ≥ 4.5:1 (WCAG AA) in
 * `ContrastoTest`. Se cambi un colore qui, il test dice subito cosa si è rotto.
 */
data class TbColors(
    val bg: Color,
    val sf: Color,
    val sf2: Color,
    val bordo: Color,
    val bordoForte: Color,
    val tx: Color,
    val sub: Color,
    val ter: Color,
    val accento: Color,
    val accento2: Color,
    val accentoSoft: Color,
    val verde: Color,
    val ambra: Color,
    val arancio: Color,
    val rosso: Color,
    val sciopero: Color,
    val onAccento: Color,
)

val ScuroTb = TbColors(
    bg = Color(0xFF000000),
    sf = Color(0xFF141416),
    sf2 = Color(0xFF1E1E21),
    bordo = Color(0x14FFFFFF),
    bordoForte = Color(0x29FFFFFF),
    tx = Color(0xEBFFF9F0),
    sub = Color(0x8CFFF9F0),
    ter = Color(0x7AFFF9F0),
    accento = Color(0xFFFFB300),
    accento2 = Color(0xFFFFCC5C),
    accentoSoft = Color(0x20FFB300),
    verde = Color(0xFF3ECF6E),
    ambra = Color(0xFFFFB300),
    arancio = Color(0xFFFF6B35),
    rosso = Color(0xFFFF3B30),
    sciopero = Color(0xFFFF4FD8),
    onAccento = Color(0xFF1A1100),
)

val LocalTb = staticCompositionLocalOf { ScuroTb }
