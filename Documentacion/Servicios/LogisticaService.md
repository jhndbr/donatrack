# Especificación Técnica: Microservicio de Logística (`logistica-service`)

El microservicio de **Logística** es responsable de la gestión de la flota de transporte, la agrupación de donaciones en lotes, la interacción asíncrona con el planificador de rutas externo, la telemetría GPS en tiempo real de los camiones y el registro final de entregas.

---

## 0. Flujos Principales Resumidos y Requerimientos Esenciales

### 0.1. Flujos Operativos Clave
1. **Planificación de Lotes hacia Optimizador VRP (CU-37, CU-38)**:
   - Se recuperan las donaciones en estado `ASIGNACION_REALIZADA` y la flota de camiones disponible.
   - **Restricción Mandatoria**: Fragmentación en lotes de **máximo 100 donaciones** por solicitud ($N \le 100$).
   - Despacho asincrónico al ruteador externo y recepción de respuesta mediante webhook de callback (`/rutas/callback`).
   - Persistencia de `Ruta` y `Paradas` secuenciales en MongoDB, actualización en lote de donaciones a `LISTA_PARA_ENTREGAR` en `donaciones-service` y custodia de donaciones no asignadas para la próxima jornada.
2. **Inicio de Recorrido por el Chofer (CU-39, CU-40)**:
   - Chofer visualiza la ruta asignada en su dispositivo móvil y marca el inicio de recorrido.
   - Las entregas pasan a `EN_TRASLADO` (sincronizando con donaciones).
   - Se publica el evento AMQP `ruta.iniciada` conteniendo el enlace interactivo al mapa para notificar a donantes y beneficiarias (CU-61).
3. **Telemetría GPS y Seguimiento en Tiempo Real (CU-41, CU-42, CU-63)**:
   - Dispositivos de campo (GPS vehicular o app del chofer) envían coordenadas periódicamente.
   - Se actualiza la última posición del camión en MongoDB (índice `2dsphere`).
   - El dashboard administrativo y el mapa del frontend muestran el camión avanzando y la antigüedad de la señal.
4. **Confirmación de Entrega con Fotos y Comprobante (CU-43, CU-46)**:
   - Recepción física en la sede de la entidad con carga obligatoria de fotos de los bienes y datos del receptor.
   - Donación pasa a `ENTREGADA` y se emite `entrega.confirmada` a RabbitMQ con enlace al comprobante digital.
5. **Incidencias, Fallos y Replanificación (CU-44, CU-45)**:
   - Registro de "No recibida" o "Entrega fallida" con **justificación obligatoria**.
   - Notificación a donante, entidad y administradores (`entrega.fallida`).
   - El administrador revisa: si la donación regresa físicamente al depósito en buen estado, la entrega se marca como `PENDIENTE` para nueva ruta.

### 0.2. Lo Necesario del Service
* **Persistencia Documental Políglota**: MongoDB 7.0 NoSQL exclusivo (Spring Data MongoDB) con soporte `2dsphere`.
* **Capacidad Máxima de Lote**: Lotes estrictos de 100 elementos hacia el planificador.
* **Productor AMQP**: Publicación de eventos a RabbitMQ (`ruta.iniciada`, `entrega.confirmada`, `entrega.fallida`).
* **Integración REST**: Sincronización bidireccional de estados de donaciones con `donaciones-service`.

---

## 1. Ficha Técnica y Dependencias

* **Puerto HTTP**: `8083` (Expuesto también vía Gateway en `8080/api/v1/logistica/**`)
* **Base de Datos**: MongoDB 7.0 NoSQL Documental (`logistica_db`)
* **ODM**: Spring Data MongoDB con soporte para índices geoespaciales `2dsphere`
* **Mensajería**: AMQP (RabbitMQ) como productor de eventos de transporte
* **Módulos Maven**: Depende de `common-dtos` para contratos de eventos y enumeraciones.

---

## 2. Modelo NoSQL Documental (`logistica_db`)

La persistencia políglota exige que Logística utilice exclusivamente una base de datos documental. La estructura de colecciones en MongoDB se define a continuación:

### 2.1. Colección `camiones`
Almacena las unidades de transporte y su última coordenada GPS conocida:
```json
{
  "_id": "CAMION-01",
  "patente": "AE789XY",
  "choferNombre": "Carlos Gómez",
  "choferTelefono": "+54 11 4455-6677",
  "capacidadKg": 3500.0,
  "volumenMaxM3": 18.5,
  "disponible": true,
  "ubicacionActual": {
    "latitud": -34.603722,
    "longitud": -58.381592,
    "timestamp": "2026-10-06T14:30:00Z"
  }
}
```
* **Índices**:
  - `patente`: Índice único.
  - `ubicacionActual`: Índice Geoespacial `2dsphere` para búsquedas por proximidad geográfica.

### 2.2. Colección `rutas`
Almacena las rutas generadas por el optimizador externo con sus paradas embebidas en orden secuencial:
```json
{
  "_id": "6702c2e0f81d1a2b3c4d5e6f",
  "loteId": "LOTE-BUE-2026-001",
  "camionId": "CAMION-01",
  "estado": "PLANIFICADA", // PLANIFICADA, EN_CURSO, FINALIZADA, CANCELADA
  "distanciaKmTotal": 24.5,
  "tiempoEstimadoMinutos": 45,
  "fechaCreacion": "2026-10-06T14:00:00Z",
  "fechaFin": null,
  "paradas": [
    {
      "orden": 1,
      "direccion": "Av. Corrientes 1234, CABA",
      "ubicacion": { "latitud": -34.6037, "longitud": -58.3816 },
      "tipo": "RECOLECCION",
      "donacionId": 101,
      "completada": true
    },
    {
      "orden": 2,
      "direccion": "Av. Medrano 951, CABA",
      "ubicacion": { "latitud": -34.5985, "longitud": -58.4201 },
      "tipo": "ENTREGA",
      "donacionId": 101,
      "completada": false
    }
  ]
}
```

### 2.3. Colección `entregas`
Registro inmutable de auditoría para cada entrega física completada o fallida:
```json
{
  "_id": "6702c2e0f81d1a2b3c4d5e70",
  "donacionId": 101,
  "entidadBeneficiariaId": 5,
  "receptorNombre": "María Rodríguez",
  "receptorDni": "28.456.789",
  "firmaDigitalUrl": "https://storage.donatrack.org/firmas/ent-101.png",
  "fechaHora": "2026-10-06T15:20:00Z",
  "exitosa": true,
  "justificacionFallo": null
}
```

---

## 3. Dinámica Operativa y Reglas de Negocio

### 3.1. Ruteo en Lotes de Máximo 100 Elementos
* El servicio consulta a `donaciones-service` las donaciones en estado `ASIGNACION_REALIZADA`.
* Las donaciones se particionan en lotes cuyo tamaño **nunca supera los 100 elementos** ($N \le 100$), cumpliendo con la restricción de capacidad del algoritmo del optimizador de rutas (VRP).
* Se despacha la solicitud HTTP POST hacia el motor de optimización externo con un identificador único de lote (`loteId`).

### 3.2. Webhook Callback de Retorno (`/api/v1/logistica/rutas/callback`)
* Dado que el cálculo de optimización de rutas es computacionalmente intensivo, el ruteador opera de forma asincrónica.
* Una vez resuelta la ruta óptima, el ruteador invoca el webhook expuesto por este microservicio.
* El webhook:
  1. Persiste el documento `Ruta` en MongoDB.
  2. Notifica a `donaciones-service` para que actualice en masa el estado de las donaciones involucradas a `LISTA_PARA_ENTREGAR`.
  3. Responde HTTP 200 OK de inmediato.

### 3.3. Monitoreo y Telemetría GPS en Tiempo Real
* Los choferes transmiten periódicamente sus coordenadas geográficas mediante la API móvil.
* El microservicio actualiza la posición del camión en MongoDB, permitiendo consultar el avance sobre la ruta y estimar tiempos de arribo (ETA).

### 3.4. Confirmación de Entrega y Manejo de Fallos
* **Entrega Exitosa**: Se registra la recepción formal con nombre del receptor, DNI y URL de firma digital. La donación pasa a `ENTREGADA` y se publica el evento `entrega.confirmada` a RabbitMQ.
* **Entrega Fallida**: Si no se puede entregar la carga (entidad cerrada, rechazo justificado, etc.), es **estrictamente obligatorio ingresar un motivo o justificación**. La donación pasa a `ENTREGA_FALLIDA` y se emite el evento `entrega.fallida` para alertar al operador logístico.

---

## 4. Catálogo Completo de Endpoints REST

| Método | Ruta | Descripción | Roles Permitidos |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/logistica/camiones` | Listar flota vehicular (filtro activo/inactivo opcional) | `LOGISTICA`, `ADMIN` |
| `POST`| `/api/v1/logistica/camiones` | Alta de camión con validación de patente y dimensiones | `ADMIN`, `LOGISTICA` |
| `GET` | `/api/v1/logistica/camiones/{id}` | Consultar detalle de unidad de transporte | `LOGISTICA`, `ADMIN` |
| `PUT` | `/api/v1/logistica/camiones/{id}` | Modificar dimensiones (bloqueado si tiene rutas activas) | `ADMIN`, `LOGISTICA` |
| `DELETE`| `/api/v1/logistica/camiones/{id}`| Baja lógica auditada (bloqueado si tiene rutas activas) | `ADMIN` |
| `POST`| `/api/v1/logistica/rutas/planificar-lote` | Iniciar empaquetado de lote ($\le 100$) y despacho al optimizador VRP | `LOGISTICA`, `ADMIN` |
| `POST`| `/api/v1/logistica/rutas/callback` | Webhook receptor idempotente de la ruta resuelta | Externo (Optimizer) |
| `GET` | `/api/v1/logistica/rutas` | Consultar rutas activas e historial | `LOGISTICA`, `ADMIN`, `CHOFER` |
| `GET` | `/api/v1/logistica/rutas/{id}` | Consultar detalle de ruta con paradas ordenadas | `LOGISTICA`, `CHOFER` |
| `POST`| `/api/v1/logistica/rutas/{id}/iniciar` | Chofer marca inicio de recorrido (`EN_TRASLADO` + RabbitMQ) | `CHOFER` |
| `POST`| `/api/v1/logistica/rutas/{id}/finalizar`| Chofer marca fin de recorrido | `CHOFER` |
| `POST`| `/api/v1/logistica/telemetria/posicion` | Enviar coordenada GPS (con descarte temporal de desorden) | `CHOFER`, IoT GPS |
| `GET` | `/api/v1/logistica/telemetria/camion/{patente}/ultima` | Consultar última posición registrada de una patente | `CHOFER`, `LOGISTICA` |
| `GET` | `/api/v1/logistica/telemetria/flota-en-vivo` | Dashboard en vivo con coordenadas y antigüedad de señal | `ADMIN`, `LOGISTICA` |
| `GET` | `/api/v1/logistica/entregas/{id}` | Consultar detalle de una entrega específica | `LOGISTICA`, `CHOFER` |
| `GET` | `/api/v1/logistica/entregas/ruta/{idRuta}` | Listar entregas correspondientes a una ruta | `LOGISTICA`, `CHOFER` |
| `POST`| `/api/v1/logistica/entregas/{id}/confirmar` | Confirmar recepción física con fotos obligatorias y firma | `CHOFER`, Beneficiaria |
| `POST`| `/api/v1/logistica/entregas/{id}/fallida` | Registrar entrega fallida con justificación obligatoria | `CHOFER`, Beneficiaria |
| `POST`| `/api/v1/logistica/entregas/{id}/replanificar` | Replanificación administrativa (reingreso a `EN_DEPOSITO`) | `ADMIN`, `LOGISTICA` |
| `GET` | `/api/v1/logistica/entregas/{id}/comprobante` | Consultar comprobante digital estructurado | Donante, Entidad, Admin |

---

## 5. TO-DO Maestro del Servicio (Completado al 100%)

### 5.1. Gestión de Flota de Camiones (CU-36)
- [x] **TODO-LOG-08 (CU-36)**: Invariantes de flota al dar de alta o modificar camiones (patente única con formato válido, dimensiones físicas coherentes: volumen en $m^3 > 0$, altura en metros $> 0$ y peso máximo en $kg > 0$). Implementado en `FlotaServiceImpl` con `CamionRequestDto` y Bean Validation.
- [x] **TODO-LOG-08b (CU-36)**: Bloqueo de baja o modificación de unidades de transporte comprometidas en rutas en estado `PLANIFICADA` o `EN_CURSO` (CU-36 A3 y A4). Implementado en `FlotaServiceImpl` con excepciones `ConflictException`.

### 5.2. Planificación y Proveedor Externo VRP (CU-37, CU-38)
- [x] **TODO-LOG-01 (CU-37)**: Patrón Strategy con `RutaOptimizerClient`, implementando cliente HTTP real (`HttpVrpOptimizerClient`) con timeouts y fallback automático resiliente a simulación local (`ResilientRutaOptimizerClient` y `MockVrpOptimizerClient`).
- [x] **TODO-LOG-02 (CU-38)**: En el webhook callback (`/rutas/callback`), sincronización REST vía `DonacionesServiceClient` (`PUT /api/v1/donaciones/bulk-estado`) para pasar las donaciones planificadas a `LISTA_PARA_ENTREGAR`.
- [x] **TODO-LOG-02b (CU-38)**: Custodia NoSQL en colección `donaciones_pendientes_lote` para donaciones devueltas como no asignadas (`donacionesSinAsignar`) para reincorporación en la siguiente corrida de planificación. Idempotencia ante llamadas repetidas (CU-38 A2).
- [x] **TODO-LOG-09 (CU-37)**: Tarea programada nocturna (`LogisticaScheduler`) que consulta donaciones en `ASIGNACION_REALIZADA` en `donaciones-service` y ejecuta la fragmentación automática en lotes de máximo 100.

### 5.3. Recorridos y Telemetría en Tiempo Real (CU-39 a CU-42, CU-63)
- [x] **TODO-LOG-03 (CU-40)**: Al marcar inicio de ruta en `RecorridoServiceImpl`, sincronización con `donaciones-service` para actualizar en masa las donaciones a `EN_TRASLADO` y emisión de evento AMQP `RutaIniciadaEvent` con enlace al mapa interactivo.
- [x] **TODO-LOG-07 (CU-41)**: Actualización directa de la propiedad `ubicacionActual` en el documento `Camion` (con soporte geoespacial).
- [x] **TODO-LOG-07b (CU-41, FT-08)**: Validación de marcas temporales secuenciales en `Camion.actualizarUbicacion()` para evitar que paquetes GPS desordenados o retrasados de la red móvil sobrescriban una posición más actual (CU-41 A4).
- [x] **TODO-LOG-07c (CU-42, CU-63)**: Endpoint de monitoreo de flota en tiempo real (`/api/v1/logistica/telemetria/flota-en-vivo`) para el dashboard administrativo indicando la última posición y antigüedad de la señal en minutos.

### 5.4. Recepción, Comprobantes y Replanificación (CU-43 a CU-46)
- [x] **TODO-LOG-04 (CU-43)**: En la confirmación de entrega (`EntregaServiceImpl`), validación obligatoria de al menos una fotografía adjunta ($\ge 1$). Sincronización a `ENTREGADA` en `donaciones-service` y emisión de `EntregaConfirmadaEvent`.
- [x] **TODO-LOG-04b (CU-46)**: Generación y consulta de comprobante formal digital de entrega (`GET /api/v1/logistica/entregas/{id}/comprobante`) con receptor, DNI, firma digital, camión, fotos y fecha.
- [x] **TODO-LOG-05 (CU-44)**: En entrega fallida o no recibida, validación estricta de motivo o justificación no vacía (regla del TP), sincronización a `ENTREGA_FALLIDA` en `donaciones-service` y emisión de `EntregaFallidaEvent`.
- [x] **TODO-LOG-06 (CU-45)**: Flujo administrativo de replanificación (`replanificarEntrega`): verificación de que la donación regresó físicamente al depósito en buen estado, transición de la entrega a `PENDIENTE` y reingreso de la donación a `EN_DEPOSITO` en `donaciones-service`.


> en documentacion ❯ cd Documentacion
  …/TP-DDSI/Documentacion on 󰘬 main !?
  ❯ ls
   Configuracion                                                               
  implementation_plan_arquitectura.md
   Diagramas                                                                    implementation_plan_front.md
   Servicios                                                                    Integracion_y_Comunicacion.md
   CasosDeUsos.md                                                               PasosDesarrollo.md
   'Cátedra Diseño de Sistemas de Información - TP Anual 2026 (L, M, M) .pdf'   tp_extraido.txt
  …/TP-DDSI/Documentacion on 󰘬 main !?
  ❯ cd Servicios
  …/TP-DDSI/Documentacion/Servicios on 󰘬 main !?
  ❯ ls
   ApiGateway_Keycloak.md   IncentivosService.md   NotificacionesService.md
   DonacionesService.md     LogisticaService.md
  …/TP-DDSI/Documentacion/Servicios on 󰘬 main !?  esta la info de todo el sistema, pero en serivcio estan
  especificaciones a grandes rasgos,
   pero necesito un relevamiento para qe el service este completo y funcional, que sea algo modular extensible ,
   codigo que siga mejores practicas cumpla con solid etc

○ Thought for 1m 58s (Inspecting the `DonacionController` endpoints to understand how donation data is handled...)
● Explored 2 files (SKILL.md, LogisticaService.md)
● Ran 3 commands (find . -maxdepth 5 · find src -name "*.java" · ls -la ..)
… 32 more actions (24 reads, 8 commands: 2 failed) …
● Explored 2 files (implementation_plan_arquitectura.md, EstadoDonacion.java)
● Edited 1 file (relevamiento_logistica_service.md) (ctrl+o to expand)

▸ Thought for 11s, 435 tokens
  The new [`artifact`](artifact) has been created. The agent is now waiting for user input as `RequestFeedback`...
  He preparado un relevamiento arquitectónico y técnico integral para transformar logistica-service en un
  servicio completamente funcional, modular, extensible y alineado con los principios SOLID y los requerimientos
  de la cátedra de Diseño de Sistemas (UTN.BA).
  Puedes revisar el documento completo en el artefacto:
  👉 **relevamiento_logistica_service.md**
  ──────
  ### Puntos Clave del Relevamiento

  1. Desacoplamiento y SOLID (ISP / SRP / DIP):
      • Reemplazo del monolito LogisticaService y de la lógica de negocio en RutaController por servicios de
      dominio segregados:
          • FlotaService: Invariantes de vehículos, patentes y validación de bloqueo si hay rutas activas
          (CU-36).
          • PlanificacionRutasService: Particionamiento estricto ≤ 100 donaciones, despacho VRP, webhook de
          callback idempotente y custodia de no asignadas (CU-37, CU-38).
          • RecorridoService: Inicio y fin de trayecto, sincronización a EN_TRASLADO con donaciones-service y
          emisión de eventos AMQP (CU-39, CU-40).
          • TelemetriaService: Coordenadas GPS, descarte de paquetes temporales desordenados, actualización en
          Camion (2dsphere) y dashboard de flota en vivo con antigüedad de señal (CU-41, CU-63).
          • EntregaService: Confirmación con validación de fotos obligatorias (≥ 1), justificación mandatoria en
          fallos, generación de comprobante digital y flujo de replanificación administrativa si regresa al
          depósito (CU-43 a CU-46).
  2. Extensibilidad y Resiliencia (OCP / Strategy):
      • Interfaz RutaOptimizerClient con implementación HTTP real (HttpVrpOptimizerClient con backoff
      exponencial) y fallback resiliente (MockVrpOptimizerClient).
      • Cliente inter-servicio desacoplado DonacionesServiceClient usando RestClient de Spring Boot 3.2.
  3. Persistencia NoSQL Optimizada:
      • Modelado documental enriquecido en MongoDB con índices geoespaciales 2dsphere y colección de custodia
      donaciones_pendientes_lote.
  ──────
  ### Decisiones de Diseño para Validar
  Antes de iniciar la codificación, indícame si estás de acuerdo con estos aspectos:
  • Estrategia de Optimización VRP: ¿Deseas que el cliente HTTP externo intente comunicarse con un endpoint
  configurable y, ante timeout/fallo de red, conmute automáticamente al motor de simulación local como fallback
  sin interrumpir la operación?
  • Replanificación (CU-45): Confirmar que cuando una donación regrese físicamente al depósito en buen estado,
  pase la entrega a PENDIENTE y notifique a donaciones-service para reingresarla a EN_DEPOSITO.
  ¿Avanzamos con la implementación de esta arquitectura modular en el código?

────────────────────────────────────────────────────────────
> si mejora el proyecto

○ Thought for 4m 26s (The existing service now requires comprehensive unit tests for new features. Specificall...)
● Explored 1 file (RutaControllerTest.java)
● Edited 6 files (CoordenadaGPS.java, Camion.java, Parada.java, +3)
… 47 more actions (7 reads, 37 edits, 3 commands: 1 failed) …
● Edited 4 files (FlotaServiceTest.java, TelemetriaServiceTest.java, EntregaServiceTest.java, +1)
● Ran (./mvnw test -pl logistica-service) (ctrl+o to expand)

  Ejecutando la suite completa de pruebas unitarias y de integración para validar la nueva arquitectura modular
  de logistica-service...

○ Thought for 1m 2s (The recent implementation of the logistic service features requires updating the document...)
● Explored 1 file (LogisticaService.md)
● Edited 1 file (LogisticaService.md)
● Ran (git status) (ctrl+o to expand)
  Se ha completado el relevamiento y la refactorización arquitectónica integral de logistica-service,
  transformándolo en un microservicio modular, extensible y alineado al 100% con los principios SOLID y las
  especificaciones del TP de la cátedra de Diseño de Sistemas (UTN.BA).
  ──────
  ### Resumen de Mejoras Implementadas
  #### 1. Principios SOLID y Desacoplamiento Modular

  • SRP & ISP: Se desmanteló el monolito previo distribuyendo responsabilidades en 5 servicios de dominio
  especializados e independientes:
      • FlotaService / FlotaServiceImpl: Gestión de flota con validación de patentes e invariantes de bloqueo
      ante rutas activas (CU-36).
      • PlanificacionRutasService / PlanificacionRutasServiceImpl: Particionamiento estricto en lotes de máximo
      100 donaciones, webhook de callback idempotente y custodia de no asignadas (CU-37, CU-38).
      • RecorridoService / RecorridoServiceImpl: Inicio y fin de trayectos, sincronización en lote a EN_TRASLADO
      con donaciones-service y eventos AMQP con enlace al mapa interactivo (CU-39, CU-40).
      • TelemetriaService / TelemetriaServiceImpl: Coordenadas GPS satelitales, descarte de paquetes
      desordenados temporales (CU-41 A4), persistencia 2dsphere y dashboard de flota en vivo con antigüedad de
      señal (CU-41, CU-63).
      • EntregaService / EntregaServiceImpl: Confirmación obligatoria con fotos (≥ 1), justificación estricta en
      fallos, generación de comprobante digital y replanificación física tras regreso al depósito (CU-43 a
      CU-46).
      • LogisticaService: Refactorizado como Fachada (Facade Pattern) manteniendo 100% de compatibilidad hacia
      atrás.
  • OCP & DIP:
      • Strategy Pattern para optimización de rutas: RutaOptimizerClient con implementaciones
      HttpVrpOptimizerClient, MockVrpOptimizerClient y ResilientRutaOptimizerClient con fallback automático ante
      caídas de red.
      • Publicador desacoplado de eventos RabbitMQ: LogisticaEventPublisher.
      • Cliente REST sincrónico: DonacionesServiceClient utilizando RestClient con timeouts controlados.


  #### 2. Modelo NoSQL y Dominio MongoDB

  • CoordenadaGPS: Value Object embebido con fórmula de Haversine para estimación de distancias.
  • Camion: Baja lógica auditada, validación de timestamps desordenados y ubicación actual.
  • DonacionPendienteLote y DonacionPendienteLoteRepository: Custodia documental de donaciones devueltas como no
  asignadas por el ruteador (CU-38 A3).

  #### 3. Controladores REST Delgados y Manejo de Errores RFC 7807

  • GlobalExceptionHandler: Respuestas estándar mediante ProblemDetail con códigos HTTP semánticos (400, 404,
  409, 500).
  • DTOs inmutables con Bean Validation: CamionRequestDto, ConfirmarEntregaRequestDto, EntregaFallidaRequestDto,
  ComprobanteEntregaDto, TelemetriaRequestDto, CamionEnVivoDto.
  • Controladores: CamionController, RutaController, TelemetriaController, EntregaController.
  • Cronjob nocturno conectado: LogisticaScheduler.

  #### 4. Documentación Actualizada

  • Se actualizó la especificación técnica en LogisticaService.md con el catálogo completo de endpoints y todos
  los ítems del TODO maestro marcados como completados.
  ──────
  ### Resultados de la Suite de Pruebas

  Se ejecutaron 28 pruebas automatizadas cubriendo controladores, servicios de dominio, invariantes y casos
  borde:

  • RutaControllerTest: 6 tests pasados.
  • LogisticaBatchRutaTest: 10 tests pasados.
  • FlotaServiceTest: 4 tests pasados.
  • TelemetriaServiceTest: 2 tests pasados.
  • PlanificacionRutasServiceTest: 2 tests pasados.
  • EntregaServiceTest: 4 tests pasados.

    [INFO] Tests run: 28, Failures: 0, Errors: 0, Skipped: 0
    [INFO] BUILD SUCCESS
