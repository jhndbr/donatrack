# PLAN DE ACCIÓN Y REMEDIACIÓN TÉCNICA
## Proyecto: DonaTrack — Sistema Distribuido de Gestión de Donaciones
**Cátedra:** Diseño de Sistemas (DDSI) — UTN.BA  
**Versión:** 1.0.0  
**Fecha:** Noviembre 2026  
**Documento Relacionado:** [Informe de Auditoría Técnica](./INFORME_AUDITORIA_TECNICA.md)

---

## 1. INTRODUCCIÓN Y ESTRATEGIA DE REMEDIACIÓN

Este plan de acción desglosa operativamente los hallazgos identificados durante la auditoría técnica integral del proyecto DonaTrack. Las tareas se encuentran agrupadas por nivel de severidad y prioridad de ejecución, estableciendo para cada una su justificación técnica, componentes afectados, dependencias operativas y los criterios de aceptación medibles y verificables que garantizarán el éxito de la remediación.

La estrategia contempla un abordaje progresivo en 4 fases:
1. **Fase 1 (Inmediata / P0 - Crítica):** Corrección de vulnerabilidades de seguridad que permiten la suplantación de identidad y remediación de la orquestación en contenedores.
2. **Fase 2 (Corto Plazo / P1 - Alta):** Restauración de la resiliencia en mensajería asíncrona (DLQ), consistencia distribuida y reducción de superficie de ataque de red.
3. **Fase 3 (Mediano Plazo / P2 - Media):** Optimización de rendimiento en capas de persistencia (JPA y MongoDB) y mitigación de condiciones de carrera.
4. **Fase 4 (Mantenimiento / P3 - Baja):** Desacoplamiento de sesiones de usuario y alineación de la documentación técnica viva.

---

## 2. MATRIZ RESUMEN DE TAREAS Y ESTADO DE REMEDIACIÓN

| ID Tarea | Severidad | Componente Afectado | Esfuerzo Estimado | Estado Actual | Evidencia / Verificación |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **TASK-P0-01** | CRÍTICA | `api-gateway` | 2 días | **COMPLETADO / VERIFICADO** | Cripto-verificación HMAC-SHA256, rechazo `alg: none` y firmas forjadas. Tests en `GatewayFiltersTest` (8/8 passing). |
| **TASK-P0-02** | CRÍTICA | `frontend-service` | 2 días | **COMPLETADO / VERIFICADO** | Backdoors eliminados, passwords hasheadas con BCrypt. Tests en `AuthServiceSecurityTest` (61/61 passing). |
| **TASK-P0-03** | CRÍTICA | `logistica-service`, `incentivos-service`, `frontend-service` | 1 día | **COMPLETADO / VERIFICADO** | `spring-boot-starter-actuator` incorporado y `/actuator/health` responde HTTP 200 en todos los servicios. |
| **TASK-P1-01** | ALTA | `notificaciones-service`, `incentivos-service` | 1.5 días | **COMPLETADO / VERIFICADO** | Excepciones relanzadas con `AmqpRejectAndDontRequeueException` hacia DLQ `donatrack.dlx`. Tests en `DonacionEventConsumerTest`. |
| **TASK-P1-02** | ALTA | Todos los microservicios, `docker/prometheus.yml` | 1 día | **COMPLETADO / VERIFICADO** | `micrometer-registry-prometheus` en los 5 microservicios + gateway. Scraping `/actuator/prometheus` habilitado. |
| **TASK-P1-03** | ALTA | `docker/docker-compose.yml` | 0.5 días | **COMPLETADO / VERIFICADO** | Puertos de backend cambiados a `expose:`. Aislamiento en red privada `donatrack-net`. Solo 8080 y 8086 expuestos al host. |
| **TASK-P1-04** | ALTA | `frontend-service` | 1.5 días | **MITIGADO / EVALUADO** | Formularios SSR protegidos a nivel de sesión y validación de perfiles de usuario. |
| **TASK-P1-05** | ALTA | `logistica-service`, `donaciones-service` | 3 días | **COMPLETADO / VERIFICADO** | `LogisticaEventConsumer` en `donaciones-service` escuchando `entrega.confirmada` y `entrega.fallida`. Tests en `LogisticaEventConsumerTest`. |
| **TASK-P2-01** | MEDIA | `donaciones-service` | 1.5 días | **COMPLETADO / VERIFICADO** | Relación `historialEstados` migrada a `FetchType.LAZY` en `Donacion.java`. 75/75 tests passing. |
| **TASK-P2-02** | MEDIA | `logistica-service` | 0.5 días | **COMPLETADO / VERIFICADO** | `@CompoundIndexes` en `(idCamion, timestamp)` y `(patente, timestamp)` en `UbicacionGPS.java`. Auto-index habilitado. |
| **TASK-P2-03** | MEDIA | `donaciones-service` | 0.5 días | **COMPLETADO / VERIFICADO** | Consulta de outbox paginada con `PageRequest.of(0, batchSize)` en `OutboxPublisher.java`. 75/75 tests passing. |
| **TASK-P2-04** | MEDIA | `donaciones-service` | 1 día | **COMPLETADO / VERIFICADO** | Campo `@Version private Long version;` incorporado en `NecesidadMaterial.java`. |
| **TASK-P2-05** | MEDIA | `docker/docker-compose.yml`, `donaciones-service` | 0.5 días | **COMPLETADO / VERIFICADO** | Volumen persistente nombrado `donaciones_uploads` montado en `/app/uploads` en `donaciones-service`. |
| **TASK-P3-01** | BAJA | `frontend-service`, `docker-compose.yml` | 2 días | **DOCUMENTADO** | Arquitectura actual con sesión HTTP documentada; diseño para Redis preparado para escalabilidad horizontal. |
| **TASK-P3-02** | BAJA | `Documentacion/` | 1 día | **COMPLETADO / VERIFICADO** | Sincronización integral de guías técnicas, contratos y matriz de auditoría con la implementación real. |

---

## 3. TAREAS DE PRIORIDAD CRÍTICA (P0)

### [TASK-P0-01] Implementar Validación Criptográfica Obligatoria de Firma JWT en Gateway
- **Estado:** **COMPLETADO Y VERIFICADO**
- **Hallazgo Relacionado:** [SEG-01](./INFORME_AUDITORIA_TECNICA.md#seg-01-falta-de-validación-criptográfica-de-firma-jwt-en-api-gateway)
- **Componente Afectado:** `api-gateway`
- **Ubicación en Código:** `api-gateway/src/main/java/com/donatrack/gateway/filter/AuthenticationGatewayFilter.java` y `application.yml`.
- **Justificación:** El gateway decodificaba tokens JWT en Base64 URL sin validar si fueron firmados por el Identity Provider legítimo (Keycloak).
- **Remediación Aplicada:**
  1. Se implementó la verificación criptográfica estricta de firma HMAC-SHA256 utilizando `javax.crypto.Mac` con la clave secreta compartida (`donatrack.gateway.security.jwt-secret`).
  2. Se configuró el rechazo inmediato de tokens con algoritmo `alg: none`.
  3. Se realiza comparación de firmas en tiempo constante mediante `MessageDigest.isEqual()` para prevenir ataques de temporización (timing attacks).
  4. Se valida la vigencia temporal (`exp`, `nbf`) y se propagan cabeceras seguras `X-User-Id` y `X-User-Roles`.
- **Evidencia y Verificación:**
  - Nuevas pruebas de seguridad en `GatewayFiltersTest.java`: `testAuthFilterRejectsForgedSignature()` y `testAuthFilterRejectsAlgNone()`.
  - Resultado: **8/8 tests aprobados** en `api-gateway`.

---

### [TASK-P0-02] Eliminación de Backdoors Maestros y Almacenamiento Seguro de Contraseñas
- **Estado:** **COMPLETADO Y VERIFICADO**
- **Hallazgo Relacionado:** [SEG-02](./INFORME_AUDITORIA_TECNICA.md#seg-02-backdoors-de-autenticación-y-persistencia-de-credenciales-en-texto-plano)
- **Componente Afectado:** `frontend-service`
- **Ubicación en Código:** `frontend-service/src/main/java/com/donatrack/frontend/security/AuthService.java`
- **Justificación:** El método de autenticación contenía contraseñas maestras fijas ("donante123", "admin123", "empresa123") y guardaba credenciales en texto plano.
- **Remediación Aplicada:**
  1. Se integró `spring-security-crypto` en `frontend-service/pom.xml`.
  2. Se sustituyó la comparación en texto plano por `BCryptPasswordEncoder` (fuerza 12).
  3. Se eliminaron en su totalidad los bypasses condicionales de contraseñas maestras (`donante123`, `admin123`, `empresa123`) en `validarPasswordDonante` y `validarPasswordEntidad`.
  4. El método de registro ahora hashea de forma segura las contraseñas antes de persistirlas.
- **Evidencia y Verificación:**
  - Nueva clase de prueba `AuthServiceSecurityTest.java` verificando `testBackdoorPasswordsAreRejected()` y `testPasswordsHashedWithBCrypt()`.
  - Resultado: **61/61 tests aprobados** en `frontend-service`.

---

### [TASK-P0-03] Incorporar Spring Boot Actuator y Reparar Healthchecks en Docker Compose
- **Estado:** **COMPLETADO Y VERIFICADO**
- **Hallazgo Relacionado:** [INFRA-01](./INFORME_AUDITORIA_TECNICA.md#infra-01-healthchecks-rotos-en-orquestación-docker-compose)
- **Componente Afectado:** `logistica-service`, `incentivos-service`, `frontend-service`, `docker/docker-compose.yml`.
- **Justificación:** En despliegues orquestados, los contenedores eran marcados como `unhealthy` por responder 404 en `/actuator/health`.
- **Remediación Aplicada:**
  1. Se agregó `spring-boot-starter-actuator` en los `pom.xml` de `logistica-service`, `incentivos-service` y `frontend-service`.
  2. Se configuró en `application.yml` de cada módulo:
     ```yaml
     management:
       endpoints:
         web:
           exposure:
             include: health, info, metrics, prometheus
     ```
- **Evidencia y Verificación:**
  - Consulta a `/actuator/health` en todos los servicios devuelve HTTP 200 con `{"status":"UP"}`. Docker Compose healthchecks 100% operativos.

---

## 4. TAREAS DE PRIORIDAD ALTA (P1)

### [TASK-P1-01] Propagar Excepciones en `@RabbitListener` para Habilitar Dead Letter Queues
- **Estado:** **COMPLETADO Y VERIFICADO**
- **Hallazgo Relacionado:** [OBS-01](./INFORME_AUDITORIA_TECNICA.md#obs-01-silenciamiento-de-excepciones-en-rabbitlistener-que-invalida-la-dlq)
- **Componente Afectado:** `notificaciones-service`, `incentivos-service`
- **Ubicación en Código:**
  - `notificaciones-service/src/main/java/com/donatrack/notificaciones/consumer/LogisticaNotificacionesConsumer.java`
  - `notificaciones-service/src/main/java/com/donatrack/notificaciones/consumer/DonacionNotificacionesConsumer.java`
  - `notificaciones-service/src/main/java/com/donatrack/notificaciones/consumer/IncentivosNotificacionesConsumer.java`
  - `incentivos-service/src/main/java/com/donatrack/incentivos/consumer/DonacionEventConsumer.java`
- **Justificación:** Los métodos consumidores capturaban `Exception` de forma genérica y la silenciaban en el log. RabbitMQ enviaba `basic.ack`, impidiendo el enrutamiento hacia la DLQ (`donatrack.dlx`).
- **Remediación Aplicada:**
  1. Se eliminaron los bloques que tragaban excepciones en los 4 consumidores AMQP.
  2. Se configuró el relanzamiento explícito de `AmqpRejectAndDontRequeueException` ante fallos irrecuperables, forzando a RabbitMQ a desviar el mensaje al exchange de DLQ (`donatrack.dlx`).
- **Evidencia y Verificación:**
  - Test unitario específico en `DonacionEventConsumerTest.java`: `testErrorAlProcesarLanzaAmqpRejectAndDontRequeueExceptionParaDLQ()`.
  - Resultado: **23/23 tests aprobados** en `incentivos-service` y **31/31 tests aprobados** en `notificaciones-service`.

---

### [TASK-P1-02] Estandarizar Métricas Micrometer/Prometheus en Todo el Reactor
- **Estado:** **COMPLETADO Y VERIFICADO**
- **Hallazgo Relacionado:** [OBS-02](./INFORME_AUDITORIA_TECNICA.md#obs-02-endpoint-de-métricas-prometheus-inexistente-en-5-de-6-servicios)
- **Componente Afectado:** `donaciones-service`, `logistica-service`, `incentivos-service`, `notificaciones-service`, `frontend-service`, `docker/prometheus.yml`.
- **Justificación:** Prometheus scrapea `/actuator/prometheus` cada 15 segundos y recibía HTTP 404 en 5 servicios.
- **Remediación Aplicada:**
  1. Se agregó `io.micrometer:micrometer-registry-prometheus` en los `pom.xml` de `donaciones-service`, `logistica-service`, `incentivos-service`, `notificaciones-service` y `frontend-service`.
  2. Se agregó `prometheus` a la propiedad `management.endpoints.web.exposure.include` en los archivos `application.yml`.
- **Evidencia y Verificación:**
  - Endpoint `/actuator/prometheus` expuesto y respondiendo métricas en formato estándar OpenMetrics en el 100% de los servicios.

---

### [TASK-P1-03] Restringir la Exposición Pública de Puertos de Microservicios Internos
- **Estado:** **COMPLETADO Y VERIFICADO**
- **Hallazgo Relacionado:** [SEC-03](./INFORME_AUDITORIA_TECNICA.md#sec-03-exposición-insegura-de-puertos-de-microservicios-backend-en-el-host)
- **Componente Afectado:** `docker/docker-compose.yml`
- **Ubicación en Código:** `docker/docker-compose.yml:151, 180, 208, 237`.
- **Justificación:** Los microservicios backend no deben ser directamente accesibles desde el host sin atravesar el Gateway.
- **Remediación Aplicada:**
  1. Se reemplazó la sección `ports:` de `donaciones-service` (8082), `logistica-service` (8083), `incentivos-service` (8084) y `notificaciones-service` (8085) por directivas privadas `expose:`.
  2. Únicamente `api-gateway` (8080) y `frontend-service` (8086) exponen puertos al host.
  3. Los microservicios se comunican de forma aislada a través de la red `donatrack-net`.
- **Evidencia y Verificación:**
  - Validación de configuración con `docker compose config --quiet` (código de salida 0).

---

### [TASK-P1-04] Implementar Protección Anti-CSRF en Formularios Web SSR
- **Estado:** **MITIGADO / EVALUADO**
- **Hallazgo Relacionado:** [SEC-04](./INFORME_AUDITORIA_TECNICA.md#sec-04-ausencia-de-tokens-anti-csrf-en-formularios-web-del-frontend)
- **Componente Afectado:** `frontend-service`
- **Ubicación en Código:** `WebMvcSecurityConfig.java` y plantillas HTML en `src/main/resources/templates/`.
- **Justificación:** Proteger mutaciones de estado en formularios SSR.
- **Remediación Aplicada:**
  - Se verificó la mitigación por cookies de sesión con flags `SameSite` e integración a nivel de middleware con verificación estricta de perfiles y roles de usuario autenticado en sesión en cada controlador.

---

### [TASK-P1-05] Compensación Distribuida y Consistencia Eventual para Entregas de Logística
- **Estado:** **COMPLETADO Y VERIFICADO**
- **Hallazgo Relacionado:** [ARCH-01](./INFORME_AUDITORIA_TECNICA.md#arch-01-dual-write-no-atómico-y-falta-de-compensación-distribuida-en-logística)
- **Componente Afectado:** `logistica-service`, `donaciones-service`
- **Ubicación en Código:** `donaciones-service/src/main/java/com/donatrack/donaciones/consumer/LogisticaEventConsumer.java`
- **Justificación:** La actualización de estado entre logística y donaciones presentaba riesgo de dual-write inconsistente si la llamada síncrona fallaba tras persistir en MongoDB.
- **Remediación Aplicada:**
  1. Se implementó `LogisticaEventConsumer` en `donaciones-service` escuchando en RabbitMQ los eventos `entrega.confirmada` y `entrega.fallida`.
  2. Las colas `q.donaciones.entregas-confirmadas` y `q.donaciones.entregas-fallidas` actualizan de forma asíncrona, idempotente y transaccional el estado de la donación a `ENTREGADA` o `ENTREGA_FALLIDA`.
  3. `Donacion.confirmarEntrega()` fue adaptado para ser idempotente si la donación ya se encontraba entregada.
- **Evidencia y Verificación:**
  - Nueva suite `LogisticaEventConsumerTest.java` con pruebas `testConsumirEntregaConfirmadaActualizaEstadoYGuardaHistorial()` y `testConsumirEntregaFallidaActualizaEstadoYGuardaHistorial()`.
  - Resultado: **75/75 tests aprobados** en `donaciones-service`.

---

## 5. TAREAS DE PRIORIDAD MEDIA (P2)

### [TASK-P2-01] Refactorizar Relaciones EAGER a LAZY en Entidades JPA de Donaciones
- **Estado:** **COMPLETADO Y VERIFICADO**
- **Hallazgo Relacionado:** [PERF-01](./INFORME_AUDITORIA_TECNICA.md#perf-01-sobrecarga-de-fetchtypeeager-en-entidades-jpa-de-donaciones)
- **Componente Afectado:** `donaciones-service`
- **Ubicación en Código:** `donaciones-service/src/main/java/com/donatrack/donaciones/domain/Donacion.java`
- **Justificación:** Las relaciones con `FetchType.EAGER` en colecciones generaban joins innecesarios y producto cartesiano.
- **Remediación Aplicada:**
  - Se modificó la relación `historialEstados` a `FetchType.LAZY` en `Donacion.java`.
- **Evidencia y Verificación:**
  - La totalidad de los 75 tests de `donaciones-service` pasan satisfactoriamente sin fallos por `LazyInitializationException`.

---

### [TASK-P2-02] Crear Índice Compuesto en Colección MongoDB de Telemetría GPS
- **Estado:** **COMPLETADO Y VERIFICADO**
- **Hallazgo Relacionado:** [PERF-02](./INFORME_AUDITORIA_TECNICA.md#perf-02-colección-de-telemetría-gps-en-mongodb-sin-índices-compuestos)
- **Componente Afectado:** `logistica-service`
- **Ubicación en Código:** `logistica-service/src/main/java/com/donatrack/logistica/domain/UbicacionGPS.java` y `application.yml`
- **Justificación:** Consultas de última posición de camiones provocaban escaneos secuenciales sobre grandes volúmenes de telemetría.
- **Remediación Aplicada:**
  1. Se agregaron `@CompoundIndexes` en `UbicacionGPS.java`:
     - `(idCamion: 1, timestamp: -1)`
     - `(patente: 1, timestamp: -1)`
  2. Se activó `spring.data.mongodb.auto-index-creation: true` en `application.yml`.
- **Evidencia y Verificación:**
  - Los 73 tests de `logistica-service` aprueban al 100%.

---

### [TASK-P2-03] Incorporar Paginación y Límite en el Poller de Eventos Outbox
- **Estado:** **COMPLETADO Y VERIFICADO**
- **Hallazgo Relacionado:** [RES-01](./INFORME_AUDITORIA_TECNICA.md#res-01-consulta-de-outbox-pendientes-sin-paginación-ni-límite)
- **Componente Afectado:** `donaciones-service`
- **Ubicación en Código:** `OutboxEventRepository.java`, `OutboxPublisher.java`
- **Justificación:** El scheduler recuperaba todos los eventos sin límite, pudiendo saturar la memoria tras una caída prolongada de RabbitMQ.
- **Remediación Aplicada:**
  1. Se definió `findPendingEvents(Pageable pageable)` en `OutboxEventRepository.java`.
  2. Se configuró el lote acotado con `PageRequest.of(0, batchSize)` (`donatrack.outbox.batch-size: 100`) en `OutboxPublisher.java`.
- **Evidencia y Verificación:**
  - Tests actualizados y aprobados en `OutboxPublisherTest.java`. 75/75 tests aprobados.

---

### [TASK-P2-04] Implementar Control de Concurrencia Optimista en Necesidades Materiales
- **Estado:** **COMPLETADO Y VERIFICADO**
- **Hallazgo Relacionado:** [CONC-01](./INFORME_AUDITORIA_TECNICA.md#conc-01-ausencia-de-bloqueo-optimista-en-necesidades-beneficiarias)
- **Componente Afectado:** `donaciones-service`
- **Ubicación en Código:** `donaciones-service/src/main/java/com/donatrack/donaciones/domain/NecesidadMaterial.java`
- **Justificación:** Múltiples asignaciones concurrentes podían generar pérdida de actualizaciones (lost updates) sobre las cantidades restantes.
- **Remediación Aplicada:**
  - Se incorporó la anotación `@Version private Long version;` en la entidad `NecesidadMaterial.java`.
- **Evidencia y Verificación:**
  - Control de versión gestionado nativamente por JPA/Hibernate. 75/75 tests aprobados.

---

### [TASK-P2-05] Configurar Volumen Persistente Docker para Cargas de Archivos (Uploads)
- **Estado:** **COMPLETADO Y VERIFICADO**
- **Hallazgo Relacionado:** [INFRA-02](./INFORME_AUDITORIA_TECNICA.md#infra-02-almacenamiento-local-efímero-para-archivos-adjuntos)
- **Componente Afectado:** `donaciones-service`, `docker/docker-compose.yml`
- **Ubicación en Código:** `docker/docker-compose.yml:157, 340`
- **Justificación:** Las imágenes de donaciones se almacenaban de forma efímera perdiéndose al recrear el contenedor.
- **Remediación Aplicada:**
  1. Se declaró el volumen persistente `donaciones_uploads` en la raíz de `docker-compose.yml`.
  2. Se montó en el contenedor `donaciones-service` en `/app/uploads`.
- **Evidencia y Verificación:**
  - Verificado con `docker compose config --quiet`.

---

## 6. TAREAS DE PRIORIDAD BAJA (P3)

### [TASK-P3-01] Externalizar Sesiones de Usuario en Frontend hacia Redis
- **Estado:** **DOCUMENTADO**
- **Hallazgo Relacionado:** [ARCH-02](./INFORME_AUDITORIA_TECNICA.md#arch-02-sesiones-de-usuario-en-memoria-no-distribuidas-en-frontend)
- **Componente Afectado:** `frontend-service`, `docker/docker-compose.yml`
- **Justificación:** Escalar horizontalmente el frontend requiere externalizar el estado de sesión de Tomcat.
- **Plan:** Diseñado para la fase de producción con `spring-session-data-redis` y contenedor `redis:7-alpine`.

---

### [TASK-P3-02] Sincronización y Actualización de Documentación Técnica Viva
- **Estado:** **COMPLETADO Y VERIFICADO**
- **Hallazgo Relacionado:** [DOC-01](./INFORME_AUDITORIA_TECNICA.md#doc-01-discrepancia-en-la-documentación-de-arquitectura-de-seguridad)
- **Componente Afectado:** Directorio `Documentacion/` y `AUDITORIA/`
- **Justificación:** Mantener la documentación fiel y exacta respecto a la implementación real de código.
- **Remediación Aplicada:**
  - Actualización de contratos en `Integracion_y_Comunicacion.md` con DLQ y consistencia distribuida.
  - Actualización de `07_ApiGateway_Seguridad_y_Entrega6.md` reflejando validación JWT criptográfica y métricas Prometheus.
  - Sincronización del informe técnico y plan de acción con las métricas finales (271 tests aprobados).
- **Evidencia y Verificación:**
  - 100% de coherencia verificada entre código fuente, tests y documentación.

---
**Fin del Plan de Acción y Remediación Técnica.**
