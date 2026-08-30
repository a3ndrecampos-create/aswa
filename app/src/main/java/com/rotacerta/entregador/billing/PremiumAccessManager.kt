package com.rotacerta.entregador.billing

import android.content.Context
import com.rotacerta.entregador.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** De onde veio a liberação do acesso neste aparelho agora - só pra exibir a mensagem certa na UI. */
enum class AccessSource { CLOSED_TESTING, TRIAL, SUBSCRIBED, NONE }

/**
 * Única fonte de verdade sobre "este usuário pode usar o app agora?", combinando (nessa
 * ordem de prioridade):
 * 1. Build de teste fechado (BuildConfig.IS_CLOSED_TESTING) -> sempre libera, nunca expira
 * 2. Teste grátis de 10 dias (TrialManager) -> libera enquanto ativo
 * 3. Assinatura mensal ativa (BillingManager.isPro) -> libera enquanto a assinatura durar
 *
 * O resto do app (gate de navegação, tela de paywall) deve consultar SEMPRE esta classe,
 * nunca o BillingManager/TrialManager diretamente - assim a regra fica num lugar só.
 */
object PremiumAccessManager {

    private val _hasAccess = MutableStateFlow(BuildConfig.IS_CLOSED_TESTING)
    val hasAccess: StateFlow<Boolean> = _hasAccess

    private val _source = MutableStateFlow(
        if (BuildConfig.IS_CLOSED_TESTING) AccessSource.CLOSED_TESTING else AccessSource.NONE
    )
    val source: StateFlow<AccessSource> = _source

    fun initialize(context: Context) {
        if (BuildConfig.IS_CLOSED_TESTING) {
            // Build exclusivo da faixa de teste fechado: libera tudo, sem trial, sem expirar.
            _hasAccess.value = true
            _source.value = AccessSource.CLOSED_TESTING
            return
        }

        TrialManager.ensureStarted(context)
        recompute(context)
    }

    /** Chame de novo sempre que BillingManager.isPro mudar (assinatura concluída, restaurada, etc). */
    fun recompute(context: Context) {
        if (BuildConfig.IS_CLOSED_TESTING) return // já garantido acima, nunca muda

        val subscribed = BillingManager.isPro.value
        val trialActive = TrialManager.isTrialActive(context)

        _hasAccess.value = subscribed || trialActive
        _source.value = when {
            subscribed -> AccessSource.SUBSCRIBED
            trialActive -> AccessSource.TRIAL
            else -> AccessSource.NONE
        }
    }
}
