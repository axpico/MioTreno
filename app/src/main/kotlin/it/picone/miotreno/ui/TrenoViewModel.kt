package it.picone.miotreno.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.picone.miotreno.Deps
import it.picone.miotreno.data.Impostazioni
import it.picone.miotreno.data.NOMI_REGIONI
import it.picone.miotreno.data.rilevanteOggiODomani
import it.picone.miotreno.domain.DettaglioTreno
import it.picone.miotreno.domain.ElementoStazione
import it.picone.miotreno.domain.etichetteCluster
import it.picone.miotreno.domain.ProssimoTreno
import it.picone.miotreno.domain.RitardoAtteso
import it.picone.miotreno.domain.ritardiAttesi
import it.picone.miotreno.domain.Sciopero
import it.picone.miotreno.domain.Statistiche
import it.picone.miotreno.domain.Stazione
import it.picone.miotreno.domain.StazioneCorrente
import it.picone.miotreno.domain.TrenoSeguito
import it.picone.miotreno.domain.trenoInEvidenza
import it.picone.miotreno.domain.calcolaStatistiche
import it.picone.miotreno.domain.PeriodoFiltro
import it.picone.miotreno.domain.filtraPerPeriodo
import it.picone.miotreno.domain.filtraPerStazione
import it.picone.miotreno.domain.passaPer
import it.picone.miotreno.widget.aggiornaWidget
import it.picone.miotreno.work.ricalcolaNotifica
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Schermata { Onboarding, Home, Dettaglio, Statistiche, Impostazioni }

/**
 * Stato puro della UI: solo modelli di dominio e primitive, nessun tipo Compose.
 * Il ViewModel è l'unico che lo scrive; le schermate lo leggono e mandano eventi.
 */
data class UiState(
    val schermata: Schermata = Schermata.Home,
    val caricamento: Boolean = true,
    val errore: String? = null,
    val stazione: StazioneCorrente? = null,
    val treni: List<ProssimoTreno> = emptyList(),
    val aggiornatoAlle: Long? = null,
    val sciopero: Sciopero? = null,
    val impostazioni: Impostazioni = Impostazioni(),
    val statistiche: Statistiche? = null,
    /** Corsa che l'utente sta seguendo, già filtrata per validità. */
    val seguito: TrenoSeguito? = null,
    val permessoNegato: Boolean = false,
    /** Notifiche consentite dal sistema. Falso = tracking solo in-app, nessun avviso. */
    val notifichePermesse: Boolean = true,
    /** Ritardo "atteso" dallo storico, per numero treno; assente se i dati non bastano. */
    val ritardiAttesi: Map<Int, RitardoAtteso> = emptyMap(),
    // dettaglio
    val trenoSelezionato: ProssimoTreno? = null,
    val dettaglio: DettaglioTreno? = null,
    val caricamentoDettaglio: Boolean = false,
    // scelta manuale della stazione
    val selettoreStazione: Boolean = false,
    val stazioni: List<Stazione> = emptyList(),
    // scelta della destinazione
    val selettoreDestinazione: Boolean = false,
    val destinazioneNome: String? = null,
    /** Nome regione usato per il filtro scioperi, per il testo in Impostazioni. */
    val regioneScioperiNome: String? = null,
    // stazione di passaggio obbligata
    val selettorePassaggio: Boolean = false,
    val stazionePassaggioNome: String? = null,
    /** Numeri dei treni verificati come passanti per [Impostazioni.stazionePassaggio]. */
    val treniConPassaggio: Set<Int> = emptySet(),
    /** Percorso reale (fermate passate/prossime) per la barra viaggio, per numero treno: la card
     * hero (prossima partenza) e quella della corsa seguita possono essere due treni diversi, e
     * servono entrambe i dati reali. */
    val dettagliViaggio: Map<Int, DettaglioTreno.Ok> = emptyMap(),
    // filtri statistiche
    val filtroPeriodo: PeriodoFiltro = PeriodoFiltro.Sempre,
    val filtroStazione: String? = null,
    val selettoreFiltroStazione: Boolean = false,
    /** One-shot: consumato da MainActivity non appena mostra l'interstitial. */
    val mostraInterstitial: Boolean = false,
) {
    /**
     * Come distinguere in elenco i treni di scali gemelli (Garibaldi superficie vs
     * sotterranea). Derivato: non c'è stato da tenere in sincrono.
     */
    val etichettePartenza: Map<String, String> get() = etichetteCluster(stazione?.nomi.orEmpty())
}

private const val POLL_DETTAGLIO_MS = 30_000L
private const val TICK_MS = 10_000L

class TrenoViewModel : ViewModel() {

    private val repo = Deps.repository
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /**
     * Orologio della UI: il countdown si aggiorna da solo ogni 10 s. `WhileSubscribed` lo
     * ferma quando nessuna schermata lo osserva, cioè con l'app in background.
     */
    val ora: StateFlow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(TICK_MS)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), System.currentTimeMillis())

    private var pollJob: Job? = null

    /** Treno chiesto dal widget, in attesa che la lista sia caricata. */
    private var trenoDaAprire: Int? = null
    private var seguiAllApertura = false

    /**
     * Esito di [passaPer] per treno+data: le fermate di un treno non cambiano tra un poll e
     * l'altro, quindi niente richieste `andamentoTreno` ripetute per lo stesso treno nella
     * sessione — l'app è già conservativa sul consumo di un'API non ufficiale senza SLA.
     * ponytail: la cache non viene mai invalidata all'interno della sessione (basta per un
     * badge informativo, non serve un TTL).
     */
    private val passaggioCache = mutableMapOf<String, Boolean>()
    private var stazionePassaggioCodice: String? = null
    private var destinazioneInizializzata = false

    init {
        viewModelScope.launch {
            Deps.impostazioni.flow.collect { imp ->
                if (imp.stazionePassaggio != stazionePassaggioCodice) {
                    stazionePassaggioCodice = imp.stazionePassaggio
                    val nome = imp.stazionePassaggio
                        ?.let { cod -> runCatching { repo.stazioni() }.getOrDefault(emptyList()).firstOrNull { it.codice == cod }?.nome }
                    _state.update { it.copy(stazionePassaggioNome = nome, treniConPassaggio = emptySet()) }
                    verificaPassaggio()
                }
                val nomeDestinazione = imp.stazioneDestinazione?.let { cod ->
                    runCatching { repo.stazioni() }.getOrDefault(emptyList()).firstOrNull { it.codice == cod }?.nome
                }
                _state.update { it.copy(impostazioni = imp, destinazioneNome = nomeDestinazione ?: it.destinazioneNome) }
                if (!destinazioneInizializzata) {
                    destinazioneInizializzata = true
                    if (imp.stazioneDestinazione == null) {
                        _state.update { it.copy(schermata = Schermata.Onboarding, caricamento = false) }
                    } else {
                        val regione = runCatching { repo.regioneDestinazione() }.getOrNull()?.let { NOMI_REGIONI[it] }
                        _state.update { it.copy(regioneScioperiNome = regione) }
                        aggiorna()
                    }
                }
            }
        }
        viewModelScope.launch {
            Deps.impostazioni.seguito.collect { s -> _state.update { it.copy(seguito = s) } }
        }
    }

    /** [silenzioso] = polling in background della home: niente spinner, niente errore a video. */
    fun aggiorna(silenzioso: Boolean = false) {
        viewModelScope.launch {
            if (!silenzioso) _state.update { it.copy(caricamento = true, errore = null) }
            runCatching {
                val imp = _state.value.impostazioni
                val destinazione = imp.stazioneDestinazione ?: run {
                    _state.update { it.copy(caricamento = false, schermata = Schermata.Onboarding) }
                    return@launch
                }
                if (imp.stazioneManuale == null && !Deps.location.hasPermission()) {
                    _state.update { it.copy(caricamento = false, permessoNegato = true) }
                    return@launch
                }
                val stazione = Deps.stazione.risolvi(usaUltimaNota = false)
                    ?: error("Posizione non disponibile: attiva il GPS o scegli la stazione a mano.")

                val seguito = _state.value.seguito
                val treni = repo.prossimiTreni(stazione.codici, destinazione, seguito = seguito)
                treni.firstOrNull { it.numeroTreno == seguito?.numeroTreno }?.let { Deps.impostazioni.aggiornaSeguito(it) }
                val sciopero = repo.scioperiInCache().rilevanteOggiODomani()
                repo.salvaRitardi(treni, stazione.codice, System.currentTimeMillis())
                val attesi = ritardiAttesi(repo.storicoRitardi(), treni)

                _state.update {
                    it.copy(
                        caricamento = false,
                        permessoNegato = false,
                        stazione = stazione,
                        treni = treni,
                        ritardiAttesi = attesi,
                        sciopero = sciopero.takeIf { _ -> it.impostazioni.avvisiSciopero },
                        aggiornatoAlle = System.currentTimeMillis(),
                        errore = null,
                        // il treno aperto va tenuto allineato: il badge cambia colore da solo
                        trenoSelezionato = it.trenoSelezionato?.let { sel ->
                            treni.firstOrNull { t -> t.numeroTreno == sel.numeroTreno } ?: sel
                        },
                    )
                }
                aggiornaTracciamentoSeguito(treni, seguito, destinazione)
                runCatching { aggiornaWidget(Deps.app) }
                verificaPassaggio()
                caricaDettaglioEvidenza(treni, seguito, destinazione)

                trenoDaAprire?.let { numero ->
                    trenoDaAprire = null
                    treni.firstOrNull { it.numeroTreno == numero }?.let(::apriRichiesto)
                }
            }.onFailure { e ->
                if (!silenzioso || _state.value.treni.isEmpty()) {
                    _state.update { it.copy(caricamento = false, errore = e.message ?: "Errore di rete") }
                }
            }
        }
    }

    /** Anche in Home verifichiamo l'arrivo: la corsa seguita non deve restare appesa al refresh. */
    private fun aggiornaTracciamentoSeguito(treni: List<ProssimoTreno>, seguito: TrenoSeguito?, codDestinazione: String) {
        val treno = treni.firstOrNull { it.numeroTreno == seguito?.numeroTreno } ?: return
        viewModelScope.launch {
            val d = runCatching { repo.dettaglio(treno, codDestinazione) }.getOrNull() as? DettaglioTreno.Ok ?: return@launch
            if (d.indiceDestinazione >= 0 && d.indiceCorrente >= d.indiceDestinazione) {
                Deps.impostazioni.smettiDiSeguire()
                runCatching { aggiornaWidget(Deps.app, seguito = 0) }
            }
        }
    }

    /**
     * Percorso reale per la barra viaggio delle card che lo mostrano: la hero (prossima
     * partenza, sempre la prima della lista) e la corsa seguita, se diversa dalla prima.
     */
    private fun caricaDettaglioEvidenza(treni: List<ProssimoTreno>, seguito: TrenoSeguito?, codDestinazione: String) {
        val hero = treni.firstOrNull { !it.cancellato }
        val seguita = trenoInEvidenza(treni, seguito)?.takeIf { it.numeroTreno != hero?.numeroTreno }
        val daCaricare = listOfNotNull(hero, seguita)
        if (daCaricare.isEmpty()) {
            _state.update { it.copy(dettagliViaggio = emptyMap()) }
            return
        }
        viewModelScope.launch {
            val esito = coroutineScope {
                daCaricare.map { t ->
                    async { t.numeroTreno to runCatching { repo.dettaglio(t, codDestinazione) }.getOrNull() as? DettaglioTreno.Ok }
                }.map { it.await() }
            }
            val numeriValidi = daCaricare.map { it.numeroTreno }.toSet()
            _state.update {
                it.copy(
                    dettagliViaggio = it.dettagliViaggio
                        .filterKeys { numero -> numero in numeriValidi }
                        + esito.mapNotNull { (numero, d) -> d?.let { numero to it } },
                )
            }
        }
    }

    /**
     * Verifica non bloccante di quali treni in lista passano per [Impostazioni.stazionePassaggio]:
     * la lista è già mostrata, i badge arrivano appena `andamentoTreno` risponde per ogni treno.
     */
    private fun verificaPassaggio() {
        val codice = stazionePassaggioCodice ?: return
        val codDestinazione = _state.value.impostazioni.stazioneDestinazione ?: return
        val treni = _state.value.treni
        if (treni.isEmpty()) return
        viewModelScope.launch {
            val esito = coroutineScope {
                treni.map { t ->
                    async {
                        // la stazione di passaggio fa parte della chiave: lo stesso treno
                        // passa per Rho e non per Gallarate, e senza il codice il verdetto
                        // calcolato per la vecchia scelta verrebbe riusato per la nuova
                        val chiave = "${t.numeroTreno}@${t.dataPartenzaTrenoMs}@$codice"
                        val passa = passaggioCache.getOrPut(chiave) {
                            runCatching { passaPer(repo.dettaglio(t, codDestinazione), codice) }.getOrDefault(false)
                        }
                        t.numeroTreno to passa
                    }
                }.map { it.await() }
            }
            if (stazionePassaggioCodice != codice) return@launch
            _state.update { it.copy(treniConPassaggio = esito.filter { (_, p) -> p }.map { it.first }.toSet()) }
        }
    }

    fun vaiA(schermata: Schermata) {
        pollJob?.cancel()
        _state.update { it.copy(schermata = schermata) }
        if (schermata == Schermata.Statistiche) caricaStatistiche()
    }

    fun apri(treno: ProssimoTreno) {
        if (treno.cancellato) return
        val codDestinazione = _state.value.impostazioni.stazioneDestinazione ?: return
        _state.update {
            it.copy(
                schermata = Schermata.Dettaglio,
                trenoSelezionato = treno,
                dettaglio = null,
                caricamentoDettaglio = true,
            )
        }
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (true) {
                val d = runCatching { repo.dettaglio(treno, codDestinazione) }
                    .getOrDefault(DettaglioTreno.DatiNonDisponibili)
                _state.update { it.copy(dettaglio = d, caricamentoDettaglio = false) }
                val ok = d as? DettaglioTreno.Ok
                if (ok != null && ok.indiceDestinazione >= 0 && ok.indiceCorrente >= ok.indiceDestinazione) {
                    if (_state.value.seguito?.numeroTreno == treno.numeroTreno) {
                        Deps.impostazioni.smettiDiSeguire()
                        runCatching { aggiornaWidget(Deps.app, seguito = 0) }
                    }
                    break
                }
                delay(POLL_DETTAGLIO_MS)
            }
        }
    }

    fun setNotifichePermesse(v: Boolean) = _state.update { it.copy(notifichePermesse = v) }

    fun indietro() {
        pollJob?.cancel()
        pollJob = null
        _state.update {
            it.copy(schermata = Schermata.Home, trenoSelezionato = null, dettaglio = null)
        }
    }

    private fun caricaStatistiche() {
        viewModelScope.launch {
            val s = _state.value
            val filtrati = filtraPerStazione(filtraPerPeriodo(repo.storicoRitardi(), s.filtroPeriodo), s.filtroStazione)
            _state.update { it.copy(statistiche = calcolaStatistiche(filtrati)) }
        }
    }

    fun impostaFiltroPeriodo(p: PeriodoFiltro) {
        _state.update { it.copy(filtroPeriodo = p) }
        caricaStatistiche()
    }

    fun apriSelettoreFiltroStazione() {
        _state.update { it.copy(selettoreFiltroStazione = true) }
        if (_state.value.stazioni.isEmpty()) viewModelScope.launch {
            val s = runCatching { repo.stazioni() }.getOrDefault(emptyList()).sortedBy { it.nome }
            _state.update { it.copy(stazioni = s) }
        }
    }

    fun chiudiSelettoreFiltroStazione() = _state.update { it.copy(selettoreFiltroStazione = false) }

    /** null = tutte le stazioni. */
    fun sceglifiltroStazione(codice: String?) {
        _state.update { it.copy(filtroStazione = codice, selettoreFiltroStazione = false) }
        caricaStatistiche()
    }

    fun setNotifiche(v: Boolean) = viewModelScope.launch {
        Deps.impostazioni.setNotifiche(v)
        ricalcolaNotifica(Deps.app)
    }

    fun setAvvisiSciopero(v: Boolean) = viewModelScope.launch { Deps.impostazioni.setAvvisiSciopero(v) }

    fun setAdsAbilitate(v: Boolean) = viewModelScope.launch { Deps.impostazioni.setAdsAbilitate(v) }

    fun interstitialMostrato() = _state.update { it.copy(mostraInterstitial = false) }

    /** Cadenza dell'interstitial: mai su polling/notifiche, solo su cambi deliberati di stazione. */
    private suspend fun contaEProponiInterstitial() {
        if (!_state.value.impostazioni.adsAbilitate) return
        if (Deps.impostazioni.incrementaContatoreCambioStazione()) {
            _state.update { it.copy(mostraInterstitial = true) }
        }
    }

    fun setAnticipo(v: Int) = viewModelScope.launch {
        Deps.impostazioni.setAnticipo(v)
        ricalcolaNotifica(Deps.app)
    }

    // ── stazione manuale ──────────────────────────────────────────────────────

    fun apriSelettoreStazione() {
        _state.update { it.copy(selettoreStazione = true) }
        if (_state.value.stazioni.isEmpty()) viewModelScope.launch {
            val s = runCatching { repo.stazioni() }.getOrDefault(emptyList()).sortedBy { it.nome }
            _state.update { it.copy(stazioni = s) }
        }
    }

    fun chiudiSelettoreStazione() = _state.update { it.copy(selettoreStazione = false) }

    /** null = torna al GPS. */
    fun scegliStazione(codice: String?) = viewModelScope.launch {
        codice?.let { repo.risolviStazione(it) }
        Deps.impostazioni.setStazioneManuale(codice)
        _state.update { it.copy(selettoreStazione = false, treni = emptyList()) }
        aggiorna()
        ricalcolaNotifica(Deps.app)
        contaEProponiInterstitial()
    }

    /** Ricerca nazionale live per nome, usata dai selettori di stazione manuale e destinazione. */
    suspend fun cercaStazioni(query: String): List<ElementoStazione> = repo.cercaStazioni(query)

    // ── destinazione ─────────────────────────────────────────────────────────

    fun apriSelettoreDestinazione() = _state.update { it.copy(selettoreDestinazione = true) }

    fun chiudiSelettoreDestinazione() = _state.update { it.copy(selettoreDestinazione = false) }

    fun scegliDestinazione(codice: String) = viewModelScope.launch {
        val primaVolta = _state.value.impostazioni.stazioneDestinazione == null
        val stazione = repo.risolviStazione(codice)
        val idRegione = repo.regioneDi(codice)
        idRegione?.let { repo.salvaRegioneDestinazione(it) }
        Deps.impostazioni.setStazioneDestinazione(codice)
        if (primaVolta) Deps.impostazioni.setOnboardingCompletato(true)
        _state.update {
            it.copy(
                selettoreDestinazione = false,
                schermata = Schermata.Home,
                treni = emptyList(),
                destinazioneNome = stazione?.nome ?: it.destinazioneNome,
                regioneScioperiNome = idRegione?.let { r -> NOMI_REGIONI[r] } ?: it.regioneScioperiNome,
            )
        }
        aggiorna()
        ricalcolaNotifica(Deps.app)
        if (!primaVolta) contaEProponiInterstitial()
    }

    /**
     * Inverte partenza e destinazione (per il viaggio di ritorno). La partenza diventa sempre
     * manuale — se prima era via GPS, la posizione rilevata in quel momento viene "congelata"
     * come nuova destinazione — e non si torna più al GPS automaticamente dopo l'inversione.
     */
    fun scambiaOrigineDestinazione() = viewModelScope.launch {
        val vecchiaOrigine = state.value.stazione ?: return@launch
        val vecchiaDestinazioneCodice = state.value.impostazioni.stazioneDestinazione

        val nuovaDestinazioneStazione = repo.risolviStazione(vecchiaOrigine.codice)
        vecchiaDestinazioneCodice?.let { repo.risolviStazione(it) }
        val idRegione = vecchiaDestinazioneCodice?.let { repo.regioneDi(it) }
        idRegione?.let { repo.salvaRegioneDestinazione(it) }

        // La stazione di passaggio sopravvive all'inversione: il viaggio di ritorno percorre
        // la stessa tratta al contrario, quindi il vincolo "passa per X" vale ancora. Si
        // azzera solo se X è diventata uno dei due capolinea, dove non vincolerebbe nulla.
        val passaggio = state.value.impostazioni.stazionePassaggio
        val nuovaOrigine = vecchiaDestinazioneCodice
        val nuovaDestinazione = vecchiaOrigine.codice
        val passaggioSuperfluo = passaggio != null &&
            (passaggio == nuovaOrigine || passaggio == nuovaDestinazione)

        Deps.impostazioni.setStazioneManuale(nuovaOrigine)
        Deps.impostazioni.setStazioneDestinazione(nuovaDestinazione)
        if (passaggioSuperfluo) Deps.impostazioni.setStazionePassaggio(null)
        if (state.value.seguito != null) Deps.impostazioni.smettiDiSeguire()

        _state.update {
            it.copy(
                treni = emptyList(),
                destinazioneNome = nuovaDestinazioneStazione?.nome ?: vecchiaOrigine.nome,
                regioneScioperiNome = idRegione?.let { r -> NOMI_REGIONI[r] } ?: it.regioneScioperiNome,
                stazionePassaggioNome = if (passaggioSuperfluo) null else it.stazionePassaggioNome,
                // i badge vanno riverificati sui treni della nuova tratta
                treniConPassaggio = emptySet(),
            )
        }
        aggiorna()
        ricalcolaNotifica(Deps.app)
        contaEProponiInterstitial()
    }

    // ── stazione di passaggio ────────────────────────────────────────────────

    fun apriSelettorePassaggio() {
        _state.update { it.copy(selettorePassaggio = true) }
        if (_state.value.stazioni.isEmpty()) viewModelScope.launch {
            val s = runCatching { repo.stazioni() }.getOrDefault(emptyList()).sortedBy { it.nome }
            _state.update { it.copy(stazioni = s) }
        }
    }

    fun chiudiSelettorePassaggio() = _state.update { it.copy(selettorePassaggio = false) }

    /** null = disattiva l'evidenziazione. La lista dei treni resta quella già caricata. */
    fun sceglipassaggio(codice: String?) = viewModelScope.launch {
        Deps.impostazioni.setStazionePassaggio(codice)
        _state.update { it.copy(selettorePassaggio = false) }
    }

    /**
     * Tap su una riga del widget. La lista potrebbe non essere ancora pronta, quindi la
     * richiesta viene messa in attesa e servita da [aggiorna] appena i treni arrivano.
     */
    fun apriDaWidget(numeroTreno: Int, segui: Boolean = false) {
        seguiAllApertura = segui
        val gia = _state.value.treni.firstOrNull { it.numeroTreno == numeroTreno }
        if (gia != null) apriRichiesto(gia) else trenoDaAprire = numeroTreno
    }

    private fun apriRichiesto(treno: ProssimoTreno) {
        apri(treno)
        if (seguiAllApertura) {
            seguiAllApertura = false
            if (_state.value.seguito?.numeroTreno != treno.numeroTreno) cambiaSeguito(treno)
        }
    }

    /** Segui questa corsa, o smetti se è già quella seguita. */
    fun cambiaSeguito(treno: ProssimoTreno) = viewModelScope.launch {
        val smetti = _state.value.seguito?.numeroTreno == treno.numeroTreno
        if (smetti) Deps.impostazioni.smettiDiSeguire() else Deps.impostazioni.segui(treno)
        runCatching { aggiornaWidget(Deps.app, seguito = if (smetti) 0 else treno.numeroTreno) }
        // la notifica riguarda la corsa seguita: va ricalcolata subito, non fra 15 minuti
        ricalcolaNotifica(Deps.app)
    }

    fun permessoConcesso() {
        _state.update { it.copy(permessoNegato = false) }
        aggiorna()
    }

    override fun onCleared() {
        pollJob?.cancel()
    }
}
