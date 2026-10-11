#!/usr/bin/env bash
set -e

# scripts/services-up.sh
# Inicia todos los microservicios de DonaTrack en background y monitorea su inicio

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$DIR"

mkdir -p logs
PID_FILE="$DIR/.services.pid"

if [ -f "$PID_FILE" ]; then
    echo "Ya existen microservicios iniciados (registrados en .services.pid)."
    echo "Ejecute ./scripts/services-down.sh primero si desea reiniciarlos."
    exit 1
fi

echo "=========================================================="
echo " Iniciando Microservicios DonaTrack"
echo "=========================================================="

JAVA_OPTS="-Xms64m -Xmx256m"

# 1. donaciones-service (:8082)
echo " -> Levantando donaciones-service en puerto 8082..."
setsid nohup java $JAVA_OPTS -jar donaciones-service/target/donaciones-service-1.0.0-SNAPSHOT.jar </dev/null > logs/donaciones-service.log 2>&1 &
PID_DONACIONES=$!
echo "$PID_DONACIONES donaciones-service" >> "$PID_FILE"

# 2. logistica-service (:8083)
echo " -> Levantando logistica-service en puerto 8083..."
setsid nohup java $JAVA_OPTS -jar logistica-service/target/logistica-service-1.0.0-SNAPSHOT.jar </dev/null > logs/logistica-service.log 2>&1 &
PID_LOGISTICA=$!
echo "$PID_LOGISTICA logistica-service" >> "$PID_FILE"

# 3. incentivos-service (:8084)
echo " -> Levantando incentivos-service en puerto 8084..."
setsid nohup java $JAVA_OPTS -jar incentivos-service/target/incentivos-service-1.0.0-SNAPSHOT.jar </dev/null > logs/incentivos-service.log 2>&1 &
PID_INCENTIVOS=$!
echo "$PID_INCENTIVOS incentivos-service" >> "$PID_FILE"

# 4. notificaciones-service (:8085)
echo " -> Levantando notificaciones-service en puerto 8085..."
setsid nohup java $JAVA_OPTS -jar notificaciones-service/target/notificaciones-service-1.0.0-SNAPSHOT.jar </dev/null > logs/notificaciones-service.log 2>&1 &
PID_NOTIF=$!
echo "$PID_NOTIF notificaciones-service" >> "$PID_FILE"

# 5. api-gateway (:8080)
echo " -> Levantando api-gateway en puerto 8080..."
setsid nohup java $JAVA_OPTS -jar api-gateway/target/api-gateway-1.0.0-SNAPSHOT.jar </dev/null > logs/api-gateway.log 2>&1 &
PID_GATEWAY=$!
echo "$PID_GATEWAY api-gateway" >> "$PID_FILE"

# 6. frontend-service (:8086)
echo " -> Levantando frontend-service en puerto 8086..."
setsid nohup java $JAVA_OPTS -jar frontend-service/target/frontend-service-1.0.0-SNAPSHOT.jar </dev/null > logs/frontend-service.log 2>&1 &
PID_FRONTEND=$!
echo "$PID_FRONTEND frontend-service" >> "$PID_FILE"

echo ""
echo "Esperando que los microservicios completen su arranque..."
READY=0
for i in {1..30}; do
    if curl -s http://localhost:8080/actuator/health > /dev/null 2>&1 && \
       curl -s http://localhost:8082/api/v1/donaciones/saludo > /dev/null 2>&1; then
        READY=1
        break
    fi
    sleep 1
    echo -n "."
done
echo ""

if [ "$READY" -eq 1 ]; then
    echo "¡Todos los microservicios respondieron exitosamente!"
else
    echo "Aviso: Algunos servicios aún están inicializando o tardando más de lo esperado."
fi

echo ""
echo "Microservicios en ejecución (logs disponibles en carpeta logs/):"
ps aux | grep java | grep SNAPSHOT | awk '{print $2, $12}' || true

echo ""
echo "Para verificar el estado completo ejecute: ./scripts/test-e2e.sh"
echo "Para detener todos los servicios ejecute:   ./scripts/services-down.sh"
echo "=========================================================="
