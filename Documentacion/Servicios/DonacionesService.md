# Especificación Técnica: Microservicio de Donaciones (`donaciones-service`)

El microservicio de **Donaciones** constituye el núcleo transaccional del sistema **DonaTrack**. Administra el catálogo de donantes (personas humanas y jurídicas), entidades beneficiarias, necesidades materiales, el catálogo de donaciones segmentadas por subcategoría, el motor algorítmico de matchmaking y la publicación transaccional de eventos hacia RabbitMQ.

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
| `GET` | `/api/v1/donaciones` | Listar donaciones con filtro por `estado` | `ADMIN`, `OPERADOR` |
| `GET` | `/api/v1/donaciones/{id}` | Obtener detalle de una donación | Autenticado |
| `POST`| `/api/v1/donaciones/carga-segmentada` | Alta de donación múltiple segmentada por subcategoría | `DONANTE`, `ADMIN` |
| `POST`| `/api/v1/donaciones/importar-csv` | Importación masiva por streaming de CSV | `ADMIN` |
| `GET` | `/api/v1/donaciones/{id}/matchmaking` | Consulta afinidad algorítmica con entidades | `OPERADOR`, `ADMIN` |
| `POST`| `/api/v1/donaciones/{id}/asignar` | Confirmar asignación a entidad beneficiaria | `OPERADOR`, `ADMIN` |
| `PUT` | `/api/v1/donaciones/{id}/estado` | Cambio de estado de la donación (incluye justificación en fallos) | `OPERADOR`, `LOGISTICA` |
| `PUT` | `/api/v1/donaciones/bulk-estado` | Actualización masiva de estado para lotes logísticos | `LOGISTICA` |
| `POST`| `/api/v1/donantes/humanos` | Registrar persona donante humana | Público / `ADMIN` |
| `POST`| `/api/v1/donantes/juridicos` | Registrar persona donante jurídica | Público / `ADMIN` |
| `POST`| `/api/v1/beneficiarias` | Registrar entidad beneficiaria | `ADMIN` |
| `POST`| `/api/v1/beneficiarias/{id}/necesidades` | Cargar necesidad material de una entidad | `BENEFICIARIA`, `ADMIN` |
