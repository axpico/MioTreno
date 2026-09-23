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

## 4. Copy per la scheda Play Store

**Nome app**: MioTreno

**Descrizione breve** (max 80 caratteri — verificala in Play Console, i limiti sono enforced lì):

> Treni in tempo reale dalla tua stazione alla destinazione che scegli.

**Descrizione completa** (bozza, adatta liberamente):

> MioTreno mostra i treni in partenza dalla stazione più vicina a te, diretti verso la
> destinazione che scegli tu — qualsiasi stazione italiana, non una tratta fissa.
>
> • Prossimi treni con binario, ritardo in tempo reale e countdown alla partenza
> • Tracking fermata-per-fermata mentre il treno è in viaggio
> • Segui una corsa: resta in evidenza su app, widget e notifica finché non arrivi
> • Storico locale dei tuoi ritardi, con statistiche per giorno e fascia oraria
> • Avvisi sciopero sulla regione della tua destinazione
> • Widget in home screen, tre formati
> • Notifica prima della partenza, con preavviso configurabile
>
> Nessun account richiesto. I tuoi dati restano sul telefono — vedi l'informativa privacy.
>
> L'app è gratuita e sostenuta da pubblicità leggera, che puoi disattivare dalle impostazioni.
>
> Dati da ViaggiaTreno (interfaccia non ufficiale RFI/Trenitalia) e dal feed scioperi del
> Ministero delle Infrastrutture e dei Trasporti. MioTreno è un progetto indipendente, non
> affiliato a Trenitalia, RFI o al MIT.

**Categoria**: Viaggi e informazioni locali (o "Mappe e navigazione", a seconda di cosa mostra
meglio l'app nella tua zona di Play Console).

**Icona / grafica**: l'icona app è già aggiornata nel redesign (nero + ambra,
`app/src/main/res/drawable/ic_launcher_fg.xml`). Servono ancora, presi da screenshot reali
dell'app (non mockup): almeno 2 screenshot telefono (consigliati: home con un treno in ritardo
per mostrare il semaforo colori, e il dettaglio con la timeline) e una feature graphic 1024×500 —
questi non sono generabili da codice, vanno catturati/composti a parte quando vuoi procedere.

## 5. Dichiarazioni obbligatorie in Play Console

Vanno dichiarate come sono davvero: una data safety sbagliata e' motivo di rimozione, non
un dettaglio burocratico.

- **Data safety**: raccolta di **"Posizione approssimativa"** (l'app non chiede piu'
  `ACCESS_FINE_LOCATION`) con scopo "Funzionalita' dell'app"; non inviata a server dello
  sviluppatore, che non esistono. Dichiara inoltre quanto raccolto da **Google AdMob**
  (identificativo pubblicitario e dati d'uso, a scopo pubblicitario): e' una libreria di
  terze parti dentro l'app, quindi e' responsabilita' tua dichiararla.
- **Pubblicita'**: **si', l'app contiene annunci.** Va spuntato "Contiene annunci" nella
  scheda e dichiarato nel questionario.
- **Consenso UMP**: in SEE/UK il consenso agli annunci personalizzati e' raccolto dalla
  piattaforma di messaggistica utente di Google, gia' integrata (`data/AdsManager.kt`).
- **Contenuti a pagamento**: nessuno (niente acquisti in-app, niente abbonamenti).
- **Pubblico di destinazione**: non rivolta ai minori di 13 anni.
- **App non ufficiale**: il disclaimer "non affiliata a Trenitalia/RFI/FS" e' dentro l'app
  (Impostazioni) e nel README; ripetilo nella descrizione della scheda. Usare marchi o
  loghi FS/Trenitalia nella grafica sarebbe il modo piu' rapido per farsi rimuovere.
- **Contatto sviluppatore**: ale@picone.it (la stessa dell'informativa).

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

## Bloccanti noti

- [ ] **AdMob e' ancora sugli ID di test di Google** (`AndroidManifest.xml`, `data/AdsManager.kt`).
      Pubblicando cosi' gli annunci si vedono ma non generano nulla: sostituisci App ID e unit ID
      con i tuoi prima di promuovere in produzione.

## Ancora da fare (solo tu puoi farlo)

- [ ] Generare la upload key e completare Play App Signing in console
- [ ] Creare l'app in Play Console e rispondere al questionario contenuti/pubblico
- [ ] Incollare l'URL dell'informativa privacy
- [ ] Screenshot reali + feature graphic 1024×500
- [ ] Caricare `app-release.aab` firmato sul canale **test interno**, installarlo da Play e
      riverificare le notifiche sulla build dello store, poi promuovere
