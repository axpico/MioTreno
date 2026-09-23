package it.picone.miotreno.domain

/** Soglia oltre cui un ritardo smette di essere "lieve". */
const val SOGLIA_RITARDO_GRAVE = 10

/**
 * Lo stato di un treno ridotto ai cinque colori che l'utente deve riconoscere a colpo d'occhio.
 * È l'unica sorgente per badge, widget e notifica: se la regola cambia, cambia qui.
 */
enum class Semaforo { InOrario, RitardoLieve, RitardoGrave, Irregolare, Cancellato }

fun ProssimoTreno.semaforo(): Semaforo = semaforoDi(stato, ritardoMinuti)

fun semaforoDi(stato: StatoTreno, ritardoMinuti: Int): Semaforo = when {
    stato is StatoTreno.Cancellato -> Semaforo.Cancellato
    stato !is StatoTreno.Regolare -> Semaforo.Irregolare
    ritardoMinuti >= SOGLIA_RITARDO_GRAVE -> Semaforo.RitardoGrave
    ritardoMinuti > 0 -> Semaforo.RitardoLieve
    else -> Semaforo.InOrario
}

/** Testo breve del badge di stato. */
fun ProssimoTreno.etichettaStato(): String = when (val s = stato) {
    is StatoTreno.Cancellato -> "Cancellato"
    is StatoTreno.Deviato -> "Deviato"
    is StatoTreno.ParzialmenteSoppresso -> "Parz. soppresso"
    is StatoTreno.Alterato -> "Servizio alterato"
    StatoTreno.Regolare -> when {
        ritardoMinuti > 0 -> "+$ritardoMinuti min"
        ritardoMinuti < 0 -> "$ritardoMinuti min"
        else -> "In orario"
    }
}
