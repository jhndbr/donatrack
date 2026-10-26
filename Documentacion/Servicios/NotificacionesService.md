# Especificación Técnica: Microservicio de Notificaciones (`notificaciones-service`)

El microservicio de **Notificaciones** es un consumidor asíncrono puro dentro de la arquitectura de **DonaTrack**. Su función primordial es escuchar eventos de dominio publicados en RabbitMQ por Donaciones, Logística e Incentivos, transformar dichos eventos en mensajes personalizados y despacharlos a través de múltiples canales de comunicación (Email, SMS y WhatsApp).

---

## 0. Flujos Principales Resumidos y Requerimientos Esenciales

### 0.1. Flujos de Notificación Clave
1. **Despacho Polimórfico Multicanal (CU-56)**:
   - Recibe eventos del Topic Exchange `donatrack.events`.
   - Resuelve el canal correspondiente (EMAIL, SMS, WHATSAPP) mediante `NotificationDispatcher` (Strategy Pattern).
   - Realiza el envío (simulado o SMTP con Mailpit en local, Twilio/Meta en producción) y audita el resultado sin afectar las transacciones de negocio origen (FT-07).
2. **Notificación de Inactividad de Donantes (CU-57)**:
   - Detecta donantes con más de 20 días sin interacción (evento `donante.inactivo`).
   - Envía correo persuasivo destacando su impacto acumulado e invitándolo a donar nuevamente.
3. **Notificación de Asignación de Donación (CU-58)**:
   - Evento `donacion.asignada`: se notifica tanto a la entidad beneficiaria (con el detalle de los bienes asignados) como al donante (informando la causa beneficiada).
4. **Notificaciones de Ludificación (CU-59, CU-60)**:
   - `mision.cumplida`: felicita al donante con insignia desbloqueada y puntos sumados.
   - `categoria.ascendida`: felicita al donante por ascender a `Colaborador`, `Sostenedor` o `Transformador`.
5. **Notificaciones del Circuito Logístico (CU-61)**:
   - `ruta.iniciada`: aviso a donantes y beneficiarias con **enlace al mapa interactivo de seguimiento en tiempo real**.
   - `entrega.confirmada`: comprobante formal de recepción con fecha, hora y camión responsable.
   - `entrega.fallida`: alerta inmediata a la beneficiaria, al donante y a **personas administradoras** con la justificación obligatoria de la falla y posibilidad de reprogramación.

### 0.2. Lo Necesario del Service
* **Consumo Asincrónico Desacoplado**: Exclusivamente `@RabbitListener` conectados a colas con Dead Letter Queue (DLQ).
* **Despacho Extensible**: Adaptadores polimórficos desacoplados de la lógica de mensajería (DIP / OCP).
* **Servidor SMTP**: Mailpit integrado en entorno local (`localhost:1025` / UI `localhost:8025`).
* **Resiliencia e Idempotencia**: Control de duplicados ante reintentos de red y preservación de fallos de envío (FT-06, FT-07).

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

---

## 6. TO-DO Maestro del Servicio (Gaps y Funcionalidades Faltantes según Enunciado y Casos de Uso)

### 6.1. Plantillas y Formato de Mensajes (CU-56 a CU-61)
- [x] **TODO-NOT-01 (CU-56..CU-61)**: Implementar motor de plantillas HTML (`NotificationTemplateService`) con header, branding institucional de DonaTrack, tarjetas de datos clave, botones de acción (CTA) y footer legal para todos los correos.
- [x] **TODO-NOT-01b (CU-61)**: En la notificación de `ruta.iniciada`, incluir el botón y enlace interactivo al mapa web con token de acceso para seguimiento directo sin login complejo.
- [x] **TODO-NOT-01c (CU-61)**: En la notificación de `entrega.confirmada`, adjuntar enlace y previsualización del comprobante formal con los datos del receptor y fecha/hora.
- [x] **TODO-NOT-01d (CU-61)**: En la notificación de `entrega.fallida`, incluir obligatoriamente el motivo/justificación de la falla y la advertencia de que la donación vuelve al depósito para ser replanificada.

### 6.2. Auditoría y Trazabilidad de Comunicaciones (CU-56, FT-07)
- [x] **TODO-NOT-02 (CU-56)**: Implementar entidad y repositorio de auditoría de notificaciones (`AuditoriaNotificacion` con `destinatario`, `canal`, `asunto`, `mensaje`, `estado`, `error_detalle`, `timestamp`, `intentos`) y endpoints de consulta en `NotificacionesController`.
- [x] **TODO-NOT-02b (FT-07)**: Asegurar que los fallos transitorios en proveedores externos de mensajería queden auditados con estado `FALLIDO` sin arrojar excepciones que fuercen rollback en la cola si ya se agotaron los reintentos.

### 6.3. Idempotencia y Deduplicación (FT-06)
- [x] **TODO-NOT-03 (FT-06)**: Implementar mecanismo de deduplicación con tabla relacional de eventos procesados (`RegistroEventoNotificacion` y `RegistroEventoNotificacionRepository`), descartando mensajes repetidos tras reintentos automáticos del broker y registrándolos como `DUPLICADO_IGNORADO`.

### 6.4. Adaptadores de Proveedores Reales (CU-56)
- [x] **TODO-NOT-04 (CU-56)**: Integrar soporte en `SmsNotificationSender` para Twilio REST API con credenciales seguras por variables de entorno y simulación estructurada en desarrollo.
- [x] **TODO-NOT-05 (CU-56)**: Integrar soporte en `WhatsAppNotificationSender` para Meta Cloud API (WhatsApp Business) con credenciales seguras y simulación estructurada en desarrollo.

### 6.5. Notificaciones Administrativas (CU-61)
- [x] **TODO-NOT-06 (CU-61)**: Despachar alerta prioritaria por correo (`[ALERTA OPERATIVA]`) a las **personas administradoras** (`admin@donatrack.org`) cada vez que ocurra una incidencia de entrega fallida o donación no recibida para su revisión y replanificación inmediata (CU-45).

