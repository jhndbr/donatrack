# Topología y Configuración de RabbitMQ - DonaTrack

Este documento define la estructura y especificación de mensajería asíncrona implementada sobre **RabbitMQ 3.13** para el sistema **DonaTrack**.

---

## 1. Parámetros de Conexión y Consola

* **Puerto AMQP**: `5672`
* **Consola Web de Administración**: [http://localhost:15672](http://localhost:15672)
* **Virtual Host**: `/`
* **Usuario**: `donatrack`
* **Contraseña**: `donatrack_password`

---

## 2. Exchange Principal (`donatrack.events`)

* **Nombre**: `donatrack.events`
* **Tipo**: `topic`
* **Durable**: `true`
* **Auto-delete**: `false`

Permite enrutamiento flexible por patrones de tópicos jerárquicos (`donacion.*`, `ruta.*`, `entrega.*`, etc.).

---

## 3. Matriz de Colas y Bindings

| Cola (`Queue Name`) | Durable | Exchange de Origen | Routing Key (Patrón de Binding) | Microservicio Consumidor |
| :--- | :---: | :--- | :--- | :--- |
| **`q.notificaciones.donaciones`** | Sí | `donatrack.events` | `donacion.*` | `notificaciones-service` |
| **`q.notificaciones.logistica`** | Sí | `donatrack.events` | `ruta.*`<br>`entrega.*` | `notificaciones-service` |
| **`q.notificaciones.incentivos`** | Sí | `donatrack.events` | `mision.*`<br>`categoria.*` | `notificaciones-service` |
| **`q.incentivos.donaciones`** | Sí | `donatrack.events` | `donacion.asignada` | `incentivos-service` |

---

## 4. Política de Tolerancia a Fallos y Dead Letter Exchange (DLX)

Para evitar la pérdida de mensajes ante excepciones no recuperables en los consumidores:
1. **Dead Letter Exchange**: `dlx.donatrack` (tipo `direct`).
2. **Cola de Mensajes Muertos**: `dlq.notificaciones`.
3. **Argumentos de las Colas Principales**:
   - `x-dead-letter-exchange`: `dlx.donatrack`
   - `x-dead-letter-routing-key`: `dlq.retry.failed`
   - `x-message-ttl`: `86400000` (24 horas)

Cuando un mensaje excede los reintentos permitidos o sufre una falla de deserialización fatal, RabbitMQ lo deposita automáticamente en `dlq.notificaciones` para auditoría, inspección y reencolado manual.

---

## 5. Inspección Rápida desde la Consola Web

1. Ingresar a [http://localhost:15672](http://localhost:15672).
2. Pestaña **Exchanges**: Verificar la presencia de `donatrack.events` y sus enlaces (*bindings*).
3. Pestaña **Queues**: Observar el número de mensajes entrantes (*Ready*), en proceso (*Unacked*) y la tasa de consumo (*Message rates*).
4. Dentro de cualquier cola, el panel **"Get messages"** permite inspeccionar visualmente el JSON del mensaje enviado para depuración.
