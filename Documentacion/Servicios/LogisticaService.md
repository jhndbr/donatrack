# Especificación Técnica: Microservicio de Logística (`logistica-service`)

El microservicio de **Logística** es responsable de la gestión de la flota de transporte, la agrupación de donaciones en lotes, la interacción asíncrona con el planificador de rutas externo, la telemetría GPS en tiempo real de los camiones y el registro final de entregas.

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

## 4. Catálogo de Endpoints REST

| Método | Ruta | Descripción | Roles Permitidos |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/logistica/camiones` | Listar unidades de la flota y su estado | `LOGISTICA`, `ADMIN` |
| `POST`| `/api/v1/logistica/camiones` | Dar de alta una unidad de transporte | `ADMIN`, `LOGISTICA` |
| `POST`| `/api/v1/logistica/camiones/{id}/ubicacion` | Enviar coordenada GPS del chofer | `CHOFER`, `ADMIN` |
| `POST`| `/api/v1/logistica/rutas/planificar-lote` | Iniciar empaquetado de lote ($\le 100$) y despacho al optimizador | `LOGISTICA`, `ADMIN` |
| `POST`| `/api/v1/logistica/rutas/callback` | Webhook receptor de la ruta resuelta | Externo (Optimizer) |
| `GET` | `/api/v1/logistica/rutas` | Consultar rutas activas e historial | `LOGISTICA`, `ADMIN` |
| `POST`| `/api/v1/logistica/rutas/{id}/iniciar` | Chofer marca inicio de recorrido | `CHOFER` |
| `POST`| `/api/v1/logistica/rutas/{id}/finalizar`| Chofer marca fin de recorrido | `CHOFER` |
| `POST`| `/api/v1/logistica/entregas/confirmar` | Registrar entrega exitosa con receptor y firma | `CHOFER`, `LOGISTICA` |
| `POST`| `/api/v1/logistica/entregas/fallida` | Registrar entrega fallida con justificación obligatoria | `CHOFER`, `LOGISTICA` |
