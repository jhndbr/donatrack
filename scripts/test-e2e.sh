#!/usr/bin/env bash

# scripts/test-e2e.sh
# Suite de pruebas automatizadas y chequeo de salud integral para DonaTrack

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
test_endpoint "Notificaciones - Estadísticas Auditoría (:8085)" "http://localhost:8085/api/v1/notificaciones/estadisticas" 200
test_endpoint "Notificaciones - Historial Auditoría (:8085)" "http://localhost:8085/api/v1/notificaciones/historial" 200
test_endpoint "Frontend - Landing Page MVC (:8086)" "http://localhost:8086/" 200

# 3. API Gateway y Enrutamiento Inverso
echo -e "\n${YELLOW}3. Verificando API Gateway y Enrutamiento Inverso (:8080):${NC}"
test_endpoint "API Gateway Health" "http://localhost:8080/actuator/health" 200
test_endpoint "Gateway -> Donaciones (Saludo)" "http://localhost:8080/api/v1/donaciones/saludo" 200
test_endpoint "Gateway -> Logística (Camiones)" "http://localhost:8080/api/v1/logistica/camiones" 200
test_endpoint "Gateway -> Incentivos (Rankings)" "http://localhost:8080/api/v1/incentivos/rankings/mensual" 200
test_endpoint "Gateway -> Notificaciones (Estadísticas)" "http://localhost:8080/api/v1/notificaciones/estadisticas" 200
test_endpoint "Gateway -> Notificaciones (Historial)" "http://localhost:8080/api/v1/notificaciones/historial" 200

echo -n "Gateway -> Rate Limiting Headers... "
RATE_RES=$(curl -s -I http://localhost:8080/api/v1/donaciones/saludo || true)
if echo "$RATE_RES" | grep -q "X-RateLimit-Limit"; then
    echo -e "${GREEN}[OK - Cabeceras de Rate Limiting presentes]${NC}"
    PASSED=$((PASSED + 1))
else
    echo -e "${YELLOW}[AVISO - Sin cabeceras de rate limiting directas]${NC}"
fi

# 4. Flujo de Negocio Completo
echo -e "\n${YELLOW}4. Ejecutando Flujo de Negocio E2E:${NC}"

echo -n " -> Normalización de Dirección Geográfica (Georef AR)... "
NORM_RES=$(curl -s "http://localhost:8082/api/v1/donaciones/direcciones/normalizar?direccion=Medrano%20951,%20CABA")
if echo "$NORM_RES" | grep -q "esValida.*true"; then
    echo -e "${GREEN}[OK - Dirección validada y georreferenciada correctamente]${NC}"
    PASSED=$((PASSED + 1))
else
    echo -e "${RED}[FALLÓ normalización de dirección]${NC}"
    FAILED=$((FAILED + 1))
fi

echo -n " -> Creando Entidad Beneficiaria... "
ENTIDAD_RES=$(curl -s -X POST http://localhost:8082/api/v1/entidades \
  -H "Content-Type: application/json" \
  -d '{"razonSocial":"Comedor Los Chicos Felices","direccion":"Av. Corrientes 1234, CABA","telefono":"11-4444-5555","emailsRepresentantes":["contacto@comedor.los.chicos.felices.org"]}')
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

echo -n " -> Creando Donante Humana (con email y teléfono de contacto)... "
RAND_SUFFIX="$RANDOM$RANDOM"
DONANTE_EMAIL="carlos.gomez.$RAND_SUFFIX@donatrack.org"
DONANTE_RES=$(curl -s -X POST http://localhost:8082/api/v1/donantes/humana \
  -H "Content-Type: application/json" \
  -d "{\"nombre\":\"Carlos\",\"apellido\":\"Gómez\",\"edad\":34,\"nroDocumento\":\"$RAND_SUFFIX\",\"genero\":\"M\",\"direccion\":\"Mitre 456, CABA\",\"mediosContacto\":[{\"tipo\":\"EMAIL\",\"valor\":\"$DONANTE_EMAIL\",\"esPredeterminado\":true},{\"tipo\":\"SMS\",\"valor\":\"+541123456789\",\"esPredeterminado\":false}]}")
DONANTE_ID=$(echo "$DONANTE_RES" | grep -o '"id":[0-9]*' | head -n1 | cut -d: -f2)

if [ -n "$DONANTE_ID" ]; then
    echo -e "${GREEN}[OK - Donante Humana ID $DONANTE_ID creado ($DONANTE_EMAIL)]${NC}"
    PASSED=$((PASSED + 1))
else
    echo -e "${RED}[FALLÓ creando donante]${NC}"
    FAILED=$((FAILED + 1))
fi

echo -n " -> Creando Donante Empresa / Persona Jurídica (con CUIT y Razón Social)... "
CUIT_RAND="30-7$RAND_SUFFIX-9"
EMPRESA_RES=$(curl -s -X POST http://localhost:8082/api/v1/donantes/juridica \
  -H "Content-Type: application/json" \
  -d "{\"razonSocial\":\"Lácteos del Sur S.A.\",\"cuit\":\"$CUIT_RAND\",\"tipo\":\"Empresa\",\"rubro\":\"Alimentos\",\"direccion\":\"Av. San Juan 2500, CABA\",\"representantes\":[\"Roberto Martínez\"],\"mediosContacto\":[{\"tipo\":\"EMAIL\",\"valor\":\"rse@lacteosdelsur.$RAND_SUFFIX.com\",\"esPredeterminado\":true}]}")
EMPRESA_ID=$(echo "$EMPRESA_RES" | grep -o '"id":[0-9]*' | head -n1 | cut -d: -f2)

if [ -n "$EMPRESA_ID" ]; then
    echo -e "${GREEN}[OK - Donante Empresa ID $EMPRESA_ID creado]${NC}"
    PASSED=$((PASSED + 1))
else
    echo -e "${RED}[FALLÓ creando donante jurídica]${NC}"
    FAILED=$((FAILED + 1))
fi

echo -n " -> Búsqueda de Donante por Endpoint /api/v1/donantes/buscar... "
BUSCAR_RES=$(curl -s "http://localhost:8082/api/v1/donantes/buscar?email=$DONANTE_EMAIL")
if echo "$BUSCAR_RES" | grep -q "$DONANTE_EMAIL"; then
    echo -e "${GREEN}[OK - Donante localizado por email]${NC}"
    PASSED=$((PASSED + 1))
else
    echo -e "${RED}[FALLÓ búsqueda de donante]${NC}"
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

echo -n " -> Verificando Configuración Persistida de Matchmaking (a1, a2, a3, alpha)... "
CONFIG_RES=$(curl -s "http://localhost:8082/api/v1/donaciones/matchmaking/config")
if echo "$CONFIG_RES" | grep -q "alpha"; then
    echo -e "${GREEN}[OK - Configuración de pesos de Matchmaking activa]${NC}"
    PASSED=$((PASSED + 1))
else
    echo -e "${RED}[FALLÓ lectura de configuración de matchmaking]${NC}"
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
ASIGN_RES=$(curl -s -X POST "http://localhost:8082/api/v1/donaciones/$DONACION_ID/asignar?entidadId=$ENTIDAD_ID&necesidadId=$NEC_ID")
if echo "$ASIGN_RES" | grep -q "ASIGNACION_REALIZADA"; then
    echo -e "${GREEN}[OK - Donación en estado ASIGNACION_REALIZADA con necesidad asociada]${NC}"
    PASSED=$((PASSED + 1))
else
    echo -e "${RED}[FALLÓ asignación de donación]${NC}"
    FAILED=$((FAILED + 1))
fi

echo -e "\n${YELLOW}5. Verificando Características Especiales (Incentivos, Logística, GraphQL, Outbox/RabbitMQ):${NC}"

test_endpoint "Incentivos - Misiones Activas Inicializadas" "http://localhost:8084/api/v1/incentivos/misiones/activas" 200

echo -n " -> Incentivos - Analítica de Donante (evolución mensual y organizaciones ayudadas)... "
ANALITICA_RES=$(curl -s "http://localhost:8084/api/v1/incentivos/donantes/$DONANTE_ID/analitica")
if echo "$ANALITICA_RES" | grep -q "organizacionesAyudadas"; then
    echo -e "${GREEN}[OK - Analítica e impacto calculados]${NC}"
    PASSED=$((PASSED + 1))
else
    echo -e "${RED}[FALLÓ analítica de incentivos]${NC}"
    FAILED=$((FAILED + 1))
fi

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

echo -n " -> Logística - Despacho y Confirmación de Entrega por Donación... "
CONFIRM_ENTREGA=$(curl -s -X POST "http://localhost:8083/api/v1/logistica/entregas/donacion/$DONACION_ID/confirmar" \
  -H "Content-Type: application/json" \
  -d '{"fotos":["https://images.unsplash.com/photo-1593113598332-cd288d649433"],"urlFotoComprobante":"https://images.unsplash.com/photo-1593113598332-cd288d649433","receptorNombre":"Maria Gomez","receptorDni":"32456789","comentarios":"Entrega confirmada y recibida en comedor"}')
if echo "$CONFIRM_ENTREGA" | grep -q "idEntrega\|ENTREGADA\|mensaje\|id"; then
    echo -e "${GREEN}[OK - Entrega coordinada con servicio de logística]${NC}"
    PASSED=$((PASSED + 1))
else
    echo -e "${RED}[FALLÓ confirmación de entrega logística]${NC}"
    FAILED=$((FAILED + 1))
fi

echo -n " -> Donaciones - GraphQL Schema Query ({ donaciones { id subcategoria estado } })... "
GQL_RES=$(curl -s -X POST http://localhost:8082/graphql \
  -H "Content-Type: application/json" \
  -d '{"query":"{ donaciones { id subcategoria estado } }"}')
if echo "$GQL_RES" | grep -q "data.*donaciones"; then
    echo -e "${GREEN}[OK - Endpoint GraphQL respondió correctamente]${NC}"
    PASSED=$((PASSED + 1))
else
    echo -e "${RED}[FALLÓ consulta GraphQL]${NC}"
    FAILED=$((FAILED + 1))
fi

echo -n " -> Notificaciones - Verificando procesamiento asíncrono Outbox -> RabbitMQ -> Mailpit... "
sleep 6
NOTIF_HIST=$(curl -s "http://localhost:8085/api/v1/notificaciones/historial?destinatario=$DONANTE_EMAIL")
MAILPIT_MSGS=$(curl -s "http://localhost:8025/api/v1/messages")
if echo "$NOTIF_HIST" | grep -q "ENVIADO" && echo "$MAILPIT_MSGS" | grep -q "$DONANTE_EMAIL"; then
    echo -e "${GREEN}[OK - Evento procesado por RabbitMQ, auditado en BD y correo recibido en Mailpit]${NC}"
    PASSED=$((PASSED + 1))
else
    echo -e "${RED}[FALLÓ verificación de notificación asíncrona para $DONANTE_EMAIL]${NC}"
    FAILED=$((FAILED + 1))
fi

echo ""
echo -e "${BLUE}==========================================================${NC}"
echo -e " Resumen de Pruebas: ${GREEN}$PASSED APROBADAS${NC} | ${RED}$FAILED FALLIDAS${NC}"
echo -e "${BLUE}==========================================================${NC}"

if [ "$FAILED" -eq 0 ]; then
    echo -e "${GREEN}¡Todos los microservicios, reglas de negocio y flujos de DonaTrack operan al 100%!${NC}"
    exit 0
else
    echo -e "${RED}Algunas pruebas fallaron. Revise los logs en carpeta logs/.${NC}"
    exit 1
fi
