package it.picone.miotreno.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import it.picone.miotreno.Deps
import it.picone.miotreno.domain.TrenoSeguito
import it.picone.miotreno.work.ricalcolaNotifica
import kotlinx.coroutines.flow.first

/**
 * Il ⟳ del design: forza il ricalcolo subito, senza aspettare il worker da 15 minuti.
 * `update` rigira `provideGlance`, che rilegge posizione e treni.
 */
class AggiornaWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        aggiornaWidget(context)
    }
}

val PARAM_NUMERO_TRENO = ActionParameters.Key<Int>("numero_treno")
val PARAM_DATA = ActionParameters.Key<String>("data")
val PARAM_ARRIVO = ActionParameters.Key<Long>("arrivo")

/**
 * Segui / smetti di seguire direttamente dal widget, senza aprire l'app. Stessa logica di
 * `TrenoViewModel.cambiaSeguito`: stato in DataStore, widget e notifica ricalcolati subito.
 *
 * Il broadcast di Glance ha 10 s: niente GPS né rete qui dentro (andava in ANR). I tre campi
 * di [TrenoSeguito] arrivano già pronti dai parametri; il ricalcolo vero lo fanno i worker.
 */
class SeguiTrenoAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val numero = parameters[PARAM_NUMERO_TRENO] ?: return
        val data = parameters[PARAM_DATA] ?: return
        val arrivo = parameters[PARAM_ARRIVO] ?: return
        Deps.init(context)
        val imp = Deps.impostazioni
        val smetti = imp.seguito.first()?.numeroTreno == numero
        if (smetti) imp.smettiDiSeguire() else imp.segui(TrenoSeguito(numero, data, arrivo))
        ricalcolaNotifica(context)
        aggiornaWidget(context, seguito = if (smetti) 0 else numero)
    }
}
