# 03 — Plan Ordenado de Corrección e Implementación (DonaTrack)

> **Propósito del documento:** Establecer una secuencia de ejecución ordenada, incremental y libre de regresiones para resolver la totalidad de los hallazgos (`HAL-001` a `HAL-024`) y pendientes (`PEND-001` a `PEND-024`) identificados en la auditoría.  
> **Criterio de ordenamiento:** Las tareas se agrupan en **8 etapas progresivas** según criticidad (`P0` $\rightarrow$ `P1` $\rightarrow$ `P2` $\rightarrow$ `P3`) y dependencias técnicas entre módulos. No se incluyen estimaciones arbitrarias de horas/días.

---

## Mapa de Dependencias y Paralelismo entre Etapas

```
[Etapa 1: Bloqueos Críticos y Seguridad P0 (CORR-001 a CORR-003)]
       │
       ├──► [Etapa 2: Reglas de Negocio en Backend (CORR-004 a CORR-007)] ──► [Etapa 5: Persistencia e Integridad de Datos (CORR-014 a CORR-016)]
       │                                │
       │                                ▼
       └──► [Etapa 3: Integración Inter-Servicio (CORR-008 a CORR-009)] ────► [Etapa 4: Completitud Funcional Frontend (CORR-010 a CORR-013)]
                                                                                              │
                                                                                              ▼
                                              [Etapa 6: Pruebas Automatizadas y Regresión (CORR-017)]
                                                                                              │
                                                                                              ▼
                                              [Etapa 7: Infraestructura, Docker y Observabilidad E6 (CORR-018)]
                                                                                              │
                                                                                              ▼
                                              [Etapa 8: Documentación, Diagramas DER y Bonus (CORR-019 a CORR-020)]
```

- **Tareas ejecutables en paralelo:**
  - Dentro de la **Etapa 1**, `CORR-001` (enrutamiento y teléfono en registro) y `CORR-002` (endurecimiento de `AuthService` y cierre de IDOR en `frontend-service`) pueden ejecutarse antes de activar el bloqueo estricto en `api-gateway` (`CORR-003`).
  - Las tareas de la **Etapa 2** (`donaciones-service` e `incentivos-service`) son independientes entre sí y pueden abordarse en paralelo.
  - En la **Etapa 4**, las mejoras de vistas (`donante/tracking.html`, `donante/donaciones.html`, `admin/matchmaking.html`, `index.html`) tocan plantillas distintas y no colisionan entre sí.

---

## Etapa 1: Bloqueos Críticos y Riesgos de Seguridad (`P0`)

### `CORR-001` — Reparación del Registro de Personas Jurídicas y Captura de Medios de Contacto
- **Prioridad:** **P0 — Crítica**
- **Hallazgos / Pendientes vinculados:** `HAL-002`, `PEND-013`
- **Objetivo:** Restablecer el funcionamiento del alta de Personas Jurídicas a través del API Gateway y persistir todos los medios de contacto (email y teléfono/WhatsApp) ingresados en `/registro`.
- **Archivos y servicios afectados:**
  - `frontend-service/src/main/java/com/donatrack/frontend/service/ApiClientService.java` (línea `629`)
  - `frontend-service/src/main/java/com/donatrack/frontend/controller/HomeController.java` (líneas `157-250`)
  - `frontend-service/src/main/resources/templates/registro.html` (pestaña Empresa / Jurídica)
  - `api-gateway/src/main/resources/application.yml` (líneas `20-23`)
- **Dependencias:** Ninguna (puede ejecutarse de inmediato).
- **Secuencia de implementación:**
  1. En `ApiClientService.crearDonanteJuridica`, cambiar la ruta `/api/v1/personas-donantes/juridica` por `/api/v1/donantes/juridica`.
  2. En `api-gateway/src/main/resources/application.yml`, agregar `/api/v1/personas-donantes/**` al predicado `donantes-service`.
  3. En `HomeController.registrarDonanteHumana` y `registrarDonanteJuridica`, agregar el teléfono ingresado como segundo elemento de `mediosContacto` y permitir registrar representantes en `PersonaJuridica`.
- **Criterios de aceptación:**
  - `POST /registro/juridica` retorna redirección `302` a `/login` con mensaje de éxito y crea el registro en `personas_juridicas` y `medios_contacto`.
- **Pruebas de validación:** Test `testRegistroJuridicaExitoso` en `FrontendControllersTest`.
- **Riesgos de regresión:** Mínimo; cambio localizado en la URL del cliente y armado del mapa de request.

---

### `CORR-002` — Persistencia de Credenciales, Eliminación de Backdoors Universales y Cierre de IDOR / Open Redirect en `frontend-service`
- **Prioridad:** **P0 — Crítica**
- **Hallazgos / Pendientes vinculados:** `HAL-003`, `PEND-016`, `PEND-023`
- **Objetivo:** Impedir la suplantación de identidad mediante contraseñas maestras o manipulación de IDs de recursos (IDOR) y asegurar que los usuarios registrados conserven su acceso.
- **Archivos y servicios afectados:**
  - `frontend-service/src/main/java/com/donatrack/frontend/security/AuthService.java`
  - `frontend-service/src/main/java/com/donatrack/frontend/controller/HomeController.java`
  - `frontend-service/src/main/java/com/donatrack/frontend/controller/BeneficiariaWebController.java`
  - `frontend-service/src/main/java/com/donatrack/frontend/controller/DonanteWebController.java`
- **Dependencias:** Ninguna.
- **Secuencia de implementación:**
  1. En `AuthService.validarPasswordEntidad` (`líneas 363-365`) y `validarPasswordDonante` (`líneas 382-384`), eliminar la aceptación universal de `"admin123"`, `"donante123"` y `"comedor123"` para cuentas arbitrarias, restringiendo las claves demo únicamente a los emails semilla (`juan.donante@example.com`, `rse@molinos.com.ar`, `esperanza@comedor.org`).
  2. Sincronizar `registrarCredencial` con Keycloak Admin API (o almacenamiento persistente con hash) y agregar soporte de login por `cuit` y `razonSocial` en `AuthService.coincideEmailDonante`.
  3. En `HomeController.procesarLogin` (`línea 116`), validar que `redirect` comience con `"/"` y no con `"//"`.
  4. En `BeneficiariaWebController` (`confirmarRecepcion`, `registrarEntregaFallida`, `obtenerComprobante`), validar que la donación/entrega consultada pertenezca a `userSession.getEntidadId()`.
  5. En `DonanteWebController` (`obtenerHistorialDonacion`, `alternarVisibilidadInsignia`), validar que la donación o insignia pertenezca a `userSession.getDonanteId()`.
- **Criterios de aceptación:**
  - Intentar confirmar o fallar una donación asignada a otra entidad devuelve error de autorización y no altera el estado de la donación.
  - `POST /login?redirect=https://externo.com` redirige al panel interno del rol y nunca al sitio externo.
- **Pruebas de validación:** Nuevos tests de seguridad en `FrontendControllersTest` verificando rechazo de IDOR y Open Redirect.
- **Riesgos de regresión:** Verificar que los 50 tests existentes de `FrontendControllersTest` utilicen las credenciales registradas en cada test o las cuentas demo oficiales.

---

### `CORR-003` — Implementación de Seguridad JWT, Propagación de Headers y Rate Limiting en `api-gateway`
- **Prioridad:** **P0 — Crítica** / **P1 — Alta**
- **Hallazgos / Pendientes vinculados:** `HAL-001`, `HAL-013`, `PEND-022`
- **Objetivo:** Asegurar el punto de entrada de la arquitectura (`api-gateway`) con validación de tokens de autorización y limitación de tasa de peticiones (*Rate Limiting* — Entrega 6 Req 4).
- **Archivos y servicios afectados:**
  - `frontend-service/src/main/java/com/donatrack/frontend/service/ApiClientService.java` (líneas `391, 405, 454, 483, 536, 711`)
  - `frontend-service/src/main/java/com/donatrack/frontend/controller/AdminWebController.java` (línea `145`)
  - `api-gateway/pom.xml`
  - `api-gateway/src/main/java/com/donatrack/gateway/config/SecurityConfig.java` (nuevo)
  - `api-gateway/src/main/java/com/donatrack/gateway/filter/RateLimitingFilter.java` (nuevo)
  - `api-gateway/src/main/resources/application.yml`
- **Dependencias:** Requiere `CORR-002` para asegurar que `frontend-service` disponga siempre de las cabeceras de autenticación en sesión.
- **Secuencia de implementación:**
  1. En `ApiClientService.java` y `AdminWebController.importarCsv`, reemplazar todas las llamadas a `new HttpHeaders()` por `buildAuthHeaders()`.
  2. Crear `RateLimitingFilter.java` en `api-gateway` implementando un *Token Bucket* por IP con límite máximo de entradas en caché (evitando fugas de memoria), cabeceras `X-RateLimit-Remaining` y respuesta HTTP `429 Too Many Requests`.
  3. Configurar `SecurityConfig.java` en `api-gateway` con validación JWT contra Keycloak (`spring.security.oauth2.resourceserver.jwt.issuer-uri`) y reglas de acceso por ruta/método.
- **Criterios de aceptación:**
  - Ráfagas que superen el límite configurado en `api-gateway` reciben `429 Too Many Requests`.
  - El flujo normal de `frontend-service` hacia `api-gateway` incluye siempre las cabeceras de autorización.
- **Pruebas de validación:** Tests en `api-gateway/src/test/java/com/donatrack/gateway/` para `RateLimitingFilter` y `SecurityConfig`.
- **Riesgos de regresión:** Si Keycloak no está levantado durante pruebas locales rápidas sin Docker, permitir modo de contingencia configurable por propiedad (`donatrack.security.jwt-enabled: true/false`).

---

## Etapa 2: Correcciones de Reglas de Negocio y Dominio en Backend (`P1`)

### `CORR-004` — Corrección de Doble Contabilización, Secuencialidad Estricta y Regla de "Hábil Donador" en `incentivos-service`
- **Prioridad:** **P1 — Alta**
- **Hallazgos / Pendientes vinculados:** `HAL-005`, `PEND-018`
- **Objetivo:** Garantizar la exactitud matemática de las métricas del donante y el cumplimiento estricto de la secuencialidad de misiones definida en la Entrega 2.
- **Archivos y servicios afectados:**
  - `incentivos-service/src/main/java/com/donatrack/incentivos/consumer/DonacionEventConsumer.java`
  - `incentivos-service/src/main/java/com/donatrack/incentivos/service/IncentivosService.java`
  - `incentivos-service/src/main/java/com/donatrack/incentivos/scheduler/IncentivosScheduler.java`
  - `donaciones-service/src/main/java/com/donatrack/donaciones/service/DonacionService.java`
- **Dependencias:** Ninguna (independiente de Etapa 1).
- **Secuencia de implementación:**
  1. En `DonacionService.registrarCargaSegmentada` (`líneas 110-145`), asegurar que `DonacionRegistradaEvent` se publique **siempre** que se registra una donación (incluso si se especificó `entidadId`).
  2. En `DonacionEventConsumer.procesarDonacionAsignada` (`líneas 39-65`), al recibir `DonacionAsignadaEvent`, registrar únicamente la organización ayudada (`registrarOrganizacionAyudada`) sin volver a sumar `+1` a `totalDonaciones` ni `+cantidad` a `totalBienesDonados`.
  3. En `IncentivosService.registrarImpactoDonacionCompleto` (`líneas 185-232`), hacer que el bucle sobre las misiones incompletas ejecute `break;` **siempre** tras evaluar la primera misión incompleta en orden secuencial, sin importar si `avanza` fue `true` o `false`.
  4. En la evaluación de `HABIL_DONADOR` (`líneas 204-212`), verificar `if (cantidadBienes >= mision.getObjetivo())` para completar la misión en lugar de acumular donaciones menores.
  5. En `IncentivosScheduler.verificarYResetearRachasMensuales()` (`líneas 106-113`), reiniciar también `perfil.setRachaMesesConsecutivos(0)` y persistir el `perfil`.
- **Criterios de aceptación:**
  - Una donación registrada y posteriormente asignada incrementa `totalDonaciones` en `1` (no en `2`).
  - Ninguna misión de orden $N+1$ puede avanzar mientras la misión de orden $N$ siga incompleta.
- **Pruebas de validación:** Ejecutar y ampliar `IncentivosProgresoTest`, `DonacionEventConsumerTest` e `IncentivosSchedulerTest`.
- **Riesgos de regresión:** Revisar en `IncentivosProgresoTest` si algún test unitario enviaba una donación chica esperando acumulación parcial en `HABIL_DONADOR`.

---

### `CORR-005` — Implementación de Modificación (Update) y Validaciones de Personas Donantes en `donaciones-service`
- **Prioridad:** **P1 — Alta**
- **Hallazgos / Pendientes vinculados:** `HAL-006`, `HAL-017`, `PEND-001`, `PEND-019`
- **Objetivo:** Completar el CRUD de Personas Donantes exigido en la Entrega 2 (pág. 14) y evitar errores HTTP 500 por falta de validaciones en el alta de Persona Jurídica y colisiones de DNI/CUIT.
- **Archivos y servicios afectados:**
  - `donaciones-service/src/main/java/com/donatrack/donaciones/service/DonanteService.java`
  - `donaciones-service/src/main/java/com/donatrack/donaciones/controller/DonanteController.java`
  - `donaciones-service/src/main/java/com/donatrack/donaciones/repository/PersonaDonanteRepository.java`
  - `donaciones-service/src/main/java/com/donatrack/donaciones/exception/GlobalExceptionHandler.java`
- **Dependencias:** Ninguna.
- **Secuencia de implementación:**
  1. En `DonanteService.registrarPersonaJuridica`, validar que `razonSocial`, `cuit`, `tipo`, `rubro` y `mediosContacto` (al menos 1 medio) no sean nulos ni vacíos.
  2. Agregar en `PersonaDonanteRepository` consultas de existencia por `nroDocumento` y `cuit` y validarlas en `DonanteService` lanzando `IllegalArgumentException` antes del `save`.
  3. Implementar `actualizarPersonaHumana(Long id, PersonaHumana datos)` y `actualizarPersonaJuridica(Long id, PersonaJuridica datos)` en `DonanteService` y exponer `PUT /api/v1/donantes/humana/{id}` y `PUT /api/v1/donantes/juridica/{id}` en `DonanteController`.
  4. Agregar manejadores para `MethodArgumentNotValidException` (HTTP 400) y `DataIntegrityViolationException` (HTTP 409) en `GlobalExceptionHandler.java`.
- **Criterios de aceptación:**
  - `PUT /api/v1/donantes/humana/{id}` y `PUT /api/v1/donantes/juridica/{id}` actualizan correctamente los datos y el medio de contacto preferido.
  - Enviar una `PersonaJuridica` sin medios de contacto o con un CUIT repetido devuelve HTTP `400`/`409` y nunca `500`.
- **Pruebas de validación:** Nuevos tests en `DonacionServiceTest` y `DonanteControllerTest`.
- **Riesgos de regresión:** Ninguno.

---

### `CORR-006` — Corrección de Cobertura de Necesidades y Transiciones de Estado en `Donacion`
- **Prioridad:** **P1 — Alta**
- **Hallazgos / Pendientes vinculados:** `HAL-015`, `PEND-017`
- **Objetivo:** Evitar que una necesidad material quede cerrada como satisfecha cuando una donación asignada vence sin entregarse, o quede sin actualizarse cuando se asigna sin `necesidadId`.
- **Archivos y servicios afectados:**
  - `donaciones-service/src/main/java/com/donatrack/donaciones/domain/Donacion.java`
  - `donaciones-service/src/main/java/com/donatrack/donaciones/service/DonacionService.java`
- **Dependencias:** Ninguna.
- **Secuencia de implementación:**
  1. En `Donacion.marcarVencida` (`Donacion.java:212-217`), verificar que no esté ya `VENCIDA` ni `ENTREGADA`; si tenía `necesidadAsignada != null`, invocar `this.necesidadAsignada.revertirCobertura(this.cantidad)` y limpiar `entidadAsignada` y `necesidadAsignada`.
  2. En `Donacion.registrarEntregaFallida` (`Donacion.java:180-189`), restringir la transición a `EN_TRASLADO` o `LISTA_PARA_ENTREGAR`.
  3. En `DonacionService.asignarDonacion` (`DonacionService.java:156-205`), si `necesidadId == null`, buscar en `necesidadRepository` la primera necesidad activa de esa `entidad` con la misma `subcategoria` y vincularla automáticamente.
  4. En `DonacionService.registrarCargaSegmentada` (`línea 80`), incluir `unidadMedida` normalizada en la clave de agrupación.
- **Criterios de aceptación:**
  - Al marcar como `VENCIDA` una donación que tenía una necesidad asignada, la necesidad revierte su `cantidadCubierta` y vuelve a estar `activa = true`.
  - Al adjudicar una donación a una entidad que tiene una necesidad activa para esa subcategoría, `necesidadAsignada` se vincula incluso si el cliente omitió `necesidadId`.
- **Pruebas de validación:** Tests en `DonacionStateTest` y `DonacionServiceTest`.
- **Riesgos de regresión:** Ninguno.

---

### `CORR-007` — Control Anti-Spam en Detección de Inactividad (> 20 días)
- **Prioridad:** **P2 — Media**
- **Hallazgos / Pendientes vinculados:** `HAL-018`, `PEND-019`
- **Objetivo:** Evitar que el scheduler nocturno envíe un correo de inactividad todos los días al mismo donante inactivo.
- **Archivos y servicios afectados:**
  - `donaciones-service/src/main/java/com/donatrack/donaciones/domain/PersonaDonante.java`
  - `donaciones-service/src/main/java/com/donatrack/donaciones/repository/PersonaDonanteRepository.java`
  - `donaciones-service/src/main/java/com/donatrack/donaciones/scheduler/DonacionesScheduler.java`
- **Dependencias:** Ninguna.
- **Secuencia de implementación:**
  1. Agregar el campo `private LocalDateTime ultimaAlertaInactividad;` en `PersonaDonante.java`.
  2. Actualizar `PersonaDonanteRepository.findDonantesInactivosDesde` para exigir `(d.ultimaAlertaInactividad IS NULL OR d.ultimaAlertaInactividad <= :fechaLimite)`.
  3. En `DonacionesScheduler.detectarDonantesInactivos()`, asignar `donante.setUltimaAlertaInactividad(LocalDateTime.now())` y guardar el donante tras encolar el `OutboxEvent`.
- **Criterios de aceptación:**
  - Si `detectarDonantesInactivos()` se ejecuta dos veces seguidas, el mismo donante inactivo solo genera 1 evento de notificación en el ciclo de 20 días.
- **Pruebas de validación:** Test en `DonacionesSchedulerTest`.
- **Riesgos de regresión:** Ninguno.

---

## Etapa 3: Integración entre Servicios (`P1` / `P2`)

### `CORR-008` — Unificación del Flujo de Confirmación y Falla de Entrega entre `frontend-service`, `logistica-service` y `donaciones-service`
- **Prioridad:** **P1 — Alta**
- **Hallazgos / Pendientes vinculados:** `HAL-007`, `PEND-014`
- **Objetivo:** Conectar la confirmación de recepción y el reporte de entrega fallida de la Entidad Beneficiaria directamente con `logistica-service` mediante `idDonacion`, garantizando el disparo de `EntregaConfirmadaEvent` / `EntregaFallidaEvent`, el envío de comprobantes y el avance de misiones en `incentivos-service`.
- **Archivos y servicios afectados:**
  - `logistica-service/src/main/java/com/donatrack/logistica/repository/EntregaRepository.java`
  - `logistica-service/src/main/java/com/donatrack/logistica/service/EntregaService.java` y `EntregaServiceImpl.java`
  - `logistica-service/src/main/java/com/donatrack/logistica/controller/EntregaController.java`
  - `frontend-service/src/main/java/com/donatrack/frontend/service/ApiClientService.java`
  - `frontend-service/src/main/java/com/donatrack/frontend/controller/BeneficiariaWebController.java`
  - `frontend-service/src/main/resources/templates/beneficiaria/confirmar.html`
- **Dependencias:** Ninguna.
- **Secuencia de implementación:**
  1. En `EntregaServiceImpl` y `EntregaController`, exponer `POST /api/v1/logistica/entregas/donacion/{idDonacion}/confirmar` y `POST /api/v1/logistica/entregas/donacion/{idDonacion}/fallida` resolviendo la entrega en MongoDB con `entregaRepository.findByIdDonacion(idDonacion)`.
  2. En `ApiClientService`, consumir dichos endpoints desde `confirmarRecepcion` y `registrarEntregaFallida` cuando `idEntrega` no haya sido provisto explícitamente.
  3. En `templates/beneficiaria/confirmar.html:105`, quitar el `value` por defecto con la URL externa de Unsplash.
- **Criterios de aceptación:**
  - Al confirmar una recepción seleccionando solo la donación en `/beneficiaria/confirmar`, se actualiza la `Entrega` en MongoDB, la `Donacion` en PostgreSQL, se envía el comprobante con patente del camión a donante y beneficiaria, y avanza la misión `DONACIONES_EXITOSAS` en `incentivos-service`.
- **Pruebas de validación:** Tests en `EntregaServiceTest` y `FrontendControllersTest`.
- **Riesgos de regresión:** Si una donación aún no tiene documento `Entrega` en MongoDB (ej. datos semilla o prueba aislada), mantener un fallback controlado en `BeneficiariaWebController` para no romper los tests unitarios de `FrontendControllersTest`.

---

### `CORR-009` — Propagación del Canal de Contacto Preferido del Donante hacia `logistica-service` e `incentivos-service`
- **Prioridad:** **P2 — Media**
- **Hallazgos / Pendientes vinculados:** `HAL-019`, `PEND-015`
- **Objetivo:** Asegurar que los eventos emitidos por `logistica-service` e `incentivos-service` incluyan el canal (`EMAIL`, `SMS`, `WHATSAPP`) y contacto predeterminado del donante.
- **Archivos y servicios afectados:**
  - `common-dtos/src/main/java/com/donatrack/common/events/DonacionRegistradaEvent.java`
  - `donaciones-service/src/main/java/com/donatrack/donaciones/service/DonacionService.java`
  - `incentivos-service/src/main/java/com/donatrack/incentivos/domain/PerfilDonanteIncentivo.java`
  - `incentivos-service/src/main/java/com/donatrack/incentivos/service/IncentivosService.java`
  - `logistica-service/src/main/java/com/donatrack/logistica/domain/Entrega.java`
  - `logistica-service/src/main/java/com/donatrack/logistica/service/PlanificacionRutasServiceImpl.java`
  - `logistica-service/src/main/java/com/donatrack/logistica/service/EntregaServiceImpl.java`
- **Dependencias:** Ninguna.
- **Secuencia de implementación:**
  1. Añadir `canalPreferidoDonante` y `contactoPreferidoDonante` en `DonacionRegistradaEvent` y poblarlos desde `DonacionService`.
  2. Guardar `canalPreferido` y `contactoPreferido` en `PerfilDonanteIncentivo` y pasarlos al construir `MisionCumplidaEvent` y `CategoriaAscendidaEvent`.
  3. Guardar `canalPreferidoDonante` y `contactoPreferidoDonante` en el documento `Entrega` de `logistica-service` al enriquecer datos desde `donaciones-service`, y pasarlos al construir `EntregaConfirmadaEvent` y `EntregaFallidaEvent`.
- **Criterios de aceptación:**
  - Un donante con canal predeterminado `WHATSAPP` o `SMS` recibe por ese canal las notificaciones de misiones, categorías y entregas.
- **Pruebas de validación:** Tests en `IncentivosProgresoTest`, `EntregaServiceTest` y `NotificacionesServiceTest`.
- **Riesgos de regresión:** Mantener constructores existentes en los DTOs de `common-dtos` para no romper código ni tests existentes.

---

## Etapa 4: Completitud Funcional del Frontend SSR (`P1` / `P2`)

### `CORR-010` — Conexión Real del Mapa en Tiempo Real (`/donante/tracking` y `/beneficiaria/tracking`)
- **Prioridad:** **P1 — Alta**
- **Hallazgos / Pendientes vinculados:** `HAL-004`, `PEND-009`
- **Objetivo:** Reemplazar el mapa estático hardcodeado de `/donante/tracking` y el mapa sin filtrar de `/beneficiaria/tracking` por los datos reales de entregas y camiones activos del usuario en sesión, mostrando ubicación y fecha/hora de última actualización.
- **Archivos y servicios afectados:**
  - `frontend-service/src/main/java/com/donatrack/frontend/controller/DonanteWebController.java`
  - `frontend-service/src/main/java/com/donatrack/frontend/controller/BeneficiariaWebController.java`
  - `frontend-service/src/main/resources/templates/donante/tracking.html`
  - `frontend-service/src/main/resources/templates/beneficiaria/tracking.html`
- **Dependencias:** Ninguna.
- **Secuencia de implementación:**
  1. En `DonanteWebController.tracking`, filtrar las entregas/rutas asociadas a las donaciones del donante en sesión y cruzarlas con `flotaEnVivo`.
  2. En `templates/donante/tracking.html`, eliminar las coordenadas hardcodeadas de `Comedor Los Chicos Felices` y renderizar los marcadores desde `/*[[${flotaEnVivo}]]*/` y `/*[[${misDonaciones}]]*/`, mostrando la hora de última actualización tanto en el popup del marcador como en la tabla.
  3. En `BeneficiariaWebController.tracking` y `templates/beneficiaria/tracking.html`, usar las coordenadas de la entidad en sesión y filtrar las entregas dirigidas a su `entidadId`.
- **Criterios de aceptación:**
  - El mapa renderiza dinámicamente los camiones reales devueltos por `logistica-service` con su timestamp de última actualización.
- **Pruebas de validación:** Tests en `FrontendControllersTest` para `/donante/tracking` y `/beneficiaria/tracking`.
- **Riesgos de regresión:** Ninguno.

---

### `CORR-011` — Incorporación de Analítica, Gráficos de Evolución por Período y Comparaciones Mensuales en `/donante/incentivos`
- **Prioridad:** **P1 — Alta**
- **Hallazgos / Pendientes vinculados:** `HAL-012`, `PEND-003`
- **Objetivo:** Mostrar en `/donante/incentivos` los totales históricos, gráficos de evolución por período, comparaciones mensuales, número de organizaciones ayudadas y posición en el ranking mensual.
- **Archivos y servicios afectados:**
  - `incentivos-service/src/main/java/com/donatrack/incentivos/service/IncentivosService.java`
  - `frontend-service/src/main/java/com/donatrack/frontend/service/ApiClientService.java`
  - `frontend-service/src/main/java/com/donatrack/frontend/controller/DonanteWebController.java`
  - `frontend-service/src/main/resources/templates/donante/incentivos.html`
- **Dependencias:** Requiere `CORR-004` (métricas sin doble contabilización).
- **Secuencia de implementación:**
  1. En `IncentivosService.obtenerAnaliticaDonante`, incluir la estructura `evolucionMensual` (lista de períodos con cantidad de donaciones y misiones cumplidas).
  2. En `ApiClientService`, implementar `obtenerAnaliticaDonante(Long donanteId)` consumiendo `GET /api/v1/incentivos/donantes/{id}/analitica`.
  3. En `DonanteWebController.incentivos`, agregar `analitica` al `Model`.
  4. En `templates/donante/incentivos.html`, agregar las tarjetas de analítica (`organizacionesAyudadas`, `posicionRanking`, comparativa mes actual vs. mes anterior) y el gráfico de evolución por período con Chart.js.
- **Criterios de aceptación:**
  - La página `/donante/incentivos` muestra los indicadores de impacto, comparativa mensual, organizaciones ayudadas y el gráfico de evolución.
- **Pruebas de validación:** Tests en `IncentivosControllerTest` y `FrontendControllersTest`.
- **Riesgos de regresión:** Manejar con valores por defecto (`0`) si `incentivos-service` no devuelve datos para un donante recién registrado.

---

### `CORR-012` — Alta de Donantes y Carga Múltiple en Depósito (`/admin`), Visibilidad Condicional en Matchmaking y Filtro por Categoría en Donaciones
- **Prioridad:** **P1 — Alta** / **P2 — Media**
- **Hallazgos / Pendientes vinculados:** `HAL-008`, `HAL-009`, `HAL-010`, `PEND-002`, `PEND-007`, `PEND-008`, `PEND-010`
- **Objetivo:** Completar los requerimientos UI/UX 5, 15 y 17 en las vistas del donante y del administrador.
- **Archivos y servicios afectados:**
  - `frontend-service/src/main/java/com/donatrack/frontend/controller/AdminWebController.java`
  - `frontend-service/src/main/java/com/donatrack/frontend/controller/DonanteWebController.java`
  - `frontend-service/src/main/java/com/donatrack/frontend/service/ApiClientService.java`
  - `frontend-service/src/main/resources/templates/admin/nueva_donacion.html`
  - `frontend-service/src/main/resources/templates/admin/matchmaking.html`
  - `frontend-service/src/main/resources/templates/donante/donaciones.html`
- **Dependencias:** Ninguna.
- **Secuencia de implementación:**
  1. En `templates/admin/matchmaking.html:135`, cambiar `th:if` para mostrar los dos rankings separados **únicamente** cuando `#lists.isEmpty(evaluacion.coincidenciasDestacadas)`, e incluir `<input type="hidden" name="necesidadId" th:value="${item.necesidadId}">` en los formularios de adjudicación (actualizando `AdminWebController.asignarDonacion` y `ApiClientService.asignarDonacion`).
  2. En `DonanteWebController.donaciones`, `ApiClientService.listarDonacionesPaginadas` y `templates/donante/donaciones.html`, añadir el filtro `categoria`.
  3. En `templates/admin/nueva_donacion.html` y `AdminWebController`, agregar modal/formulario para dar de alta un nuevo donante (humano o jurídico) en depósito y permitir agregar múltiples filas de bienes (con `fechaVencimiento` y `descripcionGeneral`) en una misma carga.
- **Criterios de aceptación:**
  - En `/admin/matchmaking`, si existe intersección no se muestran las dos columnas inferiores; si la intersección es vacía, sí se muestran.
  - En `/donante/donaciones`, el donante puede filtrar por `estado`, `categoria` y `subcategoria`.
  - En `/admin/donaciones/nueva`, el administrador puede dar de alta un donante y cargar múltiples bienes en un solo envío.
- **Pruebas de validación:** Tests en `FrontendControllersTest`.
- **Riesgos de regresión:** Conservar compatibilidad en `POST /admin/donaciones/guardar` cuando se envía un único ítem en los tests existentes.

---

### `CORR-013` — Filtrado de Último Mes en Landing Page y Edición de Necesidades/Camiones en UI
- **Prioridad:** **P2 — Media**
- **Hallazgos / Pendientes vinculados:** `HAL-011`, `HAL-021`, `PEND-006`, `PEND-011`
- **Objetivo:** Filtrar las donaciones destacadas de los últimos 30 días en la landing page, corregir enlaces/KPIs públicos y habilitar la edición/cierre de necesidades y camiones desde la interfaz web.
- **Archivos y servicios afectados:**
  - `frontend-service/src/main/java/com/donatrack/frontend/controller/HomeController.java`
  - `frontend-service/src/main/resources/templates/index.html`
  - `frontend-service/src/main/resources/templates/comprobantes.html`
  - `frontend-service/src/main/java/com/donatrack/frontend/controller/BeneficiariaWebController.java`
  - `frontend-service/src/main/resources/templates/beneficiaria/necesidades.html`
  - `frontend-service/src/main/java/com/donatrack/frontend/controller/AdminWebController.java`
  - `frontend-service/src/main/resources/templates/admin/camiones.html`
- **Dependencias:** Ninguna.
- **Secuencia de implementación:**
  1. En `HomeController.index`, filtrar las donaciones cuya fecha sea posterior a `haceUnMes` para `donacionesDestacadas`.
  2. En `templates/index.html:205`, cambiar `href="/donante/tracking"` por `href="/comprobantes"`.
  3. En `BeneficiariaWebController` y `templates/beneficiaria/necesidades.html`, conectar los endpoints de edición y baja de necesidad material con `apiClient.actualizarNecesidad` y `apiClient.eliminarNecesidad`.
  4. En `AdminWebController` y `templates/admin/camiones.html`, agregar acción para editar/cambiar estado de camión.
- **Criterios de aceptación:**
  - Las donaciones destacadas en `/` pertenecen al último mes y sus enlaces son accesibles sin login.
  - La entidad beneficiaria puede editar o cerrar necesidades desde `/beneficiaria/necesidades`.
- **Pruebas de validación:** Tests en `FrontendControllersTest`.
- **Riesgos de regresión:** Ninguno.

---

## Etapa 5: Persistencia, Rendimiento e Integridad de Datos (`P2`)

### `CORR-014` — Persistencia en Base de Datos de los Parámetros de Matchmaking ($a_1, a_2, a_3, \alpha$)
- **Prioridad:** **P2 — Media**
- **Hallazgos / Pendientes vinculados:** `HAL-016`, `PEND-012`
- **Objetivo:** Persistir en PostgreSQL los pesos configurados por el administrador para que no se pierdan al reiniciar `donaciones-service`.
- **Archivos y servicios afectados:**
  - `donaciones-service/src/main/java/com/donatrack/donaciones/domain/ConfiguracionMatchmaking.java` (nuevo)
  - `donaciones-service/src/main/java/com/donatrack/donaciones/repository/ConfiguracionMatchmakingRepository.java` (nuevo)
  - `donaciones-service/src/main/java/com/donatrack/donaciones/matchmaking/CompatibilidadSemanticaStrategy.java`
  - `donaciones-service/src/main/java/com/donatrack/donaciones/matchmaking/PrioridadSubatendidosStrategy.java`
  - `donaciones-service/src/main/java/com/donatrack/donaciones/controller/DonacionController.java`
- **Dependencias:** Ninguna.
- **Secuencia de implementación:**
  1. Crear entidad JPA `ConfiguracionMatchmaking` y su repositorio.
  2. Validar en `PrioridadSubatendidosStrategy.setAlpha` que `alpha > 0`.
  3. Al iniciar la aplicación y al ejecutar `PUT /api/v1/donaciones/matchmaking/config`, sincronizar los valores con la tabla `configuracion_matchmaking`.
- **Criterios de aceptación:**
  - Los cambios realizados en `PUT /api/v1/donaciones/matchmaking/config` quedan guardados en PostgreSQL y se restauran al iniciar el servicio.
- **Pruebas de validación:** Tests en `Fase3MatchmakingTest`.
- **Riesgos de regresión:** Mantener los constructores sin repositorio en `CompatibilidadSemanticaStrategy` y `PrioridadSubatendidosStrategy` para que los tests unitarios existentes sigan funcionando sin cambios.

---

### `CORR-015` — Resiliencia Transaccional por Fila, `tipoDocumento` e Índices SQL en Importador CSV
- **Prioridad:** **P2 — Media**
- **Hallazgos / Pendientes vinculados:** `HAL-020`, `PEND-020`
- **Objetivo:** Persistir `tipoDocumento` en `PersonaHumana`, indexar las búsquedas por email y evitar que una fila con DNI/CUIT duplicado haga rollback de todo el archivo CSV.
- **Archivos y servicios afectados:**
  - `donaciones-service/src/main/java/com/donatrack/donaciones/domain/PersonaHumana.java`
  - `donaciones-service/src/main/java/com/donatrack/donaciones/domain/MedioContacto.java`
  - `donaciones-service/src/main/java/com/donatrack/donaciones/service/CsvDonanteImporterService.java`
- **Dependencias:** Requiere `CORR-005` (consultas de existencia por DNI/CUIT en `PersonaDonanteRepository`).
- **Secuencia de implementación:**
  1. Agregar el campo `private String tipoDocumento = "DNI";` con getter/setter en `PersonaHumana.java` y asignarlo en `CsvDonanteImporterService.java:186`.
  2. Agregar `@Index` sobre `(tipo, valor)` en `MedioContacto.java` y sobre `(status, created_at)` en `OutboxEvent.java`.
  3. En `CsvDonanteImporterService.procesarLinea`, verificar si el `documento` (DNI o CUIT) ya existe en otro donante antes de invocar `donanteRepository.save(...)`, lanzando `IllegalArgumentException` controlada para que se sume a `filasConError` sin corromper la sesión de Hibernate.
- **Criterios de aceptación:**
  - `PersonaHumana` persiste `tipoDocumento`.
  - Un CSV con filas válidas y una fila con DNI repetido guarda todas las filas válidas y reporta la fila errónea en `erroresMuestra`.
- **Pruebas de validación:** Tests en `CsvDonanteImporterTest`.
- **Riesgos de regresión:** Ninguno.

---

### `CORR-016` — Optimización de Consultas en GraphQL (`DonacionSpecification`) y `NecesidadMaterialRepository`
- **Prioridad:** **P2 — Media**
- **Hallazgos / Pendientes vinculados:** `HAL-016`, `HAL-022`, `PEND-021`
- **Objetivo:** Evitar `findAll()` en memoria en el controlador GraphQL y consultas N+1 al evaluar matchmaking.
- **Archivos y servicios afectados:**
  - `donaciones-service/src/main/java/com/donatrack/donaciones/controller/DonacionGraphqlController.java`
  - `donaciones-service/src/main/java/com/donatrack/donaciones/repository/NecesidadMaterialRepository.java`
- **Dependencias:** Ninguna.
- **Secuencia de implementación:**
  1. En `DonacionGraphqlController.donaciones`, usar `donacionRepository.findAll(DonacionSpecification.conFiltros(estadoEnum, null, subcategoria, null, null))` en lugar de filtrar en memoria con `.stream()`.
  2. En `NecesidadMaterialRepository.findPendientesPorSubcategoria`, incluir `JOIN FETCH n.entidadBeneficiaria`.
- **Criterios de aceptación:**
  - La consulta GraphQL `donaciones(estado, subcategoria)` delega el filtrado directamente a la base de datos.
- **Pruebas de validación:** Test unitario/integración de `DonacionGraphqlController`.
- **Riesgos de regresión:** Ninguno.

---

## Etapa 6: Pruebas Automatizadas y Regresión (`P2`)

### `CORR-017` — Ampliación de Pruebas (`@DataJpaTest`, Controladores Faltantes y Desacoplamiento Offline de Georef)
- **Prioridad:** **P2 — Media**
- **Hallazgos / Pendientes vinculados:** `HAL-022`, `PEND-021`
- **Objetivo:** Cubrir con pruebas automatizadas las consultas JPQL de los repositorios, los controladores `DonanteController`, `EntidadBeneficiariaController`, `DonacionGraphqlController`, y los filtros de `api-gateway`, eliminando la dependencia de internet en `NormalizacionDireccionTest`.
- **Archivos y servicios afectados:**
  - `donaciones-service/src/test/java/com/donatrack/donaciones/service/NormalizacionDireccionTest.java`
  - `donaciones-service/src/test/java/com/donatrack/donaciones/repository/DonacionRepositoryIntegrationTest.java` (nuevo)
  - `donaciones-service/src/test/java/com/donatrack/donaciones/controller/DonanteControllerTest.java` (nuevo)
  - `api-gateway/src/test/java/com/donatrack/gateway/GatewayRateLimitAndSecurityTest.java` (nuevo)
- **Dependencias:** Etapas 1 a 5 completadas.
- **Secuencia de implementación:**
  1. Asegurar que `NormalizacionDireccionTest` funcione de manera 100% determinística con y sin conexión a internet.
  2. Incorporar tests de repositorio y de controladores faltantes.
  3. Ejecutar `./mvnw clean test` verificando 0 fallos y 0 errores en los 8 módulos.
- **Criterios de aceptación:**
  - `./mvnw test` finaliza con `BUILD SUCCESS` y cobertura verificada de todos los hallazgos corregidos.
- **Pruebas de validación:** Ejecución completa del reactor Maven.
- **Riesgos de regresión:** Ninguno.

---

## Etapa 7: Infraestructura, Contenedorización y Observabilidad de Entrega 6 (`P1` / `P2`)

### `CORR-018` — Creación de `Dockerfile`s, Stack Prometheus/Grafana/n8n en `docker-compose.yml` y Corrección de `scripts/infra-up.sh`
- **Prioridad:** **P1 — Alta**
- **Hallazgos / Pendientes vinculados:** `HAL-014`, `PEND-004`
- **Objetivo:** Cumplir los requerimientos de contenedorización, monitoreo proactivo (`healthcheck` + `restart`), observabilidad (`Prometheus` + `Grafana`) y automatización (`n8n`) de las Entregas 2 y 6.
- **Archivos y servicios afectados:**
  - `donaciones-service/Dockerfile`, `logistica-service/Dockerfile`, `incentivos-service/Dockerfile`, `notificaciones-service/Dockerfile`, `api-gateway/Dockerfile`, `frontend-service/Dockerfile` (nuevos)
  - `api-gateway/pom.xml` y `api-gateway/src/main/resources/application.yml`
  - `frontend-service/pom.xml` y `frontend-service/src/main/resources/application.yml`
  - `docker/prometheus.yml` (nuevo)
  - `docker/docker-compose.yml`
  - `scripts/infra-up.sh`
- **Dependencias:** Etapas 1 a 6 completadas.
- **Secuencia de implementación:**
  1. Añadir `micrometer-registry-prometheus` en `api-gateway/pom.xml` y `frontend-service/pom.xml` y exponer `prometheus` en Actuator.
  2. Crear los 6 `Dockerfile`s con imagen `eclipse-temurin:21-jre-alpine` y `HEALTHCHECK`.
  3. Crear `docker/prometheus.yml` y agregar los servicios `prometheus`, `grafana`, `n8n` y los microservicios (con `restart: unless-stopped`) en `docker/docker-compose.yml`.
  4. En `scripts/infra-up.sh`, incluir la aplicación de `02-schema-updates.sql`.
- **Criterios de aceptación:**
  - `docker compose -f docker/docker-compose.yml config` es 100% válido.
  - Los 6 servicios exponen `/actuator/health` y `/actuator/prometheus`.
- **Pruebas de validación:** Validación de sintaxis de Docker Compose y chequeo de endpoints Actuator.
- **Riesgos de regresión:** Usar *profiles* en `docker-compose.yml` (o mantener los servicios de infraestructura por defecto) para que `./scripts/infra-up.sh` + `./scripts/services-up.sh` sigan funcionando igual de rápido en desarrollo local.

---

## Etapa 8: Documentación, Diagramas DER y Puntos Bonus Opcionales (`P3`)

### `CORR-019` — Generación del Diagrama de Entidad-Relación (DER) Físico por Servicio y Actualización de Documentación
- **Prioridad:** **P3 — Baja**
- **Hallazgos / Pendientes vinculados:** `HAL-023`, `PEND-024`
- **Objetivo:** Completar el Entregable 3 de la Entrega 4 (DER físico por servicio) y sincronizar los diagramas de clases y arquitectura con el código final.
- **Archivos y servicios afectados:**
  - `Documentacion/Diagramas/DER_Fisico_Por_Servicio.puml` (nuevo)
  - `Documentacion/Diagramas/Dominio.puml`
  - `Documentacion/Diagramas/Arquitectura.puml`
- **Dependencias:** Etapas 1 a 7 completadas.
- **Secuencia de implementación:**
  1. Redactar `Documentacion/Diagramas/DER_Fisico_Por_Servicio.puml` modelando las tablas de `donaciones_db`, `incentivos_db`, `notificaciones_db` y las colecciones de `logistica_db`.
  2. Actualizar `Dominio.puml` y `Arquitectura.puml` con los nuevos campos y filtros implementados.
- **Criterios de aceptación:**
  - Diagramas PlantUML completos y consistentes con el esquema de base de datos final.
- **Pruebas de validación:** Inspección de sintaxis PlantUML.
- **Riesgos de regresión:** Ninguno.

---

### `CORR-020` — (Opcional según `DEC-05`) Flujo SSO OIDC con Social-Login en Keycloak
- **Prioridad:** **P3 — Baja (Bonus)**
- **Hallazgos / Pendientes vinculados:** `HAL-024`, `PEND-005`
- **Objetivo:** Incorporar el botón de inicio de sesión mediante redirección OAuth2/OIDC a Keycloak (con opción de proveedores sociales) en `/login`.
- **Archivos y servicios afectados:**
  - `frontend-service/src/main/java/com/donatrack/frontend/controller/HomeController.java`
  - `frontend-service/src/main/resources/templates/login.html`
  - `docker/keycloak/realm-donatrack.json`
- **Dependencias:** Confirmación de `DEC-05`.
- **Secuencia de implementación:**
  1. Habilitar `standardFlowEnabled: true` y `redirectUris` hacia `http://localhost:8086/*` en `realm-donatrack.json`.
  2. Agregar endpoint de inicio y callback OAuth2 Authorization Code en `HomeController` y botón *"Continuar con SSO (Keycloak / Social Login)"* en `templates/login.html`.
- **Criterios de aceptación:**
  - El usuario puede autenticarse vía redirección OIDC al servidor Keycloak y establecer su `UserSession`.
- **Pruebas de validación:** Test de callback OIDC en `FrontendControllersTest`.
- **Riesgos de regresión:** Mantener intacto el formulario de login directo para no afectar flujos ni pruebas existentes.
