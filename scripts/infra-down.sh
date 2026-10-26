#!/usr/bin/env bash
set -e

# scripts/infra-down.sh
# Detiene los contenedores de infraestructura Docker (incluyendo perfiles full/observability)

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$DIR"

echo "=========================================================="
echo " Deteniendo Infraestructura Docker..."
echo "=========================================================="

docker compose -f docker/docker-compose.yml --profile full down "$@"

echo "Contenedores detenidos correctamente."
