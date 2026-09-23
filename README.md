# MioTreno

App Android che mostra i treni in partenza dalla stazione più vicina e diretti alla
**destinazione che scegli tu** (qualsiasi stazione italiana, cercata al primo avvio o
cambiata in qualsiasi momento da Impostazioni), con binario, ritardo, tracking
fermata-per-fermata, storico locale dei ritardi, avvisi sciopero, widget in home screen e
notifica pre-partenza.

Dati da [ViaggiaTreno](http://www.viaggiatreno.it) (API non ufficiale RFI) e dal
[feed RSS scioperi del MIT](https://scioperi.mit.gov.it/mit2/public/scioperi/rss).

## Build

Serve il JDK 21 (il default di sistema è più recente di quanto AGP 8.x supporti); `mise.toml`
lo fissa già per questa directory.

```sh
mise install
./gradlew test           # 24 unit test su fixture reali dell'API
./gradlew assembleDebug
./gradlew installDebug
```

## Come trova i treni

ViaggiaTreno non ha un endpoint "treni da A a B". Invece di indovinare con una whitelist di
direttrici e poi arricchire ogni candidato con `andamentoTreno`, l'app **interseca due liste**:

```
partenze/{stazioneVicina}/{ora}   ∩   arrivi/{destinazioneScelta}/{ora}     (sul numero di treno)
```

Un treno che compare in entrambe parte da dove sei e ferma alla destinazione scelta. Due (tre
con la finestra allargata) chiamate HTTP, nessuna euristica. Il filtro
`orarioPartenza < orarioArrivo` scarta lo stesso numero che viaggia nella direzione opposta.
Questo funziona per qualsiasi coppia di stazioni: non c'è più un itinerario "con un cambio"
calcolato a mano per un'unica destinazione fissa, perché non ha senso generalizzarlo a una
destinazione arbitraria — solo i diretti restano.

`andamentoTreno` viene chiamato solo per il treno che apri in dettaglio.

### Non una stazione, ma il gruppo di stazioni

La ricerca non prende *la* stazione più vicina, ma tutte quelle entro 400 m dalla più vicina.
Lo stesso scalo può avere più codici ViaggiaTreno: Milano Porta Garibaldi (`S01645`) e Porta
Garibaldi Sotterranea (`S01647`) distano 193 m e servono treni diversi — le linee S passano dal
Passante, cioè dalla sotterranea. Verificato sul campo: dal piazzale in superficie il cluster
restituisce 5 treni (i Varese dal Passante *e* gli Arona dalla superficie); con la sola stazione
più vicina se ne perdeva metà, senza alcun errore visibile.

## Cose verificate sull'API, contro quanto si legge in giro

| Assunzione comune | Realtà |
|---|---|
| Nome stazione e codice ViaggiaTreno corrispondono in modo ovvio | No: es. `S01700` è **Milano Centrale**, non "la prima stazione di Milano" che viene in mente. Va sempre risolto via `elencoStazioni`/ricerca, mai indovinato |
| `partenze/{cod}/{epoch_ms}` | l'epoch restituisce `Error`. Serve la stringa `Date.toString()` di JS: `Tue Sep 01 2026 20:46:53 GMT+0200`, percent-encoded |
| `circolante: false` = treno cancellato | **No.** È false per tutti i treni non ancora partiti (21 su 24 in una risposta qualsiasi). Usarlo per le cancellazioni marcherebbe cancellata quasi tutta la lista — vedi il test di regressione in `StatoTrenoTest` |
| Una stazione con più nomi simili (es. "Nord"/"FS", superficie/sotterranea) è un solo codice | No: sono spesso stazioni o codici ViaggiaTreno distinti, a volte a poche centinaia di metri — vedi "Non una stazione, ma il gruppo di stazioni" sotto |
| `andamentoTreno` 204 è un errore | È lo stato normale per treni cancellati o senza dati: l'app lo mostra come "dati non disponibili" |

## Scelte deliberate

- **Niente Room.** Stazioni, storico ritardi e scioperi sono tre file JSON in `filesDir`. Lo
  storico cresce di ~6 righe al giorno; le statistiche sono `groupBy` + `average` in memoria.
- **Niente Retrofit.** Cinque endpoint, uno che risponde in text/plain e uno che risponde 204:
  OkHttp + kotlinx-serialization con parsing difensivo costa meno di un converter factory.
- **Niente Hilt.** `Deps` con quattro lazy singleton.
- **Niente play-services-location.** `LocationManager.getCurrentLocation` con cache a 2 minuti.
- **Un solo file di font.** Inter è variabile: i pesi 400/500/700 arrivano da `FontVariation`,
  le cifre sono tabulari (`tnum`) così gli orari si allineano in colonna.
- **Solo tema scuro.** Sfondo #0A0A0F, card vetro con bordo 1dp translucido, accento blu-viola.
  I colori vivi sono riservati allo stato del treno (verde / ambra / arancio / rosso) e allo
  sciopero (fucsia, con trama a righe). Vedi `CHANGELOG.md` per il redesign.

## Struttura

```
data/       client ViaggiaTreno, feed scioperi, store JSON, repository
domain/     modelli, haversine/stazione più vicina, aggregazioni statistiche
location/   wrapper LocationManager
ui/theme/       token del design: colori, tipografia, forme, molle, tema Material sovrascritto
ui/componenti/  libreria interna: GlassCard, TrainCard, StatusBadge, AnimatedCountdown,
                TimelineStop, StatChart, Toggle, bottoni, icone line-style, pull-to-refresh
ui/             ViewModel + UiState, home, dettaglio, statistiche, impostazioni, selettore stazione
widget/         Glance, tre formati responsive, stesso linguaggio scuro
work/       sync scioperi (1×/giorno), notifica pre-partenza, refresh widget
```

## Stazione di partenza: GPS o scelta a mano

Di default la stazione di partenza è la più vicina al GPS. Dall'header della home o da
Impostazioni si può fissarne una a mano, cercandola in tutta Italia: da quel momento app,
widget e notifica la usano tutti (la regola sta in un solo posto,
`domain/StazioneCorrente.kt` + `data/RisolutoreStazione.kt`) finché non si torna a "Usa GPS".

## Destinazione

La destinazione (dove vuoi arrivare) si sceglie al primo avvio, cercandola fra tutte le
stazioni italiane (`ViaggiaTreno` `cercaStazione`), e si può cambiare in qualsiasi momento da
Impostazioni. Non ha un fallback GPS: senza una destinazione scelta l'app resta sulla schermata
di primo avvio. Gli avvisi sciopero si aggiornano automaticamente sulla regione della
destinazione scelta (risolta una volta sola via `regione/{codStazione}`, non a ogni refresh).

## Seguire una corsa

Dal dettaglio di un treno il pulsante **Segui** lo fissa come corsa del giorno: home, widget e
notifica mostrano quello invece del prossimo treno qualsiasi. Non è un preferito ricorrente —
scade da solo al cambio di giorno o quando il treno ha superato la destinazione scelta
(`domain/Seguito.kt`), quindi non serve ricordarsi di toglierlo.

Se la corsa seguita oggi non c'è (cancellata, o sei a un'altra stazione), widget e notifica
ripiegano sul prossimo treno utile **dicendolo**: `treno 24580 non disponibile da qui`. Ripiegare
in silenzio nasconderebbe il motivo, che è l'unica cosa che l'utente non può dedurre da solo.

## Pubblicità

L'app integra Google AdMob (banner in fondo alla Home + interstitial ogni 4° cambio di
stazione/destinazione) e il consenso privacy Google UMP, obbligatorio in UE prima di mostrare
ads. Tutto è gestito da `data/AdsManager.kt`. L'utente sceglie in onboarding se vuole le ads
(scelta modificabile in ogni momento da Impostazioni → "Disabilita tutte le pubblicità"); se le
attiva, il form di consenso UMP appare subito dopo, sempre in onboarding.

**Gli ID attuali sono quelli di test ufficiali Google** (App ID in `AndroidManifest.xml`, unit
banner/interstitial in `AdsManager.kt`): l'app builda e gira così com'è, ma non va **mai**
pubblicata con questi ID (violazione delle policy AdMob).

Per andare live:

1. Crea un account [AdMob](https://admob.google.com) e aggiungi l'app (Android, package
   `it.picone.miotreno`).
2. Crea due unit pubblicitarie: una Banner e una Interstitial.
3. Sostituisci i tre ID:
   - `AndroidManifest.xml` → meta-data `com.google.android.gms.ads.APPLICATION_ID`.
   - `AdsManager.kt` → costanti `AD_UNIT_BANNER` e `AD_UNIT_INTERSTITIAL`.
4. Pubblica `app-ads.txt` sul dominio dichiarato nella scheda Play Console, con la riga che AdMob
   fornisce (`google.com, pub-XXXXXXXXXXXXXXXX, DIRECT, f08c47fec0942fa0`).
5. Una volta pubblicata l'app, collega l'app AdMob al listing Play Console (AdMob → App →
   Collega a Play Console).
6. Il messaggio di consenso UE (UMP) è configurato di default da Google; per personalizzarlo vai
   in AdMob → Privacy e messaggi.

## Limiti noti

- API non ufficiale, nessuno SLA: ogni schermata ha uno stato d'errore visibile.
- Il binario effettivo è spesso `null` finché il treno non è vicino: normale, la UI distingue
  "previsto" da "confermato".
- Il codice di soppressione *totale* non è documentato; quando c'è, il testo arriva comunque in
  `subTitle` e viene mostrato così com'è.
- Il feed MIT dice *che* c'è sciopero, non quali treni sono garantiti: il banner rimanda alla
  pagina ufficiale Trenitalia.
- Stazioni con più codici simili (es. una "Nord"/FNM distinta dalla principale RFI) non hanno
  trattamento speciale: la risoluzione della stazione non fa case-specifici, quindi se scegli
  quella sbagliata l'app non trova automaticamente i treni per la stazione giusta a meno di
  selezionarla a mano.
- **Un contenitore Glance accetta al massimo 10 figli diretti**: dall'undicesimo in poi
  spariscono in silenzio, senza errori né avvisi. È il motivo per cui i treni in coda al 2×2
  venivano calcolati correttamente ma non comparivano. Le parti ripetute stanno in `Column`
  annidate per restare sotto il limite.
- I widget usano `SizeMode.Exact`, non `Responsive`: con `Responsive` `LocalSize` restituisce il
  breakpoint scelto e non la dimensione vera, quindi il contenuto non può adattarsi a come
  l'utente ridimensiona il widget. Le soglie di larghezza (200dp / 300dp) separano i tre formati;
  quante righe mostrare si calcola dall'altezza reale. Misurata sull'emulatore: un widget alto
  due celle riporta 210dp.
- Nel widget non ci sono animazioni: è `RemoteViews`, quindi il pallino pulsante e l'anello del
  design restano statici. Per lo stesso motivo la barra di avanzamento del 3×2 non ha il pallino
  di posizione: Glance ha solo `defaultWeight()` (parti uguali), non pesi frazionari.
- Dopo aver premuto Segui il widget resta indietro di qualche secondo: `updateAll` rilancia
  `provideGlance`, che rifà le chiamate di rete. Non è un blocco, ma non è istantaneo.
- La barra si riempie solo per la corsa **seguita**, perché è l'unica che resta in lista dopo la
  partenza (`trenoAncoraUtile`). Per gli altri treni, che devono ancora partire, è vuota per
  definizione e sotto c'è scritto "parte tra N min": era il motivo per cui sembrava rotta. Essendo
  al massimo una corsa per volta, la barra chiama `andamentoTreno` una volta per refresh del
  widget (15 min) e mostra la posizione reale (fermate passate su fermate fino a destinazione,
  `progressoReale()`); se il dato non è disponibile ricade sull'interpolazione fra partenza e
  arrivo **effettivi** (programmati + ritardo), la stessa usata prima per tutte le corse.
- Il widget non ha bisogno di un fix GPS per funzionare: l'ultima stazione risolta da una
  posizione vera resta in `ultima_stazione.json` e viene riusata quando in background il fix non
  arriva (telefono in tasca, GPS freddo). Senza questo il widget mostrava un errore per la
  maggior parte dei suoi aggiornamenti.
- Il widget è sempre scuro (`ScuroTb` fisso): non legge il tema di sistema né `GlanceTheme`,
  coerente con la scelta "solo tema scuro" di tutta l'app.
- Una riga del widget non risponde al tocco se l'app è stata chiusa con `force-stop`: Android
  blocca i pending intent dei pacchetti in stato "stopped" finché non li si riapre a mano. Non è
  un bug dell'app, ma va saputo quando si prova da adb.
