#!/usr/bin/env bash
set -e

# scripts/docker-all.sh
# Construye todas las imágenes Docker de los microservicios y levanta el stack completo
# en contenedores (Bases de datos, Mensajería, Observabilidad y Microservicios DonaTrack).

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$DIR"

echo "=========================================================="
echo " Despliegue Integral Contenedorizado - DonaTrack"
echo "=========================================================="

echo "1. Verificando compilación de artefactos JAR..."
./mvnw clean package -DskipTests

echo ""
echo "2. Construyendo imágenes Docker de los microservicios..."
docker build -t donatrack/donaciones-service:latest donaciones-service/
docker build -t donatrack/logistica-service:latest logistica-service/
docker build -t donatrack/incentivos-service:latest incentivos-service/
docker build -t donatrack/notificaciones-service:latest notificaciones-service/
docker build -t donatrack/api-gateway:latest api-gateway/
docker build -t donatrack/frontend-service:latest frontend-service/

echo ""
echo "3. Iniciando ecosistema completo con Docker Compose (Profile: full)..."
docker compose -f docker/docker-compose.yml --profile full up -d

echo ""
echo "Esperando inicialización de bases de datos y servicios..."
sleep 4

for i in $(seq 1 20); do
    if docker exec donatrack-postgres pg_isready -U donatrack >/dev/null 2>&1; then
        docker exec -i donatrack-postgres psql -U donatrack -d postgres -q < docker/init-scripts/01-init-databases.sql >/dev/null \
            && docker exec -i donatrack-postgres psql -U donatrack -d donaciones_db -q < docker/init-scripts/02-schema-updates.sql >/dev/null \
            && echo "Bases de datos y esquemas verificados."
        break
    fi
    sleep 1
done

echo ""
docker compose -f docker/docker-compose.yml --profile full ps

echo ""
echo "=========================================================="
echo " Stack Completo DonaTrack Operativo en Docker:"
echo " - Portal Web:            http://localhost:8086"
echo " - API Gateway:           http://localhost:8080"
echo " - Prometheus:            http://localhost:9090"
echo " - Grafana:               http://localhost:3000 (admin/admin)"
echo " - n8n Webhooks:          http://localhost:5678"
echo " - RabbitMQ UI:           http://localhost:15672 (donatrack/donatrack_password)"
echo " - Mailpit Web:           http://localhost:8025"
echo " - Keycloak Admin:        http://localhost:8081 (admin/admin)"
echo ""
echo "Para detener el stack ejecute: ./scripts/infra-down.sh"
echo "=========================================================="
