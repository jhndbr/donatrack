# 03. Guía de Finalización: Servicio de Logística (`logistica-service`)

## 1. Resumen de Estado Actual
El módulo [`logistica-service`](file:///home/jhon/Documentos/Github/TP-DDSI/logistica-service/pom.xml) (puerto `8083`, MongoDB `logistica_db`) cumple con la **persistencia políglota documental** exigida en la Entrega 4 y está bien modularizado en 5 servicios de dominio:
* [`FlotaServiceImpl.java`](file:///home/jhon/Documentos/Github/TP-DDSI/logistica-service/src/main/java/com/donatrack/logistica/service/FlotaServiceImpl.java): ABM de camiones con validación de patentes y bloqueo si tienen rutas activas.
* [`PlanificacionRutasServiceImpl.java`](file:///home/jhon/Documentos/Github/TP-DDSI/logistica-service/src/main/java/com/donatrack/logistica/service/PlanificacionRutasServiceImpl.java): Particionamiento en lotes $\le 100$ donaciones y webhook `/api/v1/logistica/rutas/callback`.
* [`RecorridoServiceImpl.java`](file:///home/jhon/Documentos/Github/TP-DDSI/logistica-service/src/main/java/com/donatrack/logistica/service/RecorridoServiceImpl.java): Inicio y fin de ruta, cambio masivo a `EN_TRASLADO` y publicación de `RutaIniciadaEvent`.
* [`TelemetriaServiceImpl.java`](file:///home/jhon/Documentos/Github/TP-DDSI/logistica-service/src/main/java/com/donatrack/logistica/service/TelemetriaServiceImpl.java): Recepción de coordenadas GPS con descarte de paquetes desordenados y endpoint `/flota-en-vivo`.
* [`EntregaServiceImpl.java`](file:///home/jhon/Documentos/Github/TP-DDSI/logistica-service/src/main/java/com/donatrack/logistica/service/EntregaServiceImpl.java): Confirmación con fotos obligatorias, entregas fallidas con justificación, comprobante digital y replanificación.

---

## 2. Cosas que FALLAN (Bugs Críticos a Corregir)

### 2.1. BUG CRÍTICO: `POST /api/v1/logistica/rutas/planificar-lote` NO persiste las rutas cuando actúa el Mock
* **Ubicación**: [`RutaController.java`](file:///home/jhon/Documentos/Github/TP-DDSI/logistica-service/src/main/java/com/donatrack/logistica/controller/RutaController.java#L59-L74) y [`PlanificacionRutasServiceImpl.java`](file:///home/jhon/Documentos/Github/TP-DDSI/logistica-service/src/main/java/com/donatrack/logistica/service/PlanificacionRutasServiceImpl.java#L48-L79).
* **Qué ocurre hoy**:
  En `ejecutarPlanificacionNocturna()` (`PlanificacionRutasServiceImpl.java:226-228`), cuando el `RutaOptimizerClient` devuelve los `PlanificadorCallbackDto` (por ejemplo, cuando actúa `MockVrpOptimizerClient`), el código invoca `procesarCallback(dto)` para persistir las rutas en MongoDB y pasar las donaciones a `LISTA_PARA_ENTREGAR`.
  En cambio, cuando se invoca el endpoint REST `POST /api/v1/logistica/rutas/planificar-lote`, `planificarLotes()` devuelve la lista de `PlanificadorCallbackDto` pero **nunca llama a `procesarCallback(dto)`**. Como resultado, no se guarda ningún documento `Ruta` ni `Entrega` en MongoDB.
* **Cómo corregirlo**:
  En `PlanificacionRutasServiceImpl.planificarLotes()` (línea 71), si el resultado devuelto por `rutaOptimizerClient.solicitarOptimizacionLote(...)` ya contiene `rutasGeneradas` (modo simulación/sincrónico), invocar `procesarCallback(resultado)` antes de agregarlo a la lista de respuestas.

### 2.2. BUG EN PLANIFICACIÓN: Solo envía un único camión en vez de la flota disponible y el Mock inventa direcciones
* **Ubicación**: [`PlanificacionRutasServiceImpl.java`](file:///home/jhon/Documentos/Github/TP-DDSI/logistica-service/src/main/java/com/donatrack/logistica/service/PlanificacionRutasServiceImpl.java#L53-L76) y [`MockVrpOptimizerClient.java`](file:///home/jhon/Documentos/Github/TP-DDSI/logistica-service/src/main/java/com/donatrack/logistica/client/MockVrpOptimizerClient.java#L39-L49).
* **Qué ocurre hoy**:
  1. El enunciado (Entrega 3, pág. 16) dice: *"Este componente recibirá, por ejecución, un conjunto de donaciones en estado Asignación Realizada junto con la información de los camiones disponibles. Devolverá, por cada camión, una lista ordenada de destinos (direcciones de las entidades beneficiarias)"*.
  2. Actualmente `planificarLotes()` toma solo el primer camión (`flotaService.obtenerCamionDisponible()`) y `MockVrpOptimizerClient.java:39-49` ignora los datos reales de las donaciones en `donaciones-service`, inventando `"Entidad Beneficiaria Destino #" + (orden % 5 + 1)` y `"Av. Medrano " + (900 + orden * 15)`.
  3. Además, si un lote devuelve rutas para más de un camión, `rutaRepository.findByLoteId(loteId)` (`RutaRepository.java:18`) lanza `IncorrectResultSizeDataAccessException` porque devuelve `Optional<Ruta>` en lugar de `List<Ruta>`.
* **Cómo corregirlo**:
  1. Cambiar `RutaRepository.findByLoteId(String loteId)` para que retorne `List<Ruta>` y verificar `!rutaRepository.findByLoteId(loteId).isEmpty()` para idempotencia.
  2. En `PlanificacionRutasServiceImpl` / `MockVrpOptimizerClient`, consultar los detalles reales de cada donación (`donacionesServiceClient.obtenerDetalleDonacion(idDonacion)`) para usar el `idEntidad`, `razonSocial`, `direccion`, `latitud`, `longitud`, `emailDonante` y `emailEntidad` reales al construir las paradas ordenadas, y ordenar siempre las paradas con `Comparator.comparingInt(ParadaPropuestaDto::getOrden)`.

### 2.3. BUG EN CUSTODIA DE NO ASIGNADAS: `donaciones_pendientes_lote` se guarda pero jamás se vuelve a planificar
* **Ubicación**: [`PlanificacionRutasServiceImpl.java`](file:///home/jhon/Documentos/Github/TP-DDSI/logistica-service/src/main/java/com/donatrack/logistica/service/PlanificacionRutasServiceImpl.java#L190-L230) y [`DonacionPendienteLoteRepository.java`](file:///home/jhon/Documentos/Github/TP-DDSI/logistica-service/src/main/java/com/donatrack/logistica/repository/DonacionPendienteLoteRepository.java#L11).
* **Qué ocurre hoy**:
  Cuando el planificador devuelve `donacionesSinAsignar`, el callback las persiste en la colección `donaciones_pendientes_lote` con `procesado = false`. Pero `pendienteLoteRepository.findByProcesadoFalse()` **nunca se llama en ninguna parte del proyecto**.
* **Cómo corregirlo**:
  En `PlanificacionRutasServiceImpl.ejecutarPlanificacionNocturna()` (línea 214):
  1. Recuperar también los `DonacionPendienteLote` con `procesado = false`.
  2. Unir sus `idDonacion` (sin duplicados) con las donaciones en estado `ASIGNACION_REALIZADA`.
  3. Una vez planificadas en una ruta dentro de `procesarCallback()`, marcar esos registros de `DonacionPendienteLote` como `procesado = true`.

### 2.4. BUG EN CONFIRMACIÓN DE ENTREGA: No sincroniza `fotoUrl` hacia `donaciones-service` ni envía `idDonante` a `incentivos-service`
* **Ubicación**: [`EntregaServiceImpl.java`](file:///home/jhon/Documentos/Github/TP-DDSI/logistica-service/src/main/java/com/donatrack/logistica/service/EntregaServiceImpl.java#L58-L97) y [`DonacionesServiceClient.java`](file:///home/jhon/Documentos/Github/TP-DDSI/logistica-service/src/main/java/com/donatrack/logistica/client/DonacionesServiceClient.java#L92-L112).
* **Qué ocurre hoy**:
  1. Cuando se confirma una entrega en `logistica-service` con fotos (`request.fotosUrls()`), `DonacionesServiceClient.actualizarEstadoDonacion()` hace `PATCH /api/v1/donaciones/{id}/estado` enviando solo `nuevoEstado`, `justificacion` y `usuario`, pero **omite `fotoUrl`**. Como consecuencia, los `BienDonado` en `donaciones-service` quedan sin foto y la galería/mapa público no muestra la foto de la recepción.
  2. Tampoco marca la parada como completada en el documento `Ruta` (`ruta.completarParada(...)`).
  3. El `EntregaConfirmadaEvent` publicado en la línea 83 no incluye `entrega.getIdDonante()`.
* **Cómo corregirlo**:
  1. Sobrecargar `DonacionesServiceClient.actualizarEstadoDonacion(..., String fotoUrl)` para incluir `"fotoUrl": fotoUrl` en el body del `PATCH` (pasando la primera foto de `request.fotosUrls()`).
  2. Incluir `entrega.getIdDonante()` al instanciar `EntregaConfirmadaEvent`.
  3. Buscar la `Ruta` asociada (`entrega.getIdRuta()`) y marcar la parada correspondiente como completada; si todas las paradas de la ruta están completadas, finalizar la ruta (`ruta.finalizarRecorrido()`).

### 2.5. Colisión de puerto en `application.yml` y URLs hardcodeadas
* **Ubicación**: [`application.yml`](file:///home/jhon/Documentos/Github/TP-DDSI/logistica-service/src/main/resources/application.yml#L30), [`RecorridoServiceImpl.java`](file:///home/jhon/Documentos/Github/TP-DDSI/logistica-service/src/main/java/com/donatrack/logistica/service/RecorridoServiceImpl.java#L92) y [`EntregaServiceImpl.java`](file:///home/jhon/Documentos/Github/TP-DDSI/logistica-service/src/main/java/com/donatrack/logistica/service/EntregaServiceImpl.java#L82).
* **Qué ocurre hoy**:
  * `donatrack.vrp.url` tiene como valor por defecto `http://localhost:8085`, que es el puerto de `notificaciones-service`. Cambiar el default a `http://localhost:8090` (o similar).
  * `linkSeguimiento` (`http://localhost:8086/donante/tracking`) y `comprobanteUrl` (`http://localhost:8086/beneficiaria/notificaciones`) están hardcodeados en el código Java. Deben parametrizarse mediante `@Value("${donatrack.frontend.url:http://localhost:8086}")` y apuntar al comprobante real (`/beneficiaria/comprobante/{idEntrega}`).

---

## 3. Cosas que FALTAN o deben mejorarse en `logistica-service`

1. **Evitar duplicación de documentos `Entrega` tras una Replanificación**:
   * En `PlanificacionRutasServiceImpl.procesarCallback()` (`líneas 165-175`), antes de hacer `new Entrega(...)`, verificar con `entregaRepository.findByIdDonacion(idDonacion)` si ya existe una `Entrega` en estado `PENDIENTE` (replanificada tras un fallo previo) para reutilizarla actualizando su `idRuta`, `idCamion` y `patenteCamion` en vez de duplicar documentos en MongoDB.
2. **Eliminar endpoints duplicados en `RutaController`**:
   * En [`RutaController.java`](file:///home/jhon/Documentos/Github/TP-DDSI/logistica-service/src/main/java/com/donatrack/logistica/controller/RutaController.java#L112-L125) están duplicados `/rutas/entregas/{idEntrega}/confirmar` y `/fallida` que ya existen en `EntregaController`. Mantenerlos solo como delegación o deprecarlos a favor de `/api/v1/logistica/entregas/{id}/...`.
3. **Actuator y Métricas Prometheus (Entrega 6)**:
   * Añadir `spring-boot-starter-actuator` y `micrometer-registry-prometheus` en `logistica-service/pom.xml`.

---

## 4. Checklist de Finalización para `logistica-service`
- [ ] Hacer que `planificarLotes()` procese el callback inmediatamente cuando recibe rutas generadas del optimizador sincrónico/mock.
- [ ] Cambiar `RutaRepository.findByLoteId` a `List<Ruta>`.
- [ ] Incorporar las donaciones de `DonacionPendienteLoteRepository.findByProcesadoFalse()` en la corrida de planificación y marcarlas como procesadas.
- [ ] Poblar datos reales de entidad/dirección/coordenadas desde `donaciones-service` al armar las paradas de la ruta.
- [ ] Enviar `fotoUrl` a `donaciones-service` y `idDonante` en `EntregaConfirmadaEvent` al confirmar entrega.
- [ ] Reemplazar las URLs hardcodeadas (`localhost:8086` y `localhost:8085`) por propiedades en `application.yml`.
- [ ] Añadir `spring-boot-starter-actuator` en `pom.xml`.
