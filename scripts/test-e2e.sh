#!/usr/bin/env bash

# scripts/test-e2e.sh
# Suite de pruebas automatizadas y chequeo de salud para DonaTrack

set -u

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

PASSED=0
FAILED=0

test_endpoint() {
    local name="$1"
    local url="$2"
    local expected_code="$3"

    echo -n "Probando $name ($url)... "
    http_code=$(curl -s -L -o /tmp/last_test_res.txt -w "%{http_code}" "$url" || echo "000")

    if [ "$http_code" -eq "$expected_code" ]; then
        echo -e "${GREEN}[OK - HTTP $http_code]${NC}"
        PASSED=$((PASSED + 1))
    else
        echo -e "${RED}[FALLÓ - Esperado $expected_code, recibido $http_code]${NC}"
        FAILED=$((FAILED + 1))
    fi
}

echo -e "${BLUE}==========================================================${NC}"
echo -e "${BLUE} Chequeo de Salud y Pruebas de Integración - DonaTrack    ${NC}"
echo -e "${BLUE}==========================================================${NC}"

# 1. Infraestructura
echo -e "\n${YELLOW}1. Verificando Infraestructura Docker:${NC}"
test_endpoint "RabbitMQ Management UI" "http://localhost:15672" 200
test_endpoint "Mailpit Web UI" "http://localhost:8025" 200
test_endpoint "Keycloak Admin" "http://localhost:8081" 200

# 2. Microservicios Directos
echo -e "\n${YELLOW}2. Verificando Microservicios de Negocio:${NC}"
test_endpoint "Donaciones - Endpoint Saludo Entrega 1" "http://localhost:8082/api/v1/donaciones/saludo" 200
test_endpoint "Donaciones - Listar Donaciones" "http://localhost:8082/api/v1/donaciones" 200
test_endpoint "Logística - Listar Flota Camiones" "http://localhost:8083/api/v1/logistica/camiones" 200
test_endpoint "Incentivos - Ranking Mensual" "http://localhost:8084/api/v1/incentivos/rankings/mensual" 200

# 3. API Gateway y Enrutamiento Inverso
echo -e "\n${YELLOW}3. Verificando API Gateway y Enrutamiento Inverso (:8080):${NC}"
test_endpoint "API Gateway Health" "http://localhost:8080/actuator/health" 200
test_endpoint "Gateway -> Donaciones (Saludo)" "http://localhost:8080/api/v1/donaciones/saludo" 200
test_endpoint "Gateway -> Logística (Camiones)" "http://localhost:8080/api/v1/logistica/camiones" 200
test_endpoint "Gateway -> Incentivos (Rankings)" "http://localhost:8080/api/v1/incentivos/rankings/mensual" 200

# 4. Flujo de Negocio Completo
echo -e "\n${YELLOW}4. Ejecutando Flujo de Negocio E2E (Donante -> Beneficiaria -> Donación -> Matchmaking):${NC}"

echo -n " -> Creando Entidad Beneficiaria... "
ENTIDAD_RES=$(curl -s -X POST http://localhost:8082/api/v1/entidades \
  -H "Content-Type: application/json" \
  -d '{"razonSocial":"Comedor Los Chicos Felices","direccion":"Av. Corrientes 1234","telefono":"11-4444-5555"}')
ENTIDAD_ID=$(echo "$ENTIDAD_RES" | grep -o '"id":[0-9]*' | head -n1 | cut -d: -f2)

if [ -n "$ENTIDAD_ID" ]; then
    echo -e "${GREEN}[OK - Entidad ID $ENTIDAD_ID creada]${NC}"
    PASSED=$((PASSED + 1))
else
    echo -e "${RED}[FALLÓ creando entidad]${NC}"
    FAILED=$((FAILED + 1))
fi

echo -n " -> Registrando Necesidad Material (arroz, 50 kg, EXTRAORDINARIA)... "
NEC_RES=$(curl -s -X POST "http://localhost:8082/api/v1/entidades/$ENTIDAD_ID/necesidades" \
  -H "Content-Type: application/json" \
  -d '{"subcategoria":"arroz","descripcion":"arroz para comedor","tipo":"EXTRAORDINARIA","cantidadSolicitada":50,"unidadMedida":"kg"}')
NEC_ID=$(echo "$NEC_RES" | grep -o '"id":[0-9]*' | head -n1 | cut -d: -f2)

if [ -n "$NEC_ID" ]; then
    echo -e "${GREEN}[OK - Necesidad ID $NEC_ID creada]${NC}"
    PASSED=$((PASSED + 1))
else
    echo -e "${RED}[FALLÓ creando necesidad]${NC}"
    FAILED=$((FAILED + 1))
fi

echo -n " -> Creando Donante Humana (con email de contacto)... "
DONANTE_RES=$(curl -s -X POST http://localhost:8082/api/v1/donantes/humana \
  -H "Content-Type: application/json" \
  -d "{\"nombre\":\"Carlos\",\"apellido\":\"Gómez\",\"edad\":34,\"nroDocumento\":\"$RANDOM$RANDOM\",\"genero\":\"M\",\"direccion\":\"Mitre 456\",\"mediosContacto\":[{\"tipo\":\"EMAIL\",\"valor\":\"carlos.gomez@donatrack.org\",\"esPredeterminado\":true}]}")
DONANTE_ID=$(echo "$DONANTE_RES" | grep -o '"id":[0-9]*' | head -n1 | cut -d: -f2)

if [ -n "$DONANTE_ID" ]; then
    echo -e "${GREEN}[OK - Donante ID $DONANTE_ID creado]${NC}"
    PASSED=$((PASSED + 1))
else
    echo -e "${RED}[FALLÓ creando donante]${NC}"
    FAILED=$((FAILED + 1))
fi

echo -n " -> Registrando Carga Segmentada de Donación (arroz, 50 kg)... "
DONACION_RES=$(curl -s -X POST "http://localhost:8082/api/v1/donaciones/carga-segmentada?donanteId=$DONANTE_ID" \
  -H "Content-Type: application/json" \
  -d '[{"descripcion":"Bolsas de arroz fino","categoria":"Alimentos","subcategoria":"arroz","cantidad":50,"unidadMedida":"kg","esUsado":false}]')
DONACION_ID=$(echo "$DONACION_RES" | grep -o '"id":[0-9]*' | head -n1 | cut -d: -f2)

if [ -n "$DONACION_ID" ]; then
    echo -e "${GREEN}[OK - Donación ID $DONACION_ID generada y segmentada]${NC}"
    PASSED=$((PASSED + 1))
else
    echo -e "${RED}[FALLÓ registrando donación]${NC}"
    FAILED=$((FAILED + 1))
fi

echo -n " -> Evaluando Matchmaking con Estrategia Semántica y Subatendidos... "
MATCH_RES=$(curl -s "http://localhost:8082/api/v1/donaciones/$DONACION_ID/matchmaking")
if echo "$MATCH_RES" | grep -q "rankingSemanticoTop10"; then
    echo -e "${GREEN}[OK - Rankings calculados exitosamente]${NC}"
    PASSED=$((PASSED + 1))
else
    echo -e "${RED}[FALLÓ evaluación de matchmaking]${NC}"
    FAILED=$((FAILED + 1))
fi

echo -n " -> Confirmando Asignación a la Entidad Beneficiaria (Transactional Outbox)... "
ASIGN_RES=$(curl -s -X POST "http://localhost:8082/api/v1/donaciones/$DONACION_ID/asignar?entidadId=$ENTIDAD_ID")
if echo "$ASIGN_RES" | grep -q "ASIGNACION_REALIZADA"; then
    echo -e "${GREEN}[OK - Donación en estado ASIGNACION_REALIZADA con evento Outbox]${NC}"
    PASSED=$((PASSED + 1))
else
    echo -e "${RED}[FALLÓ asignación de donación]${NC}"
    FAILED=$((FAILED + 1))
fi

echo -e "\n${YELLOW}5. Verificando Características Especiales (Misiones, Logística <=100, Bulk Donaciones):${NC}"

test_endpoint "Incentivos - Misiones Activas Inicializadas" "http://localhost:8084/api/v1/incentivos/misiones/activas" 200

echo -n " -> Logística - Planificación de Lote (chunking <= 100)... "
LOTE_RES=$(curl -s -X POST http://localhost:8083/api/v1/logistica/rutas/planificar-lote \
  -H "Content-Type: application/json" \
  -d "{\"idsDonaciones\": [$DONACION_ID], \"origen\": {\"latitud\": -34.6037, \"longitud\": -58.3816}, \"destino\": {\"latitud\": -34.6083, \"longitud\": -58.3712}}")
if echo "$LOTE_RES" | grep -q "loteId"; then
    echo -e "${GREEN}[OK - Ruta por lote planificada exitosamente]${NC}"
    PASSED=$((PASSED + 1))
else
    echo -e "${RED}[FALLÓ planificación de ruta por lote]${NC}"
    FAILED=$((FAILED + 1))
fi

echo -n " -> Donaciones - Actualización Masiva de Estados (bulk-estado)... "
BULK_RES=$(curl -s -X PUT http://localhost:8082/api/v1/donaciones/bulk-estado \
  -H "Content-Type: application/json" \
  -d "{\"idsDonaciones\": [$DONACION_ID], \"nuevoEstado\": \"EN_TRANSITO\"}")
if echo "$BULK_RES" | grep -q "EN_TRANSITO"; then
    echo -e "${GREEN}[OK - Bulk update aplicado correctamente]${NC}"
    PASSED=$((PASSED + 1))
else
    echo -e "${RED}[FALLÓ bulk update de donaciones]${NC}"
    FAILED=$((FAILED + 1))
fi

echo ""
echo -e "${BLUE}==========================================================${NC}"
echo -e " Resumen de Pruebas: ${GREEN}$PASSED APROBADAS${NC} | ${RED}$FAILED FALLIDAS${NC}"
echo -e "${BLUE}==========================================================${NC}"

if [ "$FAILED" -eq 0 ]; then
    echo -e "${GREEN}¡Todos los microservicios y flujos de DonaTrack operan al 100%!${NC}"
    exit 0
else
    echo -e "${RED}Algunas pruebas fallaron. Revise los logs en carpeta logs/.${NC}"
    exit 1
fi
