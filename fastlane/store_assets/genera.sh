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

# tela 1080x1920: sfumatura rail blue, alone morbido dove poggia il telefono, didascalia in alto.
# Didascalia su due righe con due toni: la prima (il cosa) in bianco, la seconda più tenue.
tela() { # out didascalia
  local riga1="${2%%$'\n'*}" riga2="${2#*$'\n'}"
  magick -size 1080x1920 "gradient:#2D54F0-#1A33B4" \
    \( -size 1080x1920 xc:none -fill "rgba(255,255,255,0.13)" -draw "circle 540,1180 540,1880" -blur 0x110 \) \
    -composite \
    \( -background none -fill white -font "$BOLD" -pointsize 76 "label:$riga1" -trim +repage -resize "960x>" \) \
    -gravity north -geometry +0+122 -composite \
    \( -background none -fill "#C9D5FF" -font "$MEDIUM" -pointsize 58 "label:$riga2" -trim +repage -resize "960x>" \) \
    -gravity north -geometry +0+232 -composite \
    "$1"
}

# telefono: schermo arrotondato dentro una cornice scura con un filo di luce sul bordo
telefono() { # in out larghezza
  local w h
  magick "$1" -resize "$3x" "$TMP/sc.png"
  w=$(magick identify -format %w "$TMP/sc.png"); h=$(magick identify -format %h "$TMP/sc.png")
  magick "$TMP/sc.png" \( -size "${w}x${h}" xc:none -fill white -draw "roundrectangle 0,0,$((w - 1)),$((h - 1)),52,52" \) \
    -alpha set -compose DstIn -composite -compose over "$TMP/sc_r.png"
  local W=$((w + 32)) H=$((h + 32))
  magick -size "${W}x${H}" xc:none \
    -fill "#070A14" -stroke "rgba(255,255,255,0.22)" -strokewidth 3 \
    -draw "roundrectangle 1,1,$((W - 2)),$((H - 2)),70,70" \
    "$TMP/sc_r.png" -geometry +16+16 -composite \
    \( +clone -background "#06103A" -shadow 55x30+0+24 \) +swap -background none -layers merge +repage "$2"
}

# screenshot completo: telefono grande che esce dal bordo inferiore, come nelle schede dei big
schermo() { # n nome raw didascalia
  local n="$1" nome="$2" raw="$3" testo="$4"
  tela "$TMP/tela.png" "$testo"
  telefono "$RAW/$raw" "$TMP/tel.png" 800
  magick "$TMP/tela.png" "$TMP/tel.png" -gravity north -geometry +0+400 -composite \
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

# distanziatore trasparente fra i pezzi di una pila
spazio() { magick -size "1x$1" xc:none "$2"; }

# ── screenshot telefono (1080x1920, 9:16) ────────────────────────────────────
schermo 1 home "home_dark.png" "Il tuo treno
a colpo d'occhio"
schermo 2 dettaglio "detail_dark.png" "Fermata per fermata
dal punto in cui sali"

# Blocco: un'etichetta breve sopra un pezzo vero dell'interfaccia, per dire cos'è e dove compare
blocco() { # etichetta pezzo out
  magick \( -background none -fill "#C9D5FF" -font "$MEDIUM" -pointsize 40 "label:$1" -trim +repage \) \
    \( -size 1x22 xc:none \) "$2" -background none -gravity center -append "$3"
}

# Composizione: blocchi impilati direttamente sulla tela, senza un finto schermo dietro.
# Stessa scala per tutti, blocco centrato nello spazio sotto la didascalia.
composizione() { # n nome didascalia blocco...
  local n="$1" nome="$2" testo="$3"; shift 3
  tela "$TMP/tela.png" "$testo"
  local args=() primo=1
  for b in "$@"; do
    [ $primo -eq 0 ] && args+=("$TMP/gap.png"); args+=("$b"); primo=0
  done
  magick -size 1x76 xc:none "$TMP/gap.png"
  magick -background none "${args[@]}" -gravity center -append -resize "980x1450>" "$TMP/pila.png"
  local h y
  h=$(magick identify -format %h "$TMP/pila.png"); y=$((400 + (1500 - h) / 2))
  magick "$TMP/tela.png" "$TMP/pila.png" -gravity north -geometry "+0+$y" -composite \
    PNG24:"$OUT/phoneScreenshots/${n}_${nome}.png"
}

# 3: lo stesso conto in tre posti: barra di stato, tendina, app
pezzo detail_dark.png 243x63+193+31 31 480 "$TMP/chip.png"
pezzo shade_real.png 1000x372+42+776 46 1000 "$TMP/notifica.png"
pezzo detail_dark.png 1000x241+42+914 54 1000 "$TMP/avanzamento.png"
blocco "Nella barra di stato" "$TMP/chip.png" "$TMP/b1.png"
blocco "Nella tendina delle notifiche" "$TMP/notifica.png" "$TMP/b2.png"
blocco "Nell'app" "$TMP/avanzamento.png" "$TMP/b3.png"
composizione 3 live_update "Quante fermate mancano
anche a telefono bloccato" "$TMP/b1.png" "$TMP/b2.png" "$TMP/b3.png"

# 4: i tre widget presi uno per uno sui loro bordi, senza lo sfondo del launcher
pezzo widgets_dark.png 762x362+61+180 58 762 "$TMP/w1.png"
pezzo widgets_dark.png 961x562+62+585 58 961 "$TMP/w2.png"
pezzo widgets_dark.png 762x564+61+1190 58 762 "$TMP/w3.png"
blocco "La tua corsa" "$TMP/w1.png" "$TMP/b1.png"
blocco "I prossimi treni" "$TMP/w2.png" "$TMP/b2.png"
blocco "Corsa e partenze successive" "$TMP/w3.png" "$TMP/b3.png"
composizione 4 widget "Tre widget
per la schermata home" "$TMP/b1.png" "$TMP/b2.png" "$TMP/b3.png"

schermo 5 statistiche "stats_dark.png" "I ritardi della tua linea
nel tempo"
# 6: schermo intero come gli altri, tastiera compresa: un ritaglio tagliava una riga a metà
schermo 6 ricerca "picker_dark.png" "Qualsiasi stazione d'Italia
anche col GPS"
schermo 7 tema_chiaro "home_light.png" "Chiaro o scuro
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
