# Especificación Técnica: Microservicio de Notificaciones (`notificaciones-service`)

El microservicio de **Notificaciones** es un consumidor asíncrono puro dentro de la arquitectura de **DonaTrack**. Su función primordial es escuchar eventos de dominio publicados en RabbitMQ por Donaciones, Logística e Incentivos, transformar dichos eventos en mensajes personalizados y despacharlos a través de múltiples canales de comunicación (Email, SMS y WhatsApp).

---

## 1. Ficha Técnica y Dependencias

* **Puerto HTTP**: `8085` (Utilizado exclusivamente para métricas `/actuator/health`; **no expone APIs de negocio públicas** para preservar el desacoplamiento).
* **Mecanismo de Ingesta**: Exclusivamente AMQP a través de `@RabbitListener`.
* **Servidor SMTP**: Integrado con **Mailpit** (`localhost:1025` SMTP, interfaz visual en `localhost:8025`).
* **Módulos Maven**: Depende de `common-dtos` para deserializar los contratos fuertemente tipados.

---

## 2. Topología de Colas y Suscripciones AMQP

El servicio se conecta al Topic Exchange central `donatrack.events` suscribiéndose a las siguientes colas:

```
[Topic Exchange: donatrack.events]
       │
       ├── routing_key: donacion.*  ──> [q.notificaciones.donaciones]
       │
       ├── routing_key: ruta.*      ──> [q.notificaciones.logistica]
       ├── routing_key: entrega.*   ──> [q.notificaciones.logistica]
       │
       └── routing_key: mision.*    ──> [q.notificaciones.incentivos]
       └── routing_key: categoria.* ──> [q.notificaciones.incentivos]
```

---

## 3. Matriz de Eventos Consumidos y Acciones de Notificación

| Evento | Origen | Canal Predeterminado | Destinatario | Mensaje / Contenido |
| :--- | :--- | :--- | :--- | :--- |
| `donacion.asignada` | Donaciones | Email (SMTP Mailpit) | Donante y Entidad Beneficiaria | Confirmación de que los bienes fueron asignados exitosamente, detallando la entidad receptora y los próximos pasos de recolección. |
| `donante.inactivo` | Donaciones | Email / WhatsApp | Donante | Mensaje de reactivación tras 90 días sin donar, destacando el impacto de su ayuda previa. |
| `ruta.iniciada` | Logística | SMS / WhatsApp | Entidad Beneficiaria | Aviso de que el camión inició su recorrido, informando horario estimado de llegada (ETA). |
| `entrega.confirmada`| Logística | Email | Donante | Certificado formal de recepción con fecha, hora y datos del responsable que firmó el acuse de recibo. |
| `entrega.fallida` | Logística | Email / SMS | Operador y Entidad | Notificación de entrega no concretada con la justificación obligatoria ingresada por el chofer y aviso de reprogramación. |
| `mision.cumplida` | Incentivos | Email / WhatsApp | Donante | Felicitaciones por completar una misión, anunciando la nueva insignia desbloqueada y los puntos sumados. |

---

## 4. Despachadores de Comunicación

### 4.1. Despachador SMTP (Email)
* Utiliza `JavaMailSender` de Spring Framework.
* Durante desarrollo y testing, apunta al contenedor de **Mailpit**:
  - Host: `localhost` (o `mailpit` en red Docker).
  - Puerto: `1025`.
* Permite visualizar los correos formateados en HTML abriendo en el navegador [http://localhost:8025](http://localhost:8025).

### 4.2. Despachadores de SMS y WhatsApp
* Implementados como adaptadores de interfaz `MessageSenderAdapter`.
* En entorno local simulan el envío imprimiendo en el log estructurado del servicio los números de teléfono y los mensajes formateados.
* Listos para intercambiarse por clientes de producción (Twilio, Meta Cloud API) sin alterar la lógica de negocio.

---

## 5. Resiliencia, Idempotencia y Dead Letter Queue (DLQ)

* **Reintentos Automáticos**: RabbitMQ reintenta la entrega hasta 3 veces ante excepciones transitorias (ej. caída momentánea de Mailpit).
* **Cola de Mensajes Muertos (`dlq.notificaciones`)**: Si un mensaje falla de manera definitiva (ej. JSON corrupto o error irrecoverable), se redirige automáticamente al Dead Letter Exchange para auditoría manual sin bloquear la cola principal.
* **Garantía de Idempotencia**: El servicio registra en memoria/caché los identificadores únicos de los eventos procesados para prevenir el reenvío duplicado de notificaciones ante reintentos de red.
