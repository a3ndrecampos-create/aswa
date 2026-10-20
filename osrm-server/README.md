# Servidor de rotas próprio (OSRM)

Serve as **distâncias reais por ruas** que o app usa para otimizar a rota. Sem servidor
configurado, o app continua funcionando e otimiza em **linha reta** (sem chamar ninguém).

## Visão geral

1. **Gerar os dados do mapa** (uma vez, e de vez em quando para atualizar): GitHub Actions.
2. **Subir o servidor** com esses dados (Docker) na Shard Cloud.
3. **Avisar o app** o endereço do servidor (variável `ROUTING_URL`).

## 1) Gerar os dados

GitHub → aba **Actions** → **Gerar dados de rotas (OSRM)** → **Run workflow**.
- Padrão: recorta a **Grande São Paulo** (leve, roda em poucos minutos e cabe em servidor pequeno).
- Para outra cidade, mude o campo `bbox` (formato `min_lon,min_lat,max_lon,max_lat`).
  Para o estado/região inteira, deixe `bbox` vazio (arquivos bem maiores e exigem mais RAM).
- No fim, baixe o artifact **osrm-data** e descompacte o conteúdo em `osrm-server/data/`
  (devem existir arquivos `region.osrm`, `region.osrm.cell_metrics`, etc.).

## 2) Subir o servidor

Precisa de um ambiente que rode **Docker** (ou uma VPS), com ao menos ~1 GB de RAM livre para
a Grande SP (regiões maiores precisam de mais). Dentro desta pasta:

```
docker compose up -d --build
```

O servidor passa a responder na porta **5000**. Teste:

```
curl "http://SEU_SERVIDOR:5000/table/v1/driving/-46.6565,-23.5613;-46.6553,-23.5555?annotations=distance"
```
Deve voltar `"code":"Ok"`.

**HTTPS é obrigatório pro app** (o Android bloqueia http puro). Use o domínio com HTTPS que a
hospedagem fornecer, ou um proxy (Caddy/Nginx) na frente da porta 5000.

## 3) Ligar o app ao servidor

No GitHub: **Settings → Secrets and variables → Actions → aba Variables → New repository
variable** → nome `ROUTING_URL`, valor `https://rotas.seudominio.com` (sem barra no final).
Rode o build de novo. Localmente: `ROUTING_URL=https://...` no `local.properties`.

## Cuidados

- O endereço do servidor fica dentro do app (qualquer um pode descobri-lo). Em produção, limite
  taxa de requisições no proxy e monitore o uso.
- Mapas do OpenStreetMap mudam: gere os dados de novo a cada poucos meses.
- Se o servidor cair, o app volta sozinho para linha reta (mostra "estimado, sem internet").
