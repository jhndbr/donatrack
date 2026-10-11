#!/usr/bin/env bash

# scripts/services-down.sh
# Detiene todos los microservicios en ejecución

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$DIR"

PID_FILE="$DIR/.services.pid"

echo "=========================================================="
echo " Deteniendo Microservicios DonaTrack..."
echo "=========================================================="

if [ -f "$PID_FILE" ]; then
    while read -r pid service; do
        if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
            echo " - Deteniendo $service (PID $pid)..."
            kill "$pid" 2>/dev/null || true
        fi
    done < "$PID_FILE"
    rm -f "$PID_FILE"
fi

# Por seguridad, asegurar que no queden procesos huérfanos con los jars
pkill -f "donaciones-service.*jar" 2>/dev/null || true
pkill -f "logistica-service.*jar" 2>/dev/null || true
pkill -f "incentivos-service.*jar" 2>/dev/null || true
pkill -f "notificaciones-service.*jar" 2>/dev/null || true
pkill -f "api-gateway.*jar" 2>/dev/null || true
pkill -f "frontend-service.*jar" 2>/dev/null || true

sleep 1
echo "Todos los microservicios han sido detenidos correctamente."
echo "=========================================================="
