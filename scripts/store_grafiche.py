#!/usr/bin/env python3
"""
Grafiche per il Play Store dagli screenshot di store_screenshots.py.

    python3 scripts/store_grafiche.py [--src DIR] [--out DIR]

Produce in OUT:
  N-nome.png              le schermate dello store, 1080×1920
  feature-graphic.png     il banner obbligatorio del Play, 1024×500
  anteprima.png           tutte in fila, per vederle come chi scorre la scheda

Tutto PNG 24 bit senza alpha, come vuole il Play. Ogni schermata ha uno sfondo nel colore della
palette, un'etichetta col tracciato dell'header (○┄●), il titolo con una parola in evidenza e
il telefono in cornice. I dettagli da far notare (la card del treno, l'anello della puntualità,
i bottoni) sono ritagliati per *testo* dal dump uiautomator salvato accanto a ogni screenshot e
ingranditi sopra il telefono: niente coordinate scritte a mano.
"""

import argparse
import fnmatch
import re
import xml.etree.ElementTree as ET
from dataclasses import dataclass, field
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont

RADICE = Path(__file__).resolve().parent.parent
FONT = RADICE / "app/src/main/res/font/atkinson_next.ttf"
W, H = 1080, 1920
MARGINE_TESTO = 80
CORNICE = 16  # spessore del bordo del telefono, in px a dimensione finale
MARGINE_BASSO = 28  # aria minima fra un telefono e il bordo in basso
BARRA_STATO = 126  # px della barra di stato negli screenshot grezzi (1084×2412)


@dataclass(frozen=True)
class Colori:
    alto: str
    basso: str
    accento: str
    alone: str  # secondo colore dello sfondo, per non avere una sfumatura piatta


PALETTE = {
    "viola": Colori("#2B1C4D", "#0E0919", "#CDB8F7", "#7A4FD6"),
    "blu": Colori("#0E2B4D", "#060F1E", "#A3CFFB", "#1E7BE0"),
    "verde": Colori("#123A24", "#06140B", "#AEE2B6", "#22A35A"),
    "rosso": Colori("#4D1818", "#170606", "#F7AEAE", "#D9443A"),
    "grafite": Colori("#2A2A2F", "#0C0C0E", "#E8E8EE", "#6E6E7A"),
}


def font(peso: bytes, px: int) -> ImageFont.FreeTypeFont:
    f = ImageFont.truetype(str(FONT), px)
    f.set_variation_by_name(peso)
    return f


def rgb(esadecimale: str, alpha: int = 255) -> tuple[int, int, int, int]:
    h = esadecimale.lstrip("#")
    return int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), alpha


# ── sfondo e testi ────────────────────────────────────────────────────────────

def sfondo(c: Colori, w: int = W, h: int = H) -> Image.Image:
    """Sfumatura, due aloni sfocati (accento e colore pieno) e una grana leggera."""
    img = Image.composite(Image.new("RGB", (w, h), c.basso), Image.new("RGB", (w, h), c.alto),
                          Image.linear_gradient("L").resize((w, h)))
    for colore, box, forza in (
        (c.alone, (-0.3 * w, 0.45 * h, 0.75 * w, 1.15 * h), 120),
        (c.accento, (0.45 * w, 0.25 * h, 1.3 * w, 0.85 * h), 55),
    ):
        maschera = Image.new("L", (w, h), 0)
        ImageDraw.Draw(maschera).ellipse(box, fill=forza)
        maschera = maschera.filter(ImageFilter.GaussianBlur(min(w, h) * 0.18))
        img = Image.composite(Image.new("RGB", (w, h), colore), img, maschera)
    grana = Image.effect_noise((w, h), 40).convert("RGB")
    return ImageChops.blend(img, ImageChops.overlay(img, grana), 0.06)


def tracciato(d: ImageDraw.ImageDraw, x: int, y: int, colore: tuple, scala: float = 1.0) -> int:
    """Il segno dell'header in orizzontale, ○┄┄●: lega le grafiche all'app. Ritorna la x finale."""
    r = round(9 * scala)
    d.ellipse((x, y - r, x + 2 * r, y + r), outline=colore, width=max(2, round(3 * scala)))
    x0 = x + 2 * r + round(8 * scala)
    for i in range(4):
        d.rounded_rectangle((x0 + i * round(14 * scala), y - 2, x0 + i * round(14 * scala) + round(7 * scala), y + 2),
                            2, fill=colore)
    x1 = x0 + 4 * round(14 * scala) + round(2 * scala)
    d.ellipse((x1, y - r, x1 + 2 * r, y + r), fill=colore)
    return x1 + 2 * r


def etichetta(img: Image.Image, testo: str, c: Colori, y: int) -> None:
    """Pillola centrata col tracciato e il testo in maiuscolo spaziato."""
    f = font(b"Bold", 30)
    testo = " ".join(testo.upper())  # spaziatura larga, alla maniera delle overline
    lw = 112 + round(ImageDraw.Draw(img).textlength(testo, font=f))
    x = (W - lw) // 2
    strato = Image.new("RGBA", img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(strato)
    d.rounded_rectangle((x - 28, y - 30, x + lw + 28, y + 30), 30, fill=rgb(c.accento, 34), outline=rgb(c.accento, 90), width=2)
    fine = tracciato(d, x, y, rgb(c.accento))
    d.text((fine + 22, y), testo, font=f, fill=rgb(c.accento), anchor="lm")
    img.alpha_composite(strato)


def titolo(img: Image.Image, testo: str, sotto: str, c: Colori, y: int) -> None:
    """Titolo centrato su più righe; le parole fra *asterischi* nel colore d'accento."""
    d = ImageDraw.Draw(img)
    parole, evidenza = [], False
    for p in testo.split():
        inizio, fine = p.startswith("*"), p.endswith("*")
        evidenza = evidenza or inizio
        parole.append((p.strip("*"), evidenza))
        evidenza = evidenza and not fine
    # il più grande che sta in due righe: con tre il testo finisce addosso al telefono
    f, righe = adatta(d, parole, b"ExtraBold", (90, 82, 74, 66), 2)
    spazio = d.textlength(" ", font=f)
    for riga in righe:
        larghezza = sum(d.textlength(p, font=f) for p, _ in riga) + spazio * (len(riga) - 1)
        x = (W - larghezza) / 2
        for p, ev in riga:
            d.text((x, y), p, font=f, fill=c.accento if ev else "#FFFFFF", anchor="ls")
            x += d.textlength(p, font=f) + spazio
        y += round(f.size * 1.1)
    fs, righe = adatta(d, [(p, False) for p in sotto.split()], b"Medium", (40, 36, 32), 1)
    for riga in righe:
        d.text((W // 2, y + 6), " ".join(p for p, _ in riga), font=fs, fill=(255, 255, 255, 190), anchor="ms")
        y += round(fs.size * 1.3)


def adatta(d: ImageDraw.ImageDraw, parole: list, peso: bytes, misure: tuple[int, ...], max_righe: int):
    """La misura più grande in cui il testo sta in [max_righe]; altrimenti la più piccola."""
    for px in misure:
        f = font(peso, px)
        righe = a_capo(d, parole, f, d.textlength(" ", font=f))
        if len(righe) <= max_righe:
            return f, righe
    return f, righe


def a_capo(d: ImageDraw.ImageDraw, parole: list, f: ImageFont.FreeTypeFont, spazio: float) -> list[list]:
    righe: list[list] = []
    for parola in parole:
        if righe:
            prova = sum(d.textlength(p, font=f) for p, _ in righe[-1]) + spazio * len(righe[-1]) + d.textlength(parola[0], font=f)
            if prova <= W - 2 * MARGINE_TESTO:
                righe[-1].append(parola)
                continue
        righe.append([parola])
    return righe


# ── telefono e ritagli ────────────────────────────────────────────────────────

def barra_pulita(img: Image.Image) -> Image.Image:
    """
    Ridisegna la barra di stato: 9:41, campo, wifi e batteria piena. Il demo mode di Android
    non vale su tutti i telefoni (su Nothing OS l'ora e le icone restano quelle vere).
    """
    img = img.copy()
    fondo = img.getpixel((4, BARRA_STATO + 6))
    tinta = (28, 28, 30) if sum(fondo) / 3 > 128 else (240, 240, 244)
    d = ImageDraw.Draw(img)
    d.rectangle((0, 0, img.width, BARRA_STATO), fill=fondo)
    cy = BARRA_STATO // 2 + 4
    d.text((88, cy), "9:41", font=font(b"SemiBold", 40), fill=tinta, anchor="lm")
    x = img.width - 88  # batteria, da destra verso sinistra
    d.rounded_rectangle((x - 50, cy - 15, x, cy + 15), 7, fill=tinta)
    d.rounded_rectangle((x + 3, cy - 6, x + 7, cy + 6), 2, fill=tinta)
    x -= 82  # wifi: tre archi concentrici
    for r in (28, 19, 10):
        d.pieslice((x - r, cy + 12 - r, x + r, cy + 12 + r), 225, 315, fill=tinta)
        if r != 10:
            d.pieslice((x - r + 5, cy + 12 - r + 5, x + r - 5, cy + 12 + r - 5), 225, 315, fill=fondo)
    x -= 70  # campo: quattro tacche
    for i in range(4):
        d.rounded_rectangle((x + i * 12, cy + 12 - 8 - i * 7, x + i * 12 + 8, cy + 12), 2, fill=tinta)
    return img


def telefono(screenshot: Path, altezza: int) -> Image.Image:
    """Lo screenshot dentro una cornice scura arrotondata, col foro della fotocamera."""
    schermo = barra_pulita(Image.open(screenshot).convert("RGB"))
    h_s = altezza - 2 * CORNICE
    schermo = schermo.resize((round(schermo.width * h_s / schermo.height), h_s), Image.LANCZOS)
    w_t, raggio = schermo.width + 2 * CORNICE, round(schermo.width * 0.085)
    tel = Image.new("RGBA", (w_t, altezza), (0, 0, 0, 0))
    d = ImageDraw.Draw(tel)
    d.rounded_rectangle((0, 0, w_t - 1, altezza - 1), raggio + CORNICE, fill=(14, 14, 16, 255))
    d.rounded_rectangle((1, 1, w_t - 2, altezza - 2), raggio + CORNICE, outline=(78, 78, 84, 255), width=2)
    maschera = Image.new("L", schermo.size, 0)
    ImageDraw.Draw(maschera).rounded_rectangle((0, 0, *schermo.size), raggio, fill=255)
    tel.paste(schermo, (CORNICE, CORNICE), maschera)
    r = round(schermo.width * 0.014)
    cx, cy = w_t // 2, CORNICE + round(h_s * 0.019)
    d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=(5, 5, 6, 255))
    return tel


def limiti(xml: Path, testo: str, salita: int) -> tuple[int, int, int, int]:
    """Il riquadro dell'elemento col [testo] (pattern fnmatch), risalendo di [salita] contenitori."""
    radice = ET.parse(xml).getroot()
    genitore = {figlio: padre for padre in radice.iter() for figlio in padre}
    for nodo in radice.iter("node"):
        if any(fnmatch.fnmatchcase(v or "", testo) for v in (nodo.get("text"), nodo.get("content-desc"))):
            for _ in range(salita):
                nodo = genitore[nodo]
            x1, y1, x2, y2 = map(int, re.findall(r"\d+", nodo.get("bounds", "")))
            return x1, y1, x2, y2
    raise SystemExit(f'"{testo}" non c\'è in {xml}: rifai gli screenshot o cambia il ritaglio')


def ritaglio(screenshot: Path, box: tuple[int, int, int, int], larghezza: int, c: Colori) -> Image.Image:
    """Un pezzo dello schermo ingrandito come card arrotondata col filo d'accento."""
    pezzo = Image.open(screenshot).convert("RGB").crop(box)
    pezzo = pezzo.resize((larghezza, round(pezzo.height * larghezza / pezzo.width)), Image.LANCZOS)
    raggio = round(larghezza * 0.07)
    card = Image.new("RGBA", pezzo.size, (0, 0, 0, 0))
    maschera = Image.new("L", pezzo.size, 0)
    ImageDraw.Draw(maschera).rounded_rectangle((0, 0, *pezzo.size), raggio, fill=255)
    card.paste(pezzo, (0, 0), maschera)
    ImageDraw.Draw(card).rounded_rectangle((1, 1, pezzo.width - 2, pezzo.height - 2), raggio, outline=rgb(c.accento, 200), width=4)
    return card


def con_ombra(
    base: Image.Image, pezzo: Image.Image, centro: tuple[int, int], angolo: float = 0,
    forza: float = 0.6, dentro: bool = True,
) -> None:
    """Incolla con ombra morbida. [dentro]: mai tagliato dal bordo in basso (inclinato sporge)."""
    if angolo:
        pezzo = pezzo.rotate(angolo, Image.BICUBIC, expand=True)
    x, y = centro[0] - pezzo.width // 2, centro[1] - pezzo.height // 2
    if dentro:
        y = min(y, base.height - MARGINE_BASSO - pezzo.height)
    margine = 90
    tela = Image.new("RGBA", (pezzo.width + 2 * margine, pezzo.height + 2 * margine), (0, 0, 0, 0))
    ombra = Image.new("RGBA", pezzo.size, (0, 0, 0, 0))
    ombra.putalpha(pezzo.getchannel("A").point(lambda a: a * forza))
    tela.paste(ombra, (margine, margine))
    base.alpha_composite(tela.filter(ImageFilter.GaussianBlur(40)), (x - margine, y - margine + 40))
    base.alpha_composite(pezzo, (x, y))


# ── le grafiche ───────────────────────────────────────────────────────────────

@dataclass(frozen=True)
class Telefono:
    schermo: str  # "<palette>-<tema>/<NN-nome>" in src
    centro: tuple[int, int] = (W // 2, 1225)
    altezza: int = 1320
    angolo: float = 0


@dataclass(frozen=True)
class Ingrandimento:
    schermo: str
    elementi: tuple[tuple[str, int], ...]  # (testo, contenitori da risalire); si unisce il tutto
    centro: tuple[int, int]
    larghezza: int
    angolo: float = 0
    margine: int = 0  # px di schermo grezzo attorno all'unione


@dataclass(frozen=True)
class Grafica:
    nome: str
    palette: str
    etichetta: str
    titolo: str
    sotto: str
    telefoni: tuple[Telefono, ...]
    ingrandimenti: tuple[Ingrandimento, ...] = field(default_factory=tuple)


GRAFICHE = (
    Grafica(
        "1-prossimo-treno", "blu", "In tempo reale",
        "Il tuo prossimo treno, *a colpo d'occhio*", "Quanto manca, binario e ritardo, dalla stazione dove sei",
        (Telefono("blu-scuro/01-home", (430, 1225), 1280, 3),),
        (Ingrandimento("blu-scuro/01-home", (("Parte tra", 1),), (700, 1270), 700, -3),),
    ),
    Grafica(
        "2-percorso", "grafite", "Il tuo percorso",
        "Da, via, a: *sempre in alto*", "Un tocco per cambiare stazione, uno per il ritorno",
        (Telefono("originale-chiaro/01-home"),),
        (Ingrandimento("originale-chiaro/01-home", (("Partenza *", 0), ("Destinazione *", 0), ("Inverti partenza e destinazione", 0)),
                       (W // 2, 1000), 980, 0, 8),),
    ),
    Grafica(
        "3-fermate", "verde", "Fermata per fermata",
        "Dov'è il treno, *e quando arriva da te*", "Ritardi aggiornati a ogni fermata, in automatico",
        (Telefono("verde-scuro/03-dettaglio-esteso", (360, 1240), 1180, 6),
         Telefono("verde-chiaro/03-dettaglio-esteso", (720, 1250), 1230, -4)),
    ),
    Grafica(
        "4-segui", "blu", "Avvisi",
        "Segui la corsa, *al resto pensa MioTreno*", "Un avviso prima di partire e gli aggiornamenti mentre viaggi",
        (Telefono("blu-chiaro/02-dettaglio", (W // 2, 1225), 1320),),
        (Ingrandimento("blu-chiaro/02-dettaglio", (("Segui corsa", 1), ("Avvisami * prima della partenza", 0)), (W // 2, 1640), 900, 0, 10),),
    ),
    Grafica(
        "5-colori", "viola", "Material You",
        "*I colori* del tuo telefono", "Si adatta al tuo sfondo, in chiaro e in scuro",
        (Telefono("rosso-scuro/01-home", (245, 1300), 1100, 8),
         Telefono("blu-scuro/01-home", (835, 1300), 1100, -8),
         Telefono("verde-chiaro/01-home", (W // 2, 1250), 1240)),
    ),
    Grafica(
        "6-puntualita", "rosso", "Statistiche",
        "Quanto è *puntuale* la tua linea", "Lo storico dei tuoi treni resta sul telefono",
        (Telefono("rosso-scuro/04-statistiche", (420, 1225), 1280, 3),),
        (Ingrandimento("rosso-scuro/04-statistiche", (("Puntualità", 2),), (700, 1130), 620, -3),),
    ),
    Grafica(
        "7-su-misura", "viola", "Su misura",
        "Diretti o *con un cambio*", "E i treni che passano per la tua stazione, in evidenza",
        (Telefono("viola-chiaro/05-impostazioni"),),
        (Ingrandimento("viola-chiaro/05-impostazioni", (("Anche con cambi", 2), ("Evidenzia chi passa per", 2)),
                       (W // 2, 1420), 960, 0, 0),),
    ),
)


def componi(g: Grafica, src: Path) -> Image.Image:
    c = PALETTE[g.palette]
    img = sfondo(c).convert("RGBA")
    etichetta(img, g.etichetta, c, 130)
    titolo(img, g.titolo, g.sotto, c, 290)
    for t in g.telefoni:
        con_ombra(img, telefono(src / f"{t.schermo}.png", t.altezza), t.centro, t.angolo)
    for z in g.ingrandimenti:
        riquadri = [limiti(src / f"{z.schermo}.xml", testo, su) for testo, su in z.elementi]
        box = (min(b[0] for b in riquadri) - z.margine, min(b[1] for b in riquadri) - z.margine,
               max(b[2] for b in riquadri) + z.margine, max(b[3] for b in riquadri) + z.margine)
        con_ombra(img, ritaglio(src / f"{z.schermo}.png", box, z.larghezza, c), z.centro, z.angolo, 0.75)
    return img.convert("RGB")


def feature_graphic(src: Path) -> Image.Image:
    """Il banner 1024×500: nome e frase a sinistra, due telefoni inclinati a destra."""
    fw, fh = 1024, 500
    c = PALETTE["blu"]
    img = sfondo(c, fw, fh).convert("RGBA")
    d = ImageDraw.Draw(img)
    tracciato(d, 64, 150, rgb(c.accento), 1.3)
    d.text((60, 250), "MioTreno", font=font(b"ExtraBold", 104), fill="#FFFFFF", anchor="ls")
    d.text((64, 318), "Il tuo treno,", font=font(b"Medium", 40), fill=(255, 255, 255, 200), anchor="ls")
    d.text((64, 368), "in tempo reale.", font=font(b"Bold", 40), fill=c.accento, anchor="ls")
    # i telefoni escono dal bordo in basso: nel banner il taglio è voluto, fa profondità
    con_ombra(img, telefono(src / "verde-chiaro/01-home.png", 640), (730, 470), 8, dentro=False)
    con_ombra(img, telefono(src / "blu-scuro/01-home.png", 660), (900, 450), -6, dentro=False)
    return img.convert("RGB")


def main() -> None:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    base = RADICE / "build" / "store-screenshots"
    p.add_argument("--src", type=Path, default=base / "raw")
    p.add_argument("--out", type=Path, default=base / "grafiche")
    a = p.parse_args()

    servono = {t.schermo for g in GRAFICHE for t in g.telefoni} | {z.schermo for g in GRAFICHE for z in g.ingrandimenti}
    mancanti = sorted(s for s in servono if not (a.src / f"{s}.png").exists())
    if mancanti:
        raise SystemExit(f"Mancano screenshot (lancia prima store_screenshots.py): {mancanti}")
    a.out.mkdir(parents=True, exist_ok=True)

    fatte = []
    for g in GRAFICHE:
        img = componi(g, a.src)
        assert img.size == (W, H) and img.mode == "RGB"
        img.save(a.out / f"{g.nome}.png", optimize=True)
        fatte.append(img)
        print(f"  ✓ {g.nome}.png")
    banner = feature_graphic(a.src)
    banner.save(a.out / "feature-graphic.png", optimize=True)
    print("  ✓ feature-graphic.png")

    lato, g = 420, 24
    alto = round(lato * H / W)
    riga = Image.new("RGB", (len(fatte) * (lato + g) + g, alto + 2 * g + 500 + g), (24, 24, 24))
    for i, img in enumerate(fatte):
        riga.paste(img.resize((lato, alto), Image.LANCZOS), (g + i * (lato + g), g))
    riga.paste(banner, (g, alto + 2 * g))
    riga.save(a.out / "anteprima.png")
    print(f"Fatto: {a.out}")


if __name__ == "__main__":
    main()
