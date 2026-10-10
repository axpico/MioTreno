#!/usr/bin/env bash
# Genera la grafica della scheda Play Store a partire dalle catture reali in raw/.
#
#   ./fastlane/store_assets/genera.sh
#
# Serve: ImageMagick 7 (magick), rsvg-convert, uv (per fontTools, solo per estrarre
# Atkinson Bold/Medium dal font variabile dell'app: ImageMagick non sa scegliere un peso).
# Le catture in raw/ sono screenshot veri del telefono (1084x2412, status bar in demo mode):
# per rifarle vedi RELEASE.md, sezione "Grafica".
set -euo pipefail

QUI="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$QUI/../.." && pwd)"
RAW="$QUI/raw"
OUT="$REPO/fastlane/metadata/android/it-IT/images"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

BLU="#2448E0"   # rail blue, lo stesso dell'icona
ALONE="#3A5CF0"

mkdir -p "$OUT/phoneScreenshots"
rm -f "$OUT"/phoneScreenshots/*.png

# ── font: istanze statiche dal variabile dell'app ───────────────────────────────
for peso in 700 500; do
  uv run -q --no-project --with fonttools fonttools varLib.instancer \
    "$REPO/app/src/main/res/font/atkinson_next.ttf" "wght=$peso" -o "$TMP/atk$peso.ttf" >/dev/null 2>&1
done
BOLD="$TMP/atk700.ttf"
MEDIUM="$TMP/atk500.ttf"

# ── icona 512x512: gli stessi path dei vector launcher (ic_launcher_bg/fg.xml) ──────
cat > "$TMP/icona.svg" <<SVG
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="512" height="512">
  <path fill="$BLU" d="M0,0h108v108h-108z"/>
  <circle fill="$ALONE" cx="54" cy="54" r="34"/>
  <path fill="#FFFFFF" d="M46,30h16a10,10 0 0 1 10,10v24a8,8 0 0 1 -8,8h-20a8,8 0 0 1 -8,-8v-24a10,10 0 0 1 10,-10z"/>
  <path fill="$BLU" d="M45,36h18a4,4 0 0 1 4,4v10a2,2 0 0 1 -2,2h-22a2,2 0 0 1 -2,-2v-10a4,4 0 0 1 4,-4z"/>
  <circle fill="$BLU" cx="44" cy="63" r="3"/>
  <circle fill="$BLU" cx="64" cy="63" r="3"/>
  <path stroke="#FFFFFF" stroke-width="3.5" stroke-linecap="round" fill="none" d="M44,76L38,85 M64,76L70,85"/>
</svg>
SVG
rsvg-convert -w 512 -h 512 "$TMP/icona.svg" -o "$TMP/icona.png"
magick "$TMP/icona.png" PNG32:"$OUT/icon.png"

# solo il treno, per la feature graphic
sed -e "s|<path fill=\"$BLU\" d=\"M0,0h108v108h-108z\"/>||" -e "s|<circle fill=\"$ALONE\" cx=\"54\" cy=\"54\" r=\"34\"/>||" \
  "$TMP/icona.svg" > "$TMP/treno.svg"
rsvg-convert -w 200 -h 200 "$TMP/treno.svg" -o "$TMP/treno.png"

# ── utilità ──────────────────────────────────────────────────────────────────
# arrotonda: angoli tondi + ombra morbida, su sfondo trasparente
arrotonda() { # in out raggio
  local in="$1" out="$2" r="$3"
  local w h
  w=$(magick identify -format %w "$in"); h=$(magick identify -format %h "$in")
  magick "$in" \( -size "${w}x${h}" xc:none -fill white -draw "roundrectangle 0,0,$((w - 1)),$((h - 1)),$r,$r" \) \
    -alpha set -compose DstIn -composite -compose over \
    \( +clone -background "#0B1640" -shadow 45x22+0+18 \) +swap -background none -layers merge +repage "$out"
}

# tela 1080x1920 rail blue, alone decorativo, didascalia in alto
tela() { # out didascalia
  magick -size 1080x1920 "xc:$BLU" \
    -fill "$ALONE" -draw "circle 980,260 980,760" \
    \( -size 940x300 -background none -fill white -font "$BOLD" -pointsize 70 -interline-spacing 6 \
       -gravity center "caption:$2" \) -gravity north -geometry +0+90 -composite "$1"
}

# screenshot completo: ridotto a 1440 di altezza sotto la didascalia
schermo() { # n nome raw didascalia
  local n="$1" nome="$2" raw="$3" testo="$4"
  tela "$TMP/tela.png" "$testo"
  magick "$RAW/$raw" -resize x1440 "$TMP/s.png"
  arrotonda "$TMP/s.png" "$TMP/s_r.png" 44
  magick "$TMP/tela.png" "$TMP/s_r.png" -gravity north -geometry +0+420 -composite \
    PNG24:"$OUT/phoneScreenshots/${n}_${nome}.png"
}

# Ritaglia un elemento dell'interfaccia sui suoi bordi esatti (misurati sui pixel delle
# catture: vedi RELEASE.md) e ne arrotonda gli angoli con il suo stesso raggio, così non
# resta nulla dello sfondo dietro: né la tendina, né lo sfondo del launcher.
pezzo() { # raw geometria raggio larghezza out
  magick "$RAW/$1" -crop "$2" +repage "$TMP/p.png"
  arrotonda "$TMP/p.png" "$TMP/p_r.png" "$3"
  magick "$TMP/p_r.png" -resize "$4x" "$5"
}

# impila dei pezzi già pronti, centrati, sotto la didascalia; se non ci stanno in altezza
# vengono ridotti tutti insieme (stessa scala per tutti: le proporzioni restano vere)
impila() { # n nome didascalia pezzo...
  local n="$1" nome="$2" testo="$3"; shift 3
  tela "$TMP/tela.png" "$testo"
  magick -background none "$@" -gravity center -append -resize "1000x1440>" "$TMP/pila.png"
  magick "$TMP/tela.png" "$TMP/pila.png" -gravity north -geometry +0+410 -composite \
    PNG24:"$OUT/phoneScreenshots/${n}_${nome}.png"
}

# distanziatore trasparente fra i pezzi di una pila
spazio() { magick -size "1x$1" xc:none "$2"; }

# ── screenshot telefono (1080x1920, 9:16) ────────────────────────────────────
schermo 1 home "home_dark.png" "Il tuo treno,
a colpo d'occhio"
schermo 2 dettaglio "detail_dark.png" "Fermata per fermata,
dal punto in cui sali"

# 3: il chip in status bar, la notifica Live Update e lo stesso conto nell'app
spazio 34 "$TMP/sp.png"
magick "$RAW/home_light.png" -crop 640x112+0+0 +repage "$TMP/chip_src.png"
arrotonda "$TMP/chip_src.png" "$TMP/chip_r.png" 56
magick "$TMP/chip_r.png" -resize 560x "$TMP/chip.png"
pezzo shade_real.png 1000x372+42+776 46 960 "$TMP/notifica.png"
pezzo detail_dark.png 1000x241+42+914 54 960 "$TMP/avanzamento.png"
pezzo detail_dark.png 1000x557+42+315 54 960 "$TMP/partenza.png"
impila 3 live_update "Quante fermate mancano,
anche a telefono bloccato" \
  "$TMP/chip.png" "$TMP/sp.png" "$TMP/notifica.png" "$TMP/sp.png" "$TMP/avanzamento.png" "$TMP/sp.png" "$TMP/partenza.png"

# 4: i tre widget presi uno per uno sui loro bordi, senza sfondo né icone del launcher
pezzo widgets_dark.png 762x362+61+180 58 762 "$TMP/w1.png"
pezzo widgets_dark.png 961x562+62+585 58 961 "$TMP/w2.png"
pezzo widgets_dark.png 762x564+61+1190 58 762 "$TMP/w3.png"
spazio 40 "$TMP/sp.png"
impila 4 widget "Tre widget
per la schermata home" "$TMP/w1.png" "$TMP/sp.png" "$TMP/w2.png" "$TMP/sp.png" "$TMP/w3.png"

schermo 5 statistiche "stats_dark.png" "I ritardi della tua linea,
nel tempo"
# 6: schermo intero come gli altri, tastiera compresa: un ritaglio tagliava una riga a metà
schermo 6 ricerca "picker_dark.png" "Qualsiasi stazione d'Italia,
anche col GPS"
schermo 7 tema_chiaro "home_light.png" "Chiaro o scuro,
come il tuo telefono"

# ── feature graphic 1024x500 ─────────────────────────────────────────────────
magick "$RAW/home_dark.png" -crop 1000x894+42+757 +repage "$TMP/hero.png"
arrotonda "$TMP/hero.png" "$TMP/hero_big.png" 54
magick "$TMP/hero_big.png" -resize x420 "$TMP/hero_r.png"
magick -size 1024x500 "xc:$BLU" \
  -fill "$ALONE" -draw "circle 790,250 790,560" \
  \( "$TMP/treno.png" -resize 150x150 \) -gravity northwest -geometry +40+60 -composite \
  -font "$BOLD" -pointsize 84 -fill white -gravity northwest -annotate +64+210 "MioTreno" \
  -font "$MEDIUM" -pointsize 32 -fill "#DCE3FF" -annotate +66+320 "Il tuo treno, in tempo reale" \
  "$TMP/hero_r.png" -gravity east -geometry +30+0 -composite \
  PNG24:"$OUT/featureGraphic.png"

echo "Generati in $OUT:"
find "$OUT" -name '*.png' -exec magick identify -format '  %f %wx%h %[size]\n' {} \;
