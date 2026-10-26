# Especificación Técnica: Microservicio de Donaciones (`donaciones-service`)

El microservicio de **Donaciones** constituye el núcleo transaccional del sistema **DonaTrack**. Administra el catálogo de donantes (personas humanas y jurídicas), entidades beneficiarias, necesidades materiales, el catálogo de donaciones segmentadas por subcategoría, el motor algorítmico de matchmaking y la publicación transaccional de eventos hacia RabbitMQ.

---

## 0. Flujos Principales Resumidos y Requerimientos Esenciales

### 0.1. Flujos de Negocio Clave
1. **Recepción y Carga Múltiple Segmentada (CU-13, CU-14, CU-15)**:
   - El donante se identifica o registra en depósito (CU-07, CU-08).
   - Se ingresan los bienes heterogéneos con descripción, subcategoría, cantidad, unidad, perecedero/vencimiento, nuevo/usado y foto.
   - **Regla de Oro**: La `subcategoría` es la unidad mínima de asignación. El servicio agrupa y segmenta automáticamente generando una entidad `Donación` independiente por subcategoría (y fecha de vencimiento/uso cuando aplique), todas en estado inicial `EN_DEPOSITO`.
2. **Asignación y Publicación Outbox (CU-34, CU-35)**:
   - El administrador elige la entidad beneficiaria final basándose en el matchmaking.
   - Se valida el estado `EN_DEPOSITO` y coincidencia estricta de subcategoría.
   - Transiciona a `ASIGNACION_REALIZADA`, incrementa la `cantidadCubierta` en la necesidad material y persiste el evento `DonacionAsignadaEvent` en `outbox_events` bajo la misma transacción de PostgreSQL.
   - El publisher asincrónico envía el evento a RabbitMQ (`donacion.asignada`) para notificar a la entidad y al donante.
3. **Ciclo de Vida de Estados (CU-19, CU-20, CU-21)**:
   - `EN_DEPOSITO` $\rightarrow$ `ASIGNACION_REALIZADA` $\rightarrow$ `LISTA_PARA_ENTREGAR` $\rightarrow$ `EN_TRASLADO` $\rightarrow$ `ENTREGADA`.
   - Excepciones: `ENTREGA_FALLIDA` (con justificación obligatoria) $\rightarrow$ reingreso a `EN_DEPOSITO`; o `VENCIDA` por scheduler/administrador.
4. **Gestión y Baja Lógica de Actores (CU-07..CU-12, CU-22..CU-25)**:
   - Alta de donantes con correo obligatorio y medios predeterminados.
   - Baja lógica (`activo = false`) tanto para donantes como para entidades, bloqueando la acción si hay donaciones en curso hacia o desde ellos.

### 0.2. Lo Necesario del Service
* **Persistencia Transaccional ACID**: PostgreSQL con Spring Data JPA y Hibernate 6.
* **Garantía At-Least-Once**: Patrón Transactional Outbox sin dos fases (2PC).
* **Control de Concurrencia**: Prevención de doble asignación concurrente (FT-09).
* **Streaming CSV**: Lectura bufferizada en bloques de 500 para soportar >20.000 filas con bajo consumo de memoria.

---

## 1. Ficha Técnica y Dependencias

* **Puerto HTTP**: `8082` (Expuesto también vía Gateway en `8080/api/v1/donaciones/**`)
* **Base de Datos**: PostgreSQL 16 (`donaciones_db`)
* **ORM**: Spring Data JPA / Hibernate 6
* **Mensajería**: AMQP (RabbitMQ) con patrón *Transactional Outbox*
* **Módulos Maven**: Depende de `common-dtos` para contratos de eventos y enumeraciones.

---

## 2. Modelo Relacional de Base de Datos (`donaciones_db`)

```sql
-- Donantes y Medios de Contacto (Estrategia JOINED para herencia)
CREATE TABLE personas_donantes (
    id BIGSERIAL PRIMARY KEY,
    fecha_registro TIMESTAMP NOT NULL,
    ultima_actividad TIMESTAMP NOT NULL,
    tipo_donante VARCHAR(31) NOT NULL
);

CREATE TABLE personas_humanas (
    id BIGINT PRIMARY KEY REFERENCES personas_donantes(id),
    nombre VARCHAR(100) NOT NULL,
    apellido VARCHAR(100) NOT NULL,
    edad INTEGER,
    nro_documento VARCHAR(20) NOT NULL UNIQUE,
    genero VARCHAR(20),
    direccion VARCHAR(255)
);

CREATE TABLE personas_juridicas (
    id BIGINT PRIMARY KEY REFERENCES personas_donantes(id),
    razon_social VARCHAR(150) NOT NULL,
    cuit VARCHAR(20) NOT NULL UNIQUE,
    tipo VARCHAR(50),
    rubro VARCHAR(100)
);

CREATE TABLE representantes_juridicos (
    persona_juridica_id BIGINT REFERENCES personas_juridicas(id),
    representante VARCHAR(150) NOT NULL
);

CREATE TABLE medios_contacto (
    id BIGSERIAL PRIMARY KEY,
    persona_donante_id BIGINT REFERENCES personas_donantes(id),
    tipo VARCHAR(20) NOT NULL, -- EMAIL, SMS, WHATSAPP
    valor VARCHAR(150) NOT NULL,
    es_predeterminado BOOLEAN DEFAULT FALSE
);

-- Entidades Beneficiarias y Necesidades
CREATE TABLE entidades_beneficiarias (
    id BIGSERIAL PRIMARY KEY,
    razon_social VARCHAR(150) NOT NULL,
    direccion VARCHAR(255) NOT NULL,
    telefono VARCHAR(50),
    donaciones_recibidas_trimestre INTEGER DEFAULT 0
);

CREATE TABLE emails_representantes_beneficiaria (
    entidad_id BIGINT REFERENCES entidades_beneficiarias(id),
    email VARCHAR(150) NOT NULL
);

CREATE TABLE necesidades_materiales (
    id BIGSERIAL PRIMARY KEY,
    entidad_id BIGINT REFERENCES entidades_beneficiarias(id),
    subcategoria VARCHAR(100) NOT NULL,
    descripcion TEXT,
    tipo VARCHAR(20) NOT NULL, -- EXTRAORDINARIA, RECURRENTE
    cantidad_solicitada NUMERIC(10,2) NOT NULL,
    cantidad_cubierta NUMERIC(10,2) DEFAULT 0,
    unidad_medida VARCHAR(30) NOT NULL,
    periodo VARCHAR(20)
);

-- Donaciones y Bienes (Segmentación por Subcategoría)
CREATE TABLE donaciones (
    id BIGSERIAL PRIMARY KEY,
    donante_id BIGINT REFERENCES personas_donantes(id),
    entidad_beneficiaria_id BIGINT REFERENCES entidades_beneficiarias(id),
    subcategoria VARCHAR(100) NOT NULL,
    cantidad NUMERIC(10,2) NOT NULL,
    unidad_medida VARCHAR(30) NOT NULL,
    es_usado BOOLEAN DEFAULT FALSE,
    fecha_vencimiento DATE,
    estado VARCHAR(30) NOT NULL, -- EN_DEPOSITO, ASIGNACION_REALIZADA, etc.
    motivo_rechazo_o_fallo TEXT,
    fecha_carga TIMESTAMP NOT NULL,
    fecha_asignacion TIMESTAMP,
    fecha_entrega TIMESTAMP
);

CREATE TABLE bienes_donados (
    id BIGSERIAL PRIMARY KEY,
    donacion_id BIGINT REFERENCES donaciones(id),
    nombre VARCHAR(150) NOT NULL,
    subcategoria VARCHAR(100) NOT NULL,
    cantidad NUMERIC(10,2) NOT NULL,
    unidad_medida VARCHAR(30) NOT NULL,
    volumen_m3 NUMERIC(8,4),
    peso_kg NUMERIC(8,2)
);

-- Tabla de Eventos Outbox (Garantía At-Least-Once sin dos fases)
CREATE TABLE outbox_events (
    id BIGSERIAL PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    processed BOOLEAN DEFAULT FALSE,
    processed_at TIMESTAMP
);

CREATE INDEX idx_outbox_unprocessed ON outbox_events(processed, created_at);
```

---

## 3. Reglas de Dominio y Algoritmos Mandatorios

### 3.1. Segmentación Automática por Subcategoría
Cuando un donante registra una carga múltiple de bienes variados (ej. alimentos, medicamentos e indumentaria), el servicio no genera un único paquete heterogéneo. Agrupa los bienes por `subcategoria` y genera una entidad `Donacion` independiente por cada grupo con estado inicial `EN_DEPOSITO`. Cada donación resultante tiene su propia unidad mínima de asignación.

### 3.2. Motor de Matchmaking Extensible (Strategy Pattern)
El motor evalúa la afinidad entre una donación y las necesidades abiertas de todas las entidades beneficiarias registradas.

#### Componente 1: Compatibilidad Semántica
Calcula el grado de coincidencia mediante la combinación ponderada de tres factores:
1. **Similitud Textual ($\chi_1$)**:
   $$\chi_1 = 1 - \frac{\text{distanciaLevenshtein}(\text{subcat}_{\text{donacion}}, \text{subcat}_{\text{necesidad}})}{\max(\text{len}(\text{subcat}_{\text{donacion}}), \text{len}(\text{subcat}_{\text{necesidad}}))}$$
2. **Ratio de Cobertura de Volumen/Cantidad ($\chi_2$)**:
   $$\chi_2 = \min\left(1.0, \frac{\text{cantidad}_{\text{donacion}}}{\text{cantidadSolicitada} - \text{cantidadCubierta}}\right)$$
3. **Ponderación de Urgencia ($\chi_3$)**:
   * Si la necesidad es `EXTRAORDINARIA`: $\chi_3 = 1.5$
   * Si la necesidad es `RECURRENTE`: $\chi_3 = 1.0$

$$\text{PuntajeBase} = (0.4 \cdot \chi_1) + (0.3 \cdot \chi_2) + (0.3 \cdot \chi_3)$$

#### Componente 2: Decorador de Prioridad a Subatendidos
Para evitar que las entidades de mayor renombre monopolicen las donaciones, el resultado base se ajusta inversamente según la cantidad de donaciones recibidas durante el último trimestre ($N$):
$$S_{\text{sub}} = \frac{1}{1 + \alpha \cdot N} \quad (\text{con } \alpha = 0.1)$$
$$\text{ScoreFinal} = \text{PuntajeBase} \times S_{\text{sub}}$$

### 3.3. Importación Masiva por Streaming CSV (>20.000 Filas)
Para importar donaciones desde archivos CSV de gran escala sin desbordar la memoria heap de la JVM:
* Se lee el flujo línea por línea con `BufferedReader`.
* Se agrupan registros en memoria en bloques de `batch_size = 500`.
* Se invoca `entityManager.flush()` y `entityManager.clear()` al final de cada bloque liberando el contexto de persistencia de Hibernate de inmediato.

### 3.4. Patrón Transactional Outbox
Cuando una donación se asigna a una entidad beneficiaria dentro de un método anotado con `@Transactional`:
1. El estado de la `Donacion` cambia a `ASIGNACION_REALIZADA`.
2. Se incrementa la `cantidadCubierta` en la `NecesidadMaterial`.
3. Se serializa el `DonacionAsignadaEvent` en formato JSON y se persiste en la tabla `outbox_events` con `processed = false`.
4. Si la base de datos confirma el *commit*, un scheduler en segundo plano (`OutboxPublisherScheduler`) lee los registros pendientes y los publica en el exchange `donatrack.events` con routing key `donacion.asignada`, marcándolos luego como procesados.

### 3.5. Tareas Programadas (Schedulers)
* **Detección de Donantes Inactivos**: Corre periódicamente para identificar donantes cuya `ultimaActividad` supere los 90 días, disparando notificaciones de reactivación.
* **Control de Caducidad de Alimentos/Medicamentos**: Corre diariamente a medianoche marcando en estado `VENCIDA` aquellas donaciones en depósito cuya `fechaVencimiento <= hoy`.

---

## 4. Catálogo de Endpoints REST

| Método | Ruta | Descripción | Roles Permitidos |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/donaciones/saludo` | Saludo verificador de la Entrega 1 | Público |
| `GET` | `/api/v1/donaciones` | Listar donaciones con filtro multicriterio (`estado`, `subcategoria`, `donanteId`, fechas) | `ADMIN`, `OPERADOR` |
| `GET` | `/api/v1/donaciones/{id}` | Obtener detalle de una donación | Autenticado |
| `POST`| `/api/v1/donaciones/carga-segmentada` | Alta de donación múltiple segmentada por subcategoría | `DONANTE`, `ADMIN` |
| `POST`| `/api/v1/donaciones/carga-segmentada/dto` | Alta tipada con Bean Validation (`CargaSegmentadaRequestDto`) | `DONANTE`, `ADMIN` |
| `POST`| `/api/v1/donaciones/importar-csv` | Importación masiva por streaming de CSV (con reporte de discrepancias) | `ADMIN` |
| `GET` | `/api/v1/donaciones/{id}/matchmaking` | Consulta afinidad algorítmica con entidades | `OPERADOR`, `ADMIN` |
| `POST`| `/api/v1/donaciones/{id}/asignar` | Confirmar asignación a necesidad material de entidad | `OPERADOR`, `ADMIN` |
| `POST`| `/api/v1/donaciones/{id}/desasignar` | Reversión administrativa de asignación retornando a `EN_DEPOSITO` | `OPERADOR`, `ADMIN` |
| `PUT` | `/api/v1/donaciones/{id}` | Modificar bienes y cantidades (restringido a `EN_DEPOSITO`) | `OPERADOR`, `ADMIN` |
| `PUT` | `/api/v1/donaciones/{id}/estado` | Cambio de estado con justificación obligatoria y auditoría | `OPERADOR`, `LOGISTICA` |
| `PUT` | `/api/v1/donaciones/bulk-estado` | Actualización masiva de estado para lotes logísticos | `LOGISTICA` |
| `GET` | `/api/v1/donaciones/publico/mapa` | Consulta pública de donaciones entregadas para mapa interactivo | Público |
| `POST`| `/api/v1/donantes/humanos` | Registrar persona donante humana | Público / `ADMIN` |
| `POST`| `/api/v1/donantes/juridicos` | Registrar persona donante jurídica | Público / `ADMIN` |
| `POST`| `/api/v1/donantes/{id}/baja` | Baja lógica de donante (validando que no tenga donaciones activas) | `ADMIN` |
| `POST`| `/api/v1/beneficiarias` | Registrar entidad beneficiaria | `ADMIN` |
| `POST`| `/api/v1/beneficiarias/{id}/baja` | Baja lógica de entidad (validando que no tenga donaciones activas) | `ADMIN` |
| `POST`| `/api/v1/beneficiarias/{id}/necesidades` | Cargar necesidad material de una entidad | `BENEFICIARIA`, `ADMIN` |
| `PUT` | `/api/v1/beneficiarias/necesidades/{necesidadId}/cancelar` | Cancelación formal de necesidad material | `BENEFICIARIA`, `ADMIN` |

---

## 5. TO-DO Maestro del Servicio (Gaps y Funcionalidades Faltantes según Enunciado y Casos de Uso)

### 5.1. Gestión de Donaciones y Trazabilidad (CU-13 a CU-21)
- [x] **TODO-DON-01 (CU-21)**: Implementar tabla y entidad relacional `HistorialEstadoDonacion` para trazabilidad completa (auditar `fechaHora`, `estadoAnterior`, `estadoNuevo`, `usuarioResponsable`, y `justificacion` obligatoria en cancelaciones o fallos). *Implementado en `HistorialEstadoDonacion.java` y orquestado en `Donacion.transicionarA()` y `DonacionService`.*
- [x] **TODO-DON-02 (CU-13, CU-14)**: Validaciones estrictas en la carga de bienes:
  - Cantidad > 0 y unidad de medida no vacía.
  - Para mobiliario y vestimenta: obligatoriedad de consignar `esUsado` (nuevo vs usado).
  - Para bienes perecederos: obligatoriedad de `fechaVencimiento` y validación de fecha futura (> hoy).
  *Implementado en validaciones de dominio `BienDonado.validarConsistencia()` y Bean Validation en `BienInputDto.java`.*
- [x] **TODO-DON-03 (CU-34, CU-35)**: En `asignarDonacion()`, asociar la `NecesidadMaterial` receptora concreta, incrementar su `cantidadCubierta` y marcarla como satisfecha cuando corresponda. *Implementado con relación bidireccional y auto-cierre al 100% en `NecesidadMaterial.registrarCobertura()`.*
- [x] **TODO-DON-04 (CU-34, FT-09)**: Control de concurrencia: si dos operadores intentan asignar la misma donación simultáneamente, abortar con error de conflicto (`DonacionYaAsignadaException`). *Implementado con anotación `@Version` (optimistic locking) y validación de estado `EN_DEPOSITO` en `DonacionService.asignarDonacion()`.*
- [x] **TODO-DON-05 (CU-18)**: Implementar endpoint y método de desasignación administrativa (`desasignarDonacion(id, motivo)`) que revierta la necesidad y retorne la donación a `EN_DEPOSITO`. *Implementado en `DonacionService.desasignarDonacion()`, `NecesidadMaterial.revertirCobertura()` y endpoint `POST /api/v1/donaciones/{id}/desasignar`.*
- [x] **TODO-DON-06 (CU-18)**: Política de edición de donaciones: permitir modificar bienes únicamente si el estado es `EN_DEPOSITO`; bloquear si ya está en circuito logístico o entregada. *Implementado con método `Donacion.esEditable()`, excepción `EstadoInvalidoException` y endpoint `PUT /api/v1/donaciones/{id}`.*
- [x] **TODO-DON-07 (CU-16, CU-17)**: Implementar consulta y filtrado multicriterio (por listado de estados, subcategorías, fechas y donante) usando JPA Specifications. *Implementado en `DonacionSpecification.java` y `DonacionService.filtrarDonaciones()`.*
- [x] **TODO-DON-10 (CU-03, CU-04)**: Endpoint público para visualizar mapa de donaciones entregadas con marcadores, fotos y detalles sin requerir autenticación. *Implementado en `DonacionRepository.findDonacionesEntregadasPublicas()` y endpoint `GET /api/v1/donaciones/publico/mapa` retornando `DonacionMapaPublicoDto`.*

### 5.2. Matchmaking y Algoritmos de Asignación (CU-30 a CU-33)
- [x] **TODO-MATCH-01 (CU-31)**: Parametrización dinámica de pesos ($a_1, a_2, a_3$) desde configuración externa con validación de $\sum a_i = 1.0$. *Implementado en `CompatibilidadSemanticaStrategy.setPesos()` con validación estricta de tolerancia $\pm 0.001$.*
- [x] **TODO-MATCH-02 (CU-32)**: En `PrioridadSubatendidosStrategy`, calcular las donaciones del último trimestre consultando el historial dinámico de entregas confirmadas en los últimos 90 días hacia la entidad. *Implementado mediante `DonacionRepository.countEntregadasRecientesByEntidadId()`.*
- [x] **TODO-MATCH-03 (CU-30)**: Proceso Batch asincrónico nocturno (`@Scheduled` en horario de baja carga) para precalcular propuestas de afinidad sobre todas las donaciones en depósito. *Implementado en `DonacionesScheduler.ejecutarMatchmakingBatchNocturno()`.*
- [x] **TODO-MATCH-04 (OCP)**: Registro extensible de estrategias (`MatchmakingStrategyRegistry`) para incorporar futuros algoritmos (ej. cercanía geográfica) sin alterar el core. *Implementado con descubrimiento automático de beans Spring de tipo `MatchmakingStrategy` y métodos `registrar()` / `obtenerEstrategia()`.*

### 5.3. Importación Masiva CSV (CU-12)
- [x] **TODO-CSV-01 (CU-12)**: Para nuevos donantes creados durante el streaming, emitir eventos outbox transaccionales de alta. *Integrado con el ciclo de vida del donante y `outbox_events`.*
- [x] **TODO-CSV-02 (CU-12)**: Registro detallado de errores y filas inválidas para generación y descarga de reporte de rechazos por el administrador. *Implementado en `CsvDonanteImporterService.ErrorImportacion` con reporte de fila, línea original y causa de rechazo en `ResultadoImportacion.getErroresDetalle()`.*
- [x] **TODO-CSV-03 (CU-12)**: Procesamiento asincrónico desacoplado con streaming `BufferedReader` en bloques de 500 filas (`flush()` y `clear()`). *Implementado en `CsvDonanteImporterService.importarDonantesYDonaciones()`.*

### 5.4. Personas Donantes y Entidades Beneficiarias (CU-07..CU-11, CU-22..CU-29)
- [x] **TODO-DTE-01 / TODO-BEN-01 (CU-11, CU-25)**: Implementar baja lógica estricta (`activo = false`, `fechaBaja`, `motivoBaja`) tanto para donantes como para entidades, bloqueando la baja si existen donaciones o entregas activas pendientes. *Implementado en `DonanteService.darDeBajaDonante()` y `EntidadBeneficiariaService.darDeBajaEntidad()` con validación preventiva de donaciones activas.*
- [x] **TODO-DTE-03 (CU-07)**: Disparar evento de bienvenida tras el alta manual de donantes en depósito. *Integrado con el registro y medios de contacto predeterminados.*
- [x] **TODO-BEN-02 (CU-26)**: Validar obligatoriedad del período (SEMANAL/MENSUAL) para necesidades recurrentes. *Implementado en `EntidadBeneficiariaService.registrarNecesidad()` con validación de `necesidad.getPeriodo()`.*
- [x] **TODO-BEN-04 (CU-29)**: Implementar cierre o cancelación formal de necesidades materiales con justificación. *Implementado en `NecesidadMaterial.cancelar()` y endpoint `PUT /api/v1/beneficiarias/necesidades/{necesidadId}/cancelar`.*



[avance con agy]
…/TP-DDSI/Documentacion/Servicios on 󰘬 main !?
 ❯ ls
  ApiGateway_Keycloak.md   IncentivosService.md   NotificacionesService.md
  DonacionesService.md     LogisticaService.md
 …/TP-DDSI/Documentacion/Servicios on 󰘬 main !?
 ❯  en documentacion esta la info de todo el sistema, pero en serivcio estan especificaciones a grandes rasgos,
 pero necesito un relevamiento para qe el service este completo y funcional, que sea algo modular extensible ,
 codigo que siga mejores practicas cumpla con solid etc
○ Thought for 2m 9s (Absence of existing artifacts suggests a need to generate a foundational specification do...)
● Explored 1 file (SKILL.md)
● Ran 2 commands (+1 · ls -la /home/jhon/Documentos/Github/TP-DDSI/donaciones-service)
… 27 more actions (21 reads, 6 commands: 1 failed) …
● Ran (ls -la /home/jhon/.gemini/antigravity-cli/brain/3ebb4739-b769-49cf-9aa2-e46c4ca5d026 2>/dev/null || ech...)
● Edited 1 file (relevamiento_donaciones_service.md) (ctrl+o to expand)

▸ Thought for 5s, 308 tokens
 Artifact created. I'm focusing on the feedback request embedded in the metadata. The key action item is to hi...
 He completado el relevamiento integral, análisis de brechas (Gap Analysis) y diseño arquitectónico para
 donaciones-service.

 Puedes consultar el documento detallado en el artefacto:
 📄 **relevamiento_donaciones_service.md**
 ──────
 ### Decisiones Clave y Puntos a Definir
 1. Control de Concurrencia en Asignaciones (FT-09):
     • Se propone bloqueo optimista mediante @Version en Donacion para evitar colisiones sin bloquear tablas en
     PostgreSQL. ¿Prefieres bloqueo optimista o bloqueo pesimista (PESSIMISTIC_WRITE) al invocar
     asignarDonacion?
 2. Capa de Transferencia de Datos (DTOs):
     • Actualmente los endpoints en DonacionController exponen directamente entidades JPA. Se diseñaron Records
     inmutables con Bean Validation (@Valid, @NotNull, @Positive). ¿Avanzamos con el reemplazo sistemático
     hacia DTOs en todos los controladores?
 3. Estrategia de Extensibilidad (OCP):
     • Se definió el componente MatchmakingStrategyRegistry para registrar dinámicamente estrategias de
     afinidad (CompatibilidadSemanticaStrategy, PrioridadSubatendidosStrategy y futuras adiciones) y desacoplar
     sus pesos hacia application.yml.
 ¿Deseas que iniciemos con la Fase 1 del roadmap (Refuerzo del modelo de dominio, concurrencia y auditoría) o
 prefieres ajustar alguna decisión técnica antes de proceder?

────────────────────────────────────────────────────────────
> incia Fase 1

○ Thought for 1m 18s (Adding domain validation to `BienDonado.java` is needed to ensure data integrity. Implem...)
● Explored 1 file (DonacionStateTest.java)
● Ran (grep -rn "RegistroHistorialEstado" src/)
… 14 more actions (8 reads, 6 edits) …
● Edited 1 file (BienDonado.java)
● Ran (../mvnw test) (ctrl+o to expand)

 I have initiated the test execution suite to verify the Phase 1 domain model changes. Waiting for the build
 and test results.

○ Thought for 16.3s (Unit tests are the next logical step to ensure Phase 1 functionality. The initial test wi...)
● Checked 1 task (task-102)
● Edited 1 file (Fase1DominioTest.java)
● Ran (../mvnw test) (ctrl+o to expand)

 Running all unit and integration tests including the new Fase1DominioTest suite. Waiting for completion.

● Checked 1 task (task-109) (ctrl+o to expand)

▸ Thought for 7s, 750 tokens
 The build was successful, with all tests passing. The first phase focused on implementing an audit trail usin...
 La Fase 1: Refuerzo del Modelo de Dominio, Concurrencia y Auditoría ha sido completada exitosamente.
 ### Cambios Implementados
 1. Auditoría e Historial de Estados (CU-21):
     • Se creó la entidad HistorialEstadoDonacion mapeada como @OneToMany en Donacion, auditando fechaHora,
     estadoAnterior, nuevoEstado, usuarioResponsable y justificacion.
 2. Control de Concurrencia Optimista (FT-09):
     • Se incorporó @Version private Long version; en Donacion para prevenir doble asignación simultánea.
 3. Vinculación con Necesidad y Desasignación (CU-18, CU-34):
     • Se añadió el atributo necesidadAsignada en Donacion.
     • Se agregaron los métodos de negocio asignarA(entidad, necesidad, usuario) y desasignar(motivo, usuario),
     junto con esEditable() (solo en EN_DEPOSITO).
 4. Baja Lógica y Reactivación de Actores (CU-11, CU-25):
     • Atributos activo, fechaBaja y motivoBaja incorporados con métodos darDeBaja(motivo) y reactivar() en
     PersonaDonante y EntidadBeneficiaria.
 5. Ciclo de Vida de Necesidades (CU-26, CU-29):
     • En NecesidadMaterial: métodos revertirCobertura(cantidad), cancelar(motivo) y cierre automático al
     cubrirse el 100%.
 6. Validación de Consistencia de Bienes (CU-14):
     • En BienDonado: validación de cantidades positivas, subcategoría obligatoria y fechas futuras en
     perecederos.
 7. Verificación y Pruebas Unitarias:
     • Se incorporó la suite Fase1DominioTest. Todos los 41 tests pasan exitosamente.
 ──────
 ¿Avanzamos con la Fase 2 (Lógica de Servicios, Desasignación, Edición Restringida, Baja Lógica Validada y JPA
 Specifications multicriterio)?

────────────────────────────────────────────────────────────
> avanza con la fase 2

○ Thought for 5m 52s (The `asignarDonacion` and `actualizarEstado` methods in `DonacionController.java` requir...)
● Explored 1 file (GlobalExceptionHandler.java)
● Edited 2 files (DonacionYaAsignadaException.java, GlobalExceptionHandler.java)
… 29 more actions (15 reads, 13 edits, 1 command) …
● Edited 1 file (DonacionController.java)
● Ran (../mvnw test) (ctrl+o to expand)

 Running all tests to verify Phase 2 business logic and service enhancements. Waiting for build and test
 completion.

● Checked 1 task (task-183) (ctrl+o to expand)

▸ Thought for 12s, 1.0k tokens
 Enabled multi-criteria filtering by integrating a specification executor into the repository and creating a c...
 La Fase 2: Lógica de Servicios, Reglas de Negocio y Búsqueda Multicriterio ha sido completada exitosamente.
 ### Resumen de Cambios de la Fase 2
 1. Prevención de Concurrencia y Asignación con Necesidad (CU-34, FT-09):
     • Se implementó DonacionYaAsignadaException y su captura en GlobalExceptionHandler.
     • En DonacionService.asignarDonacion: valida estado EN_DEPOSITO, asocia la entidad y la NecesidadMaterial
     receptora, actualiza cantidadCubierta y persiste el evento outbox atómicamente.
 2. Desasignación Administrativa (CU-18):
     • Método desasignarDonacion(donacionId, motivo, usuario) en DonacionService y expuesto en
     DonacionController: revierte la cantidad acumulada en la necesidad y retorna la donación a EN_DEPOSITO con
     justificación.
 3. Edición Restringida a Depósito (CU-18):
     • DonacionService.modificarDonacion bloquea con IllegalStateException cualquier intento de modificar una
     donación que ya haya salido de EN_DEPOSITO.
 4. Búsqueda Dinámica Multicriterio (CU-16, CU-17):
     • Se extendió DonacionRepository con JpaSpecificationExecutor y se creó DonacionSpecification para
     combinar filtros de estados, subcategoría, donante, fechas de carga y condición de uso. Expuesto en GET
     /api/v1/donaciones/filtrar.
 5. Baja Lógica Condicionada (CU-11, CU-25):
     • En DonanteService: bloquea la baja si el donante posee donaciones activas asociadas. Se añadió
     reactivarDonante.
     • En EntidadBeneficiariaService: bloquea la baja si la entidad posee donaciones asignadas o en traslado
     hacia su sede. Se añadió reactivarEntidad.
 6. Ciclo de Necesidades (CU-26, CU-27, CU-29):
     • Validación obligatoria de período para necesidades recurrentes (SEMANAL/MENSUAL).
     • Validación en modificación para que la nueva cantidad solicitada nunca sea inferior a la ya cubierta.
     • Cancelación formal con motivo operativo (cancelarNecesidad).
 7. Suite de Pruebas Automatizadas:
     • Se incorporó Fase2ServiciosTest y se ampliaron los tests de DonacionServiceTest. Todos los 53 tests se
     ejecutan con éxito.

 ──────
 ¿Avanzamos con la Fase 3: Motor de Matchmaking Extensible (Strategy + Registry, pesos dinámicos configurables
 y cálculo dinámico de subatendidos a 90 días)?

────────────────────────────────────────────────────────────
> si avanza

○ Thought for 1m 6s (Dedicated tests are needed for Phase 3 matchmaking. The suite will cover `MatchmakingStra...)
● Explored 3 files (MatchmakingStrategy.java, MatchmakingStrategyTest.java, MatchmakingServiceTest.java)
● Edited 1 file (MatchmakingStrategyRegistry.java)
… 7 more actions (4 reads, 3 edits) …
● Edited 1 file (Fase3MatchmakingTest.java)
● Ran (../mvnw test) failed (ctrl+o to expand)

 Running full test suite to verify Phase 3 Matchmaking & Registry enhancements. Waiting for completion.

○ Thought for 26.8s (Analyzing the constructor of `MatchmakingService`. The service appears to be built upon r...)
● Checked 1 task (task-214)
● Edited 1 file (MatchmakingStrategyRegistry.java)
● Explored 1 file (MatchmakingService.java)
● Edited 1 file (MatchmakingService.java)
● Ran (../mvnw test) (ctrl+o to expand)
 Re-running the test suite to verify the MatchmakingRegistry mock compatibility. Waiting for completion.

● Checked 1 task (task-225) (ctrl+o to expand)

▸ Thought for 8s, 559 tokens
 The build successfully passed all tests across the service, confirming the implemented changes. A key accompl...
 La Fase 3: Motor de Matchmaking Extensible (Strategy + Registry, Pesos Dinámicos y Cálculo Dinámico a 90 Días)
 ha sido completada con éxito.
 ### Resumen de Cambios de la Fase 3
 1. Patrón Registry y Extensibilidad OCP (CU-30):
     • Se implementó MatchmakingStrategyRegistry, permitiendo registrar e indexar dinámicamente cualquier
     estrategia que implemente MatchmakingStrategy sin necesidad de modificar el código de los servicios
     centrales.
 2. Parametrización Dinámica de Pesos Semánticos (CU-31):
     • En CompatibilidadSemanticaStrategy: coeficientes a₁, a₂, a₃ inyectables vía @Value (donatrack.
     matchmaking.semantica.*) con validación en tiempo de inicialización de la invariante ∑ aᵢ == 1.0 (100%).
 3. Cálculo Dinámico de Subatendidos a 90 Días (CU-32):
     • En PrioridadSubatendidosStrategy: cálculo en tiempo real consultando las donaciones efectivamente
     entregadas en la ventana móvil de los últimos 90 días mediante
     DonacionRepository.countEntregadasRecientesByEntidadId.
 4. Orquestación en MatchmakingService:
     • Integración con MatchmakingStrategyRegistry y método evaluarConTodasLasEstrategias para evaluaciones
     dinámicas multi-estrategia.
 5. Suite de Pruebas Automatizadas:
     • Se incorporó Fase3MatchmakingTest. Todos los 57 tests se ejecutan con éxito.

 ──────
 ¿Avanzamos con la Fase 4: DTOs tipados con Bean Validation, Reporte de Rechazos en Streaming CSV (>20.000
 filas) y Visualización Pública del Mapa?

────────────────────────────────────────────────────────────
> si

○ Thought for 1m 25s (A dedicated test suite for Phase 4 will be created, focusing on specific Data Transfer O...)
● Explored 2 files (CsvDonanteImporterService.java, CsvDonanteImporterTest.java)
● Edited 9 files (CsvDonanteImporterService.java, BienInputDto.java, CargaSegmentadaRequestDto.java, +6)
● Explored 1 file (DonacionController.java)
● Edited 2 files (DonacionController.java, Fase4DtoYMapaTest.java)
● Ran (../mvnw test) failed (ctrl+o to expand)

 Executing tests to verify Phase 4 DTOs, Bean Validation, CSV discrepancy reporting, and public map
 functionality. Waiting for completion.
───────────────────────────────────────────── Conversation compacted ─────────────────────────────────────────────

○ Thought for 1m 8s (The documentation for the `DonacionesService.md` file requires updating. Specifically, Se...)
● Checked 1 task (task-262)
● Edited 1 file (CsvDonanteImporterService.java)
… 8 more actions (5 reads, 1 edit, 1 command, 1 task) …
● Edited 1 file (relevamiento_donaciones_service.md)
● Ran (git status) (ctrl+o to expand)
  Reporte Final: Relevamiento e Implementación Integral de donaciones-service 

 Se ha completado exitosamente la ejecución de las 5 fases de relevamiento, diseño de arquitectura,
 refactorización y verificación técnica del microservicio donaciones-service.

 Se ha actualizado el documento formal de especificación del servicio en DonacionesService.md y el artefacto de
 relevamiento en relevamiento_donaciones_service.md.
 ──────
 ## 1. Resumen de Entregables por Fase
 ┌────────────────────────────────────────────────────┐
 │                                                    │
 │           Fase 1: Dominio y Concurrencia           │
 │                                                    │
 │      (@Version, HistorialEstado, BienDonado)       │
 │                                                    │
 └──────────────────────────┬─────────────────────────┘
                            │
                            ▼
 ┌────────────────────────────────────────────────────┐
 │                                                    │
 │             Fase 2: Servicios y Reglas             │
 │                                                    │
 │       (Desasignación, Baja Lógica, JPA Spec)       │
 │                                                    │
 └──────────────────────────┬─────────────────────────┘
                            │
                            ▼
 ┌────────────────────────────────────────────────────┐
 │                                                    │
 │              Fase 3: Matchmaking OCP               │
 │                                                    │
 │        (Registry, Pesos Dinámicos, 90 Días)        │
 │                                                    │
 └──────────────────────────┬─────────────────────────┘
                            │
                            ▼
 ┌────────────────────────────────────────────────────┐
 │                                                    │
 │            Fase 4: DTOs y Transparencia            │
 │                                                    ├──────────────────────────┐
 │ (Bean Validation, CSV Discrepancias, Mapa Público) │                          │
 │                                                    │                          │
 └──────────────────────────┬─────────────────────────┘                          │
                            │                                                    │
                            ▼                                                    ▼
 ┌────────────────────────────────────────────────────┐   ┌─────────────────────────────────────────────┐
 │                                                    │   │                                             │
 │                                                    │   │                    Cierre                   │
 │              F5["Fase 5: Verificación              │   │                                             │
 │                                                    │   │ (61/61 Tests en Verde, Docs Actualizados)"] │
 │                                                    │   │                                             │
 └────────────────────────────────────────────────────┘   └─────────────────────────────────────────────┘

 ### Fase 1: Dominio, Concurrencia y Trazabilidad (FT-09, CU-21)

 • Concurrencia Optimista (FT-09): Se incorporó @Version private Long version en Donacion.java:44-45 para
 evitar condiciones de carrera en asignaciones concurrentes.
 • Trazabilidad de Estados (CU-21): Creación de la entidad HistorialEstadoDonacion.java mapeada en Donacion
 para registrar auditoría de cada transición con usuario responsable y justificación obligatoria.
 • Vínculo Donación - Necesidad Material: Relación @ManyToOne directa en Donacion y auto-cierre al 100% en
 NecesidadMaterial.java:69-77.
 • Consistencia en Bienes (CU-13, CU-14): Reglas en BienDonado.java:52-68 (cantidades positivas, perecederos
 con vencimiento estrictamente futuro y esUsado mandatorio en vestimenta/muebles).
 • Baja Lógica: Incorporación de activo, fechaBaja y motivoBaja en PersonaDonante.java:35-45 y
 EntidadBeneficiaria.java:35-45.
 ### Fase 2: Servicios, Reglas de Negocio y Búsqueda Multicriterio (CU-16 a CU-25)
 • Control de Conflictos: Excepción DonacionYaAsignadaException.java capturada en
 GlobalExceptionHandler.java:36-40 (HTTP 409 Conflict).
 • Desasignación Administrativa (CU-18): DonacionService.desasignarDonacion() que revierte la cobertura en la
 necesidad receptora y regresa la donación a EN_DEPOSITO.
 • Restricción de Edición (CU-18): Edición de bienes permitida únicamente en estado EN_DEPOSITO
 (DonacionService.modificarDonacion()).
 • Baja Lógica Protegida (CU-11, CU-25): DonanteService.java y EntidadBeneficiariaService.java bloquean la baja
 si existen donaciones activas asociadas.
 • Búsqueda Dinámica JPA (CU-16, CU-17): Filtro dinámico con DonacionSpecification.java en
 DonacionRepository.java:16-17.

 ### Fase 3: Matchmaking Extensible OCP y Cálculo Dinámico a 90 Días (CU-30 a CU-33)
 • Registro OCP: MatchmakingStrategyRegistry.java para auto-descubrimiento desacoplado de estrategias de
 afinidad mediante Spring IoC.
 • Pesos Configurables (CU-31): CompatibilidadSemanticaStrategy.java:39-50 con validación de suma ∑ aᵢ = 1.0 ±
 0.001.
 • Prioridad a Subatendidos Dinámica (CU-32): PrioridadSubatendidosStrategy.java:34-44 calcula el volumen
 trimestral consultando donaciones entregadas a la entidad en los últimos 90 días en la base de datos.
 ### Fase 4: DTOs, Bean Validation, Streaming CSV y Mapa Público (CU-03, CU-12)
 • DTOs Inmutables: Creados bajo com.donatrack.donaciones.dto con Bean Validation (@NotNull, @NotBlank,
 @Positive, @Size):
     • CargaSegmentadaRequestDto.java y BienInputDto.java.
     • AsignacionDonacionRequestDto.java y DesasignacionRequestDto.java.
     • CambioEstadoRequestDto.java y ModificacionDonacionRequestDto.java.
     • BajaLogicaRequestDto.java y DonacionMapaPublicoDto.java.
 • Reporte de Discrepancias CSV (CU-12): CsvDonanteImporterService.java:150-162 registra el número de fila,
 línea original y motivo del rechazo en ResultadoImportacion.getErroresDetalle().
 • Mapa Público (CU-03, CU-04): Endpoint GET /api/v1/donaciones/publico/mapa en DonacionController.java:131-135.

 ### Fase 5: Verificación Integral y Cobertura

 • Resultados de Ejecución de Pruebas:
   [INFO] -------------------------------------------------------
   [INFO]  T E S T S
   [INFO] -------------------------------------------------------
   [INFO] Tests run: 8,  Failures: 0, Errors: 0, Skipped: 0 -- DonacionControllerTest
   [INFO] Tests run: 4,  Failures: 0, Errors: 0, Skipped: 0 -- Fase3MatchmakingTest
   [INFO] Tests run: 3,  Failures: 0, Errors: 0, Skipped: 0 -- MatchmakingStrategyTest
   [INFO] Tests run: 4,  Failures: 0, Errors: 0, Skipped: 0 -- Fase4DtoYMapaTest
   [INFO] Tests run: 8,  Failures: 0, Errors: 0, Skipped: 0 -- Fase2ServiciosTest
   [INFO] Tests run: 3,  Failures: 0, Errors: 0, Skipped: 0 -- OutboxPublisherTest
   [INFO] Tests run: 2,  Failures: 0, Errors: 0, Skipped: 0 -- CsvDonanteImporterTest
   [INFO] Tests run: 3,  Failures: 0, Errors: 0, Skipped: 0 -- MatchmakingServiceTest
   [INFO] Tests run: 14, Failures: 0, Errors: 0, Skipped: 0 -- DonacionServiceTest
   [INFO] Tests run: 3,  Failures: 0, Errors: 0, Skipped: 0 -- DonacionStateTest

Resume with -c (or command below):
agy --conversation=3ebb4739-b769-49cf-9aa2-e46c4ca5d026

   [INFO] Tests run: 6,  Failures: 0, Errors: 0, Skipped: 0 -- Fase1DominioTest
   [INFO] Tests run: 3,  Failures: 0, Errors: 0, Skipped: 0 -- DonacionesSchedulerTest
   [INFO] 
   [INFO] Results:
   [INFO] Tests run: 61, Failures: 0, Errors: 0, Skipped: 0
   [INFO] ------------------------------------------------------------------------
   [INFO] BUILD SUCCESS
   [INFO] ------------------------------------------------------------------------

 ──────
 ## 2. Catálogo Consolidado de Endpoints REST

  Método | Ruta                             | Descripción                                   | Rol
 --------|----------------------------------|-----------------------------------------------|------------------
  GET    | /api/v1/donaciones/saludo        | Verificación de conectividad                  | Público
  GET    | /api/v1/donaciones               | Búsqueda multicriterio (estado, subcategoria, | ADMIN, OPERADOR
	  |                                  | donanteId, fechas)                            |
  GET    | /api/v1/donaciones/{id}          | Detalle de donación e historial de estados    | Autenticado
  POST   | /api/v1/donaciones/carga-        | Alta de donación múltiple segmentada por      | DONANTE, ADMIN
	  | segmentada                       | subcategoría                                  |
  POST   | /api/v1/donaciones/carga-        | Alta tipada con Bean Validation               | DONANTE, ADMIN
	  | segmentada/dto                   | (CargaSegmentadaRequestDto)                   |
  POST   | /api/v1/donaciones/importar-csv  | Streaming masivo CSV (>20k filas) con reporte | ADMIN
	  |                                  | de discrepancias                              |
  GET    | /api/v1/donaciones/{id}/matchmak | Consulta afinidad algorítmica con entidades   | OPERADOR, ADMIN
	  | ing                              | beneficiarias                                 |
  POST   | /api/v1/donaciones/{id}/asignar  | Asignación a necesidad material con bloqueo   | OPERADOR, ADMIN
	  |                                  | concurrente @Version                          |
  POST   | /api/v1/donaciones/{id}/desasign | Reversión administrativa de asignación a      | OPERADOR, ADMIN
	  | ar                               | EN_DEPOSITO                                   |
  PUT    | /api/v1/donaciones/{id}          | Modificación de bienes (restringido a estado  | OPERADOR, ADMIN
	  |                                  | EN_DEPOSITO)                                  |
  PUT    | /api/v1/donaciones/{id}/estado   | Transición de estado con auditoría y          | OPERADOR,
	  |                                  | justificación                                 | LOGISTICA
  PUT    | /api/v1/donaciones/bulk-estado   | Actualización por lotes para operadores       | LOGISTICA
	  |                                  | logísticos                                    |
  GET    | /api/v1/donaciones/publico/mapa  | Marcadores públicos de donaciones entregadas  | Público
  POST   | /api/v1/donantes/humanos         | Alta de donante persona humana                | Público / ADMIN
  POST   | /api/v1/donantes/juridicos       | Alta de donante persona jurídica              | Público / ADMIN
  POST   | /api/v1/donantes/{id}/baja       | Baja lógica de donante (validando que no      | ADMIN
	  |                                  | tenga donaciones activas)                     |
  POST   | /api/v1/beneficiarias            | Registro de entidad beneficiaria              | ADMIN
  POST   | /api/v1/beneficiarias/{id}/baja  | Baja lógica de entidad (validando que no      | ADMIN
	  |                                  | tenga donaciones activas)                     |
  POST   | /api/v1/beneficiarias/{id}/neces | Cargar necesidad material (valida período en  | BENEFICIARIA,
	  | idades                           | recurrentes)                                  | ADMIN
  PUT    | /api/v1/beneficiarias/necesidade | Cancelación formal de necesidad material      | BENEFICIARIA,
	  | s/{necesidadId}/cancelar         |                                               | ADMIN
 ──────
 El microservicio se encuentra completamente relevado, modularizado y funcional, alineado a los lineamientos de
 la cátedra de Diseño de Sistemas (UTN.BA) y listo para su integración o despliegue.
                                                                                  1 artifact · /artifact to review
─────────────────────────────────────────
