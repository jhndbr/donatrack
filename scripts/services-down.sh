#!/usr/bin/env bash

# scripts/services-down.sh
# Detiene todos los microservicios de DonaTrack en ejecución

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

# Esperar hasta 5 segundos a que liberen los puertos
for _ in {1..10}; do
    if ! pgrep -f "((donaciones|logistica|incentivos|notificaciones|frontend)-service|api-gateway).*jar" >/dev/null 2>&1; then
        break
    fi
    sleep 0.5
done

# Si alguno sigue colgado, forzar cierre inmediato
pkill -9 -f "((donaciones|logistica|incentivos|notificaciones|frontend)-service|api-gateway).*jar" 2>/dev/null || true
sleep 1

echo "Todos los microservicios han sido detenidos correctamente."
echo "=========================================================="
