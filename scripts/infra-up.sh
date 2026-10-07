#!/usr/bin/env bash
set -e

# scripts/infra-up.sh
# Levanta la infraestructura de soporte (PostgreSQL, MongoDB, RabbitMQ, Mailpit, Keycloak)

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$DIR"

echo "=========================================================="
echo " Levantar Infraestructura Docker - DonaTrack"
echo "=========================================================="

docker compose -f docker/docker-compose.yml up -d

echo ""
echo "Esperando que los servicios alcancen estado saludable..."
sleep 3

docker compose -f docker/docker-compose.yml ps

echo ""
echo "Infraestructura iniciada correctamente:"
echo " - PostgreSQL:     localhost:5432 (donaciones_db, incentivos_db, keycloak_db)"
echo " - MongoDB:        localhost:27017 (logistica_db)"
echo " - RabbitMQ:       localhost:5672 (AMQP) | UI: http://localhost:15672 (donatrack/donatrack_password)"
echo " - Mailpit Web:    http://localhost:8025 (SMTP :1025)"
echo " - Keycloak Admin: http://localhost:8081 (admin/admin)"
echo "=========================================================="
