# 05. Guía de Finalización: Servicio de Notificaciones (`notificaciones-service`)

## 1. Resumen de Estado Actual
El módulo [`notificaciones-service`](file:///home/jhon/Documentos/Github/TP-DDSI/notificaciones-service/pom.xml) (puerto `8085`) es el consumidor asincrónico por excelencia del sistema:
* **Consumidores AMQP funcionales**: [`DonacionNotificacionesConsumer.java`](file:///home/jhon/Documentos/Github/TP-DDSI/notificaciones-service/src/main/java/com/donatrack/notificaciones/consumer/DonacionNotificacionesConsumer.java), [`LogisticaNotificacionesConsumer.java`](file:///home/jhon/Documentos/Github/TP-DDSI/notificaciones-service/src/main/java/com/donatrack/notificaciones/consumer/LogisticaNotificacionesConsumer.java) e [`IncentivosNotificacionesConsumer.java`](file:///home/jhon/Documentos/Github/TP-DDSI/notificaciones-service/src/main/java/com/donatrack/notificaciones/consumer/IncentivosNotificacionesConsumer.java).
* **Despachador Polimórfico**: [`NotificationDispatcher.java`](file:///home/jhon/Documentos/Github/TP-DDSI/notificaciones-service/src/main/java/com/donatrack/notificaciones/sender/NotificationDispatcher.java) con envío de emails HTML reales mediante [`EmailNotificationSender.java`](file:///home/jhon/Documentos/Github/TP-DDSI/notificaciones-service/src/main/java/com/donatrack/notificaciones/sender/EmailNotificationSender.java) hacia Mailpit (`localhost:1025`).
* **Plantillas HTML con Branding**: [`NotificationTemplateService.java`](file:///home/jhon/Documentos/Github/TP-DDSI/notificaciones-service/src/main/java/com/donatrack/notificaciones/template/NotificationTemplateService.java).
* **Auditoría e Idempotencia**: Entidades [`AuditoriaNotificacion.java`](file:///home/jhon/Documentos/Github/TP-DDSI/notificaciones-service/src/main/java/com/donatrack/notificaciones/domain/AuditoriaNotificacion.java) y [`RegistroEventoNotificacion.java`](file:///home/jhon/Documentos/Github/TP-DDSI/notificaciones-service/src/main/java/com/donatrack/notificaciones/domain/RegistroEventoNotificacion.java).

---

## 2. Cosas que FALLAN (Bugs Críticos a Corregir)

### 2.1. FALLA CRÍTICA DE PERSISTENCIA: Usa H2 en memoria violando la Entrega 4
* **Ubicación**: [`application.yml`](file:///home/jhon/Documentos/Github/TP-DDSI/notificaciones-service/src/main/resources/application.yml#L8-L12) y [`docker/init-scripts/01-init-databases.sql`](file:///home/jhon/Documentos/Github/TP-DDSI/docker/init-scripts/01-init-databases.sql#L1-L13).
* **Qué ocurre hoy**:
  El enunciado de la Entrega 4 (pág. 19, Requerimiento 2 y pág. 20, Consideraciones) exige:
  *"El Servicio de Logística deberá utilizar una base de datos documental, mientras que los servicios restantes deberán ser persistidos en una base de datos relacional. Cada servicio debe tener persistencia en un esquema relacional propio"*.
  Actualmente `notificaciones-service` usa H2 en memoria (`jdbc:h2:mem:notificaciones_db`). Al reiniciar el contenedor o el proceso, se borra toda la auditoría e historial de notificaciones.
* **Cómo corregirlo**:
  1. En `docker/init-scripts/01-init-databases.sql`, agregar:
     ```sql
     SELECT 'CREATE DATABASE notificaciones_db'
     WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'notificaciones_db')\gexec
     ```
  2. En `notificaciones-service/pom.xml`, agregar la dependencia de PostgreSQL:
     ```xml
     <dependency>
         <groupId>org.postgresql</groupId>
         <artifactId>postgresql</artifactId>
         <scope>runtime</scope>
     </dependency>
     ```
  3. En `notificaciones-service/src/main/resources/application.yml`, configurar PostgreSQL por defecto:
     ```yaml
     spring:
       datasource:
         url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/notificaciones_db}
         driver-class-name: org.postgresql.Driver
         username: ${SPRING_DATASOURCE_USERNAME:donatrack}
         password: ${SPRING_DATASOURCE_PASSWORD:donatrack_password}
       jpa:
         hibernate:
           ddl-auto: update
     ```

### 2.2. BUG EN ELECCIÓN DE CANAL: Hardcodea `CanalNotificacion.EMAIL` ignorando el medio de contacto preferido
* **Ubicación**: [`NotificacionesService.java`](file:///home/jhon/Documentos/Github/TP-DDSI/notificaciones-service/src/main/java/com/donatrack/notificaciones/service/NotificacionesService.java#L135-L368).
* **Qué ocurre hoy**:
  Todas las invocaciones a `despacharConAuditoria` envían `CanalNotificacion.EMAIL`, ignorando si el donante eligió `WHATSAPP` o `SMS` como medio predeterminado en `PersonaDonante.getMedioContactoPredeterminado()`.
* **Cómo corregirlo**:
  Leer el `canalPreferidoDonante` y el `contactoPreferidoDonante` que vendrán en los eventos actualizados de `common-dtos`:
  ```java
  CanalNotificacion canal = evento.getCanalPreferidoDonante() != null ? evento.getCanalPreferidoDonante() : CanalNotificacion.EMAIL;
  String destinatario = (canal == CanalNotificacion.EMAIL) ? evento.getEmailDonante() : evento.getContactoPreferidoDonante();
  despacharConAuditoria(clave, "DONACION_ASIGNADA", canal, destinatario, asunto, cuerpoHtml, cuerpoPlano);
  ```

### 2.3. BUG EN IDEMPOTENCIA: Marca como procesados eventos fallidos
* **Ubicación**: [`NotificacionesService.java`](file:///home/jhon/Documentos/Github/TP-DDSI/notificaciones-service/src/main/java/com/donatrack/notificaciones/service/NotificacionesService.java#L83-L105).
* **Qué ocurre hoy**:
  Cuando `dispatcher.despachar(...)` devuelve `false` (por ejemplo, servidor SMTP temporalmente no disponible), en la línea 104 hace:
  ```java
  eventoRepo.save(new RegistroEventoNotificacion(claveEvento, tipoEvento));
  ```
  Al guardar el registro de deduplicación a pesar del fallo, los reintentos automáticos de RabbitMQ serán ignorados en la línea 73 (`DUPLICADO_IGNORADO`), perdiéndose la notificación para siempre.
* **Cómo corregirlo**:
  Guardar en `RegistroEventoNotificacion` **únicamente si `exito == true`**. Si falló, no registrar la clave como completada para permitir reintentos.

### 2.4. BUG EN DEAD LETTER QUEUE (DLQ): Las colas no tienen configurado el DLX
* **Ubicación**: [`RabbitMqConfig.java`](file:///home/jhon/Documentos/Github/TP-DDSI/notificaciones-service/src/main/java/com/donatrack/notificaciones/config/RabbitMqConfig.java#L65-L81).
* **Qué ocurre hoy**:
  El método `crearCola(String nombreCola)` instancia `new Queue(nombreCola, true)` sin argumentos `x-dead-letter-exchange` ni `x-dead-letter-routing-key`. Aunque `donatrack.dlx` y `dlq.notificaciones` existen, ningún mensaje rechazado llega a la DLQ.
* **Cómo corregirlo**:
  Crear las colas asociando los argumentos:
  ```java
  QueueBuilder.durable(nombreCola)
          .deadLetterExchange("donatrack.dlx")
          .deadLetterRoutingKey("notificaciones.muertas")
          .build();
  ```

### 2.5. BUG EN COMPROBANTE DE ENTREGA: Omite fecha y hora
* **Ubicación**: [`NotificacionesService.java`](file:///home/jhon/Documentos/Github/TP-DDSI/notificaciones-service/src/main/java/com/donatrack/notificaciones/service/NotificacionesService.java#L232-L235).
* **Qué ocurre hoy**:
  El correo de `notificarEntregaConfirmada` no incluye la fecha ni la hora de recepción, incumpliendo la Entrega 3 (pág. 17, línea 824).
* **Cómo corregirlo**:
  Formatear `evento.getTimestamp()` (o `evento.getFechaHora()`) en `dd/MM/yyyy HH:mm:ss` e incluirlo en la tabla del comprobante HTML.

---

## 3. Cosas que FALTAN según el Enunciado

### 3.1. Consumidor para `DonanteRegistradoEvent` (Bienvenida y Credenciales CSV)
* **Requerimiento**: Entrega 1 (págs. 7 y 8).
* **Implementación guía**:
  1. En `RabbitMqConfig.java`, declarar la cola `q.notificaciones.donantes.registrados` con routing key `donante.registrado`.
  2. En `DonacionNotificacionesConsumer.java`, agregar `@RabbitListener(queues = "q.notificaciones.donantes.registrados")`.
  3. En `NotificacionesService.java`, crear `notificarBienvenidaDonante(DonanteRegistradoEvent evento)` que despache un correo con las credenciales temporales generadas y el enlace de acceso a la plataforma.

### 3.2. Adaptadores HTTP reales (con simulación elegante) para Twilio y Meta Cloud API
* **Archivos**: [`SmsNotificationSender.java`](file:///home/jhon/Documentos/Github/TP-DDSI/notificaciones-service/src/main/java/com/donatrack/notificaciones/sender/SmsNotificationSender.java) y [`WhatsAppNotificationSender.java`](file:///home/jhon/Documentos/Github/TP-DDSI/notificaciones-service/src/main/java/com/donatrack/notificaciones/sender/WhatsAppNotificationSender.java).
* **Implementación guía**:
  Si las credenciales están presentes en `application.yml`, despachar mediante `RestClient` HTTP contra las APIs oficiales de Twilio y Meta; si están vacías, imprimir el payload en el log con formato estructurado simulado y retornar `true`.

---

## 4. Checklist de Finalización para `notificaciones-service`
- [ ] Configurar PostgreSQL `notificaciones_db` y actualizar `01-init-databases.sql`.
- [ ] Despachar usando el canal preferido del donante (`EMAIL`, `SMS`, `WHATSAPP`).
- [ ] Registrar en `RegistroEventoNotificacion` solo tras un envío exitoso.
- [ ] Asociar argumentos `x-dead-letter-exchange` a las 7 colas AMQP.
- [ ] Incluir fecha y hora formateadas en el comprobante de entrega confirmada.
- [ ] Implementar escucha y envío de email de bienvenida para `DonanteRegistradoEvent`.
- [ ] Añadir `spring-boot-starter-actuator` en `pom.xml`.
