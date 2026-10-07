# Integración, Comunicación y Contratos entre Microservicios - DonaTrack

Este documento define la arquitectura integral de comunicación e integración entre los distintos componentes del sistema **DonaTrack**, detallando los protocolos de transporte, contratos de datos, garantías de entrega y mecanismos de resiliencia.

---

## 1. Matriz General de Comunicación e Integración

| Origen | Destino | Tipo de Comunicación | Protocolo / Canal | Propósito |
| :--- | :--- | :--- | :--- | :--- |
| **Cliente / Front** | **`api-gateway`** | Sincrónica | HTTP REST (JSON) | Todas las peticiones externas al sistema |
| **`api-gateway`** | **`keycloak`** | Sincrónica | HTTP OIDC / JWKS | Validación de firmas JWT y autenticación |
| **`api-gateway`** | **Microservicios** | Sincrónica | HTTP REST (Reverse Proxy)| Enrutamiento de peticiones autenticadas |
| **`logistica-service`**| **`donaciones-service`**| Sincrónica | HTTP REST | Consultar donaciones asignadas y actualizar estados físicos |
| **`logistica-service`**| **Optimizador Externo** | Sincrónica / Asíncrona | HTTP POST (Lotes $\le 100$) | Despacho de paquetes para cálculo de ruta |
| **Optimizador Externo**| **`logistica-service`**| Asíncrona (Callback) | HTTP Webhook POST | Devolución de la ruta óptima calculada |
| **`donaciones-service`**| **RabbitMQ** | Asíncrona (Outbox) | AMQP (`donatrack.events`) | Emisión de `donacion.asignada` y `donante.inactivo` |
| **`logistica-service`**| **RabbitMQ** | Asíncrona | AMQP (`donatrack.events`) | Emisión de `ruta.iniciada`, `entrega.confirmada`, `entrega.fallida` |
| **`incentivos-service`**| **RabbitMQ** | Asíncrona | AMQP (`donatrack.events`) | Emisión de `mision.cumplida`, `categoria.ascendida` |
| **RabbitMQ** | **`notificaciones-service`**| Asíncrona | AMQP (Listeners) | Entrega de mensajes para despacho de emails y alertas |
| **RabbitMQ** | **`incentivos-service`**| Asíncrona | AMQP (Listeners) | Acumulación automática de puntos tras donación |
| **`incentivos-service`**| **n8n Automation** | Asíncrona (Webhook) | HTTP POST | Difusión de hitos e insignias en redes sociales |
| **`notificaciones-service`**| **Mailpit** | Sincrónica | SMTP (`localhost:1025`) | Envío de correos electrónicos transaccionales |

---

## 2. Contratos de Integración Sincrónica (REST Inter-Servicio)

### 2.1. Logística consulta Donaciones Asignadas
* **Ruta**: `GET /api/v1/donaciones?estado=ASIGNACION_REALIZADA`
* **Llamador**: `logistica-service`
* **Respuesta**:
```json
[
  {
    "id": 101,
    "subcategoria": "Alimentos no perecederos",
    "cantidad": 50.0,
    "unidadMedida": "kg",
    "entidadBeneficiariaId": 5,
    "direccionEntidad": "Av. Medrano 951, CABA",
    "estado": "ASIGNACION_REALIZADA"
  }
]
```

### 2.2. Logística actualiza Estado en Lote de Donaciones
* **Ruta**: `PUT /api/v1/donaciones/bulk-estado`
* **Llamador**: `logistica-service` (invocado por el callback de ruta)
* **Payload**:
```json
{
  "donacionIds": [101, 102, 103],
  "nuevoEstado": "LISTA_PARA_ENTREGAR"
}
```
* **Respuesta**: HTTP 200 OK (`{"actualizadas": 3}`)

---

## 3. Contratos de Eventos Asíncronos (AMQP en `common-dtos`)

Todos los eventos viajan serializados en formato JSON a través del Topic Exchange `donatrack.events`.

### 3.1. Evento `donacion.asignada`
* **Routing Key**: `donacion.asignada`
* **Clase Java**: `com.donatrack.dto.events.DonacionAsignadaEvent`
```json
{
  "donacionId": 101,
  "donanteId": 1,
  "emailDonante": "juan.perez@example.com",
  "entidadId": 5,
  "nombreEntidad": "Comedor Esperanza",
  "subcategoria": "Alimentos no perecederos",
  "cantidad": 50.0,
  "timestamp": "2026-10-06T14:32:00.123"
}
```

### 3.2. Evento `ruta.iniciada`
* **Routing Key**: `ruta.iniciada`
* **Clase Java**: `com.donatrack.dto.events.RutaIniciadaEvent`
```json
{
  "rutaId": "6702c2e0f81d1a2b3c4d5e6f",
  "camionId": "CAMION-01",
  "choferNombre": "Carlos Gómez",
  "totalParadas": 8,
  "timestamp": "2026-10-06T15:00:00"
}
```

### 3.3. Evento `entrega.confirmada`
* **Routing Key**: `entrega.confirmada`
* **Clase Java**: `com.donatrack.dto.events.EntregaConfirmadaEvent`
```json
{
  "entregaId": "6702c2e0f81d1a2b3c4d5e70",
  "donacionId": 101,
  "entidadId": 5,
  "receptorNombre": "María Rodríguez",
  "receptorDni": "28.456.789",
  "fechaHora": "2026-10-06T15:45:00"
}
```

### 3.4. Evento `entrega.fallida` (Con Justificación Obligatoria)
* **Routing Key**: `entrega.fallida`
* **Clase Java**: `com.donatrack.dto.events.EntregaFallidaEvent`
```json
{
  "entregaId": "6702c2e0f81d1a2b3c4d5e71",
  "donacionId": 102,
  "entidadId": 6,
  "motivoFallo": "Establecimiento cerrado en el horario pactado",
  "requiereReprogramacion": true,
  "fechaHora": "2026-10-06T16:10:00"
}
```

### 3.5. Evento `mision.cumplida`
* **Routing Key**: `mision.cumplida`
* **Clase Java**: `com.donatrack.dto.events.MisionCumplidaEvent`
```json
{
  "donanteId": 1,
  "emailDonante": "juan.perez@example.com",
  "misionId": 2,
  "nombreMision": "Tres donaciones en un mes",
  "insigniaOtorgada": "DONANTE_COMPROMETIDO",
  "puntosGanados": 200,
  "timestamp": "2026-10-06T16:30:00"
}
```

---

## 4. Garantía de Consistencia Eventual: Transactional Outbox Pattern

Para garantizar que un mensaje **nunca se pierda** ni se publique un evento inválido ante un fallo de base de datos (problema de las transacciones distribuidas sin 2PC):

1. **Escritura Atómica Local**:
   ```java
   @Transactional
   public void asignarDonacion(Long donacionId, Long entidadId) {
       Donacion donacion = donacionRepository.findById(donacionId).orElseThrow();
       donacion.setEstado(EstadoDonacion.ASIGNACION_REALIZADA);
       donacionRepository.save(donacion);

       // Se persiste el evento en la misma transacción de Postgres
       OutboxEvent outbox = new OutboxEvent(
           "Donacion", donacionId.toString(), "donacion.asignada", jsonPayload
       );
       outboxRepository.save(outbox);
   }
   ```
2. **Despachador Programado**:
   Un scheduler lee cada 2 segundos los eventos con `processed = false`, publica el payload a RabbitMQ y marca el registro como `processed = true`. Si RabbitMQ está caído temporalmente, la transacción de base de datos ya está segura y los eventos se publicarán en el siguiente ciclo sin pérdida de datos.

---

## 5. Webhooks Externos

### 5.1. Webhook Callback de Optimización de Rutas (VRP)
* **Endpoint expuesto en DonaTrack**: `POST /api/v1/logistica/rutas/callback`
* **Payload esperado**:
```json
{
  "loteId": "LOTE-BUE-2026-001",
  "camionId": "CAMION-01",
  "distanciaKmTotal": 24.5,
  "tiempoEstimadoMinutos": 45,
  "paradas": [
    {
      "orden": 1,
      "direccion": "Av. Corrientes 1234, CABA",
      "latitud": -34.6037,
      "longitud": -58.3816,
      "tipo": "RECOLECCION",
      "donacionId": 101
    },
    {
      "orden": 2,
      "direccion": "Av. Medrano 951, CABA",
      "latitud": -34.5985,
      "longitud": -58.4201,
      "tipo": "ENTREGA",
      "donacionId": 101
    }
  ]
}
```

### 5.2. Webhook Saliente hacia n8n (Redes Sociales)
* **Endpoint de n8n**: `POST ${N8N_WEBHOOK_URL:http://localhost:5678/webhook/difusion}`
* Se envía con timeout de 3 segundos de manera no bloqueante. Si n8n no responde, la lógica de negocio de incentivos continúa su curso normalmente sin abortar la operación del usuario.
