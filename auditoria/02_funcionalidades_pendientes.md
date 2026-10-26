# 02 — Catálogo de Funcionalidades, Requisitos y Trabajos Pendientes

> **Alcance del documento:** Este informe detalla exclusivamente las funcionalidades, requisitos del enunciado, reglas de negocio, integraciones, controles de seguridad, pruebas y definiciones que **aún no están implementados, están implementados parcialmente, presentan defectos funcionales confirmados o no pudieron verificarse**.  
> Se distingue explícitamente entre **[OBLIGATORIO ENUNCIADO]** (exigido en las Entregas 1 a 6 del TP Anual 2026) y **[BONUS ENUNCIADO]** (puntos opcionales explícitos de las Entregas 5 y 6). Las mejoras técnicas opcionales de ingeniería se documentan por separado en `auditoria/04_mejoras_tecnicas.md`.

---

## Índice de Secciones
1. [Requisitos Funcionales Faltantes](#1-requisitos-funcionales-faltantes)
2. [Funcionalidades Parcialmente Implementadas](#2-funcionalidades-parcialmente-implementadas)
3. [Integraciones Pendientes](#3-integraciones-pendientes)
4. [Reglas de Negocio Pendientes o Incompletas](#4-reglas-de-negocio-pendientes-o-incompletas)
5. [Pruebas Faltantes](#5-pruebas-faltantes)
6. [Tareas de Seguridad, Infraestructura y Configuración Pendientes](#6-tareas-de-seguridad-infraestructura-y-configuración-pendientes)
7. [Documentación Faltante o Desactualizada](#7-documentación-faltante-o-desactualizada)
8. [Decisiones que Requieren Confirmación Antes de Implementar](#8-decisiones-que-requieren-confirmación-antes-de-implementar)

---

## 1. Requisitos Funcionales Faltantes

### PEND-001 — Modificación (Update) de Personas Donantes Humanas y Jurídicas
- **Clasificación:** [OBLIGATORIO ENUNCIADO]
- **Hallazgo vinculado:** `HAL-006`
- **Requisito del que surge:** `RF-012` (Entrega 1 pág. 2 y Entrega 2 pág. 14: *"Operaciones CRUD sobre las personas donantes (jurídicas y humanas)"*).
- **Descripción del resultado esperado:** Permitir actualizar los datos identificatorios, dirección, medios de contacto, medio de contacto predeterminado y representantes de una persona donante humana o jurídica tanto vía API REST como desde la interfaz administrativa/de perfil.
- **Estado actual y evidencia:** **No implementado.** En `donaciones-service/src/main/java/com/donatrack/donaciones/controller/DonanteController.java:18-70` solo existen endpoints `GET`, `POST` (alta) y `DELETE` (baja lógica) + `POST /{id}/reactivar`. No existe ningún método `PUT` ni `PATCH` en `DonanteController.java` ni lógica de actualización en `DonanteService.java`.
- **Componentes involucrados:** `donaciones-service` (`DonanteController`, `DonanteService`), `frontend-service` (`ApiClientService`, `AdminWebController`, `DonanteWebController`).
- **Dependencias y bloqueos:** Ninguno.
- **Prioridad:** **P1 — Alta**
- **Pasos concretos de implementación:**
  1. Crear DTOs de actualización (`ActualizarPersonaHumanaDto`, `ActualizarPersonaJuridicaDto`) con validaciones Jakarta Bean Validation.
  2. Implementar `DonanteService.actualizarPersonaHumana(Long id, ...)` y `DonanteService.actualizarPersonaJuridica(Long id, ...)` verificando unicidad de email, DNI y CUIT contra otros registros.
  3. Exponer `PUT /api/v1/donantes/humana/{id}` y `PUT /api/v1/donantes/juridica/{id}` en `DonanteController.java`.
  4. Agregar método cliente en `ApiClientService.java` y formulario de edición en la vista de perfil/administración.
- **Criterios de aceptación verificables:**
  - Un `PUT /api/v1/donantes/humana/{id}` actualiza nombre, apellido, edad, dirección, coordenadas y medios de contacto (incluyendo cambio de medio predeterminado) y retorna `200 OK`.
  - Si se intenta asignar un email o documento ya perteneciente a otro donante, retorna `409 Conflict` o `400 Bad Request`.
- **Pruebas necesarias:** Tests unitarios en `DonanteServiceTest` y tests `@WebMvcTest` en `DonanteControllerTest`.
- **Definición de terminado (DoD):** Endpoints REST documentados, integrados y cubiertos por pruebas automatizadas.

---

### PEND-002 — Alta de Personas Donantes desde el Panel de la Persona Administradora
- **Clasificación:** [OBLIGATORIO ENUNCIADO]
- **Hallazgo vinculado:** `HAL-008`
- **Requisito del que surge:** `RF-010`, `RF-023` (Requerimiento UI/UX 15, pág. 5: *"La persona administradora debe registrar personas donantes y donaciones que haya recibido en el depósito, asociándolas a la persona donante correspondiente"*; Entrega 1 pág. 7).
- **Descripción del resultado esperado:** El panel de administración (`/admin`) debe contar con una vista o modal que permita a la persona administradora dar de alta en el momento a una persona donante (humana o jurídica con sus representantes y medios de contacto) cuando se acerca presencialmente al depósito sin usuario previo, disparando el correo de bienvenida.
- **Estado actual y evidencia:** **No implementado en la UI administrativa.** En `frontend-service/src/main/resources/templates/admin/nueva_donacion.html:34-43` solo hay un `<select name="donanteId">` con donantes existentes y un texto estático *"Si el donante no existe, impórtelo vía CSV o regístrelo en el sistema"*. `AdminWebController.java` no tiene ningún endpoint para crear donantes.
- **Componentes involucrados:** `frontend-service` (`AdminWebController`, `templates/admin/nueva_donacion.html` o `templates/admin/donantes.html`, `ApiClientService`).
- **Dependencias y bloqueos:** Depende de corregir el enrutamiento de alta de Persona Jurídica en el Gateway (`PEND-013` / `HAL-002`).
- **Prioridad:** **P1 — Alta**
- **Pasos concretos de implementación:**
  1. Incorporar en `/admin` (y/o como modal/sección integrada en `/admin/donaciones/nueva`) el formulario de alta de Persona Humana y Persona Jurídica con validación de dirección Georef.
  2. Implementar `POST /admin/donantes/humana` y `POST /admin/donantes/juridica` en `AdminWebController.java` consumiendo `apiClient.crearDonanteHumana` y `apiClient.crearDonanteJuridica`.
  3. Al crear el donante exitosamente, preseleccionarlo en el formulario de carga de donación en depósito.
- **Criterios de aceptación verificables:**
  - El administrador puede registrar un donante humano o jurídico sin salir del flujo de recepción en depósito.
  - Se encola el evento `DonanteRegistradoEvent` enviando el correo de bienvenida con credenciales iniciales.
- **Pruebas necesarias:** Test de controlador MVC en `FrontendControllersTest` para `POST /admin/donantes/humana` y `POST /admin/donantes/juridica`.
- **Definición de terminado (DoD):** Flujo completo operativo desde la sesión `ROLE_ADMIN` y verificado con tests.

---

### PEND-003 — Visualización de Analítica, Gráficos de Evolución por Período y Comparaciones Mensuales en el Perfil del Donante
- **Clasificación:** [OBLIGATORIO ENUNCIADO]
- **Hallazgo vinculado:** `HAL-012`
- **Requisito del que surge:** `RF-031` (Entrega 2 págs. 12-13: *"La persona donante, ya sea humana o representante de una persona jurídica, además de contar con sus datos personales, podrá observar en su perfil sus totales históricos de donaciones, gráficos de evolución por período, comparaciones mensuales y el número total de organizaciones que ha ayudado con sus aportes. También podrá visualizar su posición en el ranking de donantes activos"*).
- **Descripción del resultado esperado:** El backend de `incentivos-service` debe proveer series históricas por período (evolución mensual de donaciones y bienes donados) y comparaciones mensuales, y el `frontend-service` (`/donante/incentivos`) debe consumir dicho endpoint y renderizar gráficos interactivos (ej. Chart.js), comparativas mes actual vs. mes anterior, total de organizaciones ayudadas y posición en el ranking mensual.
- **Estado actual y evidencia:** **Parcial en backend / No implementado en frontend.**
  - En `incentivos-service/src/main/java/com/donatrack/incentivos/service/IncentivosService.java:276-323`, `obtenerAnaliticaDonante` devuelve un `Map` plano con totales acumulados (`totalDonaciones`, `totalBienesDonados`, `organizacionesAyudadas`, `misionesMesActual`, `misionesMesAnterior`, `posicionRanking`), pero **no devuelve la serie temporal de evolución de donaciones por período (mes a mes)**.
  - En `frontend-service/src/main/java/com/donatrack/frontend/service/ApiClientService.java:67`, el método para consumir `/api/v1/incentivos/donantes/{id}/analitica` nunca fue programado.
  - En `frontend-service/src/main/resources/templates/donante/incentivos.html:1-100`, no existen gráficos de evolución, ni comparaciones mensuales, ni el indicador de organizaciones ayudadas, ni la posición en el ranking.
- **Componentes involucrados:** `incentivos-service` (`IncentivosService`, `IncentivosController`), `frontend-service` (`ApiClientService`, `DonanteWebController`, `templates/donante/incentivos.html`).
- **Dependencias y bloqueos:** Requiere corregir la doble contabilización de métricas en `incentivos-service` (`PEND-018` / `HAL-005`).
- **Prioridad:** **P1 — Alta**
- **Pasos concretos de implementación:**
  1. En `incentivos-service`, registrar el historial mensual de actividad del donante (o agregar entidad/colección de actividad por período `anio-mes`) para incluir en `GET /api/v1/incentivos/donantes/{id}/analitica` la lista `evolucionPorPeriodo` (mes, cantidadDonaciones, cantidadBienes) y la comparativa de donaciones del mes actual vs. mes anterior.
  2. En `ApiClientService.java`, implementar `obtenerAnaliticaDonante(Long donanteId)`.
  3. En `DonanteWebController.incentivos`, inyectar `analitica` al `Model`.
  4. En `templates/donante/incentivos.html`, agregar las tarjetas de KPIs (`organizacionesAyudadas`, `posicionRanking`, comparativa mensual) y el gráfico de evolución por período.
- **Criterios de aceptación verificables:**
  - `GET /api/v1/incentivos/donantes/{id}/analitica` retorna totales históricos, serie mensual por período, comparativa mensual, organizaciones ayudadas y posición en el ranking.
  - La pantalla `/donante/incentivos` muestra todos estos indicadores y renderiza el gráfico de evolución.
- **Pruebas necesarias:** Tests en `IncentivosServiceTest` y `FrontendControllersTest`.
- **Definición de terminado (DoD):** Endpoint y vista funcionando de extremo a extremo con datos reales del donante en sesión.

---

### PEND-004 — Contenedorización Completa (`Dockerfile`s), Stack de Observabilidad y Despliegue Cloud (Entrega 6)
- **Clasificación:** [OBLIGATORIO ENUNCIADO]
- **Hallazgo vinculado:** `HAL-014`
- **Requisito del que surge:** `RNF-005`, `RNF-006`, `RNF-007` (Entrega 6 pág. 22, Requerimientos detallados 1, 2 y 3: Despliegue en la nube accesible públicamente, herramientas de observabilidad de métricas/trazas/logs, y monitoreo proactivo con políticas de autorestart).
- **Descripción del resultado esperado:** Contar con `Dockerfile`s para los servicios Java, definición completa en `docker-compose.yml` con políticas `restart: unless-stopped` y `healthcheck`, stack de observabilidad (Prometheus + Grafana + recolección de métricas/trazas/logs) y configuración lista para despliegue cloud.
- **Estado actual y evidencia:** **Incompleto.**
  - No existe ningún `Dockerfile` en el repositorio.
  - `docker/docker-compose.yml:1-82` solo levanta PostgreSQL, MongoDB, RabbitMQ, Mailpit y Keycloak; no incluye ninguno de los 6 servicios Java ni contenedores de Prometheus/Grafana/n8n.
  - `api-gateway/pom.xml:20-36` y `frontend-service/pom.xml:20-46` no incluyen `micrometer-registry-prometheus`, ni `api-gateway/src/main/resources/application.yml:91` expone el endpoint `prometheus`.
- **Componentes involucrados:** Raíz de cada microservicio (`Dockerfile`), `docker/docker-compose.yml`, `docker/prometheus.yml`, `api-gateway/pom.xml`, `frontend-service/pom.xml`.
- **Dependencias y bloqueos:** Ninguno.
- **Prioridad:** **P1 — Alta**
- **Pasos concretos de implementación:**
  1. Agregar `micrometer-registry-prometheus` en `api-gateway/pom.xml` y `frontend-service/pom.xml` y exponer `prometheus` en `management.endpoints.web.exposure.include`.
  2. Crear `Dockerfile` multi-stage (o basado en JRE 21 Alpine) con `HEALTHCHECK` para los 6 servicios.
  3. Crear `docker/prometheus.yml` apuntando a `/actuator/prometheus` de los 6 servicios.
  4. Incorporar Prometheus, Grafana, n8n y los perfiles de servicios en `docker/docker-compose.yml` con `restart: unless-stopped`.
- **Criterios de aceptación verificables:**
  - Todos los servicios exponen `/actuator/health` y `/actuator/prometheus` retornando `200 OK`.
  - `docker compose config` valida sin errores y Prometheus recolecta métricas de todos los servicios.
- **Pruebas necesarias:** Verificación de endpoints Actuator y validación de `docker-compose.yml`.
- **Definición de terminado (DoD):** Stack contenedorizado, monitoreado y con políticas de auto-recuperación activas.

---

### PEND-005 — Implementación de SSO con Soporte de Social-Login (Bonus Entrega 5) y Service Registry (Bonus Entrega 6)
- **Clasificación:** [BONUS ENUNCIADO]
- **Hallazgo vinculado:** `HAL-024`
- **Requisito del que surge:** `RF-040` (Entrega 5 pág. 21 Entregable 2: *"Bonus - Implementación de un SSO con soporte de Social-Login"*) y `RNF-010` (Entrega 6 pág. 22 Req 6: *"Bonus - ...registrarse a un Service Registry y sean consumidos a través de un API Gateway"*).
- **Descripción del resultado esperado:**
  1. Flujo OAuth2 Authorization Code / OIDC en `frontend-service` redirigiendo al login centralizado de Keycloak con proveedores de identidad social (Google/GitHub) habilitados en el realm.
  2. Servidor de descubrimiento (Spring Cloud Netflix Eureka o Consul) donde se registren dinámicamente los microservicios y el `api-gateway` resuelva rutas vía `lb://nombre-servicio`.
- **Estado actual y evidencia:** **No implementado.**
  - `frontend-service` usa un formulario propio (`templates/login.html`) que invoca `grant_type=password` en `AuthService.java:81-104`.
  - `api-gateway/src/main/resources/application.yml:16-75` usa URLs estáticas `${..._SERVICE_URL:http://localhost:808X}`.
- **Componentes involucrados:** `frontend-service`, `api-gateway`, `docker/keycloak/realm-donatrack.json`, todos los microservicios (si se activa Eureka).
- **Dependencias y bloqueos:** Decisión del equipo sobre si se incluirá el alcance de los puntos Bonus en la entrega final (ver Sección 8).
- **Prioridad:** **P3 — Baja (Bonus Opcional)**
- **Pasos concretos de implementación:**
  1. Para SSO: configurar cliente OIDC confidencial en Keycloak con `standardFlowEnabled: true`, agregar botón *"Ingresar con SSO / Keycloak"* en `templates/login.html` que inicie el flujo OAuth2 Authorization Code y reciba el callback en `/login/oauth2/code/keycloak`.
  2. Para Service Registry: incorporar `spring-cloud-starter-netflix-eureka-client` (o mantener resolución por DNS de contenedores si no se presenta el bonus).
- **Criterios de aceptación verificables:**
  - El usuario puede iniciar sesión mediante redirección OIDC a Keycloak y retornar autenticado a `frontend-service`.
- **Pruebas necesarias:** Test de flujo de callback OIDC.
- **Definición de terminado (DoD):** Flujo SSO operativo con Keycloak.

---

## 2. Funcionalidades Parcialmente Implementadas

### PEND-006 — Landing Page: Filtrado Real de Donaciones Destacadas del Último Mes y Corrección de KPIs
- **Clasificación:** [OBLIGATORIO ENUNCIADO]
- **Hallazgo vinculado:** `HAL-011`
- **Requisito del que surge:** `RF-001` (Requerimiento UI/UX 1, pág. 4: *"Mostrar ejemplos destacados de donaciones realizadas en el último mes"*).
- **Descripción del resultado esperado:** La landing page (`GET /`) debe filtrar las donaciones destacadas para exhibir únicamente aquellas realizadas dentro de los últimos 30 días, calcular las métricas de forma consistente con las unidades de medida, y evitar enlaces a rutas privadas (`/donante/tracking`) para visitantes anónimos.
- **Estado actual y evidencia:** **Implementado parcialmente con defectos.**
  - En `frontend-service/src/main/java/com/donatrack/frontend/controller/HomeController.java:33, 48`, se toma `donaciones.stream().limit(6).toList()` sobre todas las donaciones sin filtrar por fecha del último mes.
  - En `HomeController.java:38-41`, `totalKilos` suma el campo `cantidad` de todas las donaciones sin verificar si `unidadMedida` es `kg`, `litros` o `unidades`.
  - En `frontend-service/src/main/resources/templates/index.html:205`, las tarjetas destacadas enlazan a `/donante/tracking`, ruta protegida que redirige al visitante a `/login`.
  - En `templates/comprobantes.html:50-116` y `templates/index.html:227-262`, se inyectan datos ficticios hardcodeados de comedores con fotos de Unsplash.
- **Componentes involucrados:** `frontend-service` (`HomeController.java`, `templates/index.html`, `templates/comprobantes.html`).
- **Dependencias y bloqueos:** Ninguno.
- **Prioridad:** **P2 — Media**
- **Pasos concretos de implementación:**
  1. En `HomeController.index`, filtrar `donaciones` verificando que `fechaCreacion` / `fechaRegistro` sea posterior a `LocalDateTime.now().minusMonths(1)` antes de aplicar `.limit(6)`.
  2. Cambiar el enlace de las tarjetas destacadas en `templates/index.html:205` para que apunte a `/comprobantes` o al marcador del mapa público `#publicMap` en lugar de `/donante/tracking`.
  3. Reemplazar los casos testigo hardcodeados en `templates/comprobantes.html` y `templates/index.html` por un estado vacío limpio cuando no existan entregas registradas.
- **Criterios de aceptación verificables:**
  - Una donación con fecha mayor a 30 días no aparece en la sección de destacadas del último mes.
  - Ningún botón público de la landing page redirige a `/login` por falta de permisos.
- **Pruebas necesarias:** Test en `FrontendControllersTest` verificando el filtrado de `donacionesDestacadas`.
- **Definición de terminado (DoD):** Landing page mostrando exclusivamente datos reales filtrados del último mes.

---

### PEND-007 — Filtro por Categoría en el Listado de Donaciones de la Persona Donante
- **Clasificación:** [OBLIGATORIO ENUNCIADO]
- **Hallazgo vinculado:** `HAL-009`
- **Requisito del que surge:** `RF-005` (Requerimiento UI/UX 5, pág. 4: *"La persona donante debe poder filtrar sus donaciones realizadas por estado y categoría o subcategoría/s de bienes entregados"*).
- **Descripción del resultado esperado:** La vista `/donante/donaciones` debe permitir filtrar tanto por `estado`, como por `categoria` (`Alimentos`, `Vestimenta`, `Mobiliario`, `Salud`, `Educación`, `Higiene`) y por `subcategoria`.
- **Estado actual y evidencia:** **Implementado parcialmente.**
  - El backend en `donaciones-service` (`DonacionController.java:85-98` y `DonacionSpecification.java:18-45`) **sí** soporta el parámetro `categoria` en `GET /api/v1/donaciones/page` y `GET /api/v1/donaciones/filtrar`.
  - Sin embargo, en `frontend-service`:
    - `DonanteWebController.donaciones` (`DonanteWebController.java:148-156`) no recibe `@RequestParam("categoria")`.
    - `ApiClientService.listarDonacionesPaginadas` (`ApiClientService.java:155-175`) no envía `categoria` en la URL.
    - `templates/donante/donaciones.html:32-56` solo tiene inputs para `estado` y `subcategoria`.
- **Componentes involucrados:** `frontend-service` (`DonanteWebController.java`, `ApiClientService.java`, `templates/donante/donaciones.html`).
- **Dependencias y bloqueos:** Ninguno.
- **Prioridad:** **P2 — Media**
- **Pasos concretos de implementación:**
  1. Agregar el parámetro `String categoria` en `ApiClientService.listarDonacionesPaginadas(...)` concatenando `&categoria={categoria}` cuando no sea nulo ni vacío.
  2. Recibir `@RequestParam(value = "categoria", required = false) String categoria` en `DonanteWebController.donaciones` y pasarlo a `apiClient` y al `Model` (`categoriaFiltro`).
  3. Agregar el `<select name="categoria">` en `templates/donante/donaciones.html` y conservar el parámetro en los links de paginación.
- **Criterios de aceptación verificables:**
  - Al seleccionar una categoría en `/donante/donaciones`, la tabla muestra únicamente las donaciones del donante pertenecientes a esa categoría.
- **Pruebas necesarias:** Test en `FrontendControllersTest` para `GET /donante/donaciones?categoria=Alimentos`.
- **Definición de terminado (DoD):** Filtro por estado, categoría y subcategoría funcionando de extremo a extremo.

---

### PEND-008 — Formulario Administrativo de Recepción en Depósito con Carga Múltiple de Bienes, Descripción General y Fecha de Vencimiento
- **Clasificación:** [OBLIGATORIO ENUNCIADO]
- **Hallazgo vinculado:** `HAL-008`
- **Requisito del que surge:** `RF-013`, `REG-001` (Entrega 1 pág. 7: *"Se debe incluir una descripción general y los bienes que contiene... Se registrará la totalidad de los bienes a donar en una única carga dentro del sistema, a partir de la cual el sistema realizará automáticamente una segmentación interna, generando múltiples posibles donaciones independientes agrupadas obligatoriamente por subcategorías"*).
- **Descripción del resultado esperado:** El formulario de carga en depósito (`/admin/donaciones/nueva` y `/donante/nueva-donacion`) debe permitir ingresar una descripción general de la carga y agregar dinámicamente múltiples ítems/bienes (con su categoría, subcategoría, descripción, cantidad, unidad de medida, condición nuevo/usado, fecha de vencimiento si es perecedero y foto opcional) en un único envío para que el backend ejecute la segmentación automática en múltiples donaciones.
- **Estado actual y evidencia:** **Implementado parcialmente.**
  - El backend (`DonacionService.registrarCargaSegmentada` en `DonacionService.java:61-147`) sí recibe una `List<BienDonado>` y los segmenta por subcategoría, vencimiento y condición.
  - Pero en `frontend-service`:
    - `templates/admin/nueva_donacion.html:30-120` y `AdminWebController.guardarDonacionSegmentada` (`AdminWebController.java:187-206`) solo permiten cargar **1 único bien** (`List.of(bien)`) y ni siquiera tienen el campo `fechaVencimiento`.
    - `Donacion.java` no persiste el campo `descripcionGeneral` de la carga.
    - En `DonacionService.java:80-85`, la clave de agrupación no incluye `unidadMedida`, por lo que si dos bienes de la misma subcategoría tienen distinta unidad (`kg` vs `unidades`), suma sus cantidades erróneamente.
- **Componentes involucrados:** `donaciones-service` (`Donacion.java`, `DonacionService.java`, `CargaSegmentadaRequestDto.java`), `frontend-service` (`AdminWebController.java`, `DonanteWebController.java`, `templates/admin/nueva_donacion.html`, `templates/donante/nueva_donacion.html`).
- **Dependencias y bloqueos:** Ninguno.
- **Prioridad:** **P1 — Alta**
- **Pasos concretos de implementación:**
  1. En `Donacion.java` y `DonacionService.java`, agregar soporte para `descripcionGeneral` e incluir `unidadMedida.trim().toLowerCase()` en la clave de agrupación de `registrarCargaSegmentada`.
  2. En `templates/admin/nueva_donacion.html`, agregar el campo `fechaVencimiento`, `descripcionGeneral` y un botón *"Agregar otro bien a la carga"* que permita enviar múltiples filas de bienes en el mismo `POST`.
  3. Actualizar `AdminWebController.guardarDonacionSegmentada` para recibir listas de parámetros (`List<String> categoria`, `List<String> subcategoria`, `List<Double> cantidad`, etc.) y construir la `List<Map<String, Object>> bienes` completa.
- **Criterios de aceptación verificables:**
  - Si el administrador ingresa en una única carga 6 sillas usadas y 1 mesa usada, el sistema genera automáticamente 2 donaciones independientes en estado `EN_DEPOSITO` (una para subcategoría `sillas` y otra para `mesas`).
  - Si ingresa bienes perecederos con distinta fecha de vencimiento, genera donaciones separadas.
- **Pruebas necesarias:** Tests en `DonacionServiceTest` y `FrontendControllersTest`.
- **Definición de terminado (DoD):** Carga múltiple en depósito segmentando correctamente desde la UI y vía API.

---

### PEND-009 — Mapa en Tiempo Real de Entregas Activas para Persona Donante y Entidad Beneficiaria
- **Clasificación:** [OBLIGATORIO ENUNCIADO]
- **Hallazgo vinculado:** `HAL-004`
- **Requisito del que surge:** `RF-009`, `RF-014` (Requerimiento UI/UX 9, pág. 4: *"La persona donante debe poder seguir en un mapa las entregas activas de las donaciones que realizó, visualizando el/los camiones asociados con ubicación y última actualización"*; Requerimiento UI/UX 14, pág. 4: *"La entidad beneficiaria debe poder seguir en un mapa las entregas activas de las donaciones que debe recibir, visualizando el/los camiones asociados con ubicación y última actualización"*).
- **Descripción del resultado esperado:** Las vistas `/donante/tracking` y `/beneficiaria/tracking` deben mostrar en el mapa Leaflet los marcadores reales de los camiones que transportan entregas activas (`EN_TRASLADO` / `PENDIENTE` en ruta activa) pertenecientes específicamente al donante o a la entidad beneficiaria en sesión, incluyendo patente, ubicación GPS actual y fecha/hora de última actualización.
- **Estado actual y evidencia:** **Incorrecto / Hardcodeado en la vista.**
  - En `frontend-service/src/main/resources/templates/donante/tracking.html:98-150`, el script JavaScript **no lee ninguna variable de Thymeleaf** y dibuja coordenadas y marcadores 100% estáticos hardcodeados (`Comedor Los Chicos Felices`, `Comedor Esperanza`, `Camión AE789XY` en `-34.6065, -58.3980`).
  - En `frontend-service/src/main/resources/templates/beneficiaria/tracking.html:22-54`, la sede de la entidad está fija en `-34.6037, -58.3816`, se listan todos los camiones de `flotaEnVivo` sin filtrar cuáles van hacia esa entidad beneficiaria, y no se muestra la hora de última actualización.
- **Componentes involucrados:** `logistica-service` (`EntregaController`, `TelemetriaController`), `frontend-service` (`DonanteWebController.java`, `BeneficiariaWebController.java`, `templates/donante/tracking.html`, `templates/beneficiaria/tracking.html`).
- **Dependencias y bloqueos:** Ninguno.
- **Prioridad:** **P1 — Alta**
- **Pasos concretos de implementación:**
  1. En `DonanteWebController.tracking`, cruzar las entregas/rutas activas de las donaciones del donante (`misDonaciones`) con `flotaEnVivo` para obtener únicamente los camiones asociados a sus entregas, incluyendo `latitud`, `longitud`, `patente` y `ultimaActualizacion`.
  2. En `templates/donante/tracking.html`, reemplazar el script hardcodeado por la iteración dinámica `/*[[${camionesActivosDonante}]]*/` y mostrar en el popup y en la tabla la fecha/hora de última actualización.
  3. En `BeneficiariaWebController.tracking` y `templates/beneficiaria/tracking.html`, usar las coordenadas reales de la entidad beneficiaria en sesión y filtrar los camiones que tienen entregas dirigidas a su `entidadId`, mostrando `ultimaActualizacion`.
- **Criterios de aceptación verificables:**
  - Cuando un camión actualiza su posición vía `POST /api/v1/logistica/telemetria/posicion`, el mapa de `/donante/tracking` y `/beneficiaria/tracking` de los actores involucrados refleja las nuevas coordenadas y el timestamp de última actualización.
- **Pruebas necesarias:** Tests de controlador en `FrontendControllersTest` verificando los atributos del modelo filtrados por actor.
- **Definición de terminado (DoD):** Cero coordenadas ficticias hardcodeadas en `tracking.html`; renderizado 100% dinámico con datos de `logistica-service`.

---

### PEND-010 — Selección de Entidad Beneficiaria en Matchmaking (Visibilidad Condicional de Intersección vs. Rankings Separados y Vinculación de `necesidadId`)
- **Clasificación:** [OBLIGATORIO ENUNCIADO]
- **Hallazgo vinculado:** `HAL-010`
- **Requisito del que surge:** `RF-017`, `REG-004` (Entrega 2 pág. 12: *"este componente puede filtrar automáticamente las necesidades que hayan aparecido en el 'Top 10' de la ejecución de ambos algoritmos para que una persona administradora confirme el destino final. Si no hubo coincidencias, entonces la interfaz mostrará ambos rankings por separado"*).
- **Descripción del resultado esperado:**
  1. Si `evaluacion.coincidenciasDestacadas` (intersección entre ambos Top 10) **no es vacía**, la interfaz administrativa (`/admin/matchmaking`) debe mostrar **únicamente** la tabla de coincidencias destacadas para que el administrador elija el destino final.
  2. **Solo si `evaluacion.coincidenciasDestacadas` es vacía**, la interfaz debe mostrar las dos tablas separadas (Ranking Compatibilidad Semántica Top 10 y Ranking Prioridad a Sub-atendidos Top 10).
  3. Al confirmar la selección, el formulario debe enviar tanto `entidadId` como `necesidadId` para que la donación quede vinculada a la `NecesidadMaterial` correspondiente.
- **Estado actual y evidencia:** **Implementado parcialmente / Incorrecto en UI.**
  - En `frontend-service/src/main/resources/templates/admin/matchmaking.html:135`, la sección de rankings separados tiene `th:if="${!#lists.isEmpty(evaluacion.rankingSemanticoTop10) or !#lists.isEmpty(evaluacion.rankingSubatendidosTop10)}"`, por lo que se renderiza **siempre**, aun cuando existen coincidencias en la intersección.
  - En `templates/admin/matchmaking.html:86-90, 148-152, 171-175`, los formularios solo envían `donacionId` y `entidadId` (omiten `necesidadId`), y `AdminWebController.asignarDonacion` (`AdminWebController.java:122-135`) ni siquiera recibe `necesidadId`.
- **Componentes involucrados:** `frontend-service` (`templates/admin/matchmaking.html`, `AdminWebController.java`, `ApiClientService.java`).
- **Dependencias y bloqueos:** Ninguno.
- **Prioridad:** **P1 — Alta**
- **Pasos concretos de implementación:**
  1. En `templates/admin/matchmaking.html:135`, agregar la condición `th:if="${#lists.isEmpty(evaluacion.coincidenciasDestacadas) and (!#lists.isEmpty(evaluacion.rankingSemanticoTop10) or !#lists.isEmpty(evaluacion.rankingSubatendidosTop10))}"`.
  2. Agregar `<input type="hidden" name="necesidadId" th:value="${item.necesidadId}">` en los formularios de adjudicación de `matchmaking.html`.
  3. Actualizar `AdminWebController.asignarDonacion` y `ApiClientService.asignarDonacion` para recibir y enviar `necesidadId` a `POST /api/v1/donaciones/{id}/asignar`.
- **Criterios de aceptación verificables:**
  - Cuando hay intersección entre ambos rankings, solo se visualiza la lista filtrada de intersección.
  - Cuando la intersección es vacía, se visualizan ambos rankings por separado.
  - Al adjudicar desde matchmaking, `donacion.necesidadAsignada` queda vinculada en la base de datos.
- **Pruebas necesarias:** Tests en `FrontendControllersTest` y `DonacionServiceTest`.
- **Definición de terminado (DoD):** UI de matchmaking cumpliendo estrictamente la regla de negocio de intersección y adjudicando la necesidad seleccionada.

---

### PEND-011 — ABM Completo de Necesidades Materiales y de Camiones en la Interfaz Web
- **Clasificación:** [OBLIGATORIO ENUNCIADO]
- **Hallazgo vinculado:** `HAL-021`
- **Requisito del que surge:** `RF-010`, `RF-018` (Entrega 2 pág. 14: *"Operaciones CRUD sobre las necesidades materiales"*; Entrega 3 pág. 17: *"Gestión de flota de camiones"*).
- **Descripción del resultado esperado:**
  1. En `/beneficiaria/necesidades`, permitir a la entidad beneficiaria editar y cerrar/dar de baja sus necesidades materiales activas (los endpoints de backend `PUT /api/v1/entidades/necesidades/{id}` y `DELETE /api/v1/entidades/necesidades/{id}` y los métodos en `ApiClientService.java:656-682` ya existen, pero no están conectados en `BeneficiariaWebController` ni en `templates/beneficiaria/necesidades.html`).
  2. En `/admin/camiones`, permitir al administrador editar los datos o cambiar el estado (`DISPONIBLE`, `EN_MANTENIMIENTO`) de un camión existente.
- **Estado actual y evidencia:** **Implementado parcialmente.** Solo existen alta y listado en `templates/beneficiaria/necesidades.html:79-111`, y alta, listado y baja en `templates/admin/camiones.html:1-95`.
- **Componentes involucrados:** `frontend-service` (`BeneficiariaWebController.java`, `templates/beneficiaria/necesidades.html`, `AdminWebController.java`, `templates/admin/camiones.html`).
- **Dependencias y bloqueos:** Ninguno.
- **Prioridad:** **P2 — Media**
- **Pasos concretos de implementación:**
  1. Agregar endpoints `POST /beneficiaria/necesidades/{id}/editar` y `POST /beneficiaria/necesidades/{id}/eliminar` en `BeneficiariaWebController.java` y botones de acción en `templates/beneficiaria/necesidades.html`.
  2. Agregar endpoint `POST /admin/camiones/{id}/estado` / edición en `AdminWebController.java` y controles en `templates/admin/camiones.html`.
- **Criterios de aceptación verificables:**
  - Una entidad beneficiaria puede editar la cantidad/descripción o cerrar una necesidad desde su panel.
  - El administrador puede cambiar el estado o datos de un camión desde `/admin/camiones`.
- **Pruebas necesarias:** Tests MVC en `FrontendControllersTest`.
- **Definición de terminado (DoD):** Operaciones CRUD completas accesibles desde la interfaz de usuario.

---

### PEND-012 — Persistencia en Base de Datos de los Parámetros de Matchmaking ($a_1, a_2, a_3, \alpha$)
- **Clasificación:** [OBLIGATORIO ENUNCIADO]
- **Hallazgo vinculado:** `HAL-016`
- **Requisito del que surge:** `REG-003`, `DAT-001` (Entrega 2 pág. 12: *"Las $a_n$ son constantes configurables por el administrador del sistema. Determinan qué le importa más a la ONG. La regla de oro es que la suma de todos los pesos debe dar 1 (100%)"*).
- **Descripción del resultado esperado:** Los pesos configurados por el administrador ($a_1, a_2, a_3$ y $\alpha$) deben persistirse en PostgreSQL (`donaciones_db`) para que sobrevivan a reinicios del servicio, y validarse que $a_1, a_2, a_3 \ge 0$, $a_1 + a_2 + a_3 = 1.0$ y $\alpha > 0$.
- **Estado actual y evidencia:** **Implementado parcialmente (solo en memoria RAM).**
  - En `CompatibilidadSemanticaStrategy.java:19-26, 47-52` y `PrioridadSubatendidosStrategy.java:24-25, 70-72`, los valores se guardan en campos `double` de instancia en memoria. Si `donaciones-service` se reinicia, se pierden los cambios del administrador. Además, `setAlpha` no valida que $\alpha > 0$.
- **Componentes involucrados:** `donaciones-service` (`ConfiguracionMatchmaking` entidad JPA o tabla de parámetros, `CompatibilidadSemanticaStrategy`, `PrioridadSubatendidosStrategy`, `MatchmakingService`).
- **Dependencias y bloqueos:** Ninguno.
- **Prioridad:** **P2 — Media**
- **Pasos concretos de implementación:**
  1. Crear entidad JPA `ConfiguracionMatchmaking` (con `id = 1L`, `a1`, `a2`, `a3`, `alpha`, `fechaActualizacion`) y su `ConfiguracionMatchmakingRepository`.
  2. Al iniciar o evaluar matchmaking y al invocar `PUT /api/v1/donaciones/matchmaking/config`, leer y persistir los valores en base de datos, validando $a_i \ge 0$, $|(a_1+a_2+a_3) - 1.0| \le 0.001$ y $\alpha > 0$.
- **Criterios de aceptación verificables:**
  - Al modificar los pesos vía `PUT /api/v1/donaciones/matchmaking/config` y reiniciar `donaciones-service`, los pesos configurados se mantienen en `GET /api/v1/donaciones/matchmaking/config`.
- **Pruebas necesarias:** Test unitario y de persistencia en `Fase3MatchmakingTest`.
- **Definición de terminado (DoD):** Pesos persistidos en PostgreSQL y validados.

---

## 3. Integraciones Pendientes

### PEND-013 — Corrección de Ruta de Alta de Persona Jurídica entre `frontend-service`, `api-gateway` y `donaciones-service`
- **Clasificación:** [OBLIGATORIO ENUNCIADO]
- **Hallazgo vinculado:** `HAL-002`
- **Requisito del que surge:** `RF-003`, `RF-006` (Registro y alta de Personas Donantes Jurídicas).
- **Descripción del resultado esperado:** El registro de una Persona Jurídica desde `/registro` (`POST /registro/juridica`) debe llegar correctamente a `donaciones-service` a través del `api-gateway` y persistir la entidad junto con sus medios de contacto (incluyendo teléfono) y representantes.
- **Estado actual y evidencia:** **Roto (HTTP 404 a través del Gateway).**
  - `ApiClientService.crearDonanteJuridica` (`frontend-service/src/main/java/com/donatrack/frontend/service/ApiClientService.java:629`) envía `POST /api/v1/personas-donantes/juridica`.
  - `api-gateway/src/main/resources/application.yml:20-23` solo enruta `Path=/api/v1/donantes/**` y **no tiene ruta para `/api/v1/personas-donantes/**`**, devolviendo `404 Not Found`.
  - Además, `HomeController.registrarDonanteHumana` (`líneas 164, 180-190`) y `registrarDonanteJuridica` (`líneas 212, 228-237`) reciben `@RequestParam("telefono")` pero nunca lo agregan a la lista `mediosContacto`.
- **Componentes involucrados:** `frontend-service` (`ApiClientService.java`, `HomeController.java`, `templates/registro.html`), `api-gateway` (`application.yml`).
- **Dependencias y bloqueos:** Ninguno.
- **Prioridad:** **P0 — Crítica**
- **Pasos concretos de implementación:**
  1. Cambiar la URL en `ApiClientService.crearDonanteJuridica` (`línea 629`) a `gatewayUrl + "/api/v1/donantes/juridica"` (y agregar `/api/v1/personas-donantes/**` en `api-gateway/src/main/resources/application.yml` por retrocompatibilidad).
  2. En `HomeController.registrarDonanteHumana` y `registrarDonanteJuridica`, si `telefono` no es vacío, agregar el `MedioContacto` de tipo `TELEFONO` o `WHATSAPP` a `mediosContacto` y permitir indicar representantes en Persona Jurídica.
- **Criterios de aceptación verificables:**
  - `POST /registro/juridica` a través del frontend conectado al Gateway (`8080`) crea la `PersonaJuridica` en `donaciones_db` con email, teléfono y representantes, retornando redirección exitosa a `/login`.
- **Pruebas necesarias:** Test en `FrontendControllersTest` y verificación E2E contra el Gateway.
- **Definición de terminado (DoD):** Registro de persona jurídica funcionando sin error 404.

---

### PEND-014 — Unificación del Flujo de Confirmación de Recepción de Entrega entre `frontend-service`, `logistica-service` y `donaciones-service`
- **Clasificación:** [OBLIGATORIO ENUNCIADO]
- **Hallazgo vinculado:** `HAL-007`
- **Requisito del que surge:** `RF-012`, `INT-003` (Entrega 3 págs. 16-17: Confirmación de recepción por la entidad beneficiaria cargando fotos -> entrega pasa a `Entregada`, queda registrado qué camión realizó la entrega, se actualiza necesidad, se notifica comprobante con fecha/hora/camión a donante y beneficiaria, y se notifica a Incentivos para misión *Donaciones Exitosas*).
- **Descripción del resultado esperado:** Cuando la entidad beneficiaria confirma la recepción de una donación en `/beneficiaria/confirmar`, el sistema debe resolver automáticamente la entrega activa asociada en `logistica-service` (por `idDonacion`) sin exigir que el usuario copie un ID de MongoDB (`idEntrega`) a mano, y sin que el frontend salte artificialmente los estados logísticos (`ASIGNACION_REALIZADA -> LISTA_PARA_ENTREGAR -> EN_TRASLADO -> ENTREGADA`).
- **Estado actual y evidencia:** **Defectuoso / Desconectado.**
  - En `frontend-service/src/main/resources/templates/beneficiaria/confirmar.html:78-82`, `idEntrega` es un campo de texto libre opcional. Si el usuario solo elige la donación del `<select name="donacionId">` y deja `idEntrega` vacío, `BeneficiariaWebController.java:218-225` llama únicamente a `apiClient.entregarDonacion(donacionId, ...)`.
  - `ApiClientService.entregarDonacion` (`ApiClientService.java:423-450`) hace 3 `PATCH` seguidos directamente contra `donaciones-service` (`LISTA_PARA_ENTREGAR` -> `EN_TRASLADO` -> `ENTREGADA`), puenteando por completo a `logistica-service`. Como consecuencia, la entrega en MongoDB queda estancada en `EN_TRASLADO`, no se registra el camión receptor, no se emite `EntregaConfirmadaEvent` en RabbitMQ, no se envía el comprobante por email y `incentivos-service` nunca acredita la misión *Donaciones Exitosas*.
- **Componentes involucrados:** `logistica-service` (`EntregaController`, `EntregaService`, `EntregaRepository`), `frontend-service` (`BeneficiariaWebController`, `ApiClientService`, `templates/beneficiaria/confirmar.html`), `donaciones-service` (`DonacionService`).
- **Dependencias y bloqueos:** Ninguno.
- **Prioridad:** **P1 — Alta**
- **Pasos concretos de implementación:**
  1. En `logistica-service`, exponer `POST /api/v1/logistica/entregas/por-donacion/{idDonacion}/confirmar` (y `/fallida`) buscando la entrega mediante `entregaRepository.findByIdDonacion(idDonacion)`.
  2. En `BeneficiariaWebController.confirmarRecepcion` (y `registrarEntregaFallida`), invocar primero al endpoint de `logistica-service` por `donacionId` (el cual ya actualiza el estado en `donaciones-service` y publica `EntregaConfirmadaEvent` / `EntregaFallidaEvent`).
  3. Eliminar de `ApiClientService.entregarDonacion` el bucle que fuerza transiciones ilegales desde `ASIGNACION_REALIZADA` sin haber pasado por ruta/traslado.
  4. Quitar de `templates/beneficiaria/confirmar.html:105` la URL de foto de Unsplash pre-rellenada en `value`, exigiendo que la entidad suba una foto o ingrese una URL real.
- **Criterios de aceptación verificables:**
  - Al confirmar una recepción seleccionando solo la donación y subiendo una foto en `/beneficiaria/confirmar`, la `Entrega` en MongoDB pasa a `ENTREGADA`, la `Donacion` en PostgreSQL pasa a `ENTREGADA`, se actualiza la necesidad material, se envía el comprobante con patente del camión a donante y beneficiaria, y se acredita el avance de la misión `DONACIONES_EXITOSAS` en `incentivos-service`.
- **Pruebas necesarias:** Tests en `EntregaServiceTest` y `FrontendControllersTest`.
- **Definición de terminado (DoD):** Flujo de confirmación unificado pasando por `logistica-service` de extremo a extremo.

---

### PEND-015 — Propagación del Medio de Contacto Preferido del Donante en Eventos de Logística e Incentivos
- **Clasificación:** [OBLIGATORIO ENUNCIADO]
- **Hallazgo vinculado:** `HAL-019`
- **Requisito del que surge:** `RF-008`, `INT-001` (Entrega 1 pág. 6: *"podrá determinar cuál de ellos será su medio de contacto predeterminado para recibir notificaciones del sistema"*; Entrega 2 pág. 14).
- **Descripción del resultado esperado:** Todas las notificaciones enviadas a una persona donante (no solo las de `donaciones-service`, sino también las de inicio de ruta, entrega confirmada, entrega fallida, misión cumplida y categoría ascendida) deben respetar el canal predeterminado configurado por el donante (`EMAIL`, `SMS` o `WHATSAPP`).
- **Estado actual y evidencia:** **Implementado parcialmente.**
  - `notificaciones-service` (`NotificacionesService.java:462-483`) sí tiene la lógica para despachar por `EMAIL`, `SMS` o `WHATSAPP` según `canalPreferido`.
  - Sin embargo, cuando `logistica-service` publica `EntregaConfirmadaEvent` (`EntregaServiceImpl.java:90-99`), `EntregaFallidaEvent` (`EntregaServiceImpl.java:127-135`) y `RutaIniciadaEvent` (`RecorridoServiceImpl.java:92-101`), y cuando `incentivos-service` publica `MisionCumplidaEvent` (`IncentivosService.java:334-342`) y `CategoriaAscendidaEvent` (`IncentivosService.java:387-393`), usan los constructores cortos que hardcodean `canalPreferido = CanalNotificacion.EMAIL`.
- **Componentes involucrados:** `common-dtos`, `donaciones-service`, `logistica-service`, `incentivos-service`, `notificaciones-service`.
- **Dependencias y bloqueos:** Ninguno.
- **Prioridad:** **P2 — Media**
- **Pasos concretos de implementación:**
  1. En `DonacionRegistradaEvent` y `DonacionAsignadaEvent`, incluir `canalPreferidoDonante` y `contactoPreferidoDonante` para que `incentivos-service` los almacene en `PerfilDonanteIncentivo` y los propague en `MisionCumplidaEvent` y `CategoriaAscendidaEvent`.
  2. En `logistica-service` (`PlanificacionRutasServiceImpl` / `DonacionesServiceClient`), leer el medio de contacto predeterminado del donante desde el detalle de la donación, guardarlo en `Entrega` y propagarlo en `EntregaConfirmadaEvent` y `EntregaFallidaEvent`.
- **Criterios de aceptación verificables:**
  - Si un donante tiene `WHATSAPP` como medio predeterminado, al cumplir una misión o confirmarse la entrega de su donación, `notificaciones-service` registra la auditoría de envío por el canal `WHATSAPP`.
- **Pruebas necesarias:** Tests en `IncentivosServiceTest`, `EntregaServiceTest` y `NotificacionesServiceTest`.
- **Definición de terminado (DoD):** Canal preferido del donante respetado en todos los flujos de eventos.

---

### PEND-016 — Sincronización de Credenciales entre Registro / Importación CSV y el Servicio de Autenticación (Keycloak)
- **Clasificación:** [OBLIGATORIO ENUNCIADO]
- **Hallazgo vinculado:** `HAL-003`
- **Requisito del que surge:** `RF-003`, `RF-004`, `RF-020`, `SEG-002` (Entrega 1 págs. 7-8: *"en caso contrario, se lo deberá crear el usuario y enviarle sus credenciales de acceso"*; Entrega 5 pág. 21: *"Servicio de Autenticación"*).
- **Descripción del resultado esperado:** Cuando se registra un donante o entidad beneficiaria (desde `/registro`, desde el depósito por el Admin o vía importación CSV), sus credenciales deben persistirse de forma duradera (en Keycloak vía Admin REST API o en una tabla persistente de credenciales con hash BCrypt si Keycloak no se usa para usuarios dinámicos) y no perderse al reiniciar `frontend-service`, eliminando las contraseñas maestras universales hardcodeadas.
- **Estado actual y evidencia:** **Defectuoso / Volátil en memoria.**
  - `AuthService.java:26, 50-55` guarda los usuarios registrados desde `/registro` únicamente en un `ConcurrentHashMap<String, String> credencialesRegistradas` en memoria RAM del proceso `frontend-service`. Al reiniciar el servicio, ningún usuario registrado puede volver a iniciar sesión con su contraseña.
  - Para disimularlo, `AuthService.java:363-365, 382-384` acepta las contraseñas maestras `"donante123"`, `"comedor123"` y `"admin123"` para **cualquier** donante o entidad beneficiaria registrada en la base de datos.
  - En `DonanteService.java:134` y `CsvDonanteImporterService.java:221`, al crear un donante o importar un CSV se envía por correo la contraseña fija `"donante123"` sin crear el usuario en Keycloak.
- **Componentes involucrados:** `frontend-service` (`AuthService.java`), `donaciones-service` (`DonanteService.java`, `CsvDonanteImporterService.java`), Keycloak.
- **Dependencias y bloqueos:** Ninguno.
- **Prioridad:** **P0 — Crítica**
- **Pasos concretos de implementación:**
  1. Eliminar los backdoors de contraseñas universales en `AuthService.validarPasswordEntidad` (`líneas 363-365`) y `AuthService.validarPasswordDonante` (`líneas 382-384`), restringiendo las credenciales demo exclusivamente a los usuarios demo (`juan.donante@example.com`, `rse@molinos.com.ar`, `esperanza@comedor.org`, `admin`, `chofer`).
  2. Integrar el alta de usuarios contra la API Admin de Keycloak (`/admin/realms/donatrack/users`) y/o persistir el hash `BCrypt` de la contraseña para que las cuentas creadas en `/registro`, en depósito o por CSV sobrevivan a reinicios.
  3. Soportar en `AuthService.coincideEmailDonante` la búsqueda por `cuit` y `razonSocial` para Personas Jurídicas.
- **Criterios de aceptación verificables:**
  - Un usuario registrado en `/registro` con contraseña propia puede iniciar sesión incluso después de reiniciar `frontend-service`.
  - Un usuario registrado con contraseña `MiPasswordSegura1!` NO puede ser accedido por un tercero ingresando `admin123`.
- **Pruebas necesarias:** Tests unitarios de autenticación en `FrontendControllersTest` / `AuthServiceTest`.
- **Definición de terminado (DoD):** Credenciales persistentes, hasheadas y sin backdoors universales.

---

## 4. Reglas de Negocio Pendientes o Incompletas

### PEND-017 — Momento de Actualización de la Satisfacción de Necesidades Materiales (Al Confirmar Entrega vs. Al Asignar)
- **Clasificación:** [OBLIGATORIO ENUNCIADO]
- **Hallazgo vinculado:** `HAL-015`
- **Requisito del que surge:** `REG-002` (Entrega 1 pág. 8: *"La necesidad se considera satisfecha cuando se recibe una cantidad de bienes igualando o superando la cantidad requerida"*).
- **Descripción del resultado esperado:** Una necesidad material (extraordinaria o recurrente) solo debe considerarse formalmente satisfecha cuando la entidad beneficiaria **recibe efectivamente** los bienes (`confirmarEntrega`), o bien debe distinguir claramente entre cantidad comprometida/en tránsito (al asignar) y cantidad efectivamente recibida (al confirmar entrega), evitando que una necesidad extraordinaria quede cerrada definitivamente antes de que el camión llegue y sin que un vencimiento posterior de la donación revierta la cobertura.
- **Estado actual y evidencia:** **Incorrecto en casos de borde.**
  - En `donaciones-service/src/main/java/com/donatrack/donaciones/domain/Donacion.java:108-110`, `asignarA` invoca inmediatamente `necesidad.registrarCobertura(this.cantidad)`, lo que cierra la necesidad (`activa = false`) apenas pasa a `ASIGNACION_REALIZADA` (antes de ser entregada).
  - Peor aún: si esa donación asignada luego se marca como `VENCIDA` (`Donacion.marcarVencida`, líneas `212-217`), **no se invoca `necesidadAsignada.revertirCobertura(this.cantidad)`**, dejando la necesidad de la entidad beneficiaria cerrada como "satisfecha" aunque jamás recibió los bienes.
  - Y cuando una donación se asigna sin `necesidadId` (como ocurre actualmente desde `/admin/matchmaking` o en donación directa), `necesidadAsignada` es `null` y nunca se descuenta ninguna necesidad de la entidad.
- **Componentes involucrados:** `donaciones-service` (`Donacion.java`, `DonacionService.java`, `NecesidadMaterial.java`).
- **Dependencias y bloqueos:** Vinculado con `PEND-010`.
- **Prioridad:** **P1 — Alta**
- **Pasos concretos de implementación:**
  1. En `Donacion.marcarVencida` (`Donacion.java:212-217`), restringir la transición únicamente al estado `EN_DEPOSITO` (o `ENTREGA_FALLIDA`), o bien si se permite desde estados asignados, invocar `necesidadAsignada.revertirCobertura(this.cantidad)` y limpiar `entidadAsignada` y `necesidadAsignada`.
  2. En `DonacionService.asignarDonacion`, si `necesidadId` es `null`, buscar automáticamente una necesidad activa de la entidad para esa misma `subcategoria` y asociarla.
  3. En `Donacion.confirmarEntrega`, asegurar la acreditación definitiva sobre la necesidad material.
- **Criterios de aceptación verificables:**
  - Si una donación asignada no llega a entregarse (falla o vence), la necesidad material recupera su `cantidadPendiente` y vuelve a estar `activa = true`.
- **Pruebas necesarias:** Tests unitarios en `DonacionStateTest` y `DonacionServiceTest`.
- **Definición de terminado (DoD):** Cobertura de necesidades consistente en todos los caminos de éxito y excepción.

---

### PEND-018 — Corrección de Doble Contabilización, Secuencialidad Estricta y Regla de "Hábil Donador" en `incentivos-service`
- **Clasificación:** [OBLIGATORIO ENUNCIADO]
- **Hallazgo vinculado:** `HAL-005`
- **Requisito del que surge:** `REG-006`, `REG-007` (Entrega 2 pág. 13: *"Las misiones se completan en orden secuencial dentro de cada categoría: al completar una misión, se desbloquea la siguiente... Hábil Donador: donación que supere X cantidad de bienes"*).
- **Descripción del resultado esperado:**
  1. Cada donación debe contabilizarse **una única vez** en `totalDonaciones` y `totalBienesDonados` del donante.
  2. La misión *"Hábil Donador"* debe cumplirse cuando el donante realiza **una donación que supere X cantidad de bienes** (`cantidadBienes >= objetivo`), no acumulando micro-donaciones menores.
  3. La evaluación secuencial debe detenerse (`break`) siempre en la primera misión incompleta de la categoría actual, incluso cuando sea de tipo `COMPLETITUD` y la donación actual no agregue una nueva categoría/subcategoría.
  4. Al perderse la racha mensual en `IncentivosScheduler.verificarYResetearRachasMensuales()`, debe reiniciarse a `0` también `perfil.rachaMesesConsecutivos`.
- **Estado actual y evidencia:** **Incorrecto.**
  - **Doble contabilización (`DonacionEventConsumer.java:39-65` y `93-117`):** Cuando una donación ingresa al depósito se emite `DonacionRegistradaEvent` (que llama a `registrarImpactoDonacionCompleto`, sumando `+1` a `totalDonaciones` y `+cantidad` a `totalBienesDonados`). Cuando luego esa misma donación se asigna a una entidad, se emite `DonacionAsignadaEvent` (que vuelve a llamar a `registrarImpactoDonacionCompleto` con otra clave de idempotencia, sumando **por segunda vez** `+1` a `totalDonaciones` y `+cantidad` a `totalBienesDonados`, además de avanzar una segunda misión con la misma donación).
  - **Salto de secuencialidad en `COMPLETITUD` (`IncentivosService.java:196-229`):** Si la misión activa actual es `COMPLETITUD` (orden 2) y el donante dona una subcategoría ya donada antes, `avanza` es `false` y el bucle `for` **no hace `break`**, pasando a evaluar y completar `HABIL_DONADOR` (orden 3) sin haber completado la misión 2.
  - **Acumulación indebida en `HABIL_DONADOR` (`IncentivosService.java:205-209`):** Si el objetivo es 20 y el donante dona 5 unidades, el `else { prog.agregarProgreso(cantidadBienes); }` acumula progreso parcial en vez de evaluar si la donación individual supera X cantidad.
  - **No se emite `DonacionRegistradaEvent` en donaciones con `entidadId` (`DonacionService.java:110-145`):** El `if (entidadId != null) ... else ...` omite emitir `DonacionRegistradaEvent` cuando se pasa `entidadId`.
- **Componentes involucrados:** `incentivos-service` (`DonacionEventConsumer.java`, `IncentivosService.java`, `IncentivosScheduler.java`), `donaciones-service` (`DonacionService.java`).
- **Dependencias y bloqueos:** Ninguno.
- **Prioridad:** **P1 — Alta**
- **Pasos concretos de implementación:**
  1. En `DonacionEventConsumer.procesarDonacionAsignada` (`DonacionEventConsumer.java:39-65`), cuando llega `DonacionAsignadaEvent`, actualizar únicamente `organizacionesAyudadas` (`perfil.registrarOrganizacionAyudada(evento.getIdEntidadBeneficiaria())`) si la donación ya fue contabilizada en `donacion.registrada`, evitando duplicar `totalDonaciones` y `totalBienesDonados`.
  2. En `IncentivosService.registrarImpactoDonacionCompleto` (`líneas 219-229`), colocar el `break;` fuera del `if (avanza)` para que **siempre** evalúe únicamente la primera misión no completada en orden secuencial.
  3. En `HABIL_DONADOR` (`líneas 204-212`), evaluar `if (cantidadBienes >= mision.getObjetivo())` para completar la misión (o guardar el máximo alcanzado en una donación individual), sin sumar donaciones pequeñas entre sí.
  4. En `IncentivosScheduler.verificarYResetearRachasMensuales()` (`líneas 106-112`), ejecutar también `perfil.setRachaMesesConsecutivos(0)` y `perfilRepository.save(perfil)`.
- **Criterios de aceptación verificables:**
  - Registrar 1 donación de 30 kg y luego asignarla a una entidad resulta en `totalDonaciones = 1` y `totalBienesDonados = 30.0` (no `2` y `60.0`).
  - La misión de orden 3 nunca puede avanzar ni completarse mientras la misión de orden 2 esté incompleta.
- **Pruebas necesarias:** Actualizar y ampliar `IncentivosServiceTest` e `IncentivosSchedulerTest`.
- **Definición de terminado (DoD):** Motor de ludificación libre de duplicaciones y respetando la secuencialidad estricta.

---

### PEND-019 — Validaciones de Dominio en Alta de Persona Jurídica y Prevención de Spam Diario en Inactividad (> 20 días)
- **Clasificación:** [OBLIGATORIO ENUNCIADO]
- **Hallazgo vinculado:** `HAL-017`, `HAL-018`
- **Requisito del que surge:** `RF-006`, `REG-005` (Entrega 1 pág. 6: *"las personas jurídicas deben indicar razón social, su tipo (Gubernamental, ONG, Empresa, Institución), rubro y al menos un medio de contacto. Cada organización tendrá personas representantes habilitadas a operar en su nombre"*; Entrega 2 pág. 14: Notificación por inactividad > 20 días).
- **Descripción del resultado esperado:**
  1. `DonanteService.registrarPersonaJuridica` debe validar obligatoriamente `razonSocial`, `cuit`, `tipo`, `rubro` y que posea **al menos un medio de contacto**.
  2. `DonacionesScheduler.detectarDonantesInactivos` no debe enviar un correo repetido todos los días a las 04:00 AM al mismo donante inactivo si ya fue notificado durante su período de inactividad actual.
- **Estado actual y evidencia:** **Incompleto.**
  - `DonanteService.java:95-117` solo valida `cuit` y permite guardar una `PersonaJuridica` sin razón social, sin rubro y con `mediosContacto` vacío.
  - `DonacionesScheduler.java:177-215` busca `d.ultimaActividad <= hace20Dias` y encola `DonanteInactivoEvent`, pero no actualiza ningún campo en `PersonaDonante`, por lo que al día siguiente vuelve a cumplir la condición y envía el mismo recordatorio indefinidamente.
- **Componentes involucrados:** `donaciones-service` (`PersonaDonante.java`, `DonanteService.java`, `DonacionesScheduler.java`).
- **Dependencias y bloqueos:** Ninguno.
- **Prioridad:** **P2 — Media**
- **Pasos concretos de implementación:**
  1. En `DonanteService.registrarPersonaJuridica`, validar que `razonSocial`, `tipo`, `rubro` y `mediosContacto` no sean nulos ni vacíos, y verificar unicidad previa de `cuit` y `email` (y de `nroDocumento` en `registrarPersonaHumana`) lanzando `IllegalArgumentException` (HTTP 400) en lugar de dejar caer un error SQL 500.
  2. En `PersonaDonante.java`, agregar `LocalDateTime ultimaNotificacionInactividad` (o actualizar el filtro en `PersonaDonanteRepository.findDonantesInactivosDesde`) para no volver a notificar al mismo donante dentro de los siguientes 20 días si sigue sin actividad.
- **Criterios de aceptación verificables:**
  - Intentar crear una `PersonaJuridica` sin medios de contacto devuelve `400 Bad Request`.
  - Ejecutar `detectarDonantesInactivos()` dos veces seguidas el mismo día no genera dos eventos para el mismo donante.
- **Pruebas necesarias:** Tests en `DonacionServiceTest` y `DonacionesSchedulerTest`.
- **Definición de terminado (DoD):** Validaciones y control anti-spam implementados y probados.

---

### PEND-020 — Corrección de Transaccionalidad y Rendimiento por Lotes en la Importación Masiva CSV (+10.000 / 20.000 filas)
- **Clasificación:** [OBLIGATORIO ENUNCIADO]
- **Hallazgo vinculado:** `HAL-020`
- **Requisito del que surge:** `RF-007`, `RNF-001` (Requerimiento UI/UX 20 pág. 5 y Entrega 1 pág. 8: Importación masiva desde archivos CSV con más de 10.000 / 20.000 filas actualizando existentes por email o creando nuevos).
- **Descripción del resultado esperado:** El importador CSV debe procesar 20.000 filas sin degradación cuadrática de consultas, sin abortar la transacción entera si una fila tiene un DNI/CUIT duplicado en base de datos, y persistiendo el `tipoDocumento` leído del CSV.
- **Estado actual y evidencia:** **Parcialmente implementado con defectos de escalabilidad y atomicidad.**
  - `PersonaHumana.java:5-85` no tiene el campo `tipoDocumento`; `CsvDonanteImporterService.java:152` lo lee del CSV y lo ignora.
  - `CsvDonanteImporterService.importarDesdeStream` (`línea 89`) ejecuta todo el archivo dentro de una única transacción `@Transactional`. Si la fila 15.000 viola el constraint `UNIQUE` de `nro_documento` o `cuit` al hacer `entityManager.flush()` (`línea 122`), PostgreSQL marca toda la transacción como *aborted* y se pierden las 14.999 filas anteriores.
  - `MedioContacto.java:8` no tiene índice en `(tipo, valor)`, por lo que `donanteRepository.findByEmail(email)` (`CsvDonanteImporterService.java:168`) ejecuta 20.000 consultas lentas con auto-flush.
  - `AdminWebController.importarCsv` (`AdminWebController.java:149`) carga todo el archivo en RAM con `file.getBytes()`.
- **Componentes involucrados:** `donaciones-service` (`PersonaHumana.java`, `MedioContacto.java`, `CsvDonanteImporterService.java`), `frontend-service` (`AdminWebController.java`).
- **Dependencias y bloqueos:** Ninguno.
- **Prioridad:** **P2 — Media**
- **Pasos concretos de implementación:**
  1. Agregar el campo `tipoDocumento` (por defecto `"DNI"`) en `PersonaHumana.java` y asignarlo en `CsvDonanteImporterService.java`.
  2. Agregar `@Table(name = "medios_contacto", indexes = @Index(name = "idx_medio_tipo_valor", columnList = "tipo, valor"))` en `MedioContacto.java`.
  3. Procesar cada lote en transacciones independientes (`TransactionTemplate` con `PROPAGATION_REQUIRES_NEW` o pre-validando `nroDocumento`/`cuit` existentes en un `Set` cargado al inicio de la importación) para que una fila con documento duplicado se contabilice en `filasConError` sin abortar el resto del archivo.
- **Criterios de aceptación verificables:**
  - Un archivo CSV de 10.000+ filas con 5 filas inválidas/duplicadas persiste las 9.995 filas válidas y reporta exactamente 5 errores sin hacer rollback global.
- **Pruebas necesarias:** Test de importación con filas válidas e inválidas mezcladas en `CsvDonanteImporterTest`.
- **Definición de terminado (DoD):** Importación resiliente, indexada y persistiendo `tipoDocumento`.

---

## 5. Pruebas Faltantes

### PEND-021 — Pruebas de Integración de Repositorios (`@DataJpaTest`) y Controladores Faltantes
- **Clasificación:** [OBLIGATORIO CALIDAD / VERIFICACIÓN]
- **Hallazgo vinculado:** `HAL-022`
- **Requisito del que surge:** Validación de persistencia relacional y documental (Entrega 4) y exposición REST/GraphQL (Entregas 2, 3 y 6).
- **Descripción del resultado esperado:** Incorporar pruebas automatizadas que verifiquen las consultas JPQL/Specifications reales contra base de datos (H2/Testcontainers) y los controladores que actualmente tienen 0% de cobertura de tests.
- **Estado actual y evidencia:** **Parcial.**
  - Todos los tests de `donaciones-service` usan repositorios mockeados con Mockito; no existe ningún `@DataJpaTest` que valide las queries JPQL de `DonacionRepository`, `PersonaDonanteRepository` ni `NecesidadMaterialRepository`.
  - No existen tests para `DonanteController`, `EntidadBeneficiariaController` ni `DonacionGraphqlController`.
  - En `NormalizacionDireccionTest.java:35-76`, los tests dependen de llamadas HTTP reales en vivo a `https://apis.datos.gob.ar/georef/api/direcciones`, lo que los vuelve no determinísticos sin internet.
  - En `api-gateway`, no existe ningún test en `api-gateway/src/test`.
- **Componentes involucrados:** `donaciones-service/src/test`, `api-gateway/src/test`.
- **Dependencias y bloqueos:** Ninguno.
- **Prioridad:** **P2 — Media**
- **Pasos concretos de implementación:**
  1. Crear `DonacionRepositoryIntegrationTest` (`@DataJpaTest`) probando `findAllByEmail`, `countEntregadasRecientesByEntidadId`, `findPendientesPorSubcategoria` y `DonacionSpecification`.
  2. Crear tests `@WebMvcTest` para `DonanteController`, `EntidadBeneficiariaController` y `DonacionGraphqlController`.
  3. Aislar `NormalizacionDireccionTest` con servidor mock (`MockRestServiceServer` o similar) para que no dependa de internet externo.
  4. Crear tests de seguridad y rate limiting en `api-gateway/src/test`.
- **Criterios de aceptación verificables:**
  - `./mvnw test` pasa el 100% de las pruebas en modo offline (`-o`).
- **Pruebas necesarias:** Ejecución de `./mvnw test`.
- **Definición de terminado (DoD):** Suite de pruebas determinística y cubriendo repositorios y controladores críticos.

---

## 6. Tareas de Seguridad, Infraestructura y Configuración Pendientes

### PEND-022 — Implementación de Autenticación/Autorización JWT y Rate Limiting en `api-gateway`
- **Clasificación:** [OBLIGATORIO ENUNCIADO]
- **Hallazgo vinculado:** `HAL-001`, `HAL-013`
- **Requisito del que surge:** `SEG-001` (Entrega 5 pág. 21: *"encargado de la gestión de tokens de autorización desde el servidor frontend hacia los servicios de aplicación"*) y `RNF-008` (Entrega 6 pág. 22 Req 4: *"implementar un sistema de limitación de solicitudes (Rate Limiting) que regule el tráfico entrante, protegiendo los recursos del sistema frente a abusos o sobrecarga"*).
- **Descripción del resultado esperado:**
  1. El `api-gateway` debe validar los tokens JWT emitidos por Keycloak (OAuth2 Resource Server) y aplicar control de acceso basado en roles (RBAC) o propagar identidad verificada a los microservicios, asegurando que `ApiClientService` en `frontend-service` envíe siempre `buildAuthHeaders()`.
  2. El `api-gateway` debe contar con un filtro de Rate Limiting (Token Bucket por IP/usuario con cota de memoria) que devuelva `429 Too Many Requests` ante ráfagas abusivas.
- **Estado actual y evidencia:** **No implementado (0% en `api-gateway`).**
  - `api-gateway/src/main/java/com/donatrack/gateway/` solo contiene `ApiGatewayApplication.java`. No existe `SecurityConfig.java` ni `RateLimitingFilter.java`.
  - `api-gateway/pom.xml:20-36` no tiene `spring-boot-starter-oauth2-resource-server` ni `spring-boot-starter-security`.
  - Todas las rutas `/api/v1/**` en el puerto `8080` están completamente abiertas sin autenticación.
  - En `frontend-service/src/main/java/com/donatrack/frontend/service/ApiClientService.java:391, 405, 454, 483, 536, 711`, varios métodos instancian `new HttpHeaders()` directamente en lugar de llamar a `buildAuthHeaders()`.
- **Componentes involucrados:** `api-gateway` (`pom.xml`, `SecurityConfig.java`, `RateLimitingFilter.java`, `application.yml`), `frontend-service` (`ApiClientService.java`).
- **Dependencias y bloqueos:** Vinculado con `PEND-016` (asegurar emisión de token o cabecera autenticada desde `frontend-service`).
- **Prioridad:** **P0 — Crítica**
- **Pasos concretos de implementación:**
  1. En `ApiClientService.java`, reemplazar todas las llamadas a `new HttpHeaders()` por `buildAuthHeaders()`.
  2. Implementar `RateLimitingFilter.java` en `api-gateway` (algoritmo Token Bucket con limpieza/evicción de entradas inactivas para evitar fugas de memoria y cabeceras `X-RateLimit-Remaining`).
  3. Agregar `spring-boot-starter-oauth2-resource-server` en `api-gateway/pom.xml` y configurar `SecurityConfig.java` validando JWT contra Keycloak y protegiendo los endpoints mutantes (`POST`, `PUT`, `PATCH`, `DELETE`).
- **Criterios de aceptación verificables:**
  - Superar el umbral de peticiones por minuto al `api-gateway` devuelve `429 Too Many Requests`.
  - Peticiones no autorizadas a endpoints protegidos del `api-gateway` son rechazadas con `401 Unauthorized` o `403 Forbidden`.
- **Pruebas necesarias:** Tests de filtro en `api-gateway` y prueba de integración con `frontend-service`.
- **Definición de terminado (DoD):** Seguridad y Rate Limiting activos en `api-gateway`.

---

### PEND-023 — Mitigación de Vulnerabilidades IDOR, Open Redirect y CSRF en `frontend-service`
- **Clasificación:** [OBLIGATORIO SEGURIDAD]
- **Hallazgo vinculado:** `HAL-003`
- **Requisito del que surge:** `SEG-003` (Control de acceso por rol y propiedad de los recursos en el servidor web MVC).
- **Descripción del resultado esperado:** Ningún usuario autenticado debe poder confirmar, marcar como fallida, consultar comprobantes o cambiar la visibilidad de insignias de recursos pertenecientes a otro donante o a otra entidad beneficiaria (prevención de IDOR), ni ser redirigido a un dominio externo tras el login (prevención de Open Redirect).
- **Estado actual y evidencia:** **Vulnerable.**
  - `BeneficiariaWebController.java:197-236, 247-286` permite a cualquier entidad beneficiaria confirmar recepción, reportar falla o ver el comprobante de cualquier `donacionId` / `idEntrega` sin verificar que pertenezca a `userSession.getEntidadId()`.
  - `DonanteWebController.java:243-260` permite consultar historial de cualquier donación o cambiar la visibilidad de cualquier insignia `{id}` sin comprobar titularidad.
  - `HomeController.java:116-118` hace `return "redirect:" + redirect;` sin validar que `redirect` sea una ruta relativa local (`redirect.startsWith("/") && !redirect.startsWith("//")`).
- **Componentes involucrados:** `frontend-service` (`BeneficiariaWebController.java`, `DonanteWebController.java`, `HomeController.java`).
- **Dependencias y bloqueos:** Ninguno.
- **Prioridad:** **P0 — Crítica**
- **Pasos concretos de implementación:**
  1. En `HomeController.procesarLogin` (`línea 116`), validar que `redirect.startsWith("/") && !redirect.startsWith("//")`.
  2. En `BeneficiariaWebController` (`confirmarRecepcion`, `registrarEntregaFallida`, `obtenerComprobante`), verificar que la donación/entrega tenga `entidadAsignada.id == userSession.getEntidadId()` antes de ejecutar la acción; si no coincide, denegar con HTTP 403 / mensaje de error.
  3. En `DonanteWebController` (`obtenerHistorialDonacion`, `alternarVisibilidadInsignia`), verificar que la donación o insignia pertenezca a `userSession.getDonanteId()`.
- **Criterios de aceptación verificables:**
  - Si la Entidad Beneficiaria A envía `POST /beneficiaria/confirmar` con el `donacionId` asignado a la Entidad B, la operación es rechazada sin modificar el estado de la donación.
  - `POST /login?redirect=https://evil.com` redirige al panel local del usuario y nunca al dominio externo.
- **Pruebas necesarias:** Tests de seguridad IDOR y Open Redirect en `FrontendControllersTest`.
- **Definición de terminado (DoD):** Controles de propiedad de recursos y redirección segura verificados por tests.

---

## 7. Documentación Faltante o Desactualizada

### PEND-024 — Sincronización de Diagramas de Arquitectura, DER Físico y Documentación de API con la Implementación Real
- **Clasificación:** [OBLIGATORIO ENUNCIADO]
- **Hallazgo vinculado:** `HAL-023`
- **Requisito del que surge:** Entregables de diseño de las Entregas 1, 2, 3, 4 y 6 (Diagrama de Clases por servicio, Diagrama de Despliegue y Componentes actualizado, Diagrama de Entidad-Relación físico por servicio).
- **Descripción del resultado esperado:** Actualizar los documentos en `Documentacion/Servicios/` y diagramas en `Documentacion/Diagramas/` para que reflejen fielmente el código real y agregar el Diagrama de Entidad-Relación (DER) físico por servicio exigido en la Entrega 4 (pág. 19, Entregable 3).
- **Estado actual y evidencia:** **Incompleto / Desfasado.**
  - No existe en `Documentacion/Diagramas/` el Diagrama de Entidad-Relación (DER) físico de las bases de datos (`donaciones_db`, `incentivos_db`, `notificaciones_db` y esquema documental `logistica_db`).
  - `Documentacion/Servicios/ApiGateway_Keycloak.md` describe filtros y validaciones que aún no estaban en `api-gateway`.
- **Componentes involucrados:** `Documentacion/Diagramas/`, `Documentacion/Servicios/`.
- **Dependencias y bloqueos:** Conviene ejecutarlo al finalizar las correcciones de modelo y seguridad.
- **Prioridad:** **P3 — Baja**
- **Pasos concretos de implementación:**
  1. Crear `Documentacion/Diagramas/DER_Fisico_Por_Servicio.puml` con las tablas, columnas, PKs, FKs e índices reales de los 4 esquemas.
  2. Actualizar `Documentacion/Diagramas/Dominio.puml` y `Arquitectura.puml` tras completar las correcciones del plan.
- **Criterios de aceptación verificables:**
  - Todos los archivos `.puml` compilan sin errores de sintaxis PlantUML y coinciden 1:1 con las entidades y tablas del proyecto.
- **Pruebas necesarias:** Validación sintáctica de archivos PlantUML.
- **Definición de terminado (DoD):** Diagramas y manuales alineados con el código final.

---

## 8. Decisiones que Requieren Confirmación Antes de Implementar

Antes de abordar ciertas tareas en la fase de corrección, existen **5 puntos de definición funcional/técnica** donde el enunciado oficial (`tp_extraido.txt`) y los documentos internos de análisis presentan matices o dejan libertad de diseño al equipo:

| ID Decisión | Tema | Opción A (Conservadora / Enunciado Literal) | Opción B (Extendida / `analisisv2.md`) | Impacto en Código |
| :--- | :--- | :--- | :--- | :--- |
| **DEC-01** | **Exponente de $\chi_1$ en el Polinomio de Compatibilidad Semántica** | Mantener la fórmula lineal $S_{sem} = a_1 \cdot \chi_1 + a_2 \cdot \chi_2 + a_3 \cdot \chi_3$ tal como está actualmente en `CompatibilidadSemanticaStrategy.java:95` (en el PDF pág. 11 el "2" sobre la fórmula proviene de la nota al pie N°2 de Wikipedia sobre Levenshtein). | Elevar $\chi_1$ al cuadrado ($S_{sem} = a_1 \cdot \chi_1^2 + a_2 \cdot \chi_2 + a_3 \cdot \chi_3$) como se transcribió en `analisisv2.md:294`. | 1 línea en `CompatibilidadSemanticaStrategy.java:95` y ajuste de valor esperado en `MatchmakingStrategyTest.java`. *(Se recomienda **Opción A** porque el "2" en el PDF es el superíndice de la nota al pie 2).* |
| **DEC-02** | **¿El Donante puede auto-asignar una donación a una Entidad Beneficiaria al cargarla, o toda donación debe entrar `EN_DEPOSITO` y pasar por Matchmaking + Admin?** | Toda donación ingresa obligatoriamente en estado `EN_DEPOSITO` (Flujo 2 y 3 del enunciado, págs. 7 y 10-12); si el donante elige una entidad sugerida en la web, se guarda como preferencia/sugerencia pero requiere confirmación del Administrador. | Permitir que cuando el donante elige una entidad en `/donante/nueva-donacion`, la donación pase directamente a `ASIGNACION_REALIZADA` asociando automáticamente una necesidad compatible y emitiendo `DonacionRegistradaEvent`. | `DonacionService.registrarCargaSegmentada` (`líneas 110-145`) y `DonanteWebController.registrarDonacion`. |
| **DEC-03** | **Criterio de la misión *"Completitud"* en `incentivos-service`** | Evaluar cantidad de **Categorías** distintas donadas (`Alimentos`, `Vestimenta`, `Mobiliario`, etc.), como dice literalmente la pág. 13: *"Completitud: realizar donaciones de X categorías distintas"*. | Evaluar cantidad de **Subcategorías** distintas donadas (`arroz`, `fideos`, `frazadas`), como está programado hoy en `PerfilDonanteIncentivo.subcategoriasDonadas`. | `PerfilDonanteIncentivo.java` y `DonacionEventConsumer.java` (ambos campos `categoria` y `subcategoria` ya viajan en el evento). |
| **DEC-04** | **Modelado de Representantes en `PersonaJuridica` y `EntidadBeneficiaria`** | Mantener `@ElementCollection` con nombre/email de representantes pero agregando validación y carga en los formularios de registro y edición. | Crear una entidad JPA dedicada `Representante` (con `nombre`, `apellido`, `cargo`, `email`, `telefono`) relacionada `@OneToMany` con `PersonaJuridica` y `EntidadBeneficiaria`. | `PersonaJuridica.java`, `EntidadBeneficiaria.java`, formularios de registro y `DataInitializer.java`. |
| **DEC-05** | **Alcance de puntos Bonus (SSO Social-Login de Entrega 5 y Service Registry Eureka de Entrega 6)** | Priorizar el 100% de los requerimientos obligatorios de las Entregas 1 a 6 sin agregar un 7° microservicio Eureka Server. | Implementar también el flujo OAuth2 Authorization Code con Keycloak (SSO) y/o un módulo `discovery-server` con Eureka. | `pom.xml` raíz, `api-gateway`, `frontend-service` y `docker-compose.yml`. |
