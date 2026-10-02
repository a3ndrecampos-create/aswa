# Flex Otimizador de Rota — App Android nativo (Kotlin)

App nativo de painel do entregador, reescrito em Kotlin/Jetpack Compose. Nome no Google
Play: **Flex Otimizador de Rota**. Pacote (`applicationId`): `com.flexotimizador.entregador`.

## O que já está implementado
- Lista de entregas com prioridade, prazo, valor, status (pendente/entregue)
- Otimização de rota (vizinho mais próximo + refinamento 2-opt)
- Mapa nativo com OpenStreetMap (osmdroid — não precisa de chave de API do Google), com
  edição de sequência por arrastar ou por toque (selecionar + mover), inclusive tocando
  direto no número da parada no mapa
- Busca de endereço por CEP (ViaCEP) e geocodificação em camadas (Nominatim/OSM)
- Leitura de CEP por foto (OCR on-device via ML Kit, priorizando a palavra "CEP" pra
  reduzir erro) e leitura de código de rastreio (QR/barras) via ZXing
- Importação de planilha `.xlsx`
- Histórico de entregas com ganhos do dia/semana/mês + relatório com gráfico
- Backup/restauração dos dados em `.xml`
- Persistência local com Room (entregas/histórico) e DataStore (configurações)
- Splash screen nativa, funciona offline (exceto mapa/geocodificação de endereço novo)

## Modelo de acesso: teste grátis + assinatura
`billing/TrialManager.kt` libera **10 dias grátis** a partir da primeira abertura do app
naquele aparelho (guardado localmente, sem precisar de conta/login). Depois disso, o app
exige a assinatura mensal **Flex Pro** pra continuar sendo usado — a tela de paywall
(`ui/screens/PaywallScreen.kt`) bloqueia o app inteiro nesse caso.

**Antes de publicar**, crie o produto de assinatura no Play Console (Monetização >
Produtos de assinatura) com o ID exato `flex_otimizador_pro_mensal` (ou troque a
constante `PRO_MONTHLY_PRODUCT_ID` em `billing/BillingManager.kt` pelo ID que você usar).
Produtos de assinatura só ficam disponíveis pra teste depois do primeiro upload do app
(mesmo em teste interno).

Onde cada regra fica implementada, se precisar mexer:
- `billing/TrialManager.kt` — conta os 10 dias (guardado localmente no aparelho)
- `billing/PremiumAccessManager.kt` — combina teste fechado + trial + assinatura numa
  única resposta (`hasAccess`) que o resto do app usa
- `billing/BillingManager.kt` — só cuida da assinatura real (Google Play Billing), não
  sabe nada sobre trial ou teste fechado

## Teste fechado x Produção (variantes de build)
O app tem duas variantes ("product flavors"), escolhidas na hora de compilar:

- **`production`**: regra comercial real (10 dias grátis, depois exige assinatura). É o
  que vai pra faixa de **Produção** do Play Console.
- **`closedTesting`**: acesso sempre liberado, sem trial, sem paywall — pra quem está
  testando o app na faixa de **Teste fechado** do Play Console conseguir ver 100% das
  telas sem precisar assinar. **Nunca envie esta variante pra Produção.**

Cada variante gera um `.aab` com nome diferente (`app-production-release.aab` e
`app-closedTesting-release.aab`). Localmente: `./gradlew bundleProductionRelease` ou
`./gradlew bundleClosedTestingRelease` (o Android Studio também deixa escolher a "Build
Variant" numa aba própria).

## Assinatura de release (obrigatória pra publicar)
O Gradle assina o release automaticamente **se** as variáveis de ambiente da keystore
existirem (`ANDROID_KEYSTORE_PATH`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`,
`ANDROID_KEY_PASSWORD`) — pensado pra rodar em CI (GitHub Actions, configurando essas
variáveis como *secrets* do repositório). Sem elas, o build de release sai sem assinatura
— nesse caso, gere localmente pelo assistente **Build → Generate Signed Bundle/APK** do
Android Studio (que assina por fora do Gradle e cria a keystore no processo, se você
ainda não tiver uma).

**Guarde a keystore e as senhas em lugar seguro fora do repositório** — se perder, não
tem como atualizar o app publicado depois, só publicar um app novo do zero.

## Requisitos atuais do Google Play
- `compileSdk`/`targetSdk` **36** — exigência do Play a partir de **31/08/2026**.
- **Leitor de código de rastreio via ZXing** (`utils/ZxingDecoder.kt`), não ML Kit: a
  biblioteca nativa do ML Kit Barcode Scanning (`libbarhopper_v3.so`) é incompatível com
  a exigência de paginação de memória de 16KB do Google Play, sem correção publicada até
  agora. ZXing é 100% Java/Kotlin, sem biblioteca nativa — não tem esse problema. (O OCR
  de CEP continua com ML Kit Text Recognition, que não teve esse problema relatado.)
- `androidx.camera:*` atualizado pra `1.5.1` (corrige o alinhamento de 16KB de outra
  biblioteca nativa da câmera, `libimage_processing_util_jni.so`).
- `isMinifyEnabled = true` no release, com regras de ProGuard (`proguard-rules.pro`)
  cobrindo Room, Retrofit/Gson, ML Kit, ZXing, osmdroid e Billing.

## Antes de publicar (checklist)
- [ ] Criar a keystore de release e guardar em lugar seguro (ver seção acima)
- [ ] Criar o produto de assinatura mensal no Play Console com o ID
      `flex_otimizador_pro_mensal` (ou ajustar a constante correspondente)
- [ ] Preencher a Política de Privacidade (obrigatória — o app usa câmera, localização e
      notificações) e o Formulário de conteúdo do app na Play Console
- [ ] Gerar e testar a variante `closedTesting` primeiro, subir pra faixa de Teste
      Fechado, confirmar que a assinatura/trial funcionam antes de ir pra Produção
- [ ] Ícone adaptativo definitivo (hoje é um ícone simples, funcional, mas vale revisar)
- [ ] Screenshots e textos da loja (descrição curta/longa, categoria)

## Estrutura do projeto
```
app/src/main/java/com/rotacerta/entregador/
  billing/      Trial de 10 dias, assinatura (Google Play Billing), gate de acesso
  data/         Entidades Room, DAOs, banco de dados, configuração (DataStore)
  network/      Retrofit: ViaCEP e Nominatim
  domain/       Otimização de rota, geocodificação em camadas, importador de xlsx, backup
  ocr/          Leitura de texto por câmera (ML Kit)
  utils/        Leitor de código de barras/QR (ZXing)
  viewmodel/    RotaViewModel — estado central do app
  ui/screens/   Telas: Rota, Mapa, Histórico, Config, Paywall, diálogos e scanners
  ui/components/ Componentes reutilizáveis (card de entrega, mapa, lista reordenável)
  ui/theme/     Cores e tema
```

## Observações importantes
- O app precisa de internet para geocodificação, CEP e mapa. O resto (ver/organizar
  entregas, marcar entregue, histórico, relatório) funciona 100% offline.
- Nominatim (geocodificação gratuita) tem limite de uso — o app já respeita um intervalo
  mínimo entre requisições.
- Este projeto não é compilado no ambiente onde é editado (sem acesso ao Android SDK).
  Sempre teste com `gradlew assembleDebug` (ou equivalente) antes de considerar uma
  mudança pronta.
