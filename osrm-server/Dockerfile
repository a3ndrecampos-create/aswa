# Servidor de rotas OSRM (distâncias reais por ruas) para o Flex Otimizador de Rota.
# Os dados do mapa (pasta data/) são gerados pelo workflow "Gerar dados de rotas (OSRM)"
# do GitHub Actions — veja o README desta pasta.
FROM ghcr.io/project-osrm/osrm-backend:latest

WORKDIR /data
COPY data/ /data/

EXPOSE 5000
# MLD = menos memória e respostas rápidas. max-table-size cobre as 80 paradas do app (+2 pontos).
CMD ["osrm-routed", "--algorithm", "mld", "--port", "5000", "--max-table-size", "100", "/data/region.osrm"]
