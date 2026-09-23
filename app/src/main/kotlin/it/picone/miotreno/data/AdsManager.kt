package it.picone.miotreno.data

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import it.picone.miotreno.BuildConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * ID di TEST ufficiali Google. Vanno sostituiti con i propri prima del rilascio (vedi README.md,
 * sezione "Pubblicità") — insieme all'App ID in AndroidManifest.xml.
 */
private const val AD_UNIT_BANNER = "ca-app-pub-3940256099942544/9214589741"
private const val AD_UNIT_INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712"

/**
 * Un solo banner e un solo interstitial in tutta l'app: ponytail, un oggetto con wiring manuale
 * basta, niente livello di astrazione in più.
 */
object AdsManager {
    const val AD_UNIT_BANNER_ID = AD_UNIT_BANNER

    // ponytail: debug geography EEA in build debug, per poter testare il form UMP anche fuori UE.
    private val DEBUG_CONSENT = BuildConfig.DEBUG

    private lateinit var consentInformation: ConsentInformation
    private var interstitial: InterstitialAd? = null

    /**
     * Backed da uno State Compose: `consentInformation.canRequestAds()` di per sé non farebbe
     * ricomporre nulla quando il consenso arriva in modo asincrono (dopo il form UMP, o dopo la
     * richiesta silenziosa fatta per gli utenti già installati prima di questa versione).
     */
    var puoiMostrareAds by mutableStateOf(false)
        private set

    fun canRequestAds(): Boolean = puoiMostrareAds

    private fun aggiornaStatoConsenso() {
        puoiMostrareAds = ::consentInformation.isInitialized && consentInformation.canRequestAds()
    }

    fun richiedeGestioneConsenso(): Boolean =
        ::consentInformation.isInitialized &&
            consentInformation.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    /**
     * Aggiorna lo stato del consenso e mostra il form UMP se richiesto (utenti UE). Al ritorno,
     * [canRequestAds] riflette l'esito: se true si può inizializzare l'SDK e caricare ads.
     */
    suspend fun richiediConsenso(activity: Activity): Boolean = suspendCancellableCoroutine { cont ->
        consentInformation = UserMessagingPlatform.getConsentInformation(activity)
        val params = ConsentRequestParameters.Builder()
            .apply { if (DEBUG_CONSENT) setConsentDebugSettings(debugSettings(activity)) }
            .build()
        consentInformation.requestConsentInfoUpdate(
            activity, params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    aggiornaStatoConsenso()
                    if (canRequestAds()) {
                        MobileAds.initialize(activity)
                        caricaInterstitial(activity)
                    }
                    if (cont.isActive) cont.resume(canRequestAds())
                }
            },
            { if (cont.isActive) cont.resume(false) },
        )
    }

    /**
     * Da chiamare a ogni avvio: UMP persiste il consenso da solo, quindi se è già stato ottenuto
     * in una sessione precedente qui basta rileggerlo (nessun form da mostrare di nuovo).
     */
    fun inizializzaSeConsentito(context: Context) {
        consentInformation = UserMessagingPlatform.getConsentInformation(context)
        aggiornaStatoConsenso()
        if (canRequestAds()) {
            MobileAds.initialize(context)
            caricaInterstitial(context)
        }
    }

    fun apriGestioneConsenso(activity: Activity, onChiuso: () -> Unit = {}) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { onChiuso() }
    }

    fun caricaInterstitial(context: Context) {
        if (!canRequestAds()) return
        InterstitialAd.load(
            context, AD_UNIT_INTERSTITIAL, AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitial = ad
                }
            },
        )
    }

    /** Mostra l'interstitial se pronto e ricarica per la prossima volta; altrimenti no-op. */
    fun mostraInterstitial(activity: Activity, onChiuso: () -> Unit) {
        val ad = interstitial
        if (!canRequestAds() || ad == null) {
            onChiuso()
            return
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                interstitial = null
                caricaInterstitial(activity)
                onChiuso()
            }
            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                interstitial = null
                caricaInterstitial(activity)
                onChiuso()
            }
        }
        ad.show(activity)
    }

    private fun debugSettings(activity: Activity) = ConsentDebugSettings.Builder(activity)
        .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
        .build()
}
