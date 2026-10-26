# Especificación Técnica: Microservicio de Incentivos (`incentivos-service`)

El microservicio de **Incentivos** lidera la gamificación y fidelización de donantes en **DonaTrack**. Gestiona el sistema de acumulación de puntos, el progreso a través de misiones secuenciales, la asignación de insignias digitales, la difusión de logros hacia redes sociales mediante webhooks (n8n) y el cálculo automatizado de rankings mensuales.

---

## 0. Flujos Principales Resumidos y Requerimientos Esenciales

### 0.1. Flujos de Gamificación Clave
1. **Progreso en Misiones Secuenciales (CU-48, CU-49)**:
   - Al registrarse una donación o entrega exitosa, se evalúa el progreso de la misión activa del donante.
   - Las misiones se completan en orden estrictamente **secuencial**: completar $M_k$ desbloquea $M_{k+1}$.
   - Tipos oficiales: *Racha* (meses consecutivos, con penalización de pérdida de progreso ante inactividad), *Completitud* (variedad de categorías donadas), *Hábil Donador* (cantidad de bienes donados) y *Donaciones Exitosas* (entregas confirmadas).
   - Al cumplir el objetivo, se otorga la insignia (CU-50), se emite `mision.cumplida` a RabbitMQ y se dispara el webhook a n8n para difusión social (CU-55).
2. **Escalafón y Ascenso de Categorías (CU-53)**:
   - Categorías oficiales de la cátedra: `COLABORADOR` (inicial) $\rightarrow$ `SOSTENEDOR` $\rightarrow$ `TRANSFORMADOR`.
   - Se muestra públicamente junto al nombre de usuario.
   - Al completar todas las misiones de la categoría actual, asciende de rango y se publica `categoria.ascendida` a RabbitMQ.
3. **Difusión en Redes Sociales vía n8n (CU-55)**:
   - Despacho HTTP POST asincrónico a n8n con payload estructurado para publicación en redes (con imagen y mención).
4. **Ranking Mensual e Histórico (CU-54)**:
   - Scheduler cron ejecutado el primer día de cada mes a las 00:00:00.
   - Ordena por **cantidad de misiones cumplidas durante el mes calendario vencido** (sin importar la categoría).
   - Persiste la tabla de posiciones y destaca a los 3 primeros (podio) para la aplicación.
5. **Métricas de Impacto y Visibilidad de Insignias (CU-47, CU-51, CU-52)**:
   - Donante consulta totales históricos, comparativas mensuales y número de organizaciones beneficiadas.
   - Donante configura qué insignias son visibles públicamente en su perfil.

### 0.2. Lo Necesario del Service
* **Persistencia Relacional**: PostgreSQL (`incentivos_db`) con Spring Data JPA.
* **Consumo AMQP**: Escucha eventos `donacion.asignada` y `entrega.confirmada` con control de idempotencia (FT-06).
* **Productor AMQP**: Emisión de `mision.cumplida` y `categoria.ascendida` hacia `donatrack.events`.
* **Integración Externa n8n**: Webhook HTTP saliente con tolerancia a fallas y timeouts.

---

## 1. Ficha Técnica y Dependencias

* **Puerto HTTP**: `8084` (Expuesto también vía Gateway en `8080/api/v1/incentivos/**`)
* **Base de Datos**: PostgreSQL 16 (`incentivos_db`)
* **ORM**: Spring Data JPA / Hibernate 6
* **Mensajería**: AMQP (RabbitMQ) como consumidor de eventos de donación y productor de eventos de insignias/categorías.
* **Integración Externa**: Webhook HTTP saliente hacia **n8n** para publicación automatizada de hitos en redes sociales.

---

## 2. Modelo Relacional de Base de Datos (`incentivos_db`)

```sql
-- Puntos y Categoría del Donante
CREATE TABLE donantes_puntos (
    id BIGSERIAL PRIMARY KEY,
    donante_id BIGINT NOT NULL UNIQUE,
    puntos_acumulados INTEGER DEFAULT 0,
    categoria VARCHAR(30) DEFAULT 'BRONCE', -- BRONCE, PLATA, ORO, PLATINO
    total_donaciones_realizadas INTEGER DEFAULT 0
);

-- Misiones Secuenciales Preconfiguradas
CREATE TABLE misiones (
    id BIGSERIAL PRIMARY KEY,
    orden_secuencial INTEGER NOT NULL UNIQUE,
    nombre VARCHAR(120) NOT NULL,
    descripcion TEXT NOT NULL,
    objetivo_donaciones INTEGER NOT NULL,
    insignia_recompensa VARCHAR(100) NOT NULL,
    puntos_recompensa INTEGER NOT NULL
);

-- Progreso del Donante en las Misiones
CREATE TABLE progreso_misiones (
    id BIGSERIAL PRIMARY KEY,
    donante_id BIGINT NOT NULL,
    mision_id BIGINT NOT NULL REFERENCES misiones(id),
    donaciones_progreso INTEGER DEFAULT 0,
    completada BOOLEAN DEFAULT FALSE,
    fecha_completada TIMESTAMP,
    UNIQUE(donante_id, mision_id)
);

-- Insignias Otorgadas al Donante
CREATE TABLE insignias_otorgadas (
    id BIGSERIAL PRIMARY KEY,
    donante_id BIGINT NOT NULL,
    codigo_insignia VARCHAR(100) NOT NULL,
    nombre_insignia VARCHAR(150) NOT NULL,
    fecha_otorgada TIMESTAMP NOT NULL
);

-- Historial de Rankings Mensuales
CREATE TABLE rankings_mensuales (
    id BIGSERIAL PRIMARY KEY,
    anio INTEGER NOT NULL,
    mes INTEGER NOT NULL,
    posicion INTEGER NOT NULL,
    donante_id BIGINT NOT NULL,
    puntos_mes INTEGER NOT NULL,
    fecha_calculo TIMESTAMP NOT NULL
);
```

---

## 3. Dinámica de Gamificación y Reglas de Negocio

### 3.1. Niveles y Categorías de Donantes
La categoría de un donante se calcula y actualiza dinámicamente en función de sus puntos acumulados:
* **BRONCE**: $0 \le \text{Puntos} \le 100$
* **PLATA**: $101 \le \text{Puntos} \le 500$
* **ORO**: $501 \le \text{Puntos} \le 1500$
* **PLATINO**: $\text{Puntos} > 1500$

Al ascender de nivel, se publica el evento `categoria.ascendida` a RabbitMQ.

### 3.2. Misiones Secuenciales
Las misiones deben cumplirse de manera estrictamente **secuencial**:
1. Para avanzar en la misión $M_k$, el donante debe haber completado obligatoriamente la misión previa $M_{k-1}$.
2. Cada donación incrementa el progreso de la misión activa actual.
3. Al alcanzar el objetivo:
   - La misión se marca como completada.
   - Se otorgan los puntos de bonificación.
   - Se registra la nueva insignia en `insignias_otorgadas`.
   - Se activa la misión $M_{k+1}$.
   - Se dispara el webhook hacia **n8n** para difusión social del logro.

### 3.3. Scheduler de Ranking Mensual
* Expresión cron: `0 0 0 1 * ?` (Se ejecuta automáticamente el primer día de cada mes a las 00:00:00 hs).
* Totaliza los puntos sumados por cada donante durante el mes calendario vencido.
* Genera la tabla histórica de posiciones y destaca a los 10 donantes más solidarios.

### 3.4. Integración con Webhook n8n (Redes Sociales)
Cuando se desbloquea una insignia importante o se completa una misión:
```json
POST https://n8n.donatrack.org/webhook/difusion-insignias
Content-Type: application/json

{
  "evento": "INSIGNIA_DESBLOQUEADA",
  "donanteId": 42,
  "nombreDonante": "Lucía Benítez",
  "insignia": "HEROE_DE_LA_SOLIDARIDAD",
  "mision": "Tres donaciones en un mes",
  "puntosTotales": 650,
  "categoria": "ORO",
  "timestamp": "2026-10-06T14:45:00Z"
}
```
*n8n recibe este payload y genera automáticamente publicaciones de reconocimiento en canales de la organización (Twitter/X, LinkedIn, Telegram).*

---

## 4. Catálogo de Endpoints REST

| Método | Ruta | Descripción | Roles Permitidos |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/incentivos/donantes/{id}/perfil` | Resumen de puntos, nivel, misiones e insignias | `DONANTE`, `ADMIN` |
| `POST`| `/api/v1/incentivos/donantes/{id}/sumar-puntos`| Suma manual o programada de puntos por donación | `INTERNO`, `ADMIN` |
| `GET` | `/api/v1/incentivos/misiones/activas` | Listar catálogo de misiones disponibles | Público |
| `POST`| `/api/v1/incentivos/rankings/forzar-calculo` | Ejecución forzada manual del scheduler de ranking | `ADMIN` |

---

## 5. TO-DO Maestro del Servicio (Gaps y Funcionalidades Faltantes según Enunciado y Casos de Uso)

### 5.1. Misiones Secuenciales del Dominio (CU-48, CU-49, CU-50)
- [x] **TODO-INC-01 (CU-49)**: Implementar la lógica y reglas completas para las 4 misiones oficiales del enunciado:
  - **Racha**: donación durante $X$ meses consecutivos. Implementado con `actualizarRachaMensual(LocalDate)` con reseteo a 1 por discontinuidad/inactividad mensual.
  - **Completitud**: realizar donaciones de $X$ categorías de bienes distintas. Implementado con registro dinámico en `perfil_subcategorias_donadas` y validación de unicidad.
  - **Hábil Donador**: verificar donación que supere $X$ cantidad de bienes unitarios en una única carga o acumulativo.
  - **Donaciones Exitosas**: acumular $X$ donaciones efectivamente entregadas y confirmadas por entidades beneficiarias (escucha de `entrega.confirmada`).
- [x] **TODO-INC-01b (CU-48)**: Garantizar el desbloqueo secuencial estricto ($M_k \rightarrow M_{k+1}$) impidiendo avanzar en misiones posteriores sin haber finalizado la inmediata anterior (ordenación por `ordenSecuencia`).

### 5.2. Insignias y Configuración de Visibilidad (CU-51, CU-52)
- [x] **TODO-INC-02 (CU-52)**: Incorporar atributo `esPublica` (booleano) en la entidad `Insignia` y exponer endpoint `PATCH /api/v1/incentivos/insignias/{id}/visibilidad?visible={bool}` para que el donante elija qué logros mostrar públicamente en su perfil.

### 5.3. Categorías de Donantes (CU-53)
- [x] **TODO-INC-03 (CU-53)**: Mapear el ascenso estricto a través de las 3 categorías del enunciado (`COLABORADOR` $\rightarrow$ `SOSTENEDOR` $\rightarrow$ `TRANSFORMADOR`) tras completar el paquete de misiones de cada nivel.

### 5.4. Ranking Mensual e Histórico (CU-54)
- [x] **TODO-INC-04 (CU-54)**: Ajustar el scheduler de ranking mensual (`IncentivosScheduler`) para ordenar a los donantes por **cantidad de misiones cumplidas en el mes vencido** (sin importar categoría), persistir la tabla histórica mensual (`anio`, `mes`, `posicion`), y exponer endpoints `GET /api/v1/incentivos/rankings/mensual` y `POST /api/v1/incentivos/rankings/forzar-calculo`.

### 5.5. Analítica y Métricas de Actividad (CU-47)
- [x] **TODO-INC-05 (CU-47)**: Endpoint `GET /api/v1/incentivos/donantes/{idDonante}/analitica` y cálculo de analítica avanzada: evolución temporal por períodos, comparativa mes actual vs mes anterior, insgnias desbloqueadas, subcategorías diversas y total de entidades beneficiarias distintas ayudadas.

### 5.6. Resiliencia, Idempotencia e Integración n8n (CU-55, FT-06)
- [x] **TODO-INC-06 (CU-55)**: Integración con webhook hacia n8n con tolerancia a fallas y timeouts, y endpoint de difusión social `POST /api/v1/incentivos/insignias/{id}/publicar-red-social`.
- [x] **TODO-INC-07 (FT-06)**: Implementar control de duplicados en `DonacionEventConsumer` mediante la entidad relacional `RegistroEventoProcesado` para descartar mensajes de RabbitMQ reintentados o duplicados (`donacion-asignada-...` y `entrega-confirmada-...`).

