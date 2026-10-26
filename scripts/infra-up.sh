#!/usr/bin/env bash
set -e

# scripts/infra-up.sh
# Levanta la infraestructura de soporte de DonaTrack (PostgreSQL, MongoDB, RabbitMQ, Mailpit, Keycloak)
# Opcional: soporta perfiles de observabilidad y nube (--profile full o --profile observability)

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$DIR"

echo "=========================================================="
echo " Levantar Infraestructura Docker - DonaTrack"
echo "=========================================================="

# Permite pasar argumentos adicionales a docker compose (ej: --profile full o --profile observability)
docker compose -f docker/docker-compose.yml up -d "$@"

echo ""
echo "Esperando que los servicios alcancen estado saludable..."
sleep 3

# El init-script de Postgres solo corre al crear el volumen por primera vez.
# Se re-aplica aquí (es idempotente) para crear bases nuevas en volúmenes existentes.
for i in $(seq 1 20); do
    if docker exec donatrack-postgres pg_isready -U donatrack >/dev/null 2>&1; then
        docker exec -i donatrack-postgres psql -U donatrack -d postgres -q < docker/init-scripts/01-init-databases.sql >/dev/null \
            && docker exec -i donatrack-postgres psql -U donatrack -d donaciones_db -q < docker/init-scripts/02-schema-updates.sql >/dev/null \
            && echo "Bases de datos y migraciones verificadas (donaciones_db, incentivos_db, notificaciones_db, keycloak_db)."
        break
    fi
    sleep 1
done

echo ""
docker compose -f docker/docker-compose.yml ps

echo ""
echo "Infraestructura iniciada correctamente:"
echo " - PostgreSQL:     localhost:5432 (donaciones_db, incentivos_db, notificaciones_db, keycloak_db)"
echo " - MongoDB:        localhost:27017 (logistica_db)"
echo " - RabbitMQ:       localhost:5672 (AMQP) | UI: http://localhost:15672 (donatrack/donatrack_password)"
echo " - Mailpit Web:    http://localhost:8025 (SMTP :1025)"
echo " - Keycloak Admin: http://localhost:8081 (admin/admin)"

if docker ps --format '{{.Names}}' | grep -q "donatrack-prometheus"; then
    echo " - Prometheus:     http://localhost:9090 (Métricas scrape Actuator)"
fi
if docker ps --format '{{.Names}}' | grep -q "donatrack-grafana"; then
    echo " - Grafana:        http://localhost:3000 (admin/admin)"
fi
if docker ps --format '{{.Names}}' | grep -q "donatrack-n8n"; then
    echo " - n8n Webhook:    http://localhost:5678 (Automatización difusión insignias)"
fi

echo ""
echo "Tip: Para levantar con Observabilidad y Automatización completas:"
echo "     ./scripts/infra-up.sh --profile observability"
echo "Tip: Para levantar todo el ecosistema (microservicios en contenedores):"
echo "     ./scripts/infra-up.sh --profile full"
echo "=========================================================="
