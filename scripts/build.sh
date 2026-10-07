#!/usr/bin/env bash
set -e

# scripts/build.sh
# Compila e instala todos los micromódulos de DonaTrack

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$DIR"

echo "=========================================================="
echo " Compilando e Instalando Microservicios DonaTrack"
echo "=========================================================="

./mvnw clean install "$@"

echo ""
echo "Compilación exitosa. Jars generados y listos para ejecutar:"
ls -lh */target/*.jar 2>/dev/null || true
echo "=========================================================="
