package it.picone.miotreno.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Colori MioTreno sopra Material 3. Le superfici, il testo e l'accento vengono dal
 * [ColorScheme] (dinamico da Android 12, altrimenti [SchemaChiaro]/[SchemaScuro] dal seme
 * rail blue): [tbDa] li riporta qui con i nomi che usano le schermate.
 *
 * I colori di stato invece sono fissi e non seguono lo sfondo: verde / ambra / arancio / rosso
 * per lo stato del treno, magenta per lo sciopero. Un "in orario" che diventa viola perché lo è
 * lo sfondo perderebbe il suo significato.
 *  - [suStato]: testo sopra un colore di stato pieno.
 *
 * Contrasto: ogni colore di stato è verificato ≥ 4.5:1 (WCAG AA) sulle superfici del seme in
 * `ContrastoTest`, per entrambi i temi. In chiaro sono più scuri del "nominale" apposta.
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
    arancio = Color(0xFF9E340A),
    rosso = Color(0xFFAD2230),
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
    rosso = Color(0xFFFF7A7A),
    sciopero = Color(0xFFF472D0),
    onAccento = Color(0xFF0B1020),
    suStato = Color(0xFF0B1020),
    ombra = Color(0xFF000000),
)

val LocalTb = staticCompositionLocalOf { ChiaroTb }

/** Neutri e accento dal [scheme], stato fisso da [ChiaroTb]/[ScuroTb]. */
fun tbDa(scheme: ColorScheme, scuro: Boolean): TbColors = (if (scuro) ScuroTb else ChiaroTb).copy(
    bg = scheme.surface,
    sf = scheme.surfaceContainerLow,
    sf2 = scheme.surfaceContainerHigh,
    bordo = scheme.outlineVariant,
    bordoForte = scheme.outline,
    tx = scheme.onSurface,
    sub = scheme.onSurfaceVariant,
    ter = scheme.onSurfaceVariant,
    accento = scheme.primary,
    accento2 = scheme.primary,
    accentoSoft = scheme.secondaryContainer,
    onAccento = scheme.onPrimary,
)
