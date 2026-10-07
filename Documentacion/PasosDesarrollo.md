# Guía de Operación, Desarrollo y Pruebas - DonaTrack

Bienvenido a la documentación de arquitectura, desarrollo y testing automatizado de **DonaTrack**, sistema integral de gestión y trazabilidad de donaciones desarrollado para la cátedra de **Diseño de Sistemas de Información (UTN - DDSI 2026)**.

---

## 1. Arquitectura de Microservicios Desacoplados

El sistema implementa una arquitectura hexagonal desacoplada, orientada a eventos con RabbitMQ y con persistencia políglota:

```
TP-DDSI/
├── docker/                             # Infraestructura Docker (Postgres, Mongo, RabbitMQ, Mailpit, Keycloak)
├── Documentacion/
│   ├── Diagramas/                      # Diagramas PlantUML de Arquitectura, Dominio, Estados y Flujos
│   ├── Servicios/                      # Especificaciones técnicas detalladas por microservicio
│   ├── Configuracion/                  # Guías de configuración de Keycloak, RabbitMQ y variables
│   ├── Integracion_y_Comunicacion.md   # Contratos de integración REST y eventos AMQP
│   └── PasosDesarrollo.md              # Esta guía de ejecución, scripts y pruebas
├── scripts/                            # Suite de automatización (up, down, clean, build, test-e2e)
├── pom.xml                             # POM Raíz Multimódulo Maven
├── common-dtos/                        # Contratos y DTOs de Eventos compartidos (AMQP)
├── donaciones-service/                 # Microservicio de Donaciones (PostgreSQL :8082 + Outbox)
├── logistica-service/                  # Microservicio de Logística (MongoDB NoSQL :8083)
├── incentivos-service/                 # Microservicio de Incentivos y Analítica (PostgreSQL :8084)
├── notificaciones-service/             # Microservicio de Notificaciones AMQP (:8085)
├── api-gateway/                        # Spring Cloud Gateway (:8080)
└── logs/                               # Logs en tiempo real generados por cada microservicio
```

### Índice de Documentación Técnica en `Documentacion/`
* 📐 **Diagramas PlantUML**:
  - [Arquitectura Física y Lógica](file:///home/jhon/Documentos/Github/TP-DDSI/Documentacion/Diagramas/Arquitectura.puml)
  - [Modelo Integral de Dominio y Eventos](file:///home/jhon/Documentos/Github/TP-DDSI/Documentacion/Diagramas/Dominio.puml)
  - [Máquina de Estados de la Donación](file:///home/jhon/Documentos/Github/TP-DDSI/Documentacion/Diagramas/Maquina_Estados_Donacion.puml)
  - [Flujo Secuencia: Matchmaking, Asignación y Outbox](file:///home/jhon/Documentos/Github/TP-DDSI/Documentacion/Diagramas/Flujo_Matchmaking_Asignacion.puml)
  - [Flujo Secuencia: Logística, Lotes <= 100 y Callback](file:///home/jhon/Documentos/Github/TP-DDSI/Documentacion/Diagramas/Flujo_Logistica_Rutas.puml)
* ⚙️ **Especificaciones por Servicio**:
  - [Microservicio de Donaciones](file:///home/jhon/Documentos/Github/TP-DDSI/Documentacion/Servicios/DonacionesService.md)
  - [Microservicio de Logística](file:///home/jhon/Documentos/Github/TP-DDSI/Documentacion/Servicios/LogisticaService.md)
  - [Microservicio de Incentivos](file:///home/jhon/Documentos/Github/TP-DDSI/Documentacion/Servicios/IncentivosService.md)
  - [Microservicio de Notificaciones](file:///home/jhon/Documentos/Github/TP-DDSI/Documentacion/Servicios/NotificacionesService.md)
  - [API Gateway y Seguridad Keycloak](file:///home/jhon/Documentos/Github/TP-DDSI/Documentacion/Servicios/ApiGateway_Keycloak.md)
* 🔌 **Integración y Configuración**:
  - [Integración, Comunicación y Contratos Inter-Servicio](file:///home/jhon/Documentos/Github/TP-DDSI/Documentacion/Integracion_y_Comunicacion.md)
  - [Configuración de Keycloak (Realm, Clientes, Roles)](file:///home/jhon/Documentos/Github/TP-DDSI/Documentacion/Configuracion/Keycloak_Setup.md)
  - [Topología de RabbitMQ (Exchanges, Queues, DLQ)](file:///home/jhon/Documentos/Github/TP-DDSI/Documentacion/Configuracion/RabbitMQ_Topologia.md)
  - [Matriz de Variables de Entorno y Perfiles](file:///home/jhon/Documentos/Github/TP-DDSI/Documentacion/Configuracion/Variables_Entorno.md)

---

## 2. Mapa de Servicios y Puertos

| Componente | Tipo | Puerto | Credenciales / URLs | Datastore & Rol |
| :--- | :--- | :--- | :--- | :--- |
| **`api-gateway`** | Gateway Inverso | `8080` | `http://localhost:8080` | Spring Cloud Gateway (Enrutamiento dinámico y CORS) |
| **`keycloak`** | Identity Provider | `8081` | Admin: `admin` / `admin` (`http://localhost:8081`) | Keycloak 24 (OIDC / OAuth2 / JWT / Social-Login) |
| **`donaciones-service`** | Negocio Core | `8082` | `http://localhost:8082` | PostgreSQL (`donaciones_db`) + Transactional Outbox |
| **`logistica-service`** | Logística y Rutas | `8083` | `http://localhost:8083` | MongoDB (`logistica_db`) + Rutas por lotes $\le 100$ |
| **`incentivos-service`** | Gamificación | `8084` | `http://localhost:8084` | PostgreSQL (`incentivos_db`) + Webhooks n8n |
| **`notificaciones-service`**| Consumidor AMQP | `8085` | Consumidor asíncrono | RabbitMQ topics + Mailpit SMTP + SMS/WhatsApp simulados |
| **`postgres`** | BD Relacional | `5432` | `donatrack` / `donatrack_password` | PostgreSQL 16 Alpine (`donaciones_db`, `incentivos_db`, `keycloak_db`) |
| **`mongo`** | BD NoSQL | `27017` | Sin auth local | MongoDB 7.0 (`logistica_db`) |
| **`rabbitmq`** | Broker AMQP | `5672` (AMQP)<br>`15672` (UI) | `donatrack` / `donatrack_password`<br>`http://localhost:15672` | RabbitMQ 3.13 con Management UI |
| **`mailpit`** | Servidor SMTP Local | `1025` (SMTP)<br>`8025` (UI) | `http://localhost:8025` | Inspección visual e interactiva de emails enviados |

---

## 3. Guía de Automatización con Scripts (`scripts/`)

Para facilitar el ciclo de vida completo de la aplicación, se dispone de una suite de scripts ejecutables en `scripts/`:

### 3.1. Levantar Infraestructura Base (Bases de datos + Broker + Auth + Mail)
```bash
./scripts/infra-up.sh
```
* Inicia Docker Compose en background (`postgres`, `mongo`, `rabbitmq`, `mailpit`, `keycloak`).
* Espera hasta que PostgreSQL y RabbitMQ acepten conexiones.

### 3.2. Compilar e Instalar Paquetes Maven
```bash
./scripts/build.sh
```
* Compila el proyecto raíz multimódulo con OpenJDK (`clean install -DskipTests`).
* Registra `common-dtos` en el repositorio local `~/.m2`, permitiendo que todos los servicios resuelvan las dependencias compartidas.

### 3.3. Iniciar Todos los Microservicios
```bash
./scripts/services-up.sh
```
* Inicia en background (`setsid nohup`) los 5 microservicios con optimización de memoria JVM (`-Xms64m -Xmx256m` por servicio).
* Registra los PIDs en `.services.pid`.
* Guarda logs en tiempo real dentro del directorio `logs/`:
  - `logs/donaciones-service.log`
  - `logs/logistica-service.log`
  - `logs/incentivos-service.log`
  - `logs/notificaciones-service.log`
  - `logs/api-gateway.log`
* Ejecuta un bucle de espera de preparación (*Readiness Probe*) hasta que el Gateway y los servicios comiencen a responder HTTP 200.

### 3.4. Ejecutar la Suite de Pruebas y Verificación E2E
```bash
./scripts/test-e2e.sh
```
Ejecuta 17 pruebas automáticas:
1. **Infraestructura**: RabbitMQ (15672), Mailpit (8025), Keycloak (8081).
2. **Servicios de Negocio**:
   - Donaciones: Saludo Entrega 1 (`GET /api/v1/donaciones/saludo`).
   - Donaciones: Listado (`GET /api/v1/donaciones`).
   - Logística: Listado flota de camiones (`GET /api/v1/logistica/camiones`).
   - Incentivos: Ranking mensual (`GET /api/v1/incentivos/rankings/mensual`).
3. **API Gateway (:8080)**: Health Check y enrutamiento hacia Donaciones, Logística e Incentivos.
4. **Flujo de Negocio Completo E2E**:
   - Crea Entidad Beneficiaria.
   - Registra Necesidad Material (arroz, 50 kg, urgencia EXTRAORDINARIA).
   - Crea Donante Humano.
   - Registra Carga Segmentada de Donación (agrupada por subcategoría).
   - Ejecuta Matchmaking semántico (Levenshtein $\chi_1$ + Cobertura $\chi_2$ + Urgencia $\chi_3$ + Prioridad Subatendidos).
   - Confirma Asignación a la Entidad Beneficiaria con persistencia de evento en tabla Outbox.

### 3.5. Detener Microservicios
```bash
./scripts/services-down.sh
```
* Detiene de manera limpia los 5 procesos Java registrados en `.services.pid`.

### 3.6. Detener Infraestructura Docker
```bash
./scripts/infra-down.sh
```

### 3.7. Reset Completo (Limpieza de volúmenes)
```bash
./scripts/infra-clean.sh
```
* Apaga los contenedores y elimina todos los volúmenes persistentes (`postgres_data`, `mongo_data`, `rabbitmq_data`).

---

## 4. Desarrollo y Ejecución Individual de Microservicios

Si estás desarrollando en un único módulo y querés ejecutarlo en primer plano (con recarga rápida):

1. Asegúrate de que la infraestructura esté corriendo: `./scripts/infra-up.sh`
2. Si realizaste cambios en `common-dtos`, compílalo primero:
   ```bash
   ./mvnw -pl common-dtos install -DskipTests
   ```
3. Ejecuta el microservicio deseado con `spring-boot:run`:
   ```bash
   # Microservicio de Donaciones (:8082)
   ./mvnw -pl donaciones-service spring-boot:run

   # Microservicio de Logística (:8083)
   ./mvnw -pl logistica-service spring-boot:run

   # Microservicio de Incentivos (:8084)
   ./mvnw -pl incentivos-service spring-boot:run

   # Consumidor de Notificaciones (:8085)
   ./mvnw -pl notificaciones-service spring-boot:run

   # API Gateway (:8080)
   ./mvnw -pl api-gateway spring-boot:run
   ```

---

## 5. Pruebas Manuales con cURL y APIs

### 5.1. Entrega 1: Saludo del Microservicio de Donaciones
A través del servicio directo:
```bash
curl http://localhost:8082/api/v1/donaciones/saludo
# Salida esperada: "Hola desde el servicio de Donaciones."
```
O a través del API Gateway:
```bash
curl http://localhost:8080/api/v1/donaciones/saludo
```

### 5.2. Carga y Segmentación Automática por Subcategoría
```bash
curl -X POST "http://localhost:8080/api/v1/donaciones/carga-segmentada?donanteId=1" \
     -H "Content-Type: application/json" \
     -d '{
       "bienes": [
         {"nombre": "Leche en polvo", "subcategoria": "Lacteos", "cantidad": 20, "unidadMedida": "kg", "volumenM3": 0.05, "pesoKg": 20.0},
         {"nombre": "Yogur bebible", "subcategoria": "Lacteos", "cantidad": 10, "unidadMedida": "litros", "volumenM3": 0.02, "pesoKg": 10.0},
         {"nombre": "Remeras termicas", "subcategoria": "Indumentaria", "cantidad": 15, "unidadMedida": "unidades", "volumenM3": 0.08, "pesoKg": 5.0}
       ]
     }'
```
*Genera donaciones independientes agrupadas por su subcategoría como unidad mínima de asignación.*

### 5.3. Importación Masiva por Streaming CSV (>20.000 filas)
```bash
curl -X POST "http://localhost:8080/api/v1/donaciones/importar-csv" \
     -F "file=@ruta/al/archivo_donaciones.csv"
```
*Procesamiento en flujo (`BufferedReader`) con transacciones por lotes (`batch_size=500` con `flush`/`clear`), previniendo desbordes de memoria RAM.*

### 5.4. Callback de Ruteo en Lotes (Logística)
```bash
curl -X POST "http://localhost:8080/api/v1/logistica/rutas/callback" \
     -H "Content-Type: application/json" \
     -d '{
       "idLote": "LOTE-BUE-2026-001",
       "camionId": "CAMION-01",
       "donacionIds": [1, 2, 3],
       "distanciaKmTotal": 24.5,
       "tiempoEstimadoMinutos": 45,
       "paradas": [
         {"orden": 1, "direccion": "Av. Corrientes 1234", "latitud": -34.6037, "longitud": -58.3816, "tipo": "RECOLECCION", "donacionId": 1},
         {"orden": 2, "direccion": "Av. Medrano 951", "latitud": -34.5985, "longitud": -58.4201, "tipo": "ENTREGA", "donacionId": 1}
       ]
     }'
```

### 5.5. Paneles de Inspección Visual
* **RabbitMQ Management**: [http://localhost:15672](http://localhost:15672) (Usuario: `donatrack`, Clave: `donatrack_password`). Permite auditar colas, intercambiadores (`donatrack.events`) y el flujo de mensajes.
* **Bandeja Mailpit**: [http://localhost:8025](http://localhost:8025). Permite inspeccionar en tiempo real los emails disparados por las asignaciones.
* **Keycloak Admin**: [http://localhost:8081](http://localhost:8081) (Usuario: `admin`, Clave: `admin`). Permite gestionar el Realm, clientes y usuarios.
