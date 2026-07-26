# RotaCerta — App Android nativo (Kotlin)

App nativo de painel do entregador, reescrito em Kotlin/Jetpack Compose a partir do PWA original (`app-entregador.html`).

## O que já está implementado
- Lista de entregas com prioridade, prazo, valor, status (pendente/entregue)
- Otimização de rota (algoritmo do vizinho mais próximo, igual ao app original)
- Mapa com OpenStreetMap (osmdroid — não precisa de chave de API do Google)
- Busca de endereço por CEP (ViaCEP) e geocodificação em camadas (Nominatim/OSM)
- Leitura de endereço por foto (OCR on-device via ML Kit)
- Importação de planilha `.xlsx` (detecta colunas de endereço, CEP, prioridade, valor, lat/lng, sequência)
- Histórico de entregas com ganhos do dia/semana/mês
- Configurações: ponto de partida, veículo, app de navegação (Google Maps/Waze), valor padrão
- Navegação via Google Maps / Waze (intents)
- Persistência local com Room (entregas/histórico) e DataStore (configurações)

## Como gerar o APK (sem instalar Android Studio)

### Opção 1 — GitHub Actions (recomendado)
1. Crie um repositório novo no [GitHub](https://github.com/new) (pode ser privado).
2. Suba todos os arquivos deste projeto para o repositório (pela interface web do GitHub: "Add file" → "Upload files", ou via `git push`).
3. O workflow em `.github/workflows/build.yml` já está configurado. Assim que você subir os arquivos na branch `main`, ele roda automaticamente.
4. Vá na aba **Actions** do repositório → clique na execução mais recente → em **Artifacts**, baixe `RotaCerta-debug-apk`. Dentro está o `app-debug.apk`, pronto para instalar no celular.

Esse APK de debug já instala e funciona normalmente no Android. Para publicar na Play Store futuramente, é preciso gerar uma versão *release* assinada — isso também dá para automatizar no mesmo workflow quando chegar a hora.

### Opção 2 — Android Studio (local)
1. Baixe o [Android Studio](https://developer.android.com/studio) (gratuito).
2. Abra este projeto (`File → Open`).
3. Deixe o Android Studio baixar o SDK/dependências na primeira vez.
4. `Build → Build Bundle(s) / APK(s) → Build APK(s)`.

## Estrutura do projeto
```
app/src/main/java/com/rotacerta/entregador/
  data/         Entidades Room, DAOs, banco de dados, configuração (DataStore)
  network/      Retrofit: ViaCEP e Nominatim
  domain/       Otimização de rota, geocodificação em camadas, importador de xlsx
  ocr/          Leitura de texto por câmera (ML Kit)
  viewmodel/    RotaViewModel — estado central do app
  ui/screens/   Telas: Rota, Mapa, Histórico, Config, diálogo de nova entrega
  ui/components/ Componentes reutilizáveis (card de entrega, tira de estatísticas)
  ui/theme/     Cores e tema (mesma paleta do app original)
```

## Observações importantes
- O app precisa de internet para geocodificação, CEP e mapa (como o original).
- Nominatim (geocodificação gratuita) tem limite de uso — o app já respeita um intervalo entre requisições ao importar planilhas grandes.
- Este projeto não foi compilado neste ambiente (sem acesso ao Android SDK aqui). Pequenos ajustes podem ser necessários na primeira compilação — é normal em qualquer handoff de projeto Android novo. Se aparecer algum erro de build, cole a mensagem de erro que te ajudo a resolver.
