#!/usr/bin/env bash
set -e

# scripts/infra-clean.sh
# Detiene los contenedores Docker y elimina volumenes para reiniciar bases de datos

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$DIR"

echo "=========================================================="
echo " Limpiando Infraestructura y Volúmenes de Datos..."
echo "=========================================================="

docker compose -f docker/docker-compose.yml down -v

echo "Infraestructura y volúmenes persistentes eliminados."
