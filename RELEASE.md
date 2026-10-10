# Pubblicazione su Google Play

Stato: il codice è pronto per la build di release. Quello che segue in questo file è tutto
quello che serve fare **manualmente**, fuori da questo repo — account, chiavi, contenuti da
incollare in Play Console. Niente di tutto questo può essere automatizzato da qui.

## 1. Firma della build

`app/build.gradle.kts` legge le credenziali da variabili d'ambiente (o da `gradle.properties`,
mai committato — vedi `.gitignore`): se mancano, `bundleRelease`/`assembleRelease` producono
comunque una build valida ma non firmata, così il progetto resta utilizzabile anche senza chiave.

Variabili lette (`MIOTRENO_KEYSTORE_PATH`, `MIOTRENO_KEYSTORE_PASSWORD`, `MIOTRENO_KEY_ALIAS`,
`MIOTRENO_KEY_PASSWORD`):

```sh
export MIOTRENO_KEYSTORE_PATH=/percorso/al/tuo/keystore.jks
export MIOTRENO_KEYSTORE_PASSWORD=...
export MIOTRENO_KEY_ALIAS=...
export MIOTRENO_KEY_PASSWORD=...
./gradlew bundleRelease   # produce app/build/outputs/bundle/release/app-release.aab
```

**Consigliato: Play App Signing.** In Play Console, alla prima creazione dell'app, scegli
"Lascia che sia Google a creare e gestire la chiave di firma dell'app" — Google genera e
custodisce la chiave che firma davvero quello che arriva sugli utenti, tu firmi solo la chiave
di caricamento (upload key), sostituibile se la perdi. Se non hai già un keystore per la upload
key, generane uno con `keytool` (mai con validità di un giorno come nei test locali fatti in
questa sessione):

```sh
keytool -genkeypair -v -keystore miotreno-upload.jks -alias miotreno \
  -keyalg RSA -keysize 2048 -validity 9125
```

Conserva quel file e le password **fuori da questo repo**, in un password manager. Se lo perdi
dopo la prima pubblicazione e non hai usato Play App Signing, non potrai più aggiornare l'app
sotto lo stesso annuncio.

## 2. Cosa carichi

`app-release.aab` da `bundleRelease`, non l'apk — Play Console richiede l'Android App Bundle per
le app nuove. `versionCode`/`versionName` sono in `app/build.gradle.kts` (attualmente `1`/`"1.0"`):
incrementa `versionCode` a ogni caricamento, anche per una revisione minore.

## 3. Informativa privacy

Pubblicata e raggiungibile senza account: **https://axpico.github.io/MioTreno/privacy**
(sorgente in `docs/privacy.md`, servita da GitHub Pages dal branch `master`).

Incolla quell'URL nel campo "Informativa sulla privacy" di Play Console. Il testo copre:
posizione approssimativa usata solo sul dispositivo, i due servizi terzi contattati
(ViaggiaTreno, feed scioperi MIT), i dati che restano in locale, **Google AdMob e il consenso
UMP in SEE/UK**, e come cancellare tutto.

Se cambi qualcosa nell'app che tocca i dati trattati, aggiorna `docs/privacy.md` **e** la data
in cima: l'informativa e' una dichiarazione, non un adempimento da riempire una volta sola.

## 4. Scheda Play Store: testi e grafica

Tutto è nel repo, nel formato di `fastlane supply` (cartella `fastlane/metadata/android/it-IT/`):
si può incollare a mano in Play Console oppure caricare con `fastlane supply` se un giorno lo
installi. Solo italiano: l'app è solo in italiano, una scheda inglese prometterebbe altro.

| Campo Play Console | File | Limite |
|---|---|---|
| Nome app | `title.txt` | 30 |
| Descrizione breve | `short_description.txt` | 80 |
| Descrizione completa | `full_description.txt` | 4000 |
| Note di rilascio (versionCode 1) | `changelogs/1.txt` | 500 |
| Icona 512×512 | `images/icon.png` | PNG 32 bit |
| Grafica in primo piano 1024×500 | `images/featureGraphic.png` | |
| Screenshot telefono (7) | `images/phoneScreenshots/` | 1080×1920, 9:16 |

**Categoria**: Viaggi e info locali. **Tag**: trasporti pubblici, treni.

### Grafica

Generata da `fastlane/store_assets/genera.sh` (ImageMagick, rsvg-convert, uv) a partire dalle
catture **reali** in `fastlane/store_assets/raw/`: dati veri dell'API, nessun mockup. L'icona
512 è disegnata dagli stessi path dei vector del launcher (`ic_launcher_bg.xml` e
`ic_launcher_fg.xml`): se cambi l'icona dell'app, aggiorna anche lo script.

Per rifare le catture (telefono collegato, app installata, un treno seguito già partito):

```sh
adb shell settings put global sysui_demo_allowed 1
adb shell am broadcast -a com.android.systemui.demo -e command enter
adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 0941
adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false
adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false
adb exec-out screencap -p > fastlane/store_assets/raw/home_dark.png   # ecc., vedi lo script
adb shell am broadcast -a com.android.systemui.demo -e command exit
adb shell settings put global sysui_demo_allowed 0
```

La tendina con il Live Update (`shade_real.png`) va catturata **fuori** dal demo mode, che
nasconde le notifiche. Lo screenshot nativo del telefono (1084×2412) è più lungo di 2:1 e Play
lo rifiuterebbe così com'è: per questo lo script lo impagina su una tela 9:16.

Niente marchi o loghi FS/Trenitalia nella grafica: è il modo più rapido per farsi rimuovere.

## 5. Dichiarazioni obbligatorie in Play Console

Vanno dichiarate come sono davvero: una data safety sbagliata è motivo di rimozione, non
un dettaglio burocratico. Risposte pronte, sezione per sezione:

**Accesso all'app** — Tutte le funzionalità sono disponibili senza restrizioni (niente login).

**Annunci** — Sì, l'app contiene annunci (banner AdMob e interstitial occasionale).

**Classificazione dei contenuti (IARC)** — Categoria "Utilità, produttività, comunicazione o
altro". Rispondi **No** a violenza, sesso, linguaggio, sostanze, gioco d'azzardo; **No** a
interazione tra utenti, condivisione di posizione con altri utenti, acquisti digitali.
Esito atteso: PEGI 3 / Everyone.

**Pubblico di destinazione** — 18+ (o 13+). Non rivolta ai minori: evita gli obblighi della
policy Famiglie, incompatibili con AdMob personalizzato.

**Sicurezza dei dati (Data safety)**
- Raccolta o condivisione di dati utente: **Sì**.
- Crittografia in transito: **Sì** per AdMob (HTTPS). *Nota*: le richieste a ViaggiaTreno
  sono HTTP perché l'API non offre HTTPS, ma non trasportano dati personali.
- Possibilità di chiedere la cancellazione: **Non applicabile** (nessun account, nessun dato
  sui server dello sviluppatore; si cancella tutto disinstallando).
- **Posizione approssimativa** — raccolta, **non condivisa**, trattata solo sul dispositivo
  (spunta "elaborati temporaneamente"), **facoltativa**, scopo: Funzionalità dell'app.
- **ID dispositivo o altri ID** (ID pubblicità) — raccolti e **condivisi** da Google AdMob,
  scopo: Pubblicità o marketing, Analisi. Facoltativi solo dove il consenso UMP lo prevede:
  in Play vanno dichiarati come raccolti.
- **Interazioni con l'app** e **diagnostica** (crash) — raccolti e condivisi da AdMob, scopo:
  Pubblicità, Analisi.
- Nessun'altra categoria: niente nome, email, contatti, foto, messaggi, dati finanziari o
  sanitari.

**Informativa privacy** — https://axpico.github.io/MioTreno/privacy

**App governativa / notizie / sanitaria / finanziaria** — No a tutte.

**Contatto sviluppatore** — ale@picone.it (la stessa dell'informativa).

**App non ufficiale** — il disclaimer "non affiliata a Trenitalia/RFI/FS" è nell'app
(Impostazioni), nel README, nell'informativa e in fondo a `full_description.txt`.

## Già pronto nel repo

- [x] R8/minificazione + shrink risorse (APK release ~5 MB contro ~19 MB del debug),
      verificato installato e funzionante su device Android 16
- [x] `signingConfigs` legge le credenziali da env quando le fornisci
- [x] Bundle (`.aab`) buildabile
- [x] Informativa privacy pubblicata su GitHub Pages
- [x] Disclaimer "app non ufficiale" in app, README e informativa
- [x] Solo `ACCESS_COARSE_LOCATION`; l'app resta pienamente usabile se il permesso e' negato
- [x] CI su GitHub Actions: ktlint, unit test, Android lint e build di release
- [x] Android lint senza errori
- [x] Testi della scheda (it-IT), icona 512, grafica 1024×500 e 7 screenshot in `fastlane/`
- [x] Risposte pronte per data safety, classificazione e pubblico (sezione 5)

## Bloccanti noti

- [ ] **AdMob e' ancora sugli ID di test di Google** (`AndroidManifest.xml`, `data/AdsManager.kt`).
      Pubblicando cosi' gli annunci si vedono ma non generano nulla: sostituisci App ID e unit ID
      con i tuoi prima di promuovere in produzione.

## Ancora da fare (solo tu puoi farlo)

- [ ] Generare la upload key e completare Play App Signing in console
- [ ] Creare l'app in Play Console e rispondere al questionario contenuti/pubblico
- [ ] Incollare l'URL dell'informativa privacy
- [ ] Caricare `app-release.aab` firmato sul canale **test interno**, installarlo da Play e
      riverificare le notifiche sulla build dello store, poi promuovere
