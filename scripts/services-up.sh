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

# 0. Verificar existencia de JARs compilados
JARS=(
    "donaciones-service/target/donaciones-service-1.0.0-SNAPSHOT.jar"
    "logistica-service/target/logistica-service-1.0.0-SNAPSHOT.jar"
    "incentivos-service/target/incentivos-service-1.0.0-SNAPSHOT.jar"
    "notificaciones-service/target/notificaciones-service-1.0.0-SNAPSHOT.jar"
    "api-gateway/target/api-gateway-1.0.0-SNAPSHOT.jar"
    "frontend-service/target/frontend-service-1.0.0-SNAPSHOT.jar"
)

FALTAN_JARS=0
for jar in "${JARS[@]}"; do
    if [ ! -f "$jar" ]; then
        echo "Falta compilar: $jar"
        FALTAN_JARS=1
    fi
done

if [ "$FALTAN_JARS" -eq 1 ]; then
    echo ""
    echo "Compilando los microservicios faltantes con ./mvnw package -DskipTests..."
    ./mvnw package -DskipTests
fi

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
echo "Esperando que los 6 microservicios completen su arranque..."
READY=0
for i in {1..45}; do
    if curl -s http://localhost:8080/actuator/health > /dev/null 2>&1 && \
       curl -s http://localhost:8082/api/v1/donaciones/saludo > /dev/null 2>&1 && \
       curl -s http://localhost:8083/actuator/health > /dev/null 2>&1 && \
       curl -s http://localhost:8084/api/v1/incentivos/rankings/mensual > /dev/null 2>&1 && \
       curl -s http://localhost:8085/api/v1/notificaciones/estadisticas > /dev/null 2>&1 && \
       curl -s http://localhost:8086/ > /dev/null 2>&1; then
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
    echo "Aviso: Algunos servicios aún están inicializando o tardando más de lo esperado. Verifique logs/."
fi

echo ""
echo "Microservicios en ejecución (logs disponibles en carpeta logs/):"
ps aux | grep java | grep SNAPSHOT | awk '{print $2, $12}' || true

echo ""
echo "=========================================================="
echo " Enlaces de Acceso y Portales de DonaTrack:"
echo " - Portal Web Principal:  http://localhost:8086"
echo "   * Cuentas Demo Disponibles:"
echo "     - Donante Particular: juan.donante@example.com / donante123"
echo "     - Empresa Donante:    rse@molinos.com.ar / empresa123"
echo "     - Entidad / Comedor:  esperanza@comedor.org / comedor123"
echo "     - Administrador:      admin / admin123"
echo "     - Chofer Flota:       chofer / chofer123"
echo " - API Gateway:           http://localhost:8080"
echo " - Donaciones Service:    http://localhost:8082"
echo " - GraphQL / GraphiQL:    http://localhost:8082/graphiql"
echo " - Logística Service:     http://localhost:8083"
echo " - Incentivos Service:    http://localhost:8084"
echo " - Notificaciones:        http://localhost:8085"
echo " - Mailpit (Buzón Correo):http://localhost:8025"
echo " - RabbitMQ UI:           http://localhost:15672 (donatrack/donatrack_password)"
echo " - Keycloak Admin:        http://localhost:8081 (admin/admin)"
echo ""
echo "Para verificar el estado completo ejecute: ./scripts/test-e2e.sh"
echo "Para detener todos los servicios ejecute:   ./scripts/services-down.sh"
echo "=========================================================="
