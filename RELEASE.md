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

Bozza scritta e pubblicata: **https://claude.ai/artifact/KMtzwatV5UQwW4BnKNGRE3**

È privata di default — prima di incollarla in Play Console (campo "Informativa sulla privacy"),
aprila e usa il menu di condivisione della pagina per renderla pubblica: i revisori di Google
devono poterla leggere senza account. Se preferisci ospitarla altrove (un tuo dominio, GitHub
Pages), il contenuto HTML è comunque tuo da riusare — il testo copre: uso del GPS (elaborato solo
sul dispositivo, mai inviato come coordinate), i due servizi terzi contattati (ViaggiaTreno, feed
scioperi MIT), cosa resta solo sul telefono, assenza di account/pubblicità/tracciamento, e come
cancellare i dati.

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
> Nessun account richiesto, nessuna pubblicità, nessun tracciamento. I tuoi dati restano sul
> telefono — vedi l'informativa privacy per i dettagli.
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

- **Data safety**: dichiara raccolta di "Posizione approssimativa/precisa" con scopo
  "Funzionalità dell'app", non condivisa con terze parti per pubblicità, cancellabile
  dall'utente (disinstallazione). Nessun'altra categoria di dati raccolta.
- **Pubblico di destinazione**: non rivolta specificamente ai minori di 13 anni.
- **Contenuti a pagamento / pubblicità**: nessuno dei due.
- **Contatto sviluppatore**: email richiesta da Play Console — vedi la mail nell'informativa
  privacy se vuoi riusare la stessa.

## Già pronto (fatto in questa sessione)

- [x] R8/minificazione + shrink risorse per la release (~16.9 MB → ~3.4 MB APK; verificato
  end-to-end su device con un pacchetto di test separato, poi rimosso — vedi keep-rule per i
  Worker in `app/proguard-rules.pro`)
- [x] `signingConfigs` pronto a leggere le credenziali quando le fornisci
- [x] Bundle (`.aab`) verificato buildabile
- [x] Icona app aggiornata al nuovo redesign
- [x] Bozza informativa privacy scritta e pubblicata

## Ancora da fare (solo tu puoi farlo)

- [ ] Generare/recuperare la upload key, o completare il flusso Play App Signing in console
- [ ] Creare l'app in Play Console e rispondere al questionario contenuti/pubblico
- [ ] Rendere pubblica la pagina privacy (o riospitarla) e incollarne l'URL
- [ ] Screenshot reali + feature graphic 1024×500
- [ ] Caricare `app-release.aab` firmato e inviare in revisione
