#!/usr/bin/env python3
"""
Screenshot per il Play Store dal telefono collegato via adb.

    python3 scripts/store_screenshots.py [--serial X] [--no-build] [--out DIR]

Compila e installa la debug, pulisce la barra di stato (demo mode), attiva il Non disturbare,
fotografa Home, Dettaglio (aperto ed esteso), Statistiche e Impostazioni, per ogni palette
Material You (--palette) in tema scuro e chiaro, e salva:
  OUT/raw/<palette>-<tema>/NN-nome.png    lo schermo così com'è
  OUT/play/<palette>-<tema>/NN-nome.png   1080×1920 (9:16) senza tagli: lo schermo intero
                                          rimpicciolito, ai lati il colore di fondo dell'app
                                          (il Play rifiuta lati oltre il rapporto 2:1)

Alla fine — anche con errori o Ctrl-C — rimette il telefono com'era: palette, tema, Non
disturbare, demo mode, schermo sempre acceso. I dati dell'app non vengono mai toccati.
"""

import argparse
import os
import re
import signal
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

from PIL import Image

PACCHETTO = "it.picone.miotreno"
ATTIVITA = f"{PACCHETTO}/.ui.MainActivity"
RADICE = Path(__file__).resolve().parent.parent
PLAY_W, PLAY_H = 1080, 1920
ATTESA_DATI_S = 30
ATTESA_UI_S = 10
# zen_mode → argomento di `cmd notification set_dnd`
DND = {"0": "off", "1": "priority", "2": "none", "3": "alarms"}
# Palette Material You applicate al sistema; "originale" lascia quella del telefono
PALETTE = {"originale": None, "blu": "FF1E88E5", "verde": "FF2E7D32", "rosso": "FFE53935", "viola": "FF7E57C2"}
CHIAVE_PALETTE = "theme_customization_overlay_packages"
ATTESA_PALETTE_S = 4

serial: list[str] = []


def adb(*args: str, check: bool = True) -> str:
    r = subprocess.run(["adb", *serial, *args], capture_output=True, text=True)
    if check and r.returncode != 0:
        raise RuntimeError(f"adb {' '.join(args)}: {r.stderr.strip() or r.stdout.strip()}")
    return r.stdout.strip()


def sh(cmd: str, check: bool = True) -> str:
    return adb("shell", cmd, check=check)


# ── dispositivo ───────────────────────────────────────────────────────────────

def scegli_dispositivo(richiesto: str | None) -> None:
    righe = subprocess.run(["adb", "devices"], capture_output=True, text=True).stdout.splitlines()[1:]
    pronti = [r.split()[0] for r in righe if r.strip().endswith("\tdevice")]
    altri = [r for r in righe if r.strip() and not r.strip().endswith("\tdevice")]
    if richiesto:
        if richiesto not in pronti:
            sys.exit(f"Dispositivo {richiesto} non pronto. Collegati: {pronti or 'nessuno'}")
        pronti = [richiesto]
    if not pronti:
        hint = " (sblocca il telefono e accetta il debug USB)" if any("unauthorized" in r for r in altri) else ""
        sys.exit(f"Nessun telefono pronto{hint}.")
    if len(pronti) > 1:
        sys.exit(f"Più telefoni collegati, scegline uno con --serial: {', '.join(pronti)}")
    serial[:] = ["-s", pronti[0]]


# ── stato da salvare e ripristinare ───────────────────────────────────────────

def leggi_stato() -> dict[str, str]:
    notte = sh("cmd uimode night")  # "Night mode: yes|no|auto|custom"
    return {
        "notte": notte.split(":")[-1].strip(),
        "zen": sh("settings get global zen_mode"),
        "demo": sh("settings get global sysui_demo_allowed"),
        "stayon": sh("settings get global stay_on_while_plugged_in"),
        "palette": sh(f"settings get secure {CHIAVE_PALETTE}"),
    }


def ripristina(stato: dict[str, str]) -> None:
    """Ogni passo è indipendente: se uno fallisce, gli altri si fanno comunque."""
    passi = [
        ("barra di stato", lambda: sh("am broadcast -a com.android.systemui.demo -e command exit")),
        ("demo mode", lambda: _setting("sysui_demo_allowed", stato["demo"])),
        ("palette colori", lambda: _setting(CHIAVE_PALETTE, stato["palette"], "secure")),
        ("tema", lambda: sh(f"cmd uimode night {stato['notte']}")),
        ("Non disturbare", lambda: sh(f"cmd notification set_dnd {DND.get(stato['zen'], 'off')}")),
        ("schermo sempre acceso", lambda: _setting("stay_on_while_plugged_in", stato["stayon"])),
        ("app", lambda: (sh(f"am force-stop {PACCHETTO}"), sh(f"am start -n {ATTIVITA}"))),
    ]
    print("\nRipristino del telefono:")
    for nome, passo in passi:
        try:
            passo()
            print(f"  ✓ {nome}")
        except Exception as e:  # noqa: BLE001 — il ripristino non deve mai fermarsi a metà
            print(f"  ✗ {nome}: {e}  ← da sistemare a mano")
    dopo = leggi_stato()
    diversi = [k for k in stato if stato[k] != dopo[k]]
    print("  stato identico a prima" if not diversi else f"  ATTENZIONE, diverso da prima: {diversi}")


def _setting(nome: str, valore: str, spazio: str = "global") -> None:
    if valore == "null":
        sh(f"settings delete {spazio} {nome}")
    else:
        sh(f"settings put {spazio} {nome} '{valore}'")  # apici: la palette è un JSON


def applica_palette(nome: str, originale: str) -> None:
    colore = PALETTE[nome]
    if colore is None:
        _setting(CHIAVE_PALETTE, originale, "secure")
    else:
        json = (f'{{"android.theme.customization.system_palette":"{colore}",'
                '"android.theme.customization.theme_style":"TONAL_SPOT",'
                '"android.theme.customization.color_source":"preset"}')
        _setting(CHIAVE_PALETTE, json, "secure")
    time.sleep(ATTESA_PALETTE_S)  # il sistema rigenera i colori e li manda alle app


def prepara() -> None:
    sh("svc power stayon true")
    sh("input keyevent KEYCODE_WAKEUP")
    sh("wm dismiss-keyguard", check=False)
    sh("cmd notification set_dnd none")
    sh("settings put global sysui_demo_allowed 1")
    demo = [
        "-e command enter",
        "-e command clock -e hhmm 0941",
        "-e command battery -e level 100 -e plugged false",
        "-e command network -e wifi show -e level 4",
        "-e command network -e mobile show -e level 4 -e datatype none",
        "-e command notifications -e visible false",
    ]
    for extra in demo:
        sh(f"am broadcast -a com.android.systemui.demo {extra}")


# ── interfaccia ───────────────────────────────────────────────────────────────

def nodi() -> list[ET.Element]:
    sh("uiautomator dump /sdcard/miotreno_ui.xml", check=False)
    xml = adb("exec-out", "cat", "/sdcard/miotreno_ui.xml", check=False)
    try:
        return list(ET.fromstring(xml).iter("node"))
    except ET.ParseError:
        return []


def trova(testo: str, timeout: float = ATTESA_UI_S) -> ET.Element:
    fine = time.time() + timeout
    while time.time() < fine:
        for n in nodi():
            if testo in (n.get("text"), n.get("content-desc")):
                return n
        time.sleep(0.5)
    raise RuntimeError(f'"{testo}" non compare sullo schermo dopo {timeout:.0f} s')


def assente(testo: str, timeout: float = ATTESA_UI_S) -> None:
    fine = time.time() + timeout
    while time.time() < fine:
        if all(testo not in (n.get("text"), n.get("content-desc")) for n in nodi()):
            return
        time.sleep(0.5)
    raise RuntimeError(f'"{testo}" resta sullo schermo dopo {timeout:.0f} s')


def tocca(testo: str) -> None:
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", trova(testo).get("bounds", "")))
    sh(f"input tap {(x1 + x2) // 2} {(y1 + y2) // 2}")


def altezza_barra_stato() -> int:
    m = re.search(r"type=statusBars frame=\[0,0\]\[\d+,(\d+)\]", sh("dumpsys window", check=False))
    return int(m.group(1)) if m else 120


# ── scatti ────────────────────────────────────────────────────────────────────

def scatta(out: Path, tema: str, nome: str, barra: int) -> None:
    time.sleep(1.2)  # animazioni d'ingresso finite
    raw = out / "raw" / tema / f"{nome}.png"
    raw.parent.mkdir(parents=True, exist_ok=True)
    raw.write_bytes(subprocess.run(["adb", *serial, "exec-out", "screencap", "-p"], capture_output=True, check=True).stdout)
    # la struttura dello schermo accanto allo scatto: store_grafiche.py ci trova gli elementi
    # da ingrandire (per testo) senza coordinate scritte a mano
    sh("uiautomator dump /sdcard/miotreno_ui.xml", check=False)
    raw.with_suffix(".xml").write_text(adb("exec-out", "cat", "/sdcard/miotreno_ui.xml", check=False))

    # Copia per il Play senza tagli: tutto lo schermo (barra di stato esclusa) rimpicciolito
    # fino a 1920 di altezza, centrato su 1080 di larghezza col colore di fondo dell'app ai lati.
    img = Image.open(raw).convert("RGB")
    w, h = img.size
    schermo = img.crop((0, barra, w, h))
    scala = min(PLAY_W / schermo.width, PLAY_H / schermo.height)
    ridotto = schermo.resize((round(schermo.width * scala), round(schermo.height * scala)), Image.LANCZOS)
    fondo = img.getpixel((2, h // 2))
    tela = Image.new("RGB", (PLAY_W, PLAY_H), fondo)
    tela.paste(ridotto, ((PLAY_W - ridotto.width) // 2, (PLAY_H - ridotto.height) // 2))
    play = out / "play" / tema / f"{nome}.png"
    play.parent.mkdir(parents=True, exist_ok=True)
    tela.save(play)
    print(f"  📸 {tema}/{nome}")


def giro(out: Path, tema: str, barra: int) -> None:
    print(f"  {tema}:")
    sh(f"cmd uimode night {'yes' if tema.endswith('scuro') else 'no'}")
    sh(f"am force-stop {PACCHETTO}")
    sh(f"am start -n {ATTIVITA}")
    try:
        trova("Parte tra", ATTESA_DATI_S)
    except RuntimeError:
        raise RuntimeError("la home non mostra treni: controlla rete, stazione e destinazione") from None
    scatta(out, tema, "01-home", barra)

    tocca("Parte tra")
    assente("Partenze successive")
    assente("Carico il percorso…", ATTESA_DATI_S)
    scatta(out, tema, "02-dettaglio", barra)

    # il dettaglio è un foglio a metà: lo si tira su per vedere tutto il percorso
    w, h = map(int, re.findall(r"\d+", sh("wm size"))[-2:])
    sh(f"input swipe {w // 2} {h * 75 // 100} {w // 2} {h // 10} 400")
    assente("Carico il percorso…", ATTESA_DATI_S)
    scatta(out, tema, "03-dettaglio-esteso", barra)
    torna_alla_home()

    tocca("Statistiche")
    scatta(out, tema, "04-statistiche", barra)
    tocca("Impostazioni")
    scatta(out, tema, "05-impostazioni", barra)
    tocca("Treni")


def torna_alla_home() -> None:
    """Indietro finché il foglio del dettaglio non è chiuso (esteso può servire più di un tocco)."""
    for _ in range(3):
        sh("input keyevent KEYCODE_BACK")
        try:
            trova("Partenze successive", 3)
            return
        except RuntimeError:
            continue
    raise RuntimeError("il dettaglio non si chiude")


def main() -> None:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--serial", help="dispositivo adb, se ne hai più di uno")
    p.add_argument("--no-build", action="store_true", help="non ricompilare e reinstallare")
    p.add_argument("--out", type=Path, default=RADICE / "build" / "store-screenshots")
    p.add_argument("--palette", nargs="+", choices=PALETTE, default=list(PALETTE),
                   help="palette Material You da fotografare (default: tutte)")
    a = p.parse_args()

    scegli_dispositivo(a.serial)
    if not a.no_build:
        print("Compilo e installo la debug…")
        # ANDROID_SERIAL: installDebug installa solo sul telefono scelto
        env = {**os.environ, "ANDROID_SERIAL": serial[1]}
        subprocess.run([str(RADICE / "gradlew"), "-q", ":app:installDebug"], cwd=RADICE, env=env, check=True)

    stato = leggi_stato()
    print(f"Stato salvato: {stato}")
    # Ctrl-C e kill passano dal finally: il ripristino gira comunque
    signal.signal(signal.SIGTERM, lambda *_: sys.exit(143))
    errore = False
    try:
        prepara()
        barra = altezza_barra_stato()
        for palette in a.palette:
            print(f"\nPalette {palette}")
            applica_palette(palette, stato["palette"])
            for tema in ("scuro", "chiaro"):
                giro(a.out, f"{palette}-{tema}", barra)
    except KeyboardInterrupt:
        print("\nInterrotto.")
        errore = True
    except Exception as e:  # noqa: BLE001
        print(f"\nErrore: {e}")
        errore = True
    finally:
        signal.signal(signal.SIGINT, signal.SIG_IGN)  # un secondo Ctrl-C non deve spezzare il ripristino
        ripristina(stato)

    if errore:
        sys.exit(1)
    print(f"\nFatto: {a.out}")
    for f in sorted(a.out.rglob("*.png")):
        print(f"  {f.relative_to(a.out)}")


if __name__ == "__main__":
    main()
