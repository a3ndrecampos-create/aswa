package com.rotacerta.entregador.billing

import android.content.Context
import java.util.concurrent.TimeUnit

/**
 * Controla o período de 10 dias de acesso grátis, contado a partir da primeira vez que
 * o app é aberto naquele aparelho. Guardado localmente (SharedPreferences) - não depende
 * de conta/login, porque o app não tem sistema de contas.
 */
object TrialManager {

    private const val PREFS_NAME = "flex_trial"
    private const val KEY_FIRST_LAUNCH = "first_launch_millis"
    val TRIAL_DURATION_MILLIS = TimeUnit.DAYS.toMillis(10)

    /** Chame uma vez, cedo (Application/MainActivity), pra garantir que a data de início existe. */
    fun ensureStarted(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_FIRST_LAUNCH)) {
            prefs.edit().putLong(KEY_FIRST_LAUNCH, System.currentTimeMillis()).apply()
        }
    }

    private fun firstLaunchMillis(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(KEY_FIRST_LAUNCH, System.currentTimeMillis())
    }

    fun isTrialActive(context: Context): Boolean =
        System.currentTimeMillis() - firstLaunchMillis(context) < TRIAL_DURATION_MILLIS

    /** Dias restantes de teste (arredondado pra cima), nunca negativo. */
    fun daysRemaining(context: Context): Int {
        val elapsed = System.currentTimeMillis() - firstLaunchMillis(context)
        val remainingMillis = (TRIAL_DURATION_MILLIS - elapsed).coerceAtLeast(0)
        return TimeUnit.MILLISECONDS.toDays(remainingMillis + TimeUnit.DAYS.toMillis(1) - 1).toInt()
    }
}
