#!/bin/bash
# ============================================================================
# Define a senha da role app_backend a partir da variável de ambiente
# APP_BACKEND_DB_PASSWORD (definida no arquivo .env, nunca neste arquivo).
#
# O 00_roles_bootstrap_v2_1.sql cria a role sem senha. Sem este passo, o
# backend (que conecta como app_backend) não consegue autenticar no Docker.
#
# Executa somente na PRIMEIRA inicialização do volume de dados do PostgreSQL.
# Para volumes já existentes, execute manualmente (ver FASE0_RELATORIO.md).
# ============================================================================
set -euo pipefail

if [ -z "${APP_BACKEND_DB_PASSWORD:-}" ]; then
    echo "ERRO: APP_BACKEND_DB_PASSWORD não definida; a role app_backend ficaria sem senha." >&2
    exit 1
fi

psql -v ON_ERROR_STOP=1 \
     -v app_backend_pw="$APP_BACKEND_DB_PASSWORD" \
     --username "$POSTGRES_USER" \
     --dbname "$POSTGRES_DB" <<'EOSQL'
ALTER ROLE app_backend WITH PASSWORD :'app_backend_pw';
EOSQL

echo "Senha da role app_backend configurada."
