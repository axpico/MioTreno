# MioTreno

App Android che mostra i treni in partenza dalla stazione più vicina e diretti alla
**destinazione che scegli tu** (qualsiasi stazione italiana, cercata al primo avvio o
cambiata in qualsiasi momento da Impostazioni), con binario, ritardo, tracking
fermata-per-fermata, storico locale dei ritardi, avvisi sciopero, riquadro nelle impostazioni rapide e
notifica pre-partenza.

Dati da [ViaggiaTreno](http://www.viaggiatreno.it) (API non ufficiale RFI), dalle soluzioni
di viaggio di [LeFrecce](https://www.lefrecce.it) (API non ufficiale Trenitalia, per i viaggi
con cambi) e dal [feed RSS scioperi del MIT](https://scioperi.mit.gov.it/mit2/public/scioperi/rss).

> **App non ufficiale.** Non affiliata né approvata da Trenitalia, RFI o Ferrovie dello
> Stato Italiane. I marchi citati appartengono ai rispettivi titolari.

[Informativa sulla privacy](https://axpico.github.io/MioTreno/privacy) · [Licenza MIT](LICENSE)

## Build

Serve il JDK 21 (il default di sistema è più recente di quanto AGP 8.x supporti); `mise.toml`
lo fissa già per questa directory.

```sh
mise install
./gradlew test           # 145 unit test, in parte su fixture reali dell'API
./gradlew ktlintCheck    # stile (regole in .editorconfig)
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
Questo funziona per qualsiasi coppia di stazioni, ma trova solo i diretti.

### Viaggi con cambi

Accanto ai diretti (disattivabile: Impostazioni → "Mostra anche soluzioni con cambi") la lista
mostra i viaggi con 1–2 cambi, in `data/Coincidenze.kt`:

1. **LeFrecce** `POST /ticket/solutions` dà i viaggi completi. L'id stazione è `83` + le cifre
   del codice ViaggiaTreno su 7 posizioni (`S01700` → `830001700`); per qualche stazione non
   torna (Garibaldi Passante ha un id suo), e allora si passa al punto 2. Il primo treno deve
   comparire nelle `partenze` del cluster: da lì vengono ritardo, binario e codici veri. I diretti
   di LeFrecce si scartano (li dà già ViaggiaTreno).
2. **Ripiego su ViaggiaTreno**, solo se LeFrecce fallisce o non trova cambi: un cambio solo, agli
   scali dove un treno da qui finisce e uno per la destinazione comincia (`codDestinazione` delle
   partenze ∩ `codOrigine` degli arrivi), al massimo 3 scali, ≥ 5 minuti per cambiare. Un cambio
   in una stazione di passaggio così non si vede.

Poi un viaggio per primo treno, e via quelli chiaramente peggiori (partono prima, arrivano dopo,
cambiano di più: `filtraDominati`).

Cose verificate su LeFrecce: `bdoOrigin` di una tratta è il codice ViaggiaTreno dell'origine del
**treno** (quello che vuole `andamentoTreno`), non della stazione dove sali; le tratte non hanno
codici stazione, solo nomi. Per questo dove sali e scendi si riconosce nell'`andamentoTreno` per
orario di tabella (`indicePerOrario`), non per nome. I tratti a piedi (`UB`) in testa e in coda
sono dentro il cluster e si tolgono; quelli in mezzo restano e non contano come cambio.

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
- **Font Atkinson Hyperlegible.** Next per il testo, Mono per orari e numeri, così le cifre si
  allineano in colonna.
- **Tema chiaro e scuro** ("Rail blue"), che segue il sistema. I colori vivi sono riservati
  allo stato del treno (verde / ambra / arancio / rosso) e allo sciopero.

## Struttura

```
data/       client ViaggiaTreno, feed scioperi, store JSON, repository
domain/     modelli, haversine/stazione più vicina, aggregazioni statistiche
location/   wrapper LocationManager
ui/theme/       token del design: colori, tipografia, forme, molle, tema Material sovrascritto
ui/componenti/  libreria interna: GlassCard, TrainCard, StatusBadge, AnimatedCountdown,
                TimelineStop, StatChart, Toggle, bottoni, icone line-style, pull-to-refresh
ui/             ViewModel + UiState, home, dettaglio, statistiche, impostazioni, selettore stazione, tile
work/       sync scioperi (1×/giorno), notifica pre-partenza, tracking della corsa seguita
```

## Stazione di partenza: GPS o scelta a mano

Di default la stazione di partenza è la più vicina al GPS. Dall'header della home o da
Impostazioni si può fissarne una a mano, cercandola in tutta Italia: da quel momento app,
tile e notifica la usano tutti (la regola sta in un solo posto,
`domain/StazioneCorrente.kt` + `data/RisolutoreStazione.kt`) finché non si torna a "Usa GPS".

## Destinazione

La destinazione (dove vuoi arrivare) si sceglie al primo avvio, cercandola fra tutte le
stazioni italiane (`ViaggiaTreno` `cercaStazione`), e si può cambiare in qualsiasi momento da
Impostazioni. Non ha un fallback GPS: senza una destinazione scelta l'app apre direttamente la
ricerca della destinazione. Gli avvisi sciopero si aggiornano automaticamente sulla regione della
destinazione scelta (risolta una volta sola via `regione/{codStazione}`, non a ogni refresh).

## Seguire una corsa

Dal dettaglio di un treno il pulsante **Segui** lo fissa come corsa del giorno: home, tile e
notifica mostrano quello invece del prossimo treno qualsiasi. Non è un preferito ricorrente —
scade da solo al cambio di giorno o quando il treno ha superato la destinazione scelta
(`domain/Seguito.kt`), quindi non serve ricordarsi di toglierlo.

Se la corsa seguita oggi non c'è (cancellata, o sei a un'altra stazione), tile e notifica
ripiegano sul prossimo treno utile **dicendolo**: `treno 24580 non disponibile da qui`. Ripiegare
in silenzio nasconderebbe il motivo, che è l'unica cosa che l'utente non può dedurre da solo.

## Seguire un viaggio con cambi

Segui vale per tutto il viaggio (`work/StatoSeguito.kt`, unico controllo per app, tile e
tracking): home e notifica mostrano la tratta in corso e il prossimo cambio, e si passa
alla tratta dopo quando l'`andamentoTreno` conferma l'arrivo alla stazione di cambio. Un avviso
parte N minuti prima del cambio (Impostazioni → Avviso cambio). Se la coincidenza salta (il
treno dopo è cancellato o parte prima che arrivi il tuo) il resto del viaggio si ripianifica da
solo dalla stazione di cambio, e lo si dice con una notifica.

## Limiti noti

- API non ufficiale, nessuno SLA: ogni schermata ha uno stato d'errore visibile. Vale anche per
  LeFrecce: se non risponde restano i diretti e il ripiego su ViaggiaTreno.
- Il ripiego su ViaggiaTreno costa fino a 12 chiamate in più per refresh, e gira ogni volta che
  LeFrecce non trova cambi (anche su tratte servite bene dai diretti).
- Il passaggio per la stazione scelta si verifica solo sul primo treno di un viaggio con cambi.
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
- La tile e i worker non hanno bisogno di un fix GPS per funzionare: l'ultima stazione risolta da una
  posizione vera resta in `ultima_stazione.json` e viene riusata quando in background il fix non
  arriva (telefono in tasca, GPS freddo).
