package it.picone.miotreno.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Palette MioTreno — "Rail blue": chiaro e scuro, segue il sistema.
 *
 * 60/30/10: superfici neutre (60%), testo navy/bianco a opacità decrescente (30%),
 * un solo blu come accento (10%) per tutto ciò che è interattivo o "tuo" (treno seguito).
 *  - semaforo: verde / ambra / arancio / rosso solo per lo stato del treno, mai decorativi.
 *  - sciopero: magenta, un'altra categoria di informazione, non un ritardo.
 *  - [ombra]: tinta delle ombre (navy, mai grigio puro); in scuro le ombre non si vedono e
 *    la separazione la fa [bordo].
 *  - [suStato]: testo sopra un colore pieno (bottone primario, badge cancellato).
 *
 * Contrasto: ogni coppia testo/superficie è verificata ≥ 4.5:1 (WCAG AA) in `ContrastoTest`,
 * per entrambi i temi. In chiaro i colori di stato sono più scuri del "nominale" apposta.
 */
data class TbColors(
    val scuro: Boolean,
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
    val suStato: Color,
    val ombra: Color,
)

val ChiaroTb = TbColors(
    scuro = false,
    bg = Color(0xFFF5F7FB),
    sf = Color(0xFFFFFFFF),
    sf2 = Color(0xFFEEF2F8),
    bordo = Color(0x140E1526),
    bordoForte = Color(0x290E1526),
    tx = Color(0xFF0E1526),
    sub = Color(0xB80E1526),
    ter = Color(0x990E1526),
    accento = Color(0xFF2448E0),
    accento2 = Color(0xFF1F3DB8),
    accentoSoft = Color(0x142448E0),
    verde = Color(0xFF146B3C),
    ambra = Color(0xFF7A5300),
    arancio = Color(0xFFA6380C),
    rosso = Color(0xFFB82530),
    sciopero = Color(0xFF9E1F88),
    onAccento = Color(0xFFFFFFFF),
    suStato = Color(0xFFFFFFFF),
    ombra = Color(0xFF1B2A5A),
)

val ScuroTb = TbColors(
    scuro = true,
    bg = Color(0xFF0B1020),
    sf = Color(0xFF151B2E),
    sf2 = Color(0xFF1D2540),
    bordo = Color(0x1AEEF2FF),
    bordoForte = Color(0x33EEF2FF),
    tx = Color(0xFFEEF2FF),
    sub = Color(0xB8EEF2FF),
    ter = Color(0x99EEF2FF),
    accento = Color(0xFF7B96FF),
    accento2 = Color(0xFF9DB0FF),
    accentoSoft = Color(0x297B96FF),
    verde = Color(0xFF3DD68C),
    ambra = Color(0xFFF5B83D),
    arancio = Color(0xFFFF8A4C),
    rosso = Color(0xFFFF6B6B),
    sciopero = Color(0xFFF472D0),
    onAccento = Color(0xFF0B1020),
    suStato = Color(0xFF0B1020),
    ombra = Color(0xFF000000),
)

val LocalTb = staticCompositionLocalOf { ChiaroTb }
