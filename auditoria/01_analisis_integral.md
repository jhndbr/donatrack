# 01 — Análisis Integral de Arquitectura, Funcionalidad, Seguridad y Requisitos (DonaTrack)

> **Fecha de Auditoría:** 25 de octubre de 2026  
> **Objeto de Auditoría:** Repositorio multimódulo `TP-DDSI` (DonaTrack — Trabajo Práctico Anual Integrador 2026, Diseño de Sistemas de Información, UTN.BA)  
> **Modo de Ejecución:** Inspección estática exhaustiva del 100% del código fuente + verificación dinámica no destructiva (`./mvnw test`). Ningún archivo existente del proyecto fue modificado durante esta auditoría.

---

## Índice General
1. [Resumen Ejecutivo](#1-resumen-ejecutivo)
2. [Descripción de la Arquitectura Encontrada](#2-descripción-de-la-arquitectura-encontrada)
3. [Tecnologías y Componentes Identificados](#3-tecnologías-y-componentes-identificados)
4. [Metodología y Alcance de la Auditoría](#4-metodología-y-alcance-de-la-auditoría)
5. [Matriz Completa de Requisitos del Enunciado](#5-matriz-completa-de-requisitos-del-enunciado)
6. [Estado de Cumplimiento Detallado por Requisito y Flujo E2E](#6-estado-de-cumplimiento-detallado-por-requisito-y-flujo-e2e)
7. [Evidencias Concretas de Código (Archivos, Clases, Endpoints y Pruebas)](#7-evidencias-concretas-de-código-archivos-clases-endpoints-y-pruebas)
8. [Catálogo de Errores e Incumplimientos Confirmados (`HAL-001` a `HAL-024`)](#8-catálogo-de-errores-e-incumplimientos-confirmados-hal-001-a-hal-024)
9. [Implementaciones Parciales o Sin Verificar](#9-implementaciones-parciales-o-sin-verificar)
10. [Problemas de Arquitectura, Calidad, Seguridad y Mantenibilidad](#10-problemas-de-arquitectura-calidad-seguridad-y-mantenibilidad)
11. [Mejoras Recomendadas con Justificación Técnica](#11-mejoras-recomendadas-con-justificación-técnica)
12. [Riesgos, Limitaciones y Decisiones Pendientes](#12-riesgos-limitaciones-y-decisiones-pendientes)
13. [Resumen de Pruebas y Verificaciones Ejecutadas](#13-resumen-de-pruebas-y-verificaciones-ejecutadas)
14. [Conclusiones sobre el Grado de Cumplimiento del Proyecto](#14-conclusiones-sobre-el-grado-de-cumplimiento-del-proyecto)

---

## 1. Resumen Ejecutivo

El sistema **DonaTrack** presenta una arquitectura multimódulo en **Java 21 y Spring Boot 3.2.5** que compila íntegramente sin errores y supera actualmente **196 pruebas automatizadas** distribuidas en 30 clases de test (`0 fallos, 0 errores`). La separación de contextos delimitados (*Bounded Contexts*) y la estrategia de **persistencia políglota** exigida en la Entrega 4 se encuentran correctamente materializadas: `donaciones-service`, `incentivos-service` y `notificaciones-service` persisten en esquemas relacionales independientes sobre **PostgreSQL 16** mediante **Spring Data JPA (Hibernate)**, mientras que `logistica-service` utiliza exclusivamente **MongoDB 7.0** mediante **Spring Data MongoDB (`@Document`)**. Asimismo, la comunicación hacia `notificaciones-service` e `incentivos-service` está desacoplada asincrónicamente mediante **RabbitMQ** con patrón **Transactional Outbox** en `donaciones-service` y control de idempotencia + *Dead Letter Queue* (`dlq.notificaciones`) en los consumidores.

No obstante, al contrastar el flujo real de los datos y las llamadas entre componentes contra el enunciado oficial (`Documentacion/tp_extraido.txt`), se identificaron **24 hallazgos técnicos y funcionales** (clasificados en **3 críticos `P0`**, **11 altos `P1`**, **8 medios `P2`** y **2 bajos `P3`**) que impiden dar por cerrado el proyecto sin una fase previa de corrección controlada:

1. **Bloqueos Críticos (`P0`):**
   - **Ausencia total de seguridad y Rate Limiting en `api-gateway` (`HAL-001`):** A pesar de lo descrito en la documentación interna, `api-gateway` no posee `SecurityConfig.java`, no incluye `spring-boot-starter-oauth2-resource-server` y carece de filtro de *Rate Limiting*, dejando todos los endpoints `/api/v1/**` abiertos sin validación JWT.
   - **Ruta rota (HTTP 404) en el registro de Personas Jurídicas (`HAL-002`):** `ApiClientService.crearDonanteJuridica` invoca `POST /api/v1/personas-donantes/juridica`, ruta inexistente en `api-gateway/src/main/resources/application.yml` (que solo enruta `/api/v1/donantes/**`).
   - **Credenciales volátiles en memoria RAM, contraseñas maestras universales y vulnerabilidades IDOR en `frontend-service` (`HAL-003`):** Los usuarios registrados desde `/registro` se guardan únicamente en un `ConcurrentHashMap` en memoria que se pierde al reiniciar el servidor; además, `AuthService` acepta `"admin123"`, `"donante123"` y `"comedor123"` para cualquier cuenta existente, y los controladores web permiten operar sobre donaciones/entregas de otros usuarios cambiando el ID en la petición (IDOR).

2. **Incumplimientos Funcionales y de Reglas de Negocio (`P1`):**
   - **Mapa de seguimiento en tiempo real para Donante y Beneficiaria hardcodeado (`HAL-004`):** `templates/donante/tracking.html` ignora el modelo de Thymeleaf y dibuja marcadores y polilíneas estáticas en JavaScript; `beneficiaria/tracking.html` no filtra los camiones dirigidos a la entidad en sesión.
   - **Doble contabilización y salto de secuencialidad en `incentivos-service` (`HAL-005`):** Una misma donación suma dos veces `totalDonaciones` y `totalBienesDonados` (al ingresar a depósito por `donacion.registrada` y al asignarse por `donacion.asignada`), la misión *Hábil Donador* acumula donaciones chicas en vez de exigir una donación que supere $X$ bienes, y el bucle secuencial no se detiene cuando la misión activa es `COMPLETITUD` y se dona una subcategoría repetida.
   - **Falta de modificación (Update) de Personas Donantes (`HAL-006`)**, desconexión entre la confirmación de entrega en el frontend y `logistica-service` (`HAL-007`), ausencia de alta de donantes y carga múltiple de bienes en la vista administrativa de depósito (`HAL-008`), falta de filtro por categoría en donaciones del donante (`HAL-009`), visualización simultánea de rankings separados en matchmaking aun cuando existe intersección (`HAL-010`), falta de filtro de último mes en la landing page (`HAL-011`), ausencia de gráficos de evolución y analítica en el perfil del donante (`HAL-012`), y falta de `Dockerfile`s y contenedores de observabilidad en `docker-compose.yml` (`HAL-014`).

---

## 2. Descripción de la Arquitectura Encontrada

El repositorio implementa una arquitectura distribuida organizada en un proyecto Maven padre (`donatrack-parent`, packaging `pom`) compuesto por **7 módulos**:

```
TP-DDSI (donatrack-parent)
├── common-dtos              # Biblioteca compartida de enums, DTOs y contratos de eventos AMQP
├── donaciones-service       # Puerto 8082 | Núcleo transaccional (Donantes, Donaciones, Entidades, Necesidades, Matchmaking, Outbox)
├── logistica-service        # Puerto 8083 | Flota de camiones, Planificación VRP en lotes <=100, Callback, Entregas y Telemetría GPS
├── incentivos-service       # Puerto 8084 | Analítica de donantes, Misiones secuenciales, Insignias, Categorías, Ranking mensual y Webhook n8n
├── notificaciones-service   # Puerto 8085 | Consumidor RabbitMQ con DLQ, Idempotencia, Auditoría y Despacho (Email SMTP / SMS / WhatsApp)
├── api-gateway              # Puerto 8080 | Spring Cloud Gateway (WebFlux) como punto de entrada de APIs
└── frontend-service         # Puerto 8086 | Cliente liviano SSR (Spring Web MVC + Thymeleaf) desacoplado de la lógica de negocio
```

### Flujo de Comunicación Real Verificado en Código
1. **Cliente Web $\rightarrow$ `frontend-service` (`:8086`):** Renderizado en servidor con Thymeleaf (`templates/**/*.html`). Gestiona sesiones HTTP (`HttpSession`) mediante `UserSession` y `AuthInterceptor`.
2. **`frontend-service` $\rightarrow$ `api-gateway` (`:8080`):** `ApiClientService` realiza llamadas HTTP sincrónicas con `RestTemplate` hacia `http://localhost:8080/api/v1/**`.
3. **`api-gateway` (`:8080`) $\rightarrow$ Microservicios de Backend (`:8082`, `:8083`, `:8084`, `:8085`):** Enruta las peticiones según predicados `Path` definidos en `api-gateway/src/main/resources/application.yml`.
4. **Comunicación Inter-Servicio Sincrónica (`logistica-service` $\rightarrow$ `donaciones-service`):** `DonacionesServiceClient` en `logistica-service` invoca por HTTP REST a `donaciones-service` (`GET /api/v1/donaciones/asignadas-logistica`, `GET /api/v1/donaciones/{id}`, `PUT /api/v1/donaciones/bulk-estado`, `PATCH /api/v1/donaciones/{id}/estado`).
5. **Comunicación Inter-Servicio Asincrónica (RabbitMQ `donatrack.events.exchange`):**
   - `donaciones-service` persiste eventos en la tabla `outbox_events` dentro de la misma transacción JPA y `OutboxPublisher` los publica cada 2 segundos en RabbitMQ (`donacion.registrada`, `donacion.asignada`, `donante.inactivo`, `donante.registrado`).
   - `logistica-service` publica directamente con `RabbitTemplate` (`ruta.iniciada`, `entrega.confirmada`, `entrega.fallida`).
   - `incentivos-service` consume `donacion.registrada`, `donacion.asignada` y `entrega.confirmada`, y publica `mision.cumplida` y `categoria.ascendida`.
   - `notificaciones-service` consume todos los eventos anteriores, verifica idempotencia en `registros_eventos_notificaciones`, despacha por `EmailNotificationSender` (hacia Mailpit `:1025`), `SmsNotificationSender` o `WhatsAppNotificationSender`, y registra el resultado en `auditorias_notificaciones`.

---

## 3. Tecnologías y Componentes Identificados

| Capa / Componente | Tecnología / Versión Identificada | Ubicación de Evidencia |
| :--- | :--- | :--- |
| **Lenguaje y Runtime** | Java 21 (`maven.compiler.source/target = 21`) | `pom.xml:16-18` |
| **Framework Base** | Spring Boot `3.2.5`, Spring Cloud `2023.0.1` | `pom.xml:20-21` |
| **Persistencia Relacional (ORM)** | Spring Data JPA / Hibernate + PostgreSQL 16 (`donaciones_db`, `incentivos_db`, `notificaciones_db`, `keycloak_db`) | `docker/docker-compose.yml:5-22`, `docker/init-scripts/01-init-databases.sql:1-6` |
| **Persistencia Documental (ODM)** | Spring Data MongoDB + MongoDB 7.0 (`logistica_db`) | `logistica-service/pom.xml:31-35`, `logistica-service/src/main/resources/application.yml:8-10` |
| **Mensajería Asincrónica** | Spring AMQP + RabbitMQ `3.13-management-alpine` (Topic Exchange + DLX/DLQ) | `docker/docker-compose.yml:37-48`, `notificaciones-service/.../RabbitMqConfig.java:18-88` |
| **API Gateway** | Spring Cloud Gateway (WebFlux) | `api-gateway/pom.xml:21-24`, `api-gateway/src/main/resources/application.yml:11-85` |
| **Frontend SSR** | Spring Boot Starter Web + Thymeleaf + Bootstrap 5 + Leaflet.js | `frontend-service/pom.xml:21-33`, `frontend-service/src/main/resources/templates/layout.html` |
| **Consultas Flexibles (Entrega 6)** | Spring for GraphQL (`spring-boot-starter-graphql`) | `donaciones-service/pom.xml:35-38`, `donaciones-service/src/main/resources/graphql/schema.graphqls` |
| **Identidad y Autenticación** | Keycloak `24.0.5` (importación de `realm-donatrack.json`) + `AuthService` local | `docker/docker-compose.yml:62-76`, `frontend-service/.../AuthService.java:1-409` |
| **Servidor SMTP de Prueba** | Mailpit `v1.18` (SMTP `:1025`, Web API `:8025`) | `docker/docker-compose.yml:51-59`, `frontend-service/.../BuzonWebController.java:20-69` |
| **Integración Externa Geográfica** | API Georef Argentina (`apis.datos.gob.ar/georef/api/direcciones`) + Nominatim OSM | `donaciones-service/.../NormalizacionDireccionService.java:25-31` |
| **Observabilidad** | Spring Boot Actuator + Micrometer Prometheus (en los 4 servicios de dominio) | `donaciones-service/pom.xml:65-68`, `logistica-service/pom.xml:45-48` |

---

## 4. Metodología y Alcance de la Auditoría

1. **Inspección Completa del Árbol de Directorios:** Se auditaron la totalidad de archivos fuente Java (`src/main/java`), pruebas (`src/test/java`), plantillas HTML (`src/main/resources/templates`), archivos de configuración (`application.yml`, `pom.xml`), infraestructura (`docker/docker-compose.yml`, `docker/keycloak/realm-donatrack.json`, `docker/init-scripts/`), scripts (`scripts/*.sh`) y documentación (`Documentacion/**`).
2. **Contraste contra Fuentes Normativas:** Se comparó línea por línea contra el enunciado oficial (`Documentacion/tp_extraido.txt`), el análisis funcional (`Documentacion/analisisv2enunciado/analisisv2.md`) y el catálogo de casos de uso (`Documentacion/CasosDeUsos.md`), registrando explícitamente las diferencias encontradas entre estos documentos (ver Sección 12).
3. **Trazabilidad Punta a Punta:** Ningún requisito fue dado por válido por la mera presencia de una clase o pantalla; se verificó el flujo desde el evento de UI/HTTP en `frontend-service`, su paso por `api-gateway`, la lógica de negocio y persistencia en el microservicio, la emisión/consumo de eventos en RabbitMQ y el impacto en los servicios suscriptores.
4. **Ejecución Segura de Pruebas:** Se ejecutó `./mvnw test` sobre los 8 módulos del reactor Maven, verificando tiempos, resultados y cobertura real de cada suite.

---

## 5. Matriz Completa de Requisitos del Enunciado

### 5.1. Requisitos Funcionales de Interfaz y Acceso (`RF-001` a `RF-020` — Págs. 4 y 5 del Enunciado)

| ID | Requisito del Enunciado | Componentes Responsables | Evidencia Principal (Archivo y Líneas) | Estado |
| :--- | :--- | :--- | :--- | :--- |
| **RF-001** | Landing page pública con propósito, donaciones destacadas del último mes y enlaces a fotos sin login, registro/login y privacidad. | `frontend-service`, `donaciones-service` | `HomeController.java:29-68`, `templates/index.html:1-300`, `templates/comprobantes.html:1-171`, `templates/privacidad.html:1-24` | **Implementado parcialmente** (`HAL-011`) |
| **RF-002** | Mapa interactivo público con donaciones entregadas y visualización de detalles/fotos al hacer clic en cada marcador. | `frontend-service`, `donaciones-service` | `DonacionController.java:120-148`, `HomeController.java:43-50`, `templates/index.html:147-161, 216-298` | **Implementado parcialmente** (`HAL-011`) |
| **RF-003** | Registro de personas donantes (humanas y jurídicas) y entidades beneficiarias. | `frontend-service`, `api-gateway`, `donaciones-service` | `HomeController.java:139-290`, `templates/registro.html:1-246`, `ApiClientService.java:207-233, 624-631` | **Incorrecto** (`HAL-002`, `HAL-003`) |
| **RF-004** | Inicio de sesión de personas donantes, entidades beneficiarias y personas administradoras (más choferes). | `frontend-service`, Keycloak | `HomeController.java:70-137`, `AuthService.java:61-257`, `templates/login.html:1-161` | **Implementado parcialmente** (`HAL-003`) |
| **RF-005** | Persona donante: filtrar sus donaciones realizadas por estado y categoría o subcategoría/s. | `frontend-service`, `donaciones-service` | `DonanteWebController.java:147-196`, `templates/donante/donaciones.html:32-56`, `DonacionController.java:85-98` | **Implementado parcialmente** (`HAL-009`) |
| **RF-006** | Persona donante: navegar por las entidades beneficiarias registradas. | `frontend-service`, `donaciones-service` | `DonanteWebController.java:236-241`, `templates/donante/entidades.html:1-53` | **Cumplido y verificado** |
| **RF-007** | Persona donante: acceder a sección de Incentivos y consultar misiones a realizar, insignias obtenidas y analítica de evolución. | `frontend-service`, `incentivos-service` | `DonanteWebController.java:198-215`, `templates/donante/incentivos.html:1-100`, `IncentivosService.java:276-323` | **Implementado parcialmente** (`HAL-012`) |
| **RF-008** | Persona donante: recibir notificaciones al asignarse donación, cumplir misión, subir de categoría o confirmarse recepción. | `notificaciones-service`, `donaciones-service`, `incentivos-service`, `logistica-service` | `NotificacionesService.java:116-455`, `DonanteWebController.java:262-286`, `BuzonWebController.java:28-69` | **Cumplido y verificado** (con matiz de canal en `HAL-019`) |
| **RF-009** | Persona donante: seguir en un mapa las entregas activas de sus donaciones con ubicación y última actualización del camión. | `frontend-service`, `logistica-service` | `DonanteWebController.java:217-234`, `templates/donante/tracking.html:98-150` | **Incorrecto** (`HAL-004`) |
| **RF-010** | Entidad beneficiaria: registrar necesidades materiales concretas (recurrentes y extraordinarias). | `frontend-service`, `donaciones-service` | `BeneficiariaWebController.java:111-162`, `templates/beneficiaria/necesidades.html:1-125`, `EntidadBeneficiariaController.java:57-96` | **Cumplido y verificado** (alta/listado; edición UI en `HAL-021`) |
| **RF-011** | Entidad beneficiaria: ver el estado de las donaciones asignadas a ella. | `frontend-service`, `donaciones-service` | `BeneficiariaWebController.java:42-76`, `templates/beneficiaria/dashboard.html:71-130` | **Cumplido y verificado** |
| **RF-012** | Entidad beneficiaria: confirmar la recepción de una donación cargando fotos de la misma. | `frontend-service`, `logistica-service`, `donaciones-service` | `BeneficiariaWebController.java:197-236`, `templates/beneficiaria/confirmar.html:44-142`, `EntregaServiceImpl.java:63-105` | **Implementado parcialmente** (`HAL-007`) |
| **RF-013** | Entidad beneficiaria: recibir notificaciones en base a asignación de donaciones y confirmación de recepción. | `notificaciones-service`, `donaciones-service`, `logistica-service` | `NotificacionesService.java:152-167, 314-328`, `BeneficiariaWebController.java:78-109` | **Cumplido y verificado** |
| **RF-014** | Entidad beneficiaria: seguir en un mapa las entregas activas que debe recibir con ubicación y última actualización. | `frontend-service`, `logistica-service` | `BeneficiariaWebController.java:238-245`, `templates/beneficiaria/tracking.html:22-54` | **Implementado parcialmente** (`HAL-004`) |
| **RF-015** | Persona administradora: registrar personas donantes y donaciones recibidas en el depósito asociándolas al donante. | `frontend-service`, `donaciones-service` | `AdminWebController.java:181-206`, `templates/admin/nueva_donacion.html:30-120`, `DonacionService.java:57-147` | **Implementado parcialmente** (`HAL-008`) |
| **RF-016** | Persona administradora: actualizar el estado de las donaciones disponibles en depósito a "Vencida". | `frontend-service`, `donaciones-service` | `AdminWebController.java:247-258`, `templates/admin/dashboard.html:215-220`, `Donacion.java:212-217` | **Cumplido y verificado** (con observación en `HAL-015`) |
| **RF-017** | Persona administradora: seleccionar la entidad beneficiaria final a partir del resultado de los algoritmos de matchmaking. | `frontend-service`, `donaciones-service` | `AdminWebController.java:89-135`, `templates/admin/matchmaking.html:78-181`, `MatchmakingService.java:36-79` | **Implementado parcialmente** (`HAL-010`) |
| **RF-018** | Persona administradora: administrar los camiones disponibles. | `frontend-service`, `logistica-service` | `AdminWebController.java:53-87`, `templates/admin/camiones.html:1-95`, `FlotaController.java:23-62` | **Cumplido y verificado** (falta edición en UI `HAL-021`) |
| **RF-019** | Persona administradora: visualizar el ranking mensual de donantes más activos y el historial de rankings anteriores. | `frontend-service`, `incentivos-service` | `AdminWebController.java:170-179`, `templates/admin/rankings.html:1-124`, `IncentivosController.java:73-91` | **Cumplido y verificado** |
| **RF-020** | Persona administradora: importar personas donantes desde archivos CSV con más de 10.000 / 20.000 filas. | `frontend-service`, `donaciones-service` | `AdminWebController.java:137-168`, `CsvDonanteImporterService.java:46-240`, `DonacionController.java:220-232` | **Implementado parcialmente** (`HAL-020`) |

---

### 5.2. Requisitos de Dominio, Reglas de Negocio, Integración, Datos, Seguridad y No Funcionales (Entregas 1 a 6)

| ID | Categoría / Entrega | Descripción del Requisito | Componentes | Evidencia Principal | Estado |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **RF-021** | Dominio (E1) | Endpoint GET simple en Servicio de Donaciones que retorne `"Hola desde el servicio de Donaciones."` | `donaciones-service` | `SaludoController.java:15-18`, `DonacionController.java:59-62` | **Cumplido y verificado** |
| **RF-022** | Dominio (E1, E2) | CRUD completo sobre personas donantes (humanas y jurídicas), donaciones, entidades beneficiarias y necesidades materiales. | `donaciones-service` | `DonanteController.java:18-70`, `DonacionController.java:65-358`, `EntidadBeneficiariaController.java:20-96` | **Implementado parcialmente** (falta Update de donantes `HAL-006`) |
| **RF-023** | Dominio (E3) | Chofer de camión: visualizar su ruta del día e informar el comienzo de su recorrido. | `frontend-service`, `logistica-service` | `ChoferWebController.java:25-146`, `templates/chofer/dashboard.html`, `RecorridoServiceImpl.java:41-105` | **Cumplido y verificado** |
| **REG-001** | Regla de Negocio (E1) | Segmentación automática de una carga de bienes en múltiples donaciones por `Subcategoría` (unidad mínima), separando perecederos por fecha de vencimiento y muebles/vestimenta por condición (nuevo/usado). | `donaciones-service` | `DonacionService.java:57-147`, `BienDonado.java:49-58` | **Implementado parcialmente** (`HAL-008`) |
| **REG-002** | Regla de Negocio (E1, E2) | Distinción entre necesidades `EXTRAORDINARIA` (acumulación parcial hasta cubrir total) y `RECURRENTE` (por período con reinicio periódico). | `donaciones-service` | `NecesidadMaterial.java:69-104`, `DonacionesScheduler.java:139-172` | **Implementado parcialmente** (`HAL-015`) |
| **REG-003** | Regla de Negocio (E2) | Algoritmo de Compatibilidad Semántica ($S_{sem} = a_1 \chi_1 + a_2 \chi_2 + a_3 \chi_3$) con Levenshtein, cobertura de volumen y urgencia, y pesos configurables que sumen 1. | `donaciones-service` | `CompatibilidadSemanticaStrategy.java:16-116` | **Implementado parcialmente** (pesos no persistidos en BD `HAL-016`) |
| **REG-004** | Regla de Negocio (E2) | Algoritmo de Prioridad a Sub-atendidos ($S_{sub} = \frac{1}{1 + \alpha \cdot CantDonacionesTrimestre}$), intersección de ambos Top 10 y ejecución en horarios de baja carga. | `donaciones-service` | `PrioridadSubatendidosStrategy.java:18-73`, `MatchmakingService.java:36-79`, `DonacionesScheduler.java:73-134` | **Cumplido en backend / Parcial en UI** (`HAL-010`) |
| **REG-005** | Regla de Negocio (E2) | Detección de donantes sin interacción por más de 20 días y envío de notificación de incentivo. | `donaciones-service`, `notificaciones-service` | `DonacionesScheduler.java:177-215`, `PersonaDonanteRepository.java:26-27`, `NotificacionesService.java:169-197` | **Implementado parcialmente** (sin control anti-spam diario `HAL-018`) |
| **REG-006** | Regla de Negocio (E2) | Tres categorías de donante (`COLABORADOR`, `SOSTENEDOR`, `TRANSFORMADOR`), misiones secuenciales (*Racha*, *Completitud*, *Hábil Donador*, *Donaciones Exitosas*), insignias con visibilidad configurable y pérdida de racha mensual. | `incentivos-service` | `IncentivosService.java:153-401`, `IncentivosDataInitializer.java:29-98`, `IncentivosScheduler.java:97-115` | **Implementado parcialmente** (`HAL-005`) |
| **REG-007** | Regla de Negocio (E2) | Cierre de ranking mensual de donantes más activos calculado en función de las misiones cumplidas durante ese período, persistiendo histórico y destacando Top 3. | `incentivos-service` | `IncentivosScheduler.java:41-91`, `RankingMensual.java:6-67` | **Cumplido y verificado** |
| **REG-008** | Regla de Negocio (E2, E3) | Máquina de estados de `Donacion` (`EN_DEPOSITO` $\rightarrow$ `ASIGNACION_REALIZADA` $\rightarrow$ `LISTA_PARA_ENTREGAR` $\rightarrow$ `EN_TRASLADO` $\rightarrow$ `ENTREGADA`; ramas `ENTREGA_FALLIDA` $\rightarrow$ `EN_DEPOSITO` y `VENCIDA`) con trazabilidad/auditoría. | `donaciones-service`, `logistica-service` | `Donacion.java:94-224`, `HistorialEstadoDonacion.java:14-115` | **Implementado parcialmente** (`HAL-015`) |
| **INT-001** | Integración (E1, E2, E3) | Integración asincrónica por cola de mensajes hacia `notificaciones-service` y envío real por correo electrónico, SMS y/o WhatsApp respetando medio preferido. | Todos los servicios de dominio + `notificaciones-service` | `OutboxPublisher.java:28-64`, `NotificacionesService.java:63-497`, `EmailNotificationSender.java:17-65` | **Implementado parcialmente** (Email SMTP real; SMS/WhatsApp simulados en logs; canal en eventos de logística/incentivos fijo en `EMAIL` `HAL-019`) |
| **INT-002** | Integración (E2) | Automatización low-code (n8n) para difundir insignias obtenidas en redes sociales con generación de imagen. | `incentivos-service` | `IncentivosService.java:356-369`, `IncentivosController.java:142-157` | **Implementado parcialmente** (cliente webhook HTTP implementado; falta contenedor/flujo n8n en Docker `HAL-014`) |
| **INT-003** | Integración (E3) | Planificación de rutas en horarios de baja carga enviando lotes de máximo 100 donaciones al proveedor externo, recepción por URL de callback y custodia de donaciones sin asignar. | `logistica-service`, `donaciones-service` | `PlanificacionRutasServiceImpl.java:49-248`, `RutaController.java:79-86`, `ResilientRutaOptimizerClient.java:18-47` | **Cumplido y verificado** |
| **INT-004** | Integración (E3) | Recepción y validación de telemetría GPS de camiones en tiempo real y notificación de inicio de ruta (con link al mapa), entrega exitosa (con comprobante) y entrega fallida (a donante, beneficiaria y admin). | `logistica-service`, `notificaciones-service` | `TelemetriaServiceImpl.java:39-128`, `RecorridoServiceImpl.java:41-105`, `EntregaServiceImpl.java:63-167`, `NotificacionesService.java:244-392` | **Cumplido y verificado** |
| **DAT-001** | Persistencia (E4) | Persistencia políglota: base de datos documental (ODM) para `logistica-service` y bases relacionales propias (ORM) para los servicios restantes, con estrategias de desnormalización. | `donaciones-service`, `logistica-service`, `incentivos-service`, `notificaciones-service` | `logistica-service/pom.xml:31-35`, `Camion.java:9`, `Ruta.java:12`, `01-init-databases.sql:1-6` | **Cumplido y verificado** (faltan índices SQL en tablas relacionales `HAL-016`, `HAL-020`) |
| **RNF-001** | UI/UX y Maquetado (E4) | Principios de usabilidad, máx. 3 niveles de profundidad, microcopy, notificaciones no intrusivas, loaders, diseño responsive, WCAG AA. | `frontend-service` | `templates/layout.html:1-265`, `templates/**/*.html` | **Cumplido y verificado** |
| **RNF-002** | Arquitectura MVC (E5) | Cliente liviano desacoplado con motor de plantillas (SSR) consumiendo APIs de backend. | `frontend-service` | `frontend-service/pom.xml`, `ApiClientService.java` | **Cumplido y verificado** |
| **SEG-001** | Seguridad (E5) | Servicio de Autenticación y gestión de tokens de autorización desde el servidor frontend hacia los servicios de aplicación. | Keycloak, `frontend-service`, `api-gateway` | `AuthService.java:1-409`, `ApiGatewayApplication.java`, `ApiClientService.java` | **Incorrecto** (`HAL-001`, `HAL-003`) |
| **RNF-005** | Despliegue Cloud (E6 Req 1) | Despliegue del sistema en la nube accesible por el público general. | Infraestructura / Docker | `docker/docker-compose.yml` | **No implementado** (`HAL-014`) |
| **RNF-006** | Observabilidad (E6 Req 2) | Soluciones de observabilidad para recolectar, visualizar y analizar métricas, trazas y logs en tiempo real. | Todos los servicios + Docker | `application.yml` de servicios, `docker/docker-compose.yml` | **Implementado parcialmente** (Actuator/Micrometer en 4 servicios; sin Prometheus/Grafana/Zipkin en Docker `HAL-014`) |
| **RNF-007** | Monitoreo y Autorestart (E6 Req 3) | Herramientas de monitoreo proactivo de salud y políticas de autorestart ante fallos. | Docker / Scripts | `docker/docker-compose.yml`, `scripts/services-up.sh` | **Implementado parcialmente** (`HAL-014`) |
| **RNF-008** | Rate Limiting (E6 Req 4) | Sistema de limitación de solicitudes (Rate Limiting) que regule el tráfico entrante. | `api-gateway` | `api-gateway/src/main/java/com/donatrack/gateway/` | **No implementado** (`HAL-013`) |
| **RNF-009** | GraphQL o gRPC (E6 Req 5) | Implementar Opción 1 (gRPC) u Opción 2 (interfaz GraphQL en Donaciones o Logística para filtros dinámicos y selección de campos). | `donaciones-service`, `api-gateway` | `DonacionGraphqlController.java:16-72`, `schema.graphqls:1-18` | **Cumplido y verificado** (con mejora de filtrado en BD `HAL-022`) |
| **RNF-010** | Bonus Microservicios (E6 Req 6 y 7) | Bonus 6: Service Registry + API Gateway. Bonus 7: Patrón Transactional Outbox. | `donaciones-service`, `api-gateway` | `OutboxEvent.java`, `OutboxPublisher.java` (Bonus 7 cumplido; Bonus 6 sin Eureka `HAL-024`) | **Implementado parcialmente** |

---

## 6. Estado de Cumplimiento Detallado por Requisito y Flujo E2E

### 6.1. Flujo 1: Registro y Gestión de Personas Donantes e Importación CSV
- **Comportamiento esperado:** Permitir el alta, modificación y baja de personas humanas (nombre, apellido, edad, tipo/nro documento, género, dirección y email obligatorio + teléfono/WhatsApp opcional + medio preferido) y personas jurídicas (razón social, CUIT, tipo, rubro, medios de contacto y representantes), enviando correo de bienvenida con credenciales, y permitiendo importar archivos CSV de +10.000/20.000 filas (actualizando si el email existe o creando usuario nuevo si no existe).
- **Flujo real en código:**
  - **Alta Humana:** `POST /registro/donante` (`HomeController.java:157-203`) $\rightarrow$ `ApiClientService.crearDonanteHumana` (`POST /api/v1/donantes/humana`) $\rightarrow$ `DonanteController.registrarHumana` (`DonanteController.java:41-44`) $\rightarrow$ `DonanteService.registrarPersonaHumana` (`DonanteService.java:67-92`). Valida dirección con Georef, persiste `PersonaHumana` y guarda un `OutboxEvent` con `DonanteRegistradoEvent`.
  - **Alta Jurídica:** `POST /registro/juridica` (`HomeController.java:205-250`) $\rightarrow$ `ApiClientService.crearDonanteJuridica` (`ApiClientService.java:629`) invoca `POST /api/v1/personas-donantes/juridica`. **Falla con 404 en el Gateway** porque `api-gateway/src/main/resources/application.yml:20-23` solo enruta `/api/v1/donantes/**` (`HAL-002`). Además, en `DonanteService.registrarPersonaJuridica` (`líneas 95-117`) no se valida que tenga al menos un medio de contacto (`HAL-017`).
  - **Modificación:** No existe endpoint `PUT`/`PATCH` en `DonanteController.java` (`HAL-006`).
  - **Importación CSV:** `POST /admin/importar-csv` (`AdminWebController.java:142-168`) $\rightarrow$ `POST /api/v1/donaciones/importar-csv` (`DonacionController.java:220-232`) $\rightarrow$ `CsvDonanteImporterService.importarDesdeStream` (`CsvDonanteImporterService.java:89-141`). Lee en streaming y hace `flush()/clear()` cada 500 filas, pero ejecuta todo bajo un único `@Transactional` y sin índice en `medios_contacto(tipo, valor)` (`HAL-020`).

### 6.2. Flujo 2: Recepción en Depósito y Segmentación Automática
- **Comportamiento esperado:** Registrar una carga con descripción general y múltiples bienes; segmentar automáticamente en donaciones independientes por subcategoría, separando perecederos con distinta fecha de vencimiento y no perecederos según condición nuevo/usado, dejándolas en estado `EN_DEPOSITO` y notificando a `incentivos-service`.
- **Flujo real en código:**
  - `DonacionService.registrarCargaSegmentada` (`DonacionService.java:61-147`) agrupa por `subcategoria + "_" + fechaVencimiento + "_" + (esUsado ? "USADO" : "NUEVO")`, persiste cada `Donacion` en `EN_DEPOSITO` con traza en `HistorialEstadoDonacion` y emite `DonacionRegistradaEvent` vía `OutboxEvent`.
  - **Defectos detectados:**
    1. Si desde `/donante/nueva-donacion` se envía `entidadId != null`, `DonacionService.java:110-145` auto-asigna la donación sin `necesidadId` y **no emite `DonacionRegistradaEvent`** (`HAL-005`, `HAL-008`).
    2. El formulario administrativo `templates/admin/nueva_donacion.html` solo permite cargar **1 bien** por vez y carece del campo `fechaVencimiento` (`HAL-008`).

### 6.3. Flujo 3: Matchmaking y Selección de Entidad Beneficiaria
- **Comportamiento esperado:** Evaluar donaciones `EN_DEPOSITO` contra necesidades activas de igual subcategoría mediante Compatibilidad Semántica y Prioridad a Sub-atendidos; presentar al administrador la intersección de ambos Top 10 (o ambos rankings por separado solo si la intersección es vacía); confirmar asignación pasando a `ASIGNACION_REALIZADA` y notificando a donante y entidad beneficiaria.
- **Flujo real en código:**
  - `MatchmakingService.evaluarDonacion` (`MatchmakingService.java:36-79`) ejecuta ambas estrategias registradas en `MatchmakingStrategyRegistry`, obtiene ambos Top 10 y calcula `coincidenciasDestacadas` (intersección).
  - En `templates/admin/matchmaking.html:78-181`, se muestra la intersección pero **también se muestran siempre las dos columnas separadas debajo** (`HAL-010`), y el botón de adjudicar no envía `necesidadId`.
  - Los pesos configurados vía `PUT /api/v1/donaciones/matchmaking/config` se guardan solo en memoria RAM (`HAL-016`).

### 6.4. Flujo 4 y 5: Planificación de Rutas por Lotes, Callback, Inicio de Ruta, Telemetría y Recepción
- **Comportamiento esperado:** Planificar en lotes de hasta 100 donaciones en `ASIGNACION_REALIZADA`, procesar callback pasando a `LISTA_PARA_ENTREGAR` y encolando no asignadas; permitir al chofer iniciar ruta pasando a `EN_TRASLADO` y enviando link del mapa en vivo; recibir telemetría GPS; confirmar recepción con fotos pasando a `ENTREGADA`, actualizando necesidad, enviando comprobante con fecha/hora/camión y notificando a Incentivos; o reportar entrega fallida notificando a las 3 partes y permitiendo replanificación al regresar al depósito.
- **Flujo real en código:**
  - En `logistica-service` (`PlanificacionRutasServiceImpl`, `RecorridoServiceImpl`, `TelemetriaServiceImpl`, `EntregaServiceImpl`), toda la lógica de negocio, MongoDB, corte en lotes de 100, callback idempotente, custodia de `DonacionPendienteLote` y eventos RabbitMQ está **completamente implementada y verificada**.
  - **Defectos en la integración con `frontend-service`:**
    1. `templates/donante/tracking.html:98-150` tiene el mapa 100% hardcodeado en JS (`HAL-004`).
    2. `BeneficiariaWebController.confirmarRecepcion` (`líneas 197-236`) junto con `ApiClientService.entregarDonacion` (`líneas 423-450`) puentea a `logistica-service` cuando el usuario no ingresa manualmente el `idEntrega` de MongoDB (`HAL-007`).

### 6.5. Flujo 6 y 7: Ludificación, Difusión en Redes Sociales, Ranking Mensual y Notificaciones
- **Comportamiento esperado:** Evaluar misiones en orden secuencial por categoría (`COLABORADOR` $\rightarrow$ `SOSTENEDOR` $\rightarrow$ `TRANSFORMADOR`), otorgar insignias, difundir en redes sociales vía n8n, evaluar pérdida de racha mensual, calcular ranking mensual por misiones cumplidas en el mes y despachar notificaciones multicanal.
- **Flujo real en código:**
  - `IncentivosService` y `NotificacionesService` están implementados con persistencia en PostgreSQL e idempotencia AMQP, pero presentan la doble contabilización entre `donacion.registrada` y `donacion.asignada` (`HAL-005`) y el uso de constructores cortos de eventos con canal por defecto `EMAIL` (`HAL-019`).

---

## 7. Evidencias Concretas de Código (Archivos, Clases, Endpoints y Pruebas)

| Módulo | Clases de Dominio / Persistencia | Controladores / Endpoints Expuestos | Servicios / Schedulers / Consumidores | Pruebas Automatizadas (`src/test`) |
| :--- | :--- | :--- | :--- | :--- |
| **`common-dtos`** | Enums: `EstadoDonacion`, `TipoNecesidad`, `CategoriaDonante`, `CanalNotificacion`. Eventos: `DonacionRegistradaEvent`, `DonacionAsignadaEvent`, `DonanteRegistradoEvent`, `DonanteInactivoEvent`, `RutaIniciadaEvent`, `EntregaConfirmadaEvent`, `EntregaFallidaEvent`, `MisionCumplidaEvent`, `CategoriaAscendidaEvent`. | Biblioteca JAR compartida | N/A | 0 tests |
| **`donaciones-service`** | JPA (`PostgreSQL`): `PersonaDonante`, `PersonaHumana`, `PersonaJuridica`, `MedioContacto`, `Donacion`, `BienDonado`, `HistorialEstadoDonacion`, `EntidadBeneficiaria`, `NecesidadMaterial`, `OutboxEvent`. | `DonacionController` (`/api/v1/donaciones/**`), `DonanteController` (`/api/v1/donantes/**`), `EntidadBeneficiariaController` (`/api/v1/entidades/**`), `DireccionController` (`/api/v1/direcciones/**`), `DonacionGraphqlController` (`/graphql`), `SaludoController`. | `DonacionService`, `DonanteService`, `EntidadBeneficiariaService`, `CsvDonanteImporterService`, `MatchmakingService`, `CompatibilidadSemanticaStrategy`, `PrioridadSubatendidosStrategy`, `NormalizacionDireccionService`, `FileStorageService`, `DonacionesScheduler`, `OutboxPublisher`. | 13 clases de test (**70 tests**) |
| **`logistica-service`** | MongoDB (`@Document`): `Camion`, `Ruta`, `Parada` (embebido), `Entrega`, `DonacionPendienteLote`, `UbicacionGPS`, `CoordenadaGPS`. | `FlotaController` (`/api/v1/logistica/camiones/**`), `RutaController` (`/api/v1/logistica/rutas/**`), `EntregaController` (`/api/v1/logistica/entregas/**`), `TelemetriaController` (`/api/v1/logistica/telemetria/**`). | `FlotaServiceImpl`, `PlanificacionRutasServiceImpl`, `RecorridoServiceImpl`, `EntregaServiceImpl`, `TelemetriaServiceImpl`, `ResilientRutaOptimizerClient`, `DonacionesServiceClient`, `LogisticaEventPublisher`, `LogisticaScheduler`. | 6 clases de test (**28 tests**) |
| **`incentivos-service`** | JPA (`PostgreSQL`): `PerfilDonanteIncentivo`, `Mision`, `ProgresoMision`, `Insignia`, `RankingMensual`, `RegistroEventoProcesado`. | `IncentivosController` (`/api/v1/incentivos/**`). | `IncentivosService`, `DonacionEventConsumer`, `IncentivosScheduler`, `IncentivosDataInitializer`. | 4 clases de test (**20 tests**) |
| **`notificaciones-service`** | JPA (`PostgreSQL`): `AuditoriaNotificacion`, `RegistroEventoNotificacion`. | `NotificacionesController` (`/api/v1/notificaciones/**`). | `NotificacionesService`, `NotificationDispatcher`, `EmailNotificationSender`, `SmsNotificationSender`, `WhatsAppNotificationSender`, `DonacionNotificacionesConsumer`, `LogisticaNotificacionesConsumer`, `IncentivosNotificacionesConsumer`. | 5 clases de test (**27 tests**) |
| **`api-gateway`** | Sin persistencia | Spring Cloud Gateway (`:8080`, rutas en `application.yml`). | `ApiGatewayApplication` (única clase). | 1 clase de test (**1 test**) |
| **`frontend-service`** | Sesión HTTP en memoria (`UserSession`). | `HomeController`, `DonanteWebController`, `BeneficiariaWebController`, `AdminWebController`, `ChoferWebController`, `BuzonWebController`, `ImageProxyController`. | `ApiClientService`, `AuthService`, `AuthInterceptor`. | 1 clase de test (**50 tests**) |

---

## 8. Catálogo de Errores e Incumplimientos Confirmados (`HAL-001` a `HAL-024`)

A continuación se documenta cada hallazgo con la estructura completa exigida para su trazabilidad y posterior resolución.

---

### `HAL-001` — Ausencia Total de Validación JWT y Autorización en `api-gateway`
- **Tipo:** Problema de seguridad / Incumplimiento del enunciado
- **Prioridad:** **P0 — Crítica**
- **Requisito relacionado:** `SEG-001` (Entrega 5 pág. 21: Servicio de Autenticación y gestión de tokens de autorización; Entrega 6 pág. 22: Seguridad y control).
- **Descripción del problema:** El `api-gateway` carece por completo de configuración de seguridad OAuth2 Resource Server y no valida tokens JWT ni restringe el acceso por roles. Además, ningún microservicio aguas abajo incluye `spring-boot-starter-security`.
- **Evidencia y ubicación exacta:**
  - `api-gateway/src/main/java/com/donatrack/gateway/`: Solo contiene `ApiGatewayApplication.java:1-12`. No existe `SecurityConfig.java`.
  - `api-gateway/pom.xml:20-36`: No declara `spring-boot-starter-security` ni `spring-boot-starter-oauth2-resource-server`.
  - `api-gateway/src/main/resources/application.yml:1-95`: No posee configuración `spring.security.oauth2.resourceserver.jwt`, y en `líneas 80-85` define CORS con `allowedOrigins: "*"`.
  - `frontend-service/src/main/java/com/donatrack/frontend/service/ApiClientService.java:391, 405, 454, 483, 536, 711`: Seis métodos instancian `new HttpHeaders()` directamente en lugar de usar `buildAuthHeaders()`.
- **Comportamiento actual:** Cualquier cliente externo puede invocar directamente `DELETE /api/v1/donantes/{id}`, `POST /api/v1/donaciones/{id}/asignar`, `PATCH /api/v1/donaciones/{id}/estado` o `PUT /api/v1/donaciones/matchmaking/config` en `http://localhost:8080` sin enviar token ni credencial alguna.
- **Comportamiento esperado:** El `api-gateway` debe validar los tokens JWT o cabeceras de autorización emitidas tras la autenticación, permitiendo sin token únicamente las rutas públicas (`GET /api/v1/donaciones/publico/**`, normalización de direcciones y alta en registro público) y exigiendo autenticación/roles válidos para las operaciones protegidas.
- **Impacto:** Exposición total de las APIs de escritura y administración del sistema.
- **Causa confirmada:** La capa de seguridad documentada en `Documentacion/Servicios/ApiGateway_Keycloak.md` nunca fue agregada al código fuente de `api-gateway`.
- **Solución recomendada:** Incorporar `SecurityConfig.java` en `api-gateway` y unificar el uso de `buildAuthHeaders()` en todos los métodos de `ApiClientService.java`.
- **Archivos o componentes a modificar:** `api-gateway/pom.xml`, `api-gateway/src/main/java/com/donatrack/gateway/config/SecurityConfig.java`, `api-gateway/src/main/resources/application.yml`, `frontend-service/src/main/java/com/donatrack/frontend/service/ApiClientService.java`.
- **Pruebas necesarias:** Tests con `WebTestClient` en `api-gateway` verificando rechazo `401/403` sin credenciales y paso `200` con token válido.
- **Riesgos o dependencias de la solución:** Si `api-gateway` exige un JWT de Keycloak pero el usuario inició sesión por el fallback local de `AuthService` (cuando `accessToken == null`), el Gateway rechazará las peticiones del frontend si no se sincroniza antes `AuthService` con Keycloak (`HAL-003`).

---

### `HAL-002` — Error 404 al Registrar Personas Jurídicas por Ruta Inexistente en `api-gateway` y Pérdida del Teléfono en Registro
- **Tipo:** Error funcional / Problema de integración
- **Prioridad:** **P0 — Crítica**
- **Requisito relacionado:** `RF-003`, `RF-022` (Registro de Personas Donantes Jurídicas y Humanas).
- **Descripción del problema:** El cliente HTTP del frontend envía el alta de Persona Jurídica a `/api/v1/personas-donantes/juridica`, ruta que no está configurada en el `api-gateway`. Además, `HomeController` ignora el teléfono ingresado en el formulario tanto para Persona Humana como para Persona Jurídica.
- **Evidencia y ubicación exacta:**
  - `frontend-service/src/main/java/com/donatrack/frontend/service/ApiClientService.java:629`: `String url = gatewayUrl + "/api/v1/personas-donantes/juridica";`.
  - `api-gateway/src/main/resources/application.yml:20-23`: Solo define `- Path=/api/v1/donantes/**` (no incluye `/api/v1/personas-donantes/**`).
  - `frontend-service/src/main/java/com/donatrack/frontend/controller/HomeController.java:164, 180-190` y `212, 228-237`: Recibe `@RequestParam("telefono") String telefono` pero solo inserta el `EMAIL` en `mediosContacto`.
- **Comportamiento actual:** Al completar la pestaña "Empresa / Jurídica" en `/registro` y enviar el formulario, el Gateway devuelve `404 Not Found`, `crearDonanteJuridica` retorna `null` y el usuario recibe el error *"No se pudo completar el registro de la organización"*.
- **Comportamiento esperado:** La petición debe enrutarse a `POST /api/v1/donantes/juridica`, persistiendo la organización con todos sus medios de contacto (email y teléfono) y representantes.
- **Impacto:** Bloqueo total del registro público de organizaciones/empresas donantes.
- **Causa confirmada:** Discrepancia de rutas entre `ApiClientService.java:629` y `api-gateway/src/main/resources/application.yml:23`.
- **Solución recomendada:** Apuntar `ApiClientService.crearDonanteJuridica` a `/api/v1/donantes/juridica`, añadir `/api/v1/personas-donantes/**` al predicado del Gateway e incluir el teléfono en `mediosContacto` dentro de `HomeController`.
- **Archivos o componentes a modificar:** `frontend-service/src/main/java/com/donatrack/frontend/service/ApiClientService.java`, `frontend-service/src/main/java/com/donatrack/frontend/controller/HomeController.java`, `api-gateway/src/main/resources/application.yml`.
- **Pruebas necesarias:** Test en `FrontendControllersTest` para `POST /registro/juridica` verificando payload y redirección.
- **Riesgos o dependencias:** Ninguno.

---

### `HAL-003` — Credenciales Volátiles en RAM, Contraseñas Maestras Hardcodeadas, Vulnerabilidades IDOR y Open Redirect en `frontend-service`
- **Tipo:** Problema de seguridad / Error funcional
- **Prioridad:** **P0 — Crítica**
- **Requisito relacionado:** `RF-003`, `RF-004`, `SEG-001`.
- **Descripción del problema:**
  1. `AuthService` almacena las contraseñas de nuevos registros únicamente en un `ConcurrentHashMap` en memoria RAM (`credencialesRegistradas`), que se borra al reiniciar `frontend-service`.
  2. `AuthService` contiene contraseñas maestras hardcodeadas (`"donante123"`, `"comedor123"`, `"admin123"`) que permiten iniciar sesión en la cuenta de **cualquier** donante o entidad beneficiaria registrada.
  3. `BeneficiariaWebController` y `DonanteWebController` contienen vulnerabilidades IDOR que permiten confirmar entregas, reportar fallas o alterar visibilidad de insignias de otros usuarios cambiando el ID enviado en el `POST`.
  4. `HomeController.procesarLogin` redirige a cualquier URL externa pasada en `?redirect=`.
- **Evidencia y ubicación exacta:**
  - `frontend-service/src/main/java/com/donatrack/frontend/security/AuthService.java:26, 50-55`: `credencialesRegistradas` en memoria.
  - `AuthService.java:363-365`: `return "comedor123".equals(password) || "admin123".equals(password);` para cualquier entidad.
  - `AuthService.java:382-384`: `return "donante123".equals(password) || "admin123".equals(password);` para cualquier donante.
  - `BeneficiariaWebController.java:197-236, 247-286`: No verifica que `donacionId` o `idEntrega` pertenezca a `userSession.getEntidadId()`.
  - `DonanteWebController.java:243-260`: No verifica titularidad de la donación o insignia antes de consultar historial o cambiar visibilidad.
  - `HomeController.java:116-118`: `return "redirect:" + redirect;` sin validar prefijo local.
- **Comportamiento actual:** Un usuario puede suplantar a otro conociendo su email e ingresando `admin123`, o modificar donaciones de otra entidad enviando otro `donacionId`.
- **Comportamiento esperado:** Validación estricta de credenciales por usuario (persistidas en Keycloak o base de datos), verificación de propiedad del recurso (`entidadId` / `donanteId`) en cada controlador y validación de URLs relativas en redirecciones.
- **Impacto:** Compromiso crítico de autenticación, autorización horizontal (IDOR) y persistencia de cuentas.
- **Causa confirmada:** Implementación de contingencia en `AuthService` y falta de chequeos de pertenencia en endpoints `POST` de los controladores web.
- **Solución recomendada:** Restringir las contraseñas demo exclusivamente a los usuarios semilla, sincronizar el alta de usuarios con Keycloak / almacenamiento persistente, validar pertenencia de recursos en `BeneficiariaWebController` y `DonanteWebController`, y sanitizar `redirect` en `HomeController`.
- **Archivos o componentes a modificar:** `AuthService.java`, `HomeController.java`, `BeneficiariaWebController.java`, `DonanteWebController.java`.
- **Pruebas necesarias:** Tests unitarios de rechazo de contraseña ajena, bloqueo IDOR y bloqueo de Open Redirect en `FrontendControllersTest`.
- **Riesgos o dependencias:** Asegurar que los tests existentes en `FrontendControllersTest` usen credenciales registradas o demo válidas.

---

### `HAL-004` — Mapa de Seguimiento en Tiempo Real para Donante y Entidad Beneficiaria Hardcodeado / Sin Filtrar
- **Tipo:** Incumplimiento del enunciado / Error funcional
- **Prioridad:** **P1 — Alta**
- **Requisito relacionado:** `RF-009` (Req UI/UX 9, pág. 4) y `RF-014` (Req UI/UX 14, pág. 4).
- **Descripción del problema:** La vista `/donante/tracking` tiene el script de Leaflet 100% hardcodeado con coordenadas y comedores estáticos, ignorando los datos enviados por `DonanteWebController`. Por su parte, `/beneficiaria/tracking` muestra todos los camiones del sistema sin filtrar los que se dirigen a la entidad logueada ni mostrar la hora de última actualización.
- **Evidencia y ubicación exacta:**
  - `frontend-service/src/main/resources/templates/donante/tracking.html:98-150`: Dibuja `Comedor Los Chicos Felices`, `Comedor Esperanza` y `Camión AE789XY` con coordenadas fijas en JavaScript sin leer variables Thymeleaf.
  - `frontend-service/src/main/resources/templates/beneficiaria/tracking.html:22-54` y `BeneficiariaWebController.java:238-245`: Sede fija en `[-34.6037, -58.3816]` y listado global de `flotaEnVivo` sin filtrar por `entidadId`.
- **Comportamiento actual:** El donante siempre ve el mismo camión y ruta ficticia en el mapa, independientemente de sus donaciones reales o de la telemetría enviada a `logistica-service`.
- **Comportamiento esperado:** Mostrar únicamente los camiones asociados a las entregas activas de las donaciones realizadas por el donante (o dirigidas a la entidad beneficiaria), con su ubicación GPS actual y fecha/hora de última actualización.
- **Impacto:** Incumplimiento directo de los requerimientos mínimos 9 y 14 y del requerimiento de dominio 4 de la Entrega 3.
- **Causa confirmada:** Maqueta estática en `templates/donante/tracking.html` que no fue conectada a las variables del `Model`.
- **Solución recomendada:** Filtrar en `DonanteWebController.tracking` y `BeneficiariaWebController.tracking` las entregas/rutas activas del actor en sesión, cruzarlas con `flotaEnVivo` y renderizar dinámicamente en Leaflet los marcadores con `patente`, `latitud`, `longitud` y `ultimaActualizacion`.
- **Archivos o componentes a modificar:** `DonanteWebController.java`, `BeneficiariaWebController.java`, `templates/donante/tracking.html`, `templates/beneficiaria/tracking.html`.
- **Pruebas necesarias:** Tests de controlador verificando el filtrado de entregas activas por `donanteId` y `entidadId`.
- **Riesgos o dependencias:** Manejar un estado vacío informativo en el mapa cuando el usuario no tenga entregas en tránsito en ese momento.

---

### `HAL-005` — Doble Contabilización de Donaciones, Salto de Secuencialidad en `COMPLETITUD` y Acumulación Indebida en `HABIL_DONADOR` en `incentivos-service`
- **Tipo:** Regla de negocio incorrecta / Problema de integración
- **Prioridad:** **P1 — Alta**
- **Requisito relacionado:** `REG-006`, `RF-007` (Entrega 2 pág. 13).
- **Descripción del problema:**
  1. `DonacionEventConsumer` invoca `registrarImpactoDonacionCompleto()` tanto al recibir `DonacionRegistradaEvent` (`donacion.registrada`) como al recibir `DonacionAsignadaEvent` (`donacion.asignada`), duplicando `totalDonaciones` (`+2` por cada donación) y `totalBienesDonados` (`+2x`).
  2. En `DonacionService.registrarCargaSegmentada` (`líneas 110-145`), si `entidadId != null`, entra en el `if` y **no emite `DonacionRegistradaEvent`**.
  3. En `IncentivosService.registrarImpactoDonacionCompleto` (`líneas 196-229`), si la primera misión incompleta es `COMPLETITUD` y la donación actual no aporta una nueva subcategoría, `avanza` queda en `false` y el bucle **no hace `break`**, permitiendo avanzar y completar la misión siguiente (`HABIL_DONADOR`) rompiendo el orden secuencial.
  4. En `HABIL_DONADOR` (`líneas 204-212`), si `cantidadBienes < objetivo`, ejecuta `prog.agregarProgreso(cantidadBienes)`, acumulando donaciones chicas en vez de verificar que una donación supere $X$ cantidad de bienes.
  5. En `IncentivosScheduler.verificarYResetearRachasMensuales` (`líneas 97-115`), se resetea `ProgresoMision.progresoActual = 0` pero no se resetea `PerfilDonanteIncentivo.rachaMesesConsecutivos = 0`.
- **Evidencia y ubicación exacta:**
  - `incentivos-service/src/main/java/com/donatrack/incentivos/consumer/DonacionEventConsumer.java:39-65` y `93-117`.
  - `incentivos-service/src/main/java/com/donatrack/incentivos/service/IncentivosService.java:185-232`.
  - `incentivos-service/src/main/java/com/donatrack/incentivos/scheduler/IncentivosScheduler.java:106-113`.
  - `donaciones-service/src/main/java/com/donatrack/donaciones/service/DonacionService.java:110-145`.
- **Comportamiento actual:** Métricas infladas al doble y misiones completadas fuera de secuencia o con criterios alterados.
- **Comportamiento esperado:** Contabilizar el ingreso de cada donación una sola vez, actualizar `organizacionesAyudadas` al asignarse/entregarse, respetar estrictamente el bloqueo secuencial en la primera misión incompleta y evaluar `cantidadBienes >= objetivo` en *Hábil Donador*.
- **Impacto:** Alteración de las reglas centrales del Servicio de Incentivos (Entrega 2).
- **Causa confirmada:** Se agregó el listener de `donacion.registrada` sin separar la responsabilidad del listener previo de `donacion.asignada`.
- **Solución recomendada:** Separar en `IncentivosService` el impacto por registro de donación (`donacion.registrada`) del registro de entidad beneficiaria ayudada (`donacion.asignada`), asegurar `break` incondicional en la primera misión incompleta, corregir la condición de `HABIL_DONADOR` y resetear `rachaMesesConsecutivos` en el scheduler.
- **Archivos o componentes a modificar:** `DonacionEventConsumer.java`, `IncentivosService.java`, `IncentivosScheduler.java`, `DonacionService.java`.
- **Pruebas necesarias:** Tests en `IncentivosProgresoTest`, `DonacionEventConsumerTest` e `IncentivosSchedulerTest`.
- **Riesgos o dependencias:** Verificar los tests existentes en `IncentivosProgresoTest` que puedan asumir el comportamiento previo.

---

### `HAL-006` — Falta de Operación de Modificación (Update) en el CRUD de Personas Donantes
- **Tipo:** Incumplimiento del enunciado
- **Prioridad:** **P1 — Alta**
- **Requisito relacionado:** `RF-022` (Entrega 1 pág. 2: *"alta, baja y modificación de personas donantes"*; Entrega 2 pág. 14: *"Operaciones CRUD sobre las personas donantes (jurídicas y humanas)"*).
- **Descripción del problema:** No existe ningún endpoint REST (`PUT` o `PATCH`) ni método de servicio para modificar los datos personales, dirección, representantes o medio de contacto predeterminado de una persona donante (humana o jurídica).
- **Evidencia y ubicación exacta:**
  - `donaciones-service/src/main/java/com/donatrack/donaciones/controller/DonanteController.java:18-70`: Solo contiene `GET /`, `GET /{id}`, `POST /humana`, `POST /juridica`, `DELETE /{id}` y `POST /{id}/reactivar`.
  - `donaciones-service/src/main/java/com/donatrack/donaciones/service/DonanteService.java:1-181`: No tiene método `actualizar...` ni `modificar...`.
- **Comportamiento actual:** Una vez creada una persona donante, no es posible actualizar sus datos ni cambiar su medio de contacto predeterminado vía API (salvo re-importándola por CSV).
- **Comportamiento esperado:** Exponer `PUT /api/v1/donantes/humana/{id}` y `PUT /api/v1/donantes/juridica/{id}` (y endpoint para configurar el medio de contacto preferido).
- **Impacto:** Incumplimiento explícito del requisito de exposición REST de la Entrega 2 (pág. 14) y de la Entrega 1 (pág. 2 y 6).
- **Causa confirmada:** Omisión de los métodos de actualización en `DonanteController` y `DonanteService`.
- **Solución recomendada:** Implementar la actualización de `PersonaHumana` y `PersonaJuridica` en `DonanteService` y exponer los endpoints en `DonanteController`.
- **Archivos o componentes a modificar:** `DonanteService.java`, `DonanteController.java`, `ApiClientService.java`.
- **Pruebas necesarias:** Tests unitarios y de controlador para modificación de donante humano y jurídico.
- **Riesgos o dependencias:** Validar que al modificar el email o DNI/CUIT no colisione con otro donante distinto.

---

### `HAL-007` — Confirmación de Recepción en Frontend Puentea `logistica-service` y Fuerza Saltos de Estado
- **Tipo:** Problema de integración / Defecto de flujo
- **Prioridad:** **P1 — Alta**
- **Requisito relacionado:** `RF-012`, `INT-003`, `INT-004` (Entrega 3 págs. 16-17).
- **Descripción del problema:** En `/beneficiaria/confirmar`, el campo `idEntrega` es un input de texto manual opcional. Si la entidad beneficiaria selecciona su donación en el desplegable pero no escribe el ID de MongoDB en `idEntrega`, `BeneficiariaWebController` solo llama a `apiClient.entregarDonacion(donacionId, ...)`, el cual fuerza hasta tres `PATCH` seguidos en `donaciones-service` (`LISTA_PARA_ENTREGAR` $\rightarrow$ `EN_TRASLADO` $\rightarrow$ `ENTREGADA`) sin pasar por `logistica-service`.
- **Evidencia y ubicación exacta:**
  - `frontend-service/src/main/resources/templates/beneficiaria/confirmar.html:78-82, 105`.
  - `frontend-service/src/main/java/com/donatrack/frontend/controller/BeneficiariaWebController.java:197-236`.
  - `frontend-service/src/main/java/com/donatrack/frontend/service/ApiClientService.java:423-450`.
- **Comportamiento actual:** Al confirmar desde la UI sin conocer el `idEntrega` interno de MongoDB, `logistica-service` no actualiza la `Entrega` a `ENTREGADA`, no emite `EntregaConfirmadaEvent`, no envía el comprobante con fecha/hora/camión por email y no notifica a `incentivos-service`.
- **Comportamiento esperado:** El sistema debe localizar automáticamente la `Entrega` en `logistica-service` a partir del `idDonacion` seleccionado, ejecutar `EntregaService.confirmarEntrega` (que sincroniza `donaciones-service` y emite `EntregaConfirmadaEvent`) y exigir que la donación esté efectivamente en traslado.
- **Impacto:** Ruptura de la trazabilidad entre Logística, Donaciones, Notificaciones e Incentivos al confirmar desde la interfaz web.
- **Causa confirmada:** Workaround en `ApiClientService.entregarDonacion` para cambiar el estado en `donaciones-service` cuando no se tenía a mano el `idEntrega` de MongoDB.
- **Solución recomendada:** Exponer en `logistica-service` la confirmación y reporte de falla por `idDonacion` (`POST /api/v1/logistica/entregas/por-donacion/{idDonacion}/confirmar` y `/fallida`) y consumirlo desde `BeneficiariaWebController`.
- **Archivos o componentes a modificar:** `EntregaController.java`, `EntregaService.java`, `EntregaServiceImpl.java`, `ApiClientService.java`, `BeneficiariaWebController.java`, `templates/beneficiaria/confirmar.html`.
- **Pruebas necesarias:** Tests en `EntregaServiceTest` y `FrontendControllersTest`.
- **Riesgos o dependencias:** Si una donación en un entorno de prueba no pasó por planificación de ruta, informar claramente al usuario que la donación aún no está en traslado o delegar de forma controlada.

---

### `HAL-008` — Vista Administrativa de Depósito No Permite Registrar Donantes ni Cargar Múltiples Bienes por Carga, y Segmentación No Separa por Unidad de Medida
- **Tipo:** Incumplimiento del enunciado / Defecto funcional
- **Prioridad:** **P1 — Alta**
- **Requisito relacionado:** `RF-015`, `REG-001` (Requerimiento UI/UX 15 pág. 5; Entrega 1 págs. 6-7).
- **Descripción del problema:**
  1. El administrador no tiene pantalla ni formulario en `/admin` para registrar una nueva persona donante cuando se presenta en el depósito sin usuario.
  2. El formulario `/admin/donaciones/nueva` (`templates/admin/nueva_donacion.html`) solo permite ingresar **un (1) único bien** por envío, no tiene campo `fechaVencimiento` y no permite ingresar una descripción general de la carga.
  3. En `DonacionService.registrarCargaSegmentada` (`DonacionService.java:80-85`), la clave de agrupación no incluye `unidadMedida`, sumando cantidades incompatibles si dos ítems de igual subcategoría tienen distinta unidad.
- **Evidencia y ubicación exacta:**
  - `frontend-service/src/main/resources/templates/admin/nueva_donacion.html:34-120`.
  - `frontend-service/src/main/java/com/donatrack/frontend/controller/AdminWebController.java:187-206`.
  - `donaciones-service/src/main/java/com/donatrack/donaciones/service/DonacionService.java:80-98`.
- **Comportamiento actual:** El administrador no puede dar de alta donantes desde su panel ni cargar un lote con múltiples bienes (ej. 6 sillas y 1 mesa) en una única operación desde la UI.
- **Comportamiento esperado:** Permitir el alta de donantes desde el panel admin, permitir agregar múltiples filas de bienes (con fecha de vencimiento y condición) en `/admin/donaciones/nueva` y segmentar incluyendo `unidadMedida` en la clave.
- **Impacto:** Incumplimiento del Requerimiento UI/UX 15 y del caso de estudio de segmentación desde la interfaz administrativa.
- **Causa confirmada:** Formulario HTML estático de un solo ítem en `nueva_donacion.html`.
- **Solución recomendada:** Agregar alta de donante en `/admin`, filas dinámicas de bienes en `nueva_donacion.html` y `unidadMedida` en la clave de segmentación de `DonacionService`.
- **Archivos o componentes a modificar:** `DonacionService.java`, `AdminWebController.java`, `templates/admin/nueva_donacion.html`.
- **Pruebas necesarias:** Tests en `DonacionServiceTest` y `FrontendControllersTest`.
- **Riesgos o dependencias:** Mantener compatibilidad en `AdminWebController` cuando se envía un solo bien.

---

### `HAL-009` — Falta de Filtro por Categoría en el Listado de Donaciones de la Persona Donante
- **Tipo:** Incumplimiento del enunciado
- **Prioridad:** **P1 — Alta**
- **Requisito relacionado:** `RF-005` (Requerimiento UI/UX 5, pág. 4: *"La persona donante debe poder filtrar sus donaciones realizadas por estado y categoría o subcategoría/s de bienes entregados"*).
- **Descripción del problema:** Aunque `donaciones-service` soporta filtrar por `categoria`, la pantalla `/donante/donaciones`, `DonanteWebController.donaciones` y `ApiClientService.listarDonacionesPaginadas` omiten el filtro `categoria`.
- **Evidencia y ubicación exacta:**
  - `frontend-service/src/main/resources/templates/donante/donaciones.html:32-56`.
  - `frontend-service/src/main/java/com/donatrack/frontend/controller/DonanteWebController.java:148-156`.
  - `frontend-service/src/main/java/com/donatrack/frontend/service/ApiClientService.java:155-175`.
- **Comportamiento actual:** El donante solo puede filtrar por `estado` y `subcategoria`.
- **Comportamiento esperado:** Poder filtrar por `estado`, `categoria` y `subcategoria`.
- **Impacto:** Incumplimiento parcial del Requerimiento UI/UX 5.
- **Causa confirmada:** El parámetro `categoria` no fue expuesto en el controlador ni en la plantilla Thymeleaf del donante.
- **Solución recomendada:** Añadir el parámetro `categoria` en `DonanteWebController.donaciones`, `ApiClientService.listarDonacionesPaginadas` y `templates/donante/donaciones.html`.
- **Archivos o componentes a modificar:** `DonanteWebController.java`, `ApiClientService.java`, `templates/donante/donaciones.html`.
- **Pruebas necesarias:** Test en `FrontendControllersTest` filtrando por `categoria`.
- **Riesgos o dependencias:** Ninguno.

---

### `HAL-010` — Interfaz de Matchmaking Muestra Ambos Rankings Separados Aunque Existan Coincidencias en el Top 10 y No Envía `necesidadId`
- **Tipo:** Regla de negocio incorrecta / Defecto funcional
- **Prioridad:** **P1 — Alta**
- **Requisito relacionado:** `RF-017`, `REG-004` (Entrega 2 pág. 12).
- **Descripción del problema:**
  1. El enunciado exige filtrar las necesidades que aparezcan en el Top 10 de ambos algoritmos y, **si no hubo coincidencias**, mostrar ambos rankings por separado. En `templates/admin/matchmaking.html:135`, ambos rankings separados se muestran **siempre**, incluso cuando `coincidenciasDestacadas` no es vacía.
  2. Los formularios de adjudicación en `matchmaking.html:86-90, 148-152, 171-175` y `AdminWebController.asignarDonacion` (`líneas 122-135`) no envían `necesidadId`, por lo que al adjudicar desde la UI la donación queda con `necesidadAsignada = null`.
- **Evidencia y ubicación exacta:**
  - `frontend-service/src/main/resources/templates/admin/matchmaking.html:78-92, 135-181`.
  - `frontend-service/src/main/java/com/donatrack/frontend/controller/AdminWebController.java:122-135`.
  - `frontend-service/src/main/java/com/donatrack/frontend/service/ApiClientService.java:390-401`.
- **Comportamiento actual:** Se muestran siempre las tres tablas a la vez y la adjudicación no vincula ni cubre la `NecesidadMaterial` elegida.
- **Comportamiento esperado:** Mostrar únicamente la intersección cuando exista al menos una coincidencia (y los dos rankings separados solo cuando la intersección sea vacía), y enviar `necesidadId` al adjudicar.
- **Impacto:** Incumplimiento de la regla de presentación de matchmaking y falta de trazabilidad sobre la necesidad cubierta.
- **Causa confirmada:** Condición `th:if` incompleta en `matchmaking.html:135` y omisión del campo oculto `necesidadId`.
- **Solución recomendada:** Ajustar `th:if` en `matchmaking.html:135` para exigir `#lists.isEmpty(evaluacion.coincidenciasDestacadas)` y propagar `necesidadId` en el formulario, `AdminWebController` y `ApiClientService`.
- **Archivos o componentes a modificar:** `templates/admin/matchmaking.html`, `AdminWebController.java`, `ApiClientService.java`.
- **Pruebas necesarias:** Tests en `FrontendControllersTest`.
- **Riesgos o dependencias:** Ninguno.

---

### `HAL-011` — Landing Page No Filtra Donaciones del Último Mes, Suma Unidades Heterogéneas en `totalKilos` e Incluye Enlaces/Datos Hardcodeados
- **Tipo:** Incumplimiento del enunciado / Defecto funcional
- **Prioridad:** **P2 — Media**
- **Requisito relacionado:** `RF-001`, `RF-002` (Requerimientos UI/UX 1 y 2, pág. 4).
- **Descripción del problema:**
  1. `HomeController.index` toma `.limit(6)` sobre todas las donaciones sin filtrar las realizadas en el último mes.
  2. `totalKilos` suma `cantidad` de todas las donaciones sin verificar si la unidad es `kg`.
  3. El botón *"Ver Ruta"* en las tarjetas destacadas (`templates/index.html:205`) apunta a `/donante/tracking`, redirigiendo al visitante público a `/login`.
  4. `templates/comprobantes.html:50-116` y `templates/index.html:227-262` muestran casos testigo hardcodeados con fotos externas de Unsplash.
- **Evidencia y ubicación exacta:**
  - `frontend-service/src/main/java/com/donatrack/frontend/controller/HomeController.java:33-50`.
  - `frontend-service/src/main/resources/templates/index.html:205, 227-262`.
  - `frontend-service/src/main/resources/templates/comprobantes.html:50-116`.
- **Comportamiento actual:** Se muestran donaciones de cualquier fecha y el clic en *"Ver Ruta"* exige login.
- **Comportamiento esperado:** Mostrar ejemplos destacados del último mes y dirigir a vistas públicas (`/comprobantes` o `#publicMap`).
- **Impacto:** Defecto funcional visible en la pantalla principal de acceso público.
- **Causa confirmada:** Falta de filtro de fecha en `HomeController.java:48` y enlace mal dirigido en `index.html:205`.
- **Solución recomendada:** Filtrar por fecha de los últimos 30 días en `HomeController`, corregir el enlace en `index.html` y limpiar los datos ficticios hardcodeados.
- **Archivos o componentes a modificar:** `HomeController.java`, `templates/index.html`, `templates/comprobantes.html`.
- **Pruebas necesarias:** Test en `FrontendControllersTest` para `GET /`.
- **Riesgos o dependencias:** Si en un entorno recién instalado todas las donaciones son del día actual, seguirán mostrándose normalmente por estar dentro de los últimos 30 días.

---

### `HAL-012` — Ausencia de Gráficos de Evolución por Período, Comparaciones Mensuales y Organizaciones Ayudadas en la Vista de Incentivos
- **Tipo:** Incumplimiento del enunciado
- **Prioridad:** **P1 — Alta**
- **Requisito relacionado:** `RF-007` (Entrega 2 págs. 12-13).
- **Descripción del problema:** El perfil de incentivos del donante (`/donante/incentivos`) no muestra gráficos de evolución por período, ni comparaciones mensuales, ni el número de organizaciones ayudadas, ni la posición en el ranking; y el endpoint `/api/v1/incentivos/donantes/{id}/analitica` de `incentivos-service` no devuelve series temporales por período ni es consumido por `ApiClientService`.
- **Evidencia y ubicación exacta:**
  - `incentivos-service/src/main/java/com/donatrack/incentivos/service/IncentivosService.java:276-323`.
  - `frontend-service/src/main/java/com/donatrack/frontend/service/ApiClientService.java:67`.
  - `frontend-service/src/main/java/com/donatrack/frontend/controller/DonanteWebController.java:198-215`.
  - `frontend-service/src/main/resources/templates/donante/incentivos.html:1-100`.
- **Comportamiento actual:** El donante solo ve puntos, categoría, lista de misiones e insignias.
- **Comportamiento esperado:** Visualizar totales históricos, gráficos de evolución por período, comparaciones mensuales, número total de organizaciones ayudadas y posición en el ranking.
- **Impacto:** Incumplimiento de la sección "Servicio de Incentivos - Analítica de donantes" (Entrega 2, págs. 12-13).
- **Causa confirmada:** El método en `ApiClientService` y la sección de analítica en `donante/incentivos.html` quedaron sin implementar.
- **Solución recomendada:** Enriquecer `obtenerAnaliticaDonante` en `IncentivosService` con la serie por período, consumirlo en `ApiClientService` y `DonanteWebController`, y renderizar los indicadores y gráficos en `templates/donante/incentivos.html`.
- **Archivos o componentes a modificar:** `IncentivosService.java`, `ApiClientService.java`, `DonanteWebController.java`, `templates/donante/incentivos.html`.
- **Pruebas necesarias:** Tests en `IncentivosProgresoTest` y `FrontendControllersTest`.
- **Riesgos o dependencias:** Ninguno.

---

### `HAL-013` — Ausencia de Rate Limiting en `api-gateway` (Entrega 6 Req 4)
- **Tipo:** Incumplimiento del enunciado
- **Prioridad:** **P1 — Alta**
- **Requisito relacionado:** `RNF-008` (Entrega 6 pág. 22 Req 4: *"Se deberá implementar un sistema de limitación de solicitudes (Rate Limiting) que regule el tráfico entrante"*).
- **Descripción del problema:** No existe ningún filtro ni configuración de Rate Limiting en `api-gateway` ni en los microservicios.
- **Evidencia y ubicación exacta:**
  - `api-gateway/src/main/java/com/donatrack/gateway/`: Solo contiene `ApiGatewayApplication.java`.
  - `api-gateway/src/main/resources/application.yml:1-95`: Sin filtros de limitación de tasa.
- **Comportamiento actual:** El sistema acepta un número ilimitado de solicitudes por segundo desde cualquier IP sin devolver `429 Too Many Requests`.
- **Comportamiento esperado:** Regular el tráfico entrante por IP/cliente devolviendo HTTP `429 Too Many Requests` cuando se supere la cuota configurada.
- **Impacto:** Incumplimiento directo del Requerimiento detallado 4 de la Entrega 6.
- **Causa confirmada:** No implementado en el código de `api-gateway`.
- **Solución recomendada:** Implementar un `GlobalFilter` reactivo (`RateLimitingFilter`) con algoritmo Token Bucket acotado en memoria y configurable por propiedades en `api-gateway`.
- **Archivos o componentes a modificar:** `api-gateway/src/main/java/com/donatrack/gateway/filter/RateLimitingFilter.java`, `api-gateway/src/main/resources/application.yml`.
- **Pruebas necesarias:** Test unitario/integración en `api-gateway` verificando respuesta `429` al exceder el límite.
- **Riesgos o dependencias:** Configurar un umbral amplio en desarrollo/tests para no bloquear la navegación normal del frontend SSR.

---

### `HAL-014` — Ausencia de `Dockerfile`s, Contenedores de Microservicios, Stack de Observabilidad (Prometheus/Grafana), `n8n` y Configuración Cloud en `docker/`
- **Tipo:** Incumplimiento del enunciado (Entrega 6 Reqs 1, 2 y 3; Entrega 2 Entregable 5)
- **Prioridad:** **P1 — Alta**
- **Requisito relacionado:** `RNF-005`, `RNF-006`, `RNF-007`, `INT-002`.
- **Descripción del problema:**
  1. No existe ningún `Dockerfile` para los 6 servicios Java.
  2. `docker/docker-compose.yml` solo incluye los 5 contenedores de infraestructura base (`postgres`, `mongo`, `rabbitmq`, `mailpit`, `keycloak`).
  3. No están definidos en `docker-compose.yml` los contenedores de Prometheus, Grafana, Zipkin/Jaeger ni `n8n`, ni existe `docker/prometheus.yml`.
  4. `api-gateway` y `frontend-service` no incluyen `micrometer-registry-prometheus`.
  5. `scripts/infra-up.sh:24` ejecuta `01-init-databases.sql` pero omite `02-schema-updates.sql`.
- **Evidencia y ubicación exacta:**
  - `docker/docker-compose.yml:1-82`.
  - `api-gateway/pom.xml:20-36`, `frontend-service/pom.xml:20-46`.
  - `scripts/infra-up.sh:24`.
- **Comportamiento actual:** Los microservicios solo pueden correrse localmente con `java -jar` en la máquina host y las métricas de Prometheus no tienen servidor recolector ni tableros configurados.
- **Comportamiento esperado:** Contar con `Dockerfile`s, configuración de observabilidad (`prometheus.yml`, Prometheus, Grafana), contenedor `n8n` y políticas de `restart` y `healthcheck`.
- **Impacto:** Incumplimiento de los entregables de infraestructura y observabilidad de la Entrega 6.
- **Causa confirmada:** Pendiente de implementación en la carpeta `docker/` y en los `pom.xml` de `api-gateway` y `frontend-service`.
- **Solución recomendada:** Crear los `Dockerfile`s, `docker/prometheus.yml`, agregar `micrometer-registry-prometheus` a `api-gateway` y `frontend-service`, y completar `docker/docker-compose.yml`.
- **Archivos o componentes a modificar:** `Dockerfile` en cada módulo, `docker/docker-compose.yml`, `docker/prometheus.yml`, `api-gateway/pom.xml`, `frontend-service/pom.xml`, `scripts/infra-up.sh`.
- **Pruebas necesarias:** Validación con `docker compose -f docker/docker-compose.yml config` y chequeo de `/actuator/prometheus`.
- **Riesgos o dependencias:** Usar *profiles* de Docker Compose o mantener compatibilidad con `./scripts/services-up.sh` para no consumir memoria excesiva en máquinas de desarrollo.

---

### `HAL-015` — Brechas en Máquina de Estados de `Donacion` (`marcarVencida` y `registrarEntregaFallida`) y Cobertura Prematura de `NecesidadMaterial`
- **Tipo:** Regla de negocio incorrecta
- **Prioridad:** **P1 — Alta**
- **Requisito relacionado:** `REG-002`, `REG-008` (Entrega 1 pág. 8; Entrega 2 págs. 10-11).
- **Descripción del problema:**
  1. `Donacion.marcarVencida` (`Donacion.java:212-217`) permite marcar como `VENCIDA` una donación que estaba en `ASIGNACION_REALIZADA`, `LISTA_PARA_ENTREGAR` o `EN_TRASLADO` **sin revertir la cobertura (`revertirCobertura`) sobre `necesidadAsignada`**, dejando la necesidad de la entidad beneficiaria cerrada como satisfecha aunque nunca recibió los bienes.
  2. `Donacion.registrarEntregaFallida` (`Donacion.java:180-189`) permite marcar como `ENTREGA_FALLIDA` una donación en `ASIGNACION_REALIZADA` antes de que haya iniciado traslado.
  3. Cuando se asigna una donación con `necesidadId == null` (`DonacionService.java:156-205`), no se busca ni vincula automáticamente ninguna necesidad compatible de la entidad beneficiaria.
- **Evidencia y ubicación exacta:**
  - `donaciones-service/src/main/java/com/donatrack/donaciones/domain/Donacion.java:108-110, 180-189, 212-217`.
  - `donaciones-service/src/main/java/com/donatrack/donaciones/service/DonacionService.java:156-205`.
- **Comportamiento actual:** Inconsistencia en la cantidad cubierta de `NecesidadMaterial` cuando una donación vence o se asigna sin `necesidadId`.
- **Comportamiento esperado:** Revertir cobertura si una donación asignada no llega a entregarse (o permitir `marcarVencida` solo desde `EN_DEPOSITO` / `ENTREGA_FALLIDA` revirtiendo asignación) y vincular automáticamente la necesidad activa de igual subcategoría si `necesidadId` no fue especificado.
- **Impacto:** Corrupción del estado de satisfacción de necesidades materiales de las entidades beneficiarias.
- **Causa confirmada:** Falta de llamada a `revertirCobertura` en `Donacion.marcarVencida` y falta de búsqueda de necesidad compatible en `DonacionService.asignarDonacion`.
- **Solución recomendada:** En `Donacion.marcarVencida`, si `necesidadAsignada != null`, invocar `necesidadAsignada.revertirCobertura(this.cantidad)` y limpiar la asignación; en `DonacionService.asignarDonacion`, si `necesidadId == null`, asociar la necesidad activa de igual subcategoría de esa entidad.
- **Archivos o componentes a modificar:** `Donacion.java`, `DonacionService.java`.
- **Pruebas necesarias:** Tests unitarios en `DonacionStateTest` y `DonacionServiceTest`.
- **Riesgos o dependencias:** Ninguno.

---

### `HAL-016` — Parámetros de Matchmaking ($a_1, a_2, a_3, \alpha$) No Se Persisten en Base de Datos y Faltan Índices Relacionales
- **Tipo:** Problema de datos / Regla de negocio incompleta
- **Prioridad:** **P2 — Media**
- **Requisito relacionado:** `REG-003`, `DAT-001` (Entrega 2 pág. 12; Entrega 4 pág. 19).
- **Descripción del problema:**
  1. Los pesos $a_1, a_2, a_3$ en `CompatibilidadSemanticaStrategy` y $\alpha$ en `PrioridadSubatendidosStrategy` solo viven en memoria RAM y se pierden al reiniciar `donaciones-service`. Además, `setAlpha` no valida que $\alpha > 0$.
  2. En `MatchmakingService.evaluarDonacion`, `necesidadRepository.findPendientesPorSubcategoria` no hace `JOIN FETCH` de `entidadBeneficiaria`, generando consultas N+1 por cada necesidad evaluada.
- **Evidencia y ubicación exacta:**
  - `donaciones-service/src/main/java/com/donatrack/donaciones/matchmaking/CompatibilidadSemanticaStrategy.java:19-26, 47-52`.
  - `donaciones-service/src/main/java/com/donatrack/donaciones/matchmaking/PrioridadSubatendidosStrategy.java:24-25, 70-72`.
  - `donaciones-service/src/main/java/com/donatrack/donaciones/repository/NecesidadMaterialRepository.java:14-15`.
- **Comportamiento actual:** Al reiniciar `donaciones-service`, la configuración guardada por el administrador vuelve a `0.40 / 0.40 / 0.20 / 0.1`.
- **Comportamiento esperado:** Persistir los parámetros en PostgreSQL y cargar `entidadBeneficiaria` con `JOIN FETCH`.
- **Impacto:** Pérdida de configuración administrativa tras reinicios y sobrecarga de queries en matchmaking.
- **Causa confirmada:** Variables de instancia en beans `@Component` sin respaldo en tabla JPA.
- **Solución recomendada:** Persistir la configuración en una tabla JPA `configuracion_matchmaking` y agregar `JOIN FETCH n.entidadBeneficiaria` en `NecesidadMaterialRepository.findPendientesPorSubcategoria`.
- **Archivos o componentes a modificar:** `CompatibilidadSemanticaStrategy.java`, `PrioridadSubatendidosStrategy.java`, `NecesidadMaterialRepository.java`, nueva entidad `ConfiguracionMatchmaking.java`.
- **Pruebas necesarias:** Tests en `Fase3MatchmakingTest`.
- **Riesgos o dependencias:** Ninguno.

---

### `HAL-017` — Falta de Validaciones en Alta de Persona Jurídica y Error HTTP 500 ante DNI/CUIT Duplicados
- **Tipo:** Error funcional / Defecto de validación
- **Prioridad:** **P2 — Media**
- **Requisito relacionado:** `RF-022`, `RF-003` (Entrega 1 pág. 6).
- **Descripción del problema:**
  1. `DonanteService.registrarPersonaJuridica` no valida que la organización tenga `razonSocial`, `tipo`, `rubro` ni **al menos un medio de contacto**.
  2. Ni `registrarPersonaHumana` ni `registrarPersonaJuridica` verifican previamente si `nroDocumento` o `cuit` ya existen en la base de datos, ni `GlobalExceptionHandler` captura `DataIntegrityViolationException` ni `MethodArgumentNotValidException`, devolviendo **HTTP 500 Internal Server Error** en lugar de HTTP 400 / 409.
- **Evidencia y ubicación exacta:**
  - `donaciones-service/src/main/java/com/donatrack/donaciones/service/DonanteService.java:67-117`.
  - `donaciones-service/src/main/java/com/donatrack/donaciones/exception/GlobalExceptionHandler.java:14-85`.
- **Comportamiento actual:** Se pueden crear personas jurídicas sin medios de contacto, y un DNI o CUIT duplicado arroja HTTP 500.
- **Comportamiento esperado:** Validar campos obligatorios y medios de contacto, y retornar HTTP 400 / 409 ante datos inválidos o duplicados.
- **Impacto:** Datos inconsistentes en `donaciones_db` y respuestas 500 ante errores de entrada del usuario.
- **Causa confirmada:** Validaciones incompletas en `DonanteService` y manejadores faltantes en `GlobalExceptionHandler`.
- **Solución recomendada:** Validar `razonSocial`, `mediosContacto`, unicidad de `nroDocumento` y `cuit` en `DonanteService`, y agregar `@ExceptionHandler({MethodArgumentNotValidException.class, DataIntegrityViolationException.class})` en `GlobalExceptionHandler`.
- **Archivos o componentes a modificar:** `DonanteService.java`, `PersonaDonanteRepository.java`, `GlobalExceptionHandler.java`.
- **Pruebas necesarias:** Tests en `DonacionServiceTest` y `DonacionControllerTest`.
- **Riesgos o dependencias:** Ninguno.

---

### `HAL-018` — Job de Detección de Inactividad (> 20 días) Envía Spam Diario Repetido al Mismo Donante
- **Tipo:** Regla de negocio incompleta
- **Prioridad:** **P2 — Media**
- **Requisito relacionado:** `REG-005` (Entrega 2 pág. 14).
- **Descripción del problema:** `DonacionesScheduler.detectarDonantesInactivos()` busca diariamente a todos los donantes con `ultimaActividad <= LocalDateTime.now().minusDays(20)` y encola un `DonanteInactivoEvent`, pero no registra que el donante ya fue notificado ni actualiza ninguna fecha de control.
- **Evidencia y ubicación exacta:**
  - `donaciones-service/src/main/java/com/donatrack/donaciones/scheduler/DonacionesScheduler.java:177-215`.
  - `donaciones-service/src/main/java/com/donatrack/donaciones/repository/PersonaDonanteRepository.java:26-27`.
- **Comportamiento actual:** A partir del día 21 de inactividad, el donante recibe un correo de inactividad todos los días a las 04:00 AM indefinidamente.
- **Comportamiento esperado:** Notificar al cumplirse el umbral de > 20 días de inactividad y no volver a notificar hasta que transcurra un nuevo ciclo de 20 días o el donante registre nueva actividad.
- **Impacto:** Envío masivo reiterado de notificaciones de inactividad.
- **Causa confirmada:** Ausencia de marca temporal `ultimaAlertaInactividad` (o actualización de control) tras emitir el evento.
- **Solución recomendada:** Agregar `ultimaNotificacionInactividad` en `PersonaDonante` y filtrar en `findDonantesInactivosDesde` que `ultimaNotificacionInactividad IS NULL OR ultimaNotificacionInactividad <= :fechaLimite`.
- **Archivos o componentes a modificar:** `PersonaDonante.java`, `PersonaDonanteRepository.java`, `DonacionesScheduler.java`.
- **Pruebas necesarias:** Test en `DonacionesSchedulerTest` verificando que una segunda ejecución consecutiva no re-encole al mismo donante.
- **Riesgos o dependencias:** Ninguno.

---

### `HAL-019` — Eventos de Logística e Incentivos No Propagan el Canal de Contacto Preferido del Donante
- **Tipo:** Problema de integración
- **Prioridad:** **P2 — Media**
- **Requisito relacionado:** `INT-001`, `RF-008` (Entrega 1 pág. 6 y Entrega 2 pág. 14).
- **Descripción del problema:** Aunque `notificaciones-service` soporta despachar por `EMAIL`, `SMS` o `WHATSAPP` según el medio preferido del donante, `logistica-service` (`EntregaServiceImpl`, `RecorridoServiceImpl`) e `incentivos-service` (`IncentivosService`) instancian `EntregaConfirmadaEvent`, `EntregaFallidaEvent`, `RutaIniciadaEvent`, `MisionCumplidaEvent` y `CategoriaAscendidaEvent` usando constructores cortos que fijan `canalPreferido = CanalNotificacion.EMAIL`.
- **Evidencia y ubicación exacta:**
  - `logistica-service/src/main/java/com/donatrack/logistica/service/EntregaServiceImpl.java:90-99, 127-135`.
  - `logistica-service/src/main/java/com/donatrack/logistica/service/RecorridoServiceImpl.java:92-101`.
  - `incentivos-service/src/main/java/com/donatrack/incentivos/service/IncentivosService.java:334-342, 387-393`.
- **Comportamiento actual:** Si un donante eligió `WHATSAPP` o `SMS` como medio predeterminado, solo recibe por ese canal las notificaciones de `donaciones-service`, mientras que las de `logistica-service` e `incentivos-service` salen siempre por `EMAIL`.
- **Comportamiento esperado:** Propagar `canalPreferidoDonante` y `contactoPreferidoDonante` en todos los eventos dirigidos al donante.
- **Impacto:** Inconsistencia en el canal de notificación respecto a la preferencia del donante.
- **Causa confirmada:** Uso de constructores abreviados en `EntregaServiceImpl` e `IncentivosService`.
- **Solución recomendada:** Propagar el canal y contacto preferido desde `donaciones-service` hacia `incentivos-service` (en `PerfilDonanteIncentivo`) y `logistica-service` (en `Entrega`), y usarlos al construir los eventos.
- **Archivos o componentes a modificar:** `DonacionRegistradaEvent.java`, `PerfilDonanteIncentivo.java`, `IncentivosService.java`, `Entrega.java`, `PlanificacionRutasServiceImpl.java`, `EntregaServiceImpl.java`.
- **Pruebas necesarias:** Tests en `IncentivosProgresoTest` y `EntregaServiceTest`.
- **Riesgos o dependencias:** Ninguno.

---

### `HAL-020` — Importador CSV Carece de Índice en `medios_contacto`, Descarta `tipoDocumento` y Aborta Todo el Archivo ante un Único Registro Inválido en BD
- **Tipo:** Problema de datos / Error funcional
- **Prioridad:** **P2 — Media**
- **Requisito relacionado:** `RF-020` (Entrega 1 pág. 8).
- **Descripción del problema:**
  1. `PersonaHumana` no posee el atributo `tipoDocumento`; `CsvDonanteImporterService.java:152` lee la columna `TipoDoc` del CSV y la descarta.
  2. La tabla `medios_contacto` no tiene índice sobre `(tipo, valor)`, por lo que `findByEmail` ejecuta un *full scan* por cada una de las +10.000/20.000 filas.
  3. `importarDesdeStream` corre bajo un único `@Transactional` (`línea 89`). Si una sola fila tiene un DNI o CUIT que ya existe con otro email, el `flush()` lanza `ConstraintViolationException` y hace rollback de las 10.000 filas anteriores.
  4. `AdminWebController.importarCsv` (`línea 149`) carga todo el CSV en memoria con `file.getBytes()`.
- **Evidencia y ubicación exacta:**
  - `donaciones-service/src/main/java/com/donatrack/donaciones/domain/PersonaHumana.java:5-85`.
  - `donaciones-service/src/main/java/com/donatrack/donaciones/domain/MedioContacto.java:8`.
  - `donaciones-service/src/main/java/com/donatrack/donaciones/service/CsvDonanteImporterService.java:89-205`.
  - `frontend-service/src/main/java/com/donatrack/frontend/controller/AdminWebController.java:149`.
- **Comportamiento actual:** `tipoDocumento` no se guarda, la búsqueda por email no usa índice y una colisión de DNI/CUIT aborta todo el lote.
- **Comportamiento esperado:** Persistir `tipoDocumento`, indexar `medios_contacto(tipo, valor)` y aislar errores por fila sin abortar la importación completa.
- **Impacto:** Riesgo de fallo transaccional y lentitud en archivos CSV de +10.000 / 20.000 filas.
- **Causa confirmada:** Transacción monolítica y falta de `@Index` en `MedioContacto`.
- **Solución recomendada:** Añadir `tipoDocumento` en `PersonaHumana`, `@Index` en `MedioContacto`, y validar duplicados de documento/CUIT antes de persistir cada fila.
- **Archivos o componentes a modificar:** `PersonaHumana.java`, `MedioContacto.java`, `CsvDonanteImporterService.java`.
- **Pruebas necesarias:** Tests en `CsvDonanteImporterTest`.
- **Riesgos o dependencias:** Ninguno.

---

### `HAL-021` — Falta de Edición/Baja de Necesidades Materiales y Edición de Camiones en la Interfaz Web
- **Tipo:** Funcionalidad parcialmente implementada
- **Prioridad:** **P2 — Media**
- **Requisito relacionado:** `RF-010`, `RF-018` (Entrega 2 pág. 14; Entrega 3 pág. 17).
- **Descripción del problema:** Aunque el backend y `ApiClientService` tienen soporte para actualizar y eliminar necesidades materiales y modificar camiones, las plantillas `templates/beneficiaria/necesidades.html` y `templates/admin/camiones.html` no incluyen los botones ni los endpoints en `BeneficiariaWebController` y `AdminWebController`.
- **Evidencia y ubicación exacta:**
  - `frontend-service/src/main/resources/templates/beneficiaria/necesidades.html:79-111`.
  - `frontend-service/src/main/resources/templates/admin/camiones.html:1-95`.
  - `frontend-service/src/main/java/com/donatrack/frontend/service/ApiClientService.java:656-682`.
- **Comportamiento actual:** Desde la web solo se pueden crear y listar necesidades, y crear, listar y dar de baja camiones.
- **Comportamiento esperado:** Permitir editar y cerrar necesidades desde `/beneficiaria/necesidades` y editar camiones desde `/admin/camiones`.
- **Impacto:** CRUD incompleto en la capa de presentación.
- **Causa confirmada:** Métodos de `ApiClientService` no conectados en los controladores MVC.
- **Solución recomendada:** Agregar los endpoints `POST` en `BeneficiariaWebController` y `AdminWebController` y los botones/modales en las vistas correspondientes.
- **Archivos o componentes a modificar:** `BeneficiariaWebController.java`, `templates/beneficiaria/necesidades.html`, `AdminWebController.java`, `templates/admin/camiones.html`.
- **Pruebas necesarias:** Tests en `FrontendControllersTest`.
- **Riesgos o dependencias:** Ninguno.

---

### `HAL-022` — Controlador GraphQL Filtra en Memoria con `findAll()`, Tests de Georef Dependen de Internet Real y Faltan Tests `@DataJpaTest`
- **Tipo:** Falta de pruebas / Deuda técnica
- **Prioridad:** **P2 — Media**
- **Requisito relacionado:** `RNF-009` (Entrega 6 Req 5b: GraphQL para filtros dinámicos y eficiencia en consultas).
- **Descripción del problema:**
  1. `DonacionGraphqlController.donaciones` (`DonacionGraphqlController.java:31-40`) hace `donacionRepository.findAll()` cuando `estado == null` y filtra `subcategoria` en memoria con `.stream().filter(...)` en vez de usar `DonacionSpecification.conFiltros(...)`.
  2. `NormalizacionDireccionTest.java:35-76` realiza llamadas HTTP reales a `https://apis.datos.gob.ar/georef/api/direcciones`.
  3. No existen tests `@DataJpaTest` que validen las queries JPQL de los repositorios contra una base relacional en `donaciones-service`, ni tests para `DonanteController`, `EntidadBeneficiariaController` y `DonacionGraphqlController`.
- **Evidencia y ubicación exacta:**
  - `donaciones-service/src/main/java/com/donatrack/donaciones/controller/DonacionGraphqlController.java:31-40`.
  - `donaciones-service/src/test/java/com/donatrack/donaciones/service/NormalizacionDireccionTest.java:35-76`.
- **Comportamiento actual:** GraphQL carga toda la tabla `donaciones` en RAM cuando se filtra solo por `subcategoria`.
- **Comportamiento esperado:** Delegar el filtrado de GraphQL a `donacionRepository.findAll(DonacionSpecification.conFiltros(...))` y contar con tests determinísticos offline.
- **Impacto:** Ineficiencia en consultas GraphQL y fragilidad de tests ante caídas de red externa.
- **Causa confirmada:** Implementación simplificada en `DonacionGraphqlController`.
- **Solución recomendada:** Usar `DonacionSpecification.conFiltros` en `DonacionGraphqlController` y agregar tests de integración de repositorios y controladores.
- **Archivos o componentes a modificar:** `DonacionGraphqlController.java`, `NormalizacionDireccionTest.java`, nuevos tests en `donaciones-service/src/test`.
- **Pruebas necesarias:** `./mvnw test`.
- **Riesgos o dependencias:** Ninguno.

---

### `HAL-023` — Desfase Documental y Ausencia de Diagrama de Entidad-Relación (DER) Físico por Servicio
- **Tipo:** Deuda documental / Entregable escrito pendiente
- **Prioridad:** **P3 — Baja**
- **Requisito relacionado:** Entregable 3 de la Entrega 4 (pág. 19: *"Modelo de datos: Diagrama de entidad-relación físico por Servicio"*).
- **Descripción del problema:** En `Documentacion/Diagramas/` existen los diagramas de Arquitectura, Dominio, Máquina de Estados y Flujos de Matchmaking y Logística, pero no existe el Diagrama de Entidad-Relación (DER) físico por servicio. Además, `Documentacion/Servicios/ApiGateway_Keycloak.md` describe componentes que aún no estaban en el código de `api-gateway`.
- **Evidencia y ubicación exacta:**
  - `Documentacion/Diagramas/`: Contiene `Arquitectura.puml`, `Dominio.puml`, `Flujo_Logistica_Rutas.puml`, `Flujo_Matchmaking_Asignacion.puml`, `Maquina_Estados_Donacion.puml`.
- **Comportamiento actual:** Falta el archivo PlantUML del DER físico por servicio.
- **Comportamiento esperado:** Contar con el diagrama físico de tablas/colecciones de los 4 servicios y documentación alineada con el código.
- **Impacto:** Entregable documental faltante para la Entrega 4.
- **Causa confirmada:** No generado aún en `Documentacion/Diagramas/`.
- **Solución recomendada:** Generar `Documentacion/Diagramas/DER_Fisico_Por_Servicio.puml` tras aplicar las correcciones de modelo.
- **Archivos o componentes a modificar:** `Documentacion/Diagramas/DER_Fisico_Por_Servicio.puml`, `Documentacion/Servicios/ApiGateway_Keycloak.md`.
- **Pruebas necesarias:** Verificación de sintaxis PlantUML.
- **Riesgos o dependencias:** Ninguno.

---

### `HAL-024` — Puntos Bonus Opcionales (SSO con Social-Login en Entrega 5 y Service Registry en Entrega 6) No Implementados
- **Tipo:** Mejora opcional / Bonus del enunciado
- **Prioridad:** **P3 — Baja**
- **Requisito relacionado:** Entrega 5 Entregable 2 (pág. 21) y Entrega 6 Req 6 (pág. 22).
- **Descripción del problema:** El login actual utiliza un formulario propio en `frontend-service` que consulta a Keycloak por ROPC (`grant_type=password`) sin redirección OIDC SSO ni botones de Social-Login, y el `api-gateway` enruta mediante variables de entorno (`DONACIONES_SERVICE_URL`, etc.) sin un servidor Eureka/Consul. (Nota: el Bonus 7 de la Entrega 6, *Transactional Outbox*, **sí** está implementado en `donaciones-service`).
- **Evidencia y ubicación exacta:**
  - `frontend-service/src/main/java/com/donatrack/frontend/security/AuthService.java:81-104`.
  - `api-gateway/src/main/resources/application.yml:16-75`.
- **Comportamiento actual:** Sin SSO OIDC ni Service Registry dinámico.
- **Comportamiento esperado:** Opcional según alcance que defina el equipo (ver `DEC-05` en `02_funcionalidades_pendientes.md`).
- **Impacto:** Solo afecta puntos Bonus opcionales.
- **Causa confirmada:** Priorización de requerimientos base sobre ítems Bonus.
- **Solución recomendada:** Si se desea presentar el Bonus de Entrega 5, agregar flujo OAuth2 Authorization Code hacia Keycloak en `frontend-service`.
- **Archivos o componentes a modificar:** `frontend-service`, `docker/keycloak/realm-donatrack.json`.
- **Pruebas necesarias:** Test de redirección OIDC.
- **Riesgos o dependencias:** Confirmar decisión `DEC-05`.

---

## 9. Implementaciones Parciales o Sin Verificar

1. **Adaptadores de SMS (`SmsNotificationSender.java:14-48`) y WhatsApp (`WhatsAppNotificationSender.java:14-46`):**
   - **Estado:** *Implementado parcialmente (simulado en logs).* Mientras que `EmailNotificationSender` realiza envíos SMTP reales mediante `JavaMailSender` contra Mailpit (`localhost:1025`), los adaptadores de SMS y WhatsApp leen las propiedades de Twilio y Meta Cloud API pero solo escriben `log.info("[SMS-SIMULADO]...")` y `log.info("[WHATSAPP-SIMULADO]...")` devolviendo `true`. Dado que no se cuenta con cuentas pagas de Twilio/Meta en el entorno académico, la estructura Strategy/Adapter cumple el diseño arquitectónico, pero la llamada HTTP real a la API externa no se ejecuta.
2. **Integración con el Optimizador Externo de Rutas VRP (`HttpVrpOptimizerClient.java:41-58`):**
   - **Estado:** *Implementado con fallback simulado activo por defecto.* `ResilientRutaOptimizerClient` soporta invocar por HTTP a un servidor externo en `http://localhost:5000/api/v1/vrp/optimize`, pero tiene `donatrack.vrp.http-enabled: false` en `application.yml:31`, operando por defecto contra `MockVrpOptimizerClient` y recibiendo el callback en `/api/v1/logistica/rutas/callback`.
3. **Integración con Webhook de `n8n` (`IncentivosService.java:356-369`):**
   - **Estado:** *Cliente HTTP implementado; servidor n8n no levantado en Docker.* `IncentivosService` realiza el `POST` real con `RestTemplate` hacia `http://localhost:5678/webhook/insignias`, pero como `n8n` no está incluido en `docker/docker-compose.yml`, la llamada cae en el `catch` de contingencia.

---

## 10. Problemas de Arquitectura, Calidad, Seguridad y Mantenibilidad

### 10.1. Exposición Directa de Entidades JPA en Controladores REST y DTOs Huérfanos
- En `donaciones-service` (`DonacionController`, `DonanteController`, `EntidadBeneficiariaController`) e `incentivos-service` (`IncentivosController`), los endpoints REST reciben y devuelven directamente las entidades JPA (`Donacion`, `PersonaDonante`, `EntidadBeneficiaria`, `NecesidadMaterial`, `PerfilDonanteIncentivo`).
- Paradójicamente, existen 5 clases DTO en `donaciones-service/src/main/java/com/donatrack/donaciones/dto/` (`AsignacionDonacionRequestDto`, `BajaLogicaRequestDto`, `CambioEstadoRequestDto`, `DesasignacionRequestDto`, `ModificacionDonacionRequestDto`) con anotaciones `@NotNull` / `@NotBlank` que **nunca son referenciadas en ningún controlador** (en su lugar, `DonacionController` recibe `@RequestBody Map<String, Object>`).

### 10.2. Consultas N+1 y Relaciones `EAGER` en Cascada en `donaciones-service`
- `Donacion` define como `FetchType.EAGER` sus relaciones `donante`, `entidadAsignada`, `necesidadAsignada` e `historialEstados`, mientras que `bienes` es `LAZY`. Al serializar `List<Donacion>` a JSON o iterar en `CompatibilidadSemanticaStrategy`, Hibernate dispara 1 query adicional por cada donación para cargar `bienes`.
- En `PrioridadSubatendidosStrategy.calcularScore` (`líneas 48-55`), se ejecuta una query `COUNT` (`countEntregadasRecientesByEntidadId`) dentro del bucle de evaluación por cada necesidad candidata.

### 10.3. Secretos en Texto Plano y Configuración de Desarrollo
- `docker/docker-compose.yml` y los `application.yml` contienen credenciales de desarrollo (`donatrack_password`, `admin/admin`), aceptables para el entorno local de cátedra pero que deben parametrizarse mediante variables de entorno para el despliegue cloud de la Entrega 6.

---

## 11. Mejoras Recomendadas con Justificación Técnica

*(El detalle exhaustivo de cada mejora opcional con su análisis costo-beneficio se encuentra en `auditoria/04_mejoras_tecnicas.md`).*

1. **`MEJ-001` — Uso de los DTOs de Request ya creados con `@Valid` y `ProblemDetail` (RFC 7807) en `donaciones-service`:** Reemplazar los `@RequestBody Map<String, Object>` en `DonacionController` por los DTOs tipados que ya existen en el paquete `dto`, evitando errores `NullPointerException` / `ClassCastException` (HTTP 500) ante payloads malformados.
2. **`MEJ-002` — Optimización de Consultas N+1 con `@EntityGraph` / `JOIN FETCH` en `DonacionRepository` y `NecesidadMaterialRepository`:** Reducir de $O(N)$ a $O(1)$ las consultas SQL durante el listado de donaciones y la ejecución del motor de matchmaking.
3. **`MEJ-003` — Precálculo por Lote de Donaciones Entregadas en el Trimestre para `PrioridadSubatendidosStrategy`:** Evitar ejecutar un `SELECT COUNT(*)` repetido por cada necesidad de una misma entidad durante una corrida de matchmaking.
4. **`MEJ-004` — Streaming Multipart en `AdminWebController.importarCsv`:** Reemplazar `new ByteArrayResource(file.getBytes())` por `file.getResource()` para no duplicar en la RAM del `frontend-service` archivos CSV de decenas de megabytes.

---

## 12. Riesgos, Limitaciones y Decisiones Pendientes

### Limitaciones del Entorno Durante la Auditoría
- **Proveedores externos de pago (SMS Twilio / WhatsApp Meta / Optimizador VRP externo):** No se dispone de credenciales comerciales reales de Twilio ni Meta ni de un contenedor del proveedor externo VRP de la cátedra en `:5000`; por lo tanto, el comportamiento de sus adaptadores fue verificado mediante inspección de código y pruebas con los componentes simulados/locales.
- **Georef API (`NormalizacionDireccionTest`):** La prueba unitaria en `donaciones-service` realiza llamadas reales a `apis.datos.gob.ar`, las cuales tuvieron éxito durante la verificación porque el entorno contó con salida a internet, pero fallarían en una red aislada.

### Diferencias Detectadas entre Documentos del Repositorio
1. **Fórmula de Compatibilidad Semántica (`tp_extraido.txt:507` vs `analisisv2.md:294`):** En el PDF oficial el número `2` que aparece encima de la fórmula corresponde a la llamada de la nota al pie N°2 (`Distancia de Levenshtein2`), mientras que `analisisv2.md` lo interpretó como un exponente cuadrático ($\chi_1^2$). El código actual en `CompatibilidadSemanticaStrategy.java:95` implementa $a_1 \chi_1 + a_2 \chi_2 + a_3 \chi_3$ (lineal), lo cual garantiza que si $a_1+a_2+a_3=1$ y $\chi_i = 1$, el puntaje máximo sea exactamente $1.0$.
2. **Incentivos para Persona Humana vs. Persona Jurídica (`tp_extraido.txt:153` vs `tp_extraido.txt:600`):** El Requerimiento UI/UX 7 menciona *"La persona humana donante debe poder acceder a una sección de Incentivos"*, mientras que la Entrega 2 (pág. 13) indica *"La persona donante, ya sea humana o representante de una persona jurídica... podrá observar en su perfil sus totales históricos de donaciones..."*. El código actual asocia el perfil de incentivos a `PersonaDonante.id` (válido para ambas), lo cual satisface ambos casos sin fricción.

---

## 13. Resumen de Pruebas y Verificaciones Ejecutadas

Se ejecutó de forma limpia y reproducible la suite completa de pruebas del proyecto mediante el comando:

```bash
./mvnw test
```

### Resultados Obtenidos por Módulo (Ejecución Real Verificada)

| Módulo Maven | Clases de Test Ejecutadas | Tests Ejecutados | Fallos | Errores | Omitidos | Tiempo | Estado |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| `donatrack-parent` | 0 | 0 | 0 | 0 | 0 | 0.003 s | `SUCCESS` |
| `common-dtos` | 0 | 0 | 0 | 0 | 0 | 1.206 s | `SUCCESS` |
| `donaciones-service` | 13 | **70** | 0 | 0 | 0 | 13.305 s | `SUCCESS` |
| `logistica-service` | 6 | **28** | 0 | 0 | 0 | 7.619 s | `SUCCESS` |
| `incentivos-service` | 4 | **20** | 0 | 0 | 0 | 8.146 s | `SUCCESS` |
| `notificaciones-service` | 5 | **27** | 0 | 0 | 0 | 7.301 s | `SUCCESS` |
| `api-gateway` | 1 | **1** | 0 | 0 | 0 | 8.421 s | `SUCCESS` |
| `frontend-service` | 1 | **50** | 0 | 0 | 0 | 10.003 s | `SUCCESS` |
| **TOTAL REACTOR** | **30** | **196** | **0** | **0** | **0** | **56.835 s** | **`BUILD SUCCESS`** |

---

## 14. Conclusiones sobre el Grado de Cumplimiento del Proyecto

| Dimensión Evaluada | Grado de Avance Estimado | Diagnóstico Sintético |
| :--- | :---: | :--- |
| **Arquitectura Multimódulo y Persistencia Políglota (E1, E4)** | **92%** | Cumple estrictamente la separación en servicios, PostgreSQL + JPA para Donaciones/Incentivos/Notificaciones y MongoDB + ODM para Logística. Solo falta indexación en tablas puntuales y persistir pesos de matchmaking en BD. |
| **Servicio de Donaciones y Matchmaking (E1, E2)** | **82%** | Dominio rico, segmentación, máquina de estados, estrategias de matchmaking, GraphQL y Transactional Outbox funcionando. Pendiente: endpoint Update de donantes, revertir cobertura al vencer donación asignada y control anti-spam en inactividad. |
| **Servicio de Logística (E3, E4)** | **95%** | Es el microservicio más sólido del backend: lotes $\le 100$, callback idempotente, custodia de no asignadas en MongoDB, telemetría GPS con validación temporal y publicación AMQP completa. |
| **Servicio de Incentivos (E2)** | **75%** | Modelo de misiones, insignias, categorías, webhook n8n y ranking mensual implementados, pero requiere corregir la doble contabilización (`donacion.registrada` + `donacion.asignada`), el `break` secuencial en `COMPLETITUD`, la condición de `HABIL_DONADOR` y la serie temporal de analítica. |
| **Servicio de Notificaciones (E1, E2, E3)** | **90%** | Consumidores RabbitMQ con DLQ, idempotencia en PostgreSQL, plantillas HTML y envío real por SMTP (Mailpit) verificados. Solo requiere recibir el canal preferido en los eventos de Logística e Incentivos. |
| **Frontend SSR MVC y UI/UX (E4, E5)** | **72%** | Plantillas modernas, accesibles y desacopladas, pero requiere corregir la ruta 404 en alta de Persona Jurídica, reemplazar el mapa hardcodeado de `/donante/tracking`, mostrar la analítica en `/donante/incentivos`, ocultar rankings separados cuando hay intersección en `/admin/matchmaking`, agregar filtro por categoría y cerrar las vulnerabilidades IDOR y de `AuthService`. |
| **API Gateway, Seguridad, Observabilidad y Despliegue (E5, E6)** | **45%** | El `api-gateway` solo enruta tráfico sin seguridad JWT ni Rate Limiting, y faltan los `Dockerfile`s y contenedores de Prometheus/Grafana/n8n en `docker-compose.yml`. |

**Dictamen general:** El proyecto cuenta con una base arquitectónica y de dominio sumamente sólida y bien estructurada, sin necesidad de reescrituras estructurales. Siguiendo el plan de trabajo por etapas detallado en `auditoria/03_plan_de_correccion.md`, la totalidad de los hallazgos `P0`, `P1` y `P2` puede corregirse de manera incremental, quirúrgica y verificable.
