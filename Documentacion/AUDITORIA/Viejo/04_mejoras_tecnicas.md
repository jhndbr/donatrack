# 04 — Propuestas de Mejoras Técnicas No Obligatorias (DonaTrack)

> **Alcance del documento:** Este documento reúne exclusivamente las **mejoras técnicas opcionales** que no constituyen incumplimientos obligatorios del enunciado del Trabajo Práctico Anual, pero que elevarían de forma medible la calidad arquitectónica, el rendimiento, la seguridad en profundidad, la mantenibilidad y la observabilidad del sistema.  
> No se proponen cambios de tecnología arbitrarios ni refactorizaciones cosméticas; cada propuesta responde a una limitación real verificada en el código fuente.

---

## Resumen de Propuestas Técnicas

| ID | Área | Propuesta | Prioridad Sugerida | Recomendación |
| :--- | :--- | :--- | :---: | :--- |
| **MEJ-001** | Arquitectura / API REST | Adopción de DTOs tipados con `@Valid` y respuestas de error `ProblemDetail` (RFC 7807) en lugar de `@RequestBody Map` y exposición de entidades JPA | **P2 — Media** | **Implementar** (los DTOs ya están creados en disco) |
| **MEJ-002** | Rendimiento / JPA | Eliminación de consultas N+1 y cascadas `FetchType.EAGER` en `Donacion` mediante `@EntityGraph` / `JOIN FETCH` | **P2 — Media** | **Implementar** |
| **MEJ-003** | Rendimiento / Matchmaking | Precálculo en lote (*batch query*) de donaciones entregadas en el trimestre para `PrioridadSubatendidosStrategy` | **P2 — Media** | **Implementar** |
| **MEJ-004** | Rendimiento / Memoria | Streaming de `InputStreamResource` en `AdminWebController.importarCsv` y secuencias PostgreSQL para JDBC Batching | **P2 — Media** | **Implementar streaming en frontend; posponer cambio de PKs** |
| **MEJ-005** | Confiabilidad / Mensajería | Extensión del patrón *Transactional Outbox* a `incentivos-service` y `logistica-service` | **P3 — Baja** | **Posponer** (`donaciones-service` ya demuestra el patrón para el Bonus E6) |
| **MEJ-006** | Seguridad en Profundidad | Protección CSRF mediante tokens sincronizadores en formularios Thymeleaf de `frontend-service` | **P2 — Media** | **Implementar o configurar `SameSite=Strict` en cookies de sesión** |
| **MEJ-007** | Base de Datos / Migraciones | Incorporación de versionado de esquemas SQL con Flyway en lugar de `hibernate.ddl-auto: update` | **P3 — Baja** | **Posponer** para no complejizar el entorno de corrección de la cátedra |
| **MEJ-008** | Resiliencia / Comunicación HTTP | Incorporación de *Circuit Breaker* y *Retry* con exponencial backoff (Resilience4j) en llamadas sincrónicas entre servicios | **P3 — Baja** | **Posponer** (actualmente ya existen timeouts explícitos y bloques de contingencia) |

---

## Detalle de Cada Propuesta

### `MEJ-001` — Adopción de los DTOs Tipados Existentes con `@Valid` y Estándar `ProblemDetail` (RFC 7807) en `donaciones-service`
- **Situación actual:**
  - En `donaciones-service/src/main/java/com/donatrack/donaciones/controller/DonacionController.java`, los endpoints `modificarDonacion` (`línea 188`), `desasignarDonacion` (`línea 305`), `actualizarEstado` (`línea 314`) y `actualizarEstadoLote` (`línea 336`) reciben `@RequestBody Map<String, Object>` o `Map<String, String>` y extraen los campos haciendo casteos manuales.
  - Al mismo tiempo, en el paquete `donaciones-service/src/main/java/com/donatrack/donaciones/dto/` ya existen creadas las clases `ModificacionDonacionRequestDto.java`, `DesasignacionRequestDto.java`, `CambioEstadoRequestDto.java`, `AsignacionDonacionRequestDto.java` y `BajaLogicaRequestDto.java` con anotaciones `@NotNull` y `@NotBlank`, pero no se usan en ningún controlador.
  - `GlobalExceptionHandler.java:14-85` construye mapas JSON ad-hoc en vez de usar `org.springframework.http.ProblemDetail` nativo de Spring Boot 3.
- **Problema o limitación:**
  - El uso de `Map<String, Object>` impide que Jakarta Bean Validation (`@Valid`) valide automáticamente los tipos y campos obligatorios antes de entrar al controlador, provocando `NullPointerException` o `ClassCastException` ante JSONs incompletos y dificultando la documentación automática en OpenAPI/Swagger.
- **Evidencia:** `DonacionController.java:187-194, 304-350` y `donaciones-service/src/main/java/com/donatrack/donaciones/dto/*.java`.
- **Alternativa recomendada:**
  - Conectar los DTOs existentes en las firmas de `DonacionController` con `@Valid @RequestBody` y agregar el manejo de `MethodArgumentNotValidException` en `GlobalExceptionHandler`.
- **Beneficios:** Validación declarativa segura, contratos OpenAPI precisos y eliminación de código muerto (los 5 DTOs huérfanos pasan a cumplir su función).
- **Costos y complejidad:** Bajos (las clases DTO y sus tests en `Fase4DtoYMapaTest.java` ya existen).
- **Riesgos y desventajas:** Se debe verificar que los nombres de propiedades JSON enviados por `ApiClientService.java` coincidan exactamente con los nombres de campos de esos DTOs.
- **Prioridad:** **P2 — Media**
- **Motivo para implementarla:** Aprovecha código ya escrito en el repositorio y elimina una fuente de errores 500.

---

### `MEJ-002` — Eliminación de Consultas N+1 y Cascadas `FetchType.EAGER` en `Donacion` y `EntidadBeneficiaria`
- **Situación actual:**
  - `Donacion.java:52-72` define `donante`, `entidadAsignada`, `necesidadAsignada` e `historialEstados` como `FetchType.EAGER`, mientras que `bienes` (`línea 67`) es `FetchType.LAZY`.
  - `EntidadBeneficiaria.java:45-47` define `necesidades` como `FetchType.LAZY` sin `@JsonIgnore`.
- **Problema o limitación:**
  - Cada vez que se invoca `GET /api/v1/donaciones` o `GET /api/v1/donaciones/page`, Jackson serializa cada `Donacion` accediendo a `getBienes()`, lo que ejecuta 1 `SELECT` adicional por cada donación listada ($N+1$ queries). Lo mismo ocurre en `GET /api/v1/entidades` al serializar `getNecesidades()` por cada entidad beneficiaria.
- **Evidencia:** `Donacion.java:52-72`, `EntidadBeneficiaria.java:45-47`, `DonacionController.java:66, 85, 133`, `EntidadBeneficiariaController.java:27`.
- **Alternativa recomendada:**
  - Utilizar `@EntityGraph(attributePaths = {"donante", "entidadAsignada", "necesidadAsignada", "bienes"})` o `@BatchSize(size = 50)` sobre las colecciones `bienes` y `necesidades` (o configurar `spring.jpa.properties.hibernate.default_batch_fetch_size: 50` en `donaciones-service/src/main/resources/application.yml`).
- **Beneficios:** Configurar `hibernate.default_batch_fetch_size: 50` reduce instantáneamente50 consultas N+1 a 1 única consulta `WHERE donacion_id IN (?, ?, ...)` sin romper la serialización JSON ni requerir cambiar todas las firmas de los controladores.
- **Costos y complejidad:** Mínimos (1 línea en `application.yml` + `@EntityGraph` en métodos clave).
- **Riesgos y desventajas:** Ninguno.
- **Prioridad:** **P2 — Media**
- **Motivo para implementarla:** Altísimo retorno de rendimiento con riesgo de regresión prácticamente nulo.

---

### `MEJ-003` — Precálculo en Lote de Donaciones Entregadas en el Trimestre para `PrioridadSubatendidosStrategy`
- **Situación actual:**
  - Durante la evaluación de matchmaking de una donación contra $M$ necesidades candidatas, `PrioridadSubatendidosStrategy.calcularScore` (`PrioridadSubatendidosStrategy.java:53-55`) ejecuta `donacionRepository.countEntregadasRecientesByEntidadId(entidad.getId(), hace90Dias)` de forma individual por cada necesidad evaluada.
- **Problema o limitación:**
  - Si hay 50 donaciones en depósito y 100 necesidades activas, el job nocturno de matchmaking ejecuta $50 \times 100 = 5.000$ consultas `SELECT COUNT(*)` repetidas contra PostgreSQL para las mismas entidades beneficiarias.
- **Evidencia:** `PrioridadSubatendidosStrategy.java:48-60`, `DonacionRepository.java:37-38`.
- **Alternativa recomendada:**
  - Mantener actualizado el campo desnormalizado `EntidadBeneficiaria.donacionesRecibidasTrimestre` (tal como sugiere el Requerimiento detallado 3 de la Entrega 4, pág. 19: *"Se deberán contemplar estrategias de desnormalización cuando resulte necesario para optimizar consultas de lectura"*) o agregar una consulta agrupada `SELECT d.entidadAsignada.id, COUNT(d) FROM Donacion d WHERE d.estado = 'ENTREGADA' AND d.fechaActualizacionEstado >= :desde GROUP BY d.entidadAsignada.id`.
- **Beneficios:** Reduce miles de queries `COUNT` individuales a 1 sola consulta agrupada (y sirve además como ejemplo concreto de desnormalización/optimización de lectura para la defensa de la Entrega 4).
- **Costos y complejidad:** Bajos.
- **Riesgos y desventajas:** Ninguno.
- **Prioridad:** **P2 — Media**
- **Motivo para implementarla:** Aporta un argumento técnico directo para la justificación de diseño relacional y desnormalización de la Entrega 4.

---

### `MEJ-004` — Streaming Multipart en `AdminWebController.importarCsv`
- **Situación actual:**
  - En `frontend-service/src/main/java/com/donatrack/frontend/controller/AdminWebController.java:149`, al reenviar el archivo CSV hacia el backend se ejecuta `new ByteArrayResource(file.getBytes())`.
- **Problema o limitación:**
  - `file.getBytes()` aloja la totalidad del archivo CSV en un arreglo contiguo en el *heap* de `frontend-service` (que corre con `-Xmx256m` en `scripts/services-up.sh:34`).
- **Evidencia:** `AdminWebController.java:148-154`.
- **Alternativa recomendada:**
  - Utilizar `file.getResource()` directamente en el `MultiValueMap` (`body.add("file", file.getResource())`), lo que delega a Spring la transmisión por `InputStream` sin copiar el arreglo completo en memoria.
- **Beneficios:** Menor consumo de memoria RAM en `frontend-service` al importar archivos CSV de +20.000 filas.
- **Costos y complejidad:** Muy bajos (reemplazo de 6 líneas por 1 línea).
- **Riesgos y desventajas:** Ninguno.
- **Prioridad:** **P2 — Media**
- **Motivo para implementarla:** Cambio simple, seguro y alineado con el requisito de importación masiva.

---

### `MEJ-005` — Extensión del Patrón Transactional Outbox a `logistica-service` e `incentivos-service`
- **Situación actual:**
  - `donaciones-service` implementa el patrón *Transactional Outbox* (`OutboxEvent.java` + `OutboxPublisher.java`), cumpliendo el **Bonus 7 de la Entrega 6** (pág. 22).
  - En cambio, `logistica-service` (`LogisticaEventPublisher.java:24-47`) e `incentivos-service` (`IncentivosService.java:344, 395`) publican directamente en RabbitMQ mediante `rabbitTemplate.convertAndSend(...)` dentro del método de servicio.
- **Problema o limitación:**
  - Si RabbitMQ se encuentra caído en el instante en que se confirma una entrega en `logistica-service` o se desbloquea una insignia en `incentivos-service`, la base de datos local confirma el cambio pero el mensaje AMQP puede perderse (dual-write problem).
- **Evidencia:** `LogisticaEventPublisher.java:24-47`, `IncentivosService.java:343-348`.
- **Alternativa recomendada:**
  - Incorporar una tabla/colección `outbox_events` en `incentivos-service` y `logistica-service` análoga a la de `donaciones-service`.
- **Beneficios:** Garantía *at-least-once delivery* uniforme en todos los servicios productores.
- **Costos y complejidad:** Medios (requiere nuevas entidades/colecciones y schedulers de relevo en ambos servicios).
- **Riesgos y desventajas:** Incrementa la cantidad de tablas y tareas programadas activas.
- **Prioridad:** **P3 — Baja**
- **Motivo para posponerla:** El enunciado (Entrega 6 Req 7) plantea el *Transactional Outbox* como un punto Bonus y ya se encuentra implementado y demostrado de forma completa en `donaciones-service`.

---

### `MEJ-006` — Protección contra CSRF mediante Cookie `SameSite=Lax/Strict` y/o Token Sincronizador en `frontend-service`
- **Situación actual:**
  - `frontend-service` utiliza un interceptor MVC propio (`AuthInterceptor.java`) sobre sesiones HTTP sin tokens `_csrf` en los formularios HTML.
- **Problema o limitación:**
  - Sin protección CSRF explícita ni configuración estricta de `SameSite` en la cookie `JSESSIONID`, un navegador antiguo podría enviar la cookie de sesión en peticiones `POST` originadas desde otro sitio.
- **Evidencia:** `frontend-service/src/main/java/com/donatrack/frontend/security/AuthInterceptor.java:10-74`, `frontend-service/src/main/resources/application.yml`.
- **Alternativa recomendada:**
  - Configurar `server.servlet.session.cookie.same-site: lax` y `server.servlet.session.cookie.http-only: true` en `frontend-service/src/main/resources/application.yml` y validar el header `Origin` / `Referer` en peticiones `POST` dentro de `AuthInterceptor`.
- **Beneficios:** Mitiga ataques CSRF Cross-Origin en todos los formularios sin necesidad de modificar los 29 archivos HTML ni romper los tests `mockMvc.perform(post(...))` de `FrontendControllersTest`.
- **Costos y complejidad:** Muy bajos.
- **Riesgos y desventajas:** Ninguno.
- **Prioridad:** **P2 — Media**
- **Motivo para implementarla:** Brinda defensa efectiva en profundidad sin romper la suite de 50 tests MVC existentes.

---

### `MEJ-007` — Migraciones Versionadas de Base de Datos con Flyway
- **Situación actual:**
  - Los servicios relacionales (`donaciones-service`, `incentivos-service`, `notificaciones-service`) utilizan `spring.jpa.hibernate.ddl-auto: update` en sus `application.yml`, complementado con `docker/init-scripts/01-init-databases.sql` y `02-schema-updates.sql`.
- **Problema o limitación:**
  - En entornos productivos de evolución continua, `ddl-auto: update` no elimina columnas obsoletas ni aplica transformaciones de datos complejas.
- **Evidencia:** `donaciones-service/src/main/resources/application.yml:29`, `incentivos-service/src/main/resources/application.yml:16`, `notificaciones-service/src/main/resources/application.yml:15`.
- **Alternativa recomendada:**
  - Incorporar `flyway-core` y `flyway-database-postgresql` con scripts `V1__init.sql` y `dl-auto: validate`.
- **Beneficios:** Reproducibilidad exacta del esquema SQL entre entornos.
- **Costos y complejidad:** Medios-Altos (exige mantener sincronizados los scripts SQL con cualquier ajuste en las entidades JPA y en los tests H2).
- **Riesgos y desventajas:** Un mínimo desajuste de tipos entre PostgreSQL y H2 en los scripts de Flyway puede romper los tests unitarios o el arranque si la base local ya tenía tablas creadas por Hibernate.
- **Prioridad:** **P3 — Baja**
- **Motivo para posponerla:** Para el contexto académico del TP Anual, `ddl-auto: update` junto con `DataInitializer` garantiza que cualquier docente o corrector pueda levantar el proyecto desde cero sin fallos de checksum de Flyway.
