# Especificación Técnica: Microservicio de Incentivos (`incentivos-service`)

El microservicio de **Incentivos** lidera la gamificación y fidelización de donantes en **DonaTrack**. Gestiona el sistema de acumulación de puntos, el progreso a través de misiones secuenciales, la asignación de insignias digitales, la difusión de logros hacia redes sociales mediante webhooks (n8n) y el cálculo automatizado de rankings mensuales.

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
| `GET` | `/api/v1/incentivos/rankings/mensual` | Consulta del Top 10 del ranking mensual actual | Público |
| `POST`| `/api/v1/incentivos/rankings/forzar-calculo` | Ejecución forzada manual del scheduler de ranking | `ADMIN` |
