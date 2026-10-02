package com.rotacerta.entregador.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rotacerta.entregador.billing.AccessSource
import com.rotacerta.entregador.ui.theme.Accent
import com.rotacerta.entregador.ui.theme.Bg
import com.rotacerta.entregador.ui.theme.Muted
import com.rotacerta.entregador.ui.theme.TextMain

/**
 * Tela cheia mostrada quando o app ainda não pode ser usado: teste grátis de 10 dias
 * acabou e ainda não tem assinatura ativa. Bloqueia o app inteiro (diferente do app de
 * referência, que só bloqueava abas específicas) porque aqui não faz sentido meio-termo —
 * o RotaCerta é uma ferramenta de trabalho única, não tem uma versão "sempre grátis".
 *
 * Quando [daysRemaining] > 0 (ainda dentro do trial), mostra um aviso mais discreto
 * incentivando a assinar cedo em vez do bloqueio total.
 */
@Composable
fun PaywallScreen(
    daysRemaining: Int,
    monthlyPriceLabel: String?,
    isPurchasing: Boolean,
    onSubscribeClick: () -> Unit
) {
    val trialActive = daysRemaining > 0

    Column(
        modifier = Modifier.fillMaxSize().background(Bg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.WorkspacePremium,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = Accent
            )
            Spacer(Modifier.height(16.dp))
            Text(
                if (trialActive) "Seu teste grátis termina em $daysRemaining ${if (daysRemaining == 1) "dia" else "dias"}"
                else "Seu teste grátis de 10 dias acabou",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = TextMain
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (trialActive) "Assine agora pra não perder o acesso às suas rotas quando o teste terminar."
                else "Assine o Flex Pro pra continuar organizando suas entregas, otimizando rotas e acompanhando seus ganhos.",
                style = MaterialTheme.typography.bodyMedium,
                color = Muted,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))

            BenefitLine("Otimização automática de rota")
            BenefitLine("Mapa com edição de sequência")
            BenefitLine("Leitor de CEP e código de rastreio")
            BenefitLine("Relatório de ganhos e backup dos seus dados")

            Spacer(Modifier.height(28.dp))
            Button(onClick = onSubscribeClick, enabled = !isPurchasing, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                if (isPurchasing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(
                        if (monthlyPriceLabel != null) "Assinar • $monthlyPriceLabel/mês" else "Assinar Flex Pro",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (trialActive) {
                Spacer(Modifier.height(14.dp))
                Text(
                    "Enquanto isso, você continua com acesso total durante o teste.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Muted,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun BenefitLine(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
    ) {
        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = TextMain)
    }
}
