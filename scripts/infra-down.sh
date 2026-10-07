#!/usr/bin/env bash
set -e

# scripts/infra-down.sh
# Detiene los contenedores de infraestructura Docker

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$DIR"

echo "=========================================================="
echo " Deteniendo Infraestructura Docker..."
echo "=========================================================="

docker compose -f docker/docker-compose.yml down

echo "Contenedores detenidos correctamente."
