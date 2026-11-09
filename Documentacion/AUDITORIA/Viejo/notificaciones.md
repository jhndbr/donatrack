Voy a definir el Servicio de Notificaciones de DonaTrack como una especificación funcional y técnica detallada, tomando como base el enunciado oficial del TP Anual 2026 que compartiste y separando los requisitos obligatorios de las recomendaciones de diseño.

La idea es que puedas utilizar este documento como contexto para tu IA de desarrollo, para revisar tu microservicio Spring Boot, comprobar lo que ya implementaste y definir los flujos completos desde el frontend hasta el envío de cada notificación.

El enunciado establece tres aspectos fundamentales:

- Primera iteración: enviar un destinatario, un mensaje y un medio de notificación (correo electrónico, SMS o WhatsApp), simulando el envío.
- Segunda iteración: integrar los envíos reales y generar notificaciones automáticamente ante eventos de Donaciones e Incentivos.
- Integración con Logística: utilizar comunicación asíncrona mediante una cola de mensajes para que los fallos o los picos de carga del notificador no bloqueen los servicios de negocio.&#x20;

  Cátedra Diseño de Sistemas de Información - TP Anual 2026 (L, M, M) (1).pdf



# Especificación funcional: Servicio de Notificaciones — DonaTrack

## 1. Objetivo y responsabilidades del microservicio

El Servicio de Notificaciones es el componente responsable de recibir solicitudes o eventos que requieren informar a una persona donante, a una entidad beneficiaria o a una persona administradora, y gestionar el envío del mensaje por uno o varios medios de comunicación.

No debe decidir si una donación se asigna, si una entrega fue exitosa o si una misión se completó. Esas decisiones pertenecen a los servicios de dominio correspondientes. El notificador recibe la información del evento y se encarga de convertirla en una comunicación.

## Responsabilidades principales

1\. Recepción de solicitudes y eventos

Recibir solicitudes autorizadas desde el backend o mensajes de eventos de otros microservicios.

2\. Resolución del destinatario

Identificar al destinatario y disponer de sus datos de contacto válidos para el medio seleccionado.

3\. Construcción del mensaje

Generar el asunto, contenido, variables y enlaces necesarios según el tipo de evento y el canal.

4\. Gestión del envío

Enviar por email, SMS o WhatsApp, con simulación en la primera iteración y proveedores reales en la segunda.

5\. Historial y seguimiento

Registrar el estado, los intentos, los errores y los resultados de cada envío para poder auditarlo.

### Qué no debería hacer

- Gestionar las donaciones o modificar sus estados.
- Asignar donaciones a entidades.
- Calcular misiones, insignias o categorías de donantes.
- Calcular rutas o modificar el estado de los camiones.
- Confiar en que el frontend indique arbitrariamente que ocurrió un evento de negocio.
- Acoplar la lógica de negocio a un proveedor específico de correo, SMS o WhatsApp.

Estas responsabilidades deben mantenerse en sus respectivos servicios.

## 2. Eventos que debe cubrir

La siguiente matriz reúne los flujos establecidos en las entregas del enunciado. Los eventos de logística corresponden a la integración posterior de la entrega 3; no todos forman parte de la primera iteración.

| Evento                         | Destinatario                       | Contenido mínimo                                  |
| ------------------------------ | ---------------------------------- | ------------------------------------------------- |
| Inactividad superior a 20 días | Donante                            | Invitación a volver a donar                       |
| Donación asignada              | Entidad beneficiaria               | Bienes, cantidades e información de la asignación |
| Donación asignada              | Donante                            | Confirmación de asignación y entidad beneficiaria |
| Misión completada              | Donante                            | Misión e insignia obtenida                        |
| Cambio de categoría            | Donante                            | Categoría anterior y nueva categoría              |
| Inicio de ruta                 | Entidad y donante                  | Aviso de salida y enlace al mapa                  |
| Entrega exitosa                | Entidad y donante                  | Comprobante con fecha, hora y camión              |
| Entrega no satisfactoria       | Entidad, donante y administradores | Motivo, estado y próximos pasos                   |
| Bienvenida al registrarse      | Donante                            | Bienvenida y acceso inicial                       |

Los primeros cinco eventos aparecen expresamente en la entrega 2, y los tres de logística se incorporan en la entrega 3. El correo de bienvenida se define en la sección de registro de donantes de la entrega 1.&#x20;

Cátedra Diseño de Sistemas de Información - TP Anual 2026 (L, M, M) (1).pdf



Importante: las notificaciones por cambios de estado de donaciones y por recompensas también figuran en los requerimientos generales de UI/UX. Conviene contemplar la extensión del catálogo de eventos para cubrirlos a medida que se implementan las funcionalidades.

## 3. Arquitectura y recorrido completo

El flujo normal debe ser el siguiente:

Frontend web (SSR)

Donante, entidad beneficiaria o administrador

API Gateway / servicio de aplicación

Autenticación, autorización y operación de negocio

Servicio de dominio

Donaciones, Incentivos o Logística

Cola de eventos / mensajes

Entrega asíncrona y desacoplamiento

Servicio de Notificaciones

Valida, registra, prepara y procesa el envío

Email

SMS

WhatsApp

### Regla arquitectónica fundamental

El frontend no debería enviar directamente correos, SMS ni WhatsApp. Debe interactuar con los servicios de aplicación y estos deben generar eventos cuando se producen hechos de negocio.

Por ejemplo, si un administrador asigna una donación a una entidad:

1. El frontend solicita la asignación.
2. El Servicio de Donaciones valida y registra la asignación.
3. El servicio genera el evento `DONATION_ASSIGNED`.
4. El evento se publica en la cola.
5. El Servicio de Notificaciones lo consume.
6. Se crean las notificaciones destinadas al donante y a la entidad.
7. Se intenta enviar cada notificación por los canales elegidos.
8. Se registran los resultados.

El frontend puede consultar el historial o estado de una notificación, pero no debería ser responsable de iniciar el envío por el mero hecho de mostrar una pantalla.

La entrega 3 exige explícitamente una integración asíncrona mediante cola entre los servicios de dominio y el notificador.&#x20;

Cátedra Diseño de Sistemas de Información - TP Anual 2026 (L, M, M) (1).pdf



## 4. Flujos completos desde el frontend

En esta sección se especifica qué ve el usuario, qué acción realiza, qué servicio interviene, qué evento se genera y qué debe hacer el Servicio de Notificaciones.

### Flujo 1. Registro de un donante y correo de bienvenida

Entrega 1

### 1

Frontend: formulario de registro

El donante introduce los datos personales o jurídicos, su email y, opcionalmente, teléfono o WhatsApp. El sistema valida los campos y el formato de contacto.

### 2

Servicio de Donaciones

Crea el registro del donante. Si corresponde, el proceso de autenticación prepara el acceso inicial.

### 3

Servicio de Notificaciones

Recibe la solicitud de bienvenida, crea el registro del envío y envía un correo con información de acceso inicial.

### 4

Frontend: resultado

Muestra que el registro se completó. Si el correo falla, informa que el registro existe pero que el envío está pendiente o tuvo un error, según el resultado real.

Reglas importantes

- El fallo de un correo no debería deshacer el registro del donante.
- No enviar contraseñas permanentes en texto plano. Es preferible utilizar un enlace de activación o recuperación temporal.
- Si se reintenta el proceso, evitar que se generen múltiples correos de bienvenida por la misma operación.

El uso de una cola y un mecanismo de reintento para este flujo es una recomendación de arquitectura; el enunciado inicial exige el envío de bienvenida, pero no prescribe estos mecanismos.

### Flujo 2. Donante inactivo durante más de 20 días

Entrega 2

Este proceso debe ser automático. El usuario no tiene que entrar en el frontend para que se produzca.

Proceso programado

1\. Un trabajo programado del Servicio de Donaciones busca donantes cuya última interacción relevante con la plataforma supera los 20 días.

2\. Se verifica que el donante siga activo y que no se le haya enviado ya el recordatorio correspondiente al período.

3\. Donaciones publica un evento de inactividad en la cola.

4\. Notificaciones genera el mensaje y determina el medio permitido según las preferencias y los datos de contacto.

5\. El usuario recibe el recordatorio y puede entrar a DonaTrack para consultar las donaciones y necesidades disponibles.

Ejemplo de mensaje:

Asunto: Te esperamos nuevamente en DonaTrack

¡Hola! Hace un tiempo que no registrás actividad en DonaTrack. Tu colaboración ayuda a que más recursos lleguen a quienes los necesitan. Cuando quieras, podés ingresar a la plataforma y conocer las nuevas oportunidades para donar.

Puntos que deben definirse en el código: qué significa exactamente una interacción, si cuentan los inicios de sesión o solo acciones de negocio, la frecuencia máxima de recordatorios y cómo se evita enviar varios mensajes al mismo donante en una misma ejecución. El requisito especifica el umbral de 20 días, pero no resuelve estas políticas.

### Flujo 3. Una donación se asigna a una entidad beneficiaria

Entrega 2

Este es uno de los flujos centrales del microservicio porque implica dos destinatarios diferentes.

A. Administrador confirma la asignación

Desde el frontend administrativo, selecciona una entidad entre los resultados del algoritmo de asignación. El Servicio de Donaciones valida la selección y persiste el cambio de estado.

B. Se genera un único evento de negocio

El Servicio de Donaciones publica `DONATION_ASSIGNED`, incluyendo los identificadores de la donación, del donante y de la entidad, junto con los datos necesarios para construir las notificaciones.

C. Entidad beneficiaria

Recibe una notificación con los bienes asignados, sus cantidades y el acceso a la información de la donación.

D. Persona donante

Recibe la confirmación de que su donación fue asignada y la identificación de la entidad beneficiaria, conforme a los permisos de acceso.

Reglas de negocio y consistencia

- La notificación se genera solo después de confirmar la asignación.
- Si la asignación falla, no debe anunciarse como realizada.
- Cada destinatario tiene su propio registro de notificación y estado de envío.
- Si falla el email de la entidad, el envío al donante debe poder continuar.
- Si se modifica una asignación, debe definirse si corresponde enviar una notificación de actualización o de cancelación.

La entrega 2 requiere notificar tanto a la entidad como al donante.&#x20;

Cátedra Diseño de Sistemas de Información - TP Anual 2026 (L, M, M) (1).pdf



### Flujo 4. Una persona donante completa una misión

Entrega 2

1\. El usuario realiza una donación y el Servicio de Donaciones registra el cambio correspondiente.

2\. El Servicio de Incentivos procesa la actividad y actualiza el progreso de sus misiones.

3\. Cuando se cumple una condición, Incentivos registra la misión como completada y la insignia correspondiente.

4\. Incentivos emite `MISSION_COMPLETED`.

5\. Notificaciones genera el mensaje para el donante y registra el resultado de cada canal utilizado.

6\. El frontend muestra la insignia en la sección de Incentivos cuando el usuario vuelve a consultar su perfil.

Ejemplo:

¡Misión completada!

Donaciones exitosas

Completaste tu misión y desbloqueaste una nueva insignia. Ingresá a DonaTrack para consultar tu progreso y tus logros.

Reglas esenciales

- El Servicio de Notificaciones no debe calcular el progreso de las misiones.
- Una misión completada no debe generar dos notificaciones por recibir dos veces el mismo evento.
- La notificación debe identificar la misión o insignia concreta, no limitarse a un mensaje genérico.
- La difusión de la insignia en redes sociales es un flujo diferente: la entrega 2 pide una automatización específica para generar una imagen y publicar el logro. No debe confundirse con el envío de email, SMS o WhatsApp.

### Flujo 5. Cambio de categoría de un donante

Entrega 2

Este flujo es parecido al anterior, pero el hecho de negocio es la promoción de categoría.

1. Incentivos evalúa las misiones completadas.
2. El servicio determina que el donante cumple las condiciones para subir de categoría.
3. Se registra la nueva categoría.
4. Se publica `DONOR_CATEGORY_CHANGED`, con la categoría anterior y la nueva.
5. Notificaciones crea el mensaje y lo envía por el canal configurado.
6. El frontend muestra la nueva categoría en el perfil.

Ejemplo:

Asunto: ¡Subiste de categoría en DonaTrack!

¡Felicitaciones! Tu participación te permitió avanzar de Colaborador a Sostenedor. Gracias por seguir contribuyendo a la comunidad. Podés consultar tus misiones e insignias en tu perfil.

Si una misma operación completa una misión y provoca un cambio de categoría, ambos eventos pueden ser válidos. Debe decidirse si se envían dos notificaciones o si se combinan en un único mensaje; esa política no está especificada por el enunciado.

### Flujo 6. Inicio de ruta de un camión

Entrega 3

Este flujo se inicia desde la aplicación o interfaz utilizada por el chofer, no desde el perfil del donante.

1. El chofer abre la ruta asignada y confirma su inicio.
2. Logística valida el inicio y actualiza las entregas que correspondan al estado de traslado.
3. Logística publica `ROUTE_STARTED`.
4. Notificaciones identifica las entregas y destinatarios relacionados con esa ruta.
5. Envía el aviso a cada entidad beneficiaria y a cada donante involucrado.
6. Incluye un enlace al mapa interactivo de seguimiento en tiempo real.
7. El frontend de donantes y entidades muestra la entrega como activa y permite consultar el seguimiento.

El mensaje debe estar vinculado a la entrega correspondiente. No debe compartirse información de otras donaciones o entidades que el destinatario no tenga permiso para consultar.

### Flujo 7. Entrega realizada con éxito

Entrega 3

1. La entidad beneficiaria abre la entrega pendiente desde su frontend.
2. Confirma la recepción y carga las fotos requeridas.
3. Logística valida la operación y registra la entrega como realizada.
4. Se publica `DELIVERY_CONFIRMED`.
5. Notificaciones genera una comunicación para la entidad y otra para el donante.
6. El mensaje incluye el comprobante de entrega con fecha, hora y camión responsable.
7. El frontend muestra la entrega como completada y permite consultar la información de trazabilidad.

Ejemplo:

Entrega confirmada

Comprobante ilustrativo

Fecha y hora

Registradas por Logística

Camión

Identificado por patente

Estado

Entregada

Los datos del comprobante deben proceder de Logística. Notificaciones no debería inventar la fecha de entrega ni asumir que la operación fue exitosa porque el frontend mostró un mensaje de confirmación.

### Flujo 8. Entrega no satisfactoria

Entrega 3

Este flujo es especialmente importante porque requiere informar a tres grupos de destinatarios.

1. La entrega no puede concretarse o la entidad indica que no recibió los bienes.
2. Logística registra el resultado, la justificación y el estado correspondiente.
3. Se publica `DELIVERY_FAILED` o un evento equivalente con el motivo y la identificación de la entrega.
4. Notificaciones crea envíos separados para la entidad, el donante y las personas administradoras.
5. Los destinatarios reciben el motivo de la incidencia y, cuando corresponda, el estado del caso.
6. El frontend permite consultar la incidencia y el estado actualizado de la entrega.
7. Si se replanifica la entrega, una notificación posterior puede informar el nuevo recorrido.

El mensaje debe distinguir una entrega fallida de una entrega todavía pendiente. También debe evitar asegurar que habrá una nueva entrega si esa decisión aún no fue tomada por Logística o por el administrador.

La entrega 3 exige notificar los casos no satisfactorios y contemplar la posible replanificación.&#x20;

Cátedra Diseño de Sistemas de Información - TP Anual 2026 (L, M, M) (1).pdf



## 5. Qué debería mostrar el frontend de DonaTrack

Hay que distinguir dos experiencias diferentes: la interfaz que utiliza una persona para consultar sus notificaciones y la interfaz administrativa para supervisar los envíos.

### A. Panel del donante

## Notificaciones

Ejemplo conceptual de la interfaz

3 nuevas

Tu donación fue asignada

Nueva

Tu donación ya tiene una entidad beneficiaria asignada. Consultá los detalles de la donación.

Hace 10 minutos

Completaste una misión

Desbloqueaste una insignia. Revisá tus logros y el progreso de tus misiones.

Ayer

Entrega en camino

Tu donación está en traslado. Podés consultar la ubicación del camión.

Hace 2 días

Los registros, fechas y estados son ilustrativos; no representan datos reales de DonaTrack.

La pantalla debería permitir:

- Ver notificaciones nuevas y anteriores.
- Diferenciar las leídas de las no leídas.
- Consultar la fecha, el tipo y el contenido.
- Abrir la donación, la misión o la entrega relacionada.
- Filtrar por tipo de evento.
- Consultar el historial sin que las notificaciones antiguas desaparezcan automáticamente.

El enunciado exige los avisos de negocio, pero no define expresamente una bandeja persistente de notificaciones dentro de la aplicación. La recomiendo como funcionalidad complementaria, especialmente si se quiere que el usuario pueda revisar los avisos cuando no estaba conectado o si un canal externo falla.

### B. Panel de la entidad beneficiaria

La entidad necesita ver notificaciones relacionadas exclusivamente con sus necesidades y entregas.

- Nueva donación asignada.
- Inicio de ruta con enlace de seguimiento.
- Entrega confirmada y comprobante.
- Entrega no satisfactoria y motivo.
- Actualizaciones de una entrega, cuando correspondan.

Al seleccionar una notificación, el usuario debe llegar a la pantalla de la donación o entrega asociada, siempre que tenga autorización para acceder.

### C. Panel administrativo de notificaciones

## Supervisión de envíos

Procesadas

# —

En el período seleccionado

Fallidas

# —

Pendientes de revisión

Ejemplo de estructura; los guiones indican que no se consultaron datos reales.

Filtros sugeridos

Fecha

Canal

Estado

Tipo de evento

Destinatario

Acciones administrativas

- Consultar el detalle y los intentos de envío.
- Revisar errores de proveedores.
- Reintentar envíos fallidos, si corresponde.
- Identificar eventos duplicados o mensajes atascados.

Esta consola es una recomendación de operación, no una pantalla exigida expresamente en el enunciado. Debe estar protegida para que solo el personal autorizado pueda consultar datos de contacto y detalles de los mensajes.

## 6. Qué componentes debería tener el backend

Para tu arquitectura Java con Spring Boot, una separación razonable sería la siguiente. Los nombres son orientativos: no implican que debas crear un microservicio adicional para cada componente.

`NotificationController`

Expone la API REST para operaciones administrativas o solicitudes explícitas autorizadas. No debe contener la lógica de envío.

`NotificationEventConsumer`

Consume los mensajes publicados por Donaciones, Incentivos y Logística desde la cola.

`NotificationApplicationService`

Orquesta la creación de notificaciones, validaciones, persistencia y procesamiento.

`RecipientResolver`

Obtiene los datos de contacto del destinatario y verifica qué canales pueden utilizarse.

`NotificationTemplateService`

Construye asuntos y mensajes a partir del evento, el destinatario y las plantillas.

`NotificationDispatcher`

Selecciona el adaptador correspondiente al canal y coordina el envío.

`EmailSender`, `SmsSender`, `WhatsAppSender`

Adaptadores independientes para simular o invocar los proveedores reales.

`NotificationRepository` y `NotificationAttemptRepository`

Persisten las notificaciones y sus intentos, para conservar la trazabilidad.

### Modelo de dominio sugerido

| Entidad                  | Responsabilidad                                                         |
| ------------------------ | ----------------------------------------------------------------------- |
| `Notification`           | Representa una notificación destinada a un destinatario por un canal.   |
| `NotificationAttempt`    | Registra cada intento de envío y su resultado.                          |
| `NotificationTemplate`   | Define el contenido reutilizable de un tipo de mensaje.                 |
| `NotificationPreference` | Representa preferencias de canales, si se implementa esa funcionalidad. |
| `ProcessedEvent`         | Permite identificar eventos ya procesados y evitar duplicaciones.       |

No todas estas clases son obligatorias para la primera iteración. `Notification` y el componente notificador son el núcleo; los intentos, las preferencias y el registro de eventos procesados son extensiones recomendables para una solución más robusta.

#### Campos sugeridos de `Notification`

- `id`: identificador único.
- `eventId`: identificador del evento de origen.
- `eventType`: tipo de evento.
- `recipientId`: identificador del destinatario.
- `recipientType`: donante, entidad o administrador.
- `channel`: email, SMS o WhatsApp.
- `destination`: dirección o número utilizado, con las medidas de protección apropiadas.
- `subject`: asunto, cuando corresponda.
- `message`: contenido del mensaje o referencia a la plantilla y sus variables.
- `status`: estado del envío.
- `createdAt`, `updatedAt`, `sentAt`: fechas relevantes.
- `attemptCount`: cantidad de intentos realizados.
- `lastError`: descripción técnica sanitizada del último error.

#### Estados recomendados

PENDING

Creada, todavía no procesada.

PROCESSING

Un trabajador está procesando el envío.

SENT

El adaptador completó el envío o la simulación.

RETRY_PENDING

Hubo un fallo recuperable y se programó otro intento.

FAILED

El envío terminó sin éxito y no tiene más reintentos programados.

No conviene usar `SENT` como sinónimo de `DELIVERED`: que un proveedor acepte un mensaje no significa necesariamente que haya llegado al dispositivo del destinatario. Si más adelante se integran confirmaciones de entrega del proveedor, conviene modelarlas por separado.

## 7. API REST que conviene definir

El enunciado solicita una API REST para los servicios, pero no prescribe las rutas exactas del Servicio de Notificaciones. Esta es una propuesta de contrato para que el frontend y el backend puedan integrarse.

| Método  | Endpoint propuesto                    | Función                                               |
| ------- | ------------------------------------- | ----------------------------------------------------- |
| `POST`  | `/api/notifications`                  | Solicitar un envío explícito y autorizado.            |
| `GET`   | `/api/notifications/me`               | Consultar las notificaciones del usuario autenticado. |
| `GET`   | `/api/notifications/me/unread-count`  | Consultar cuántas están sin leer.                     |
| `PATCH` | `/api/notifications/{id}/read`        | Marcar una notificación como leída.                   |
| `GET`   | `/api/admin/notifications`            | Buscar notificaciones para supervisión.               |
| `GET`   | `/api/admin/notifications/{id}`       | Consultar el detalle de un envío.                     |
| `POST`  | `/api/admin/notifications/{id}/retry` | Solicitar un reintento autorizado.                    |

Los endpoints de la bandeja y de administración son opcionales según el alcance que decidas implementar. El endpoint `POST /api/notifications` debe protegerse para evitar que cualquier usuario pueda enviar mensajes arbitrarios o suplantar a otro destinatario.

Importante: los eventos de negocio no deberían depender de que el frontend llame a ese endpoint. Por ejemplo, asignar una donación debe generar el evento desde Donaciones, y no requerir una segunda petición del navegador para avisar al destinatario.

### Ejemplo de solicitud de envío explícito

```

{
  "recipientId": "123",
  "channel": "EMAIL",
  "subject": "Bienvenido a DonaTrack",
  "message": "Tu registro se completó correctamente."
}

```

Este contrato es ilustrativo. En producción, sería preferible que las plantillas de negocio se seleccionen mediante un tipo de notificación permitido, en lugar de aceptar cualquier contenido del cliente.

### Ejemplo de evento entre servicios

```

{
  "eventId": "evt-uuid",
  "eventType": "DONATION_ASSIGNED",
  "occurredAt": "2026-10-27T10:30:00Z",
  "donationId": "donation-456",
  "donorId": "donor-123",
  "beneficiaryId": "beneficiary-789",
  "correlationId": "operation-uuid"
}

```

El evento contiene identificadores y metadatos para relacionar las operaciones. Los nombres y el formato son propuestas de diseño, no un contrato impuesto por el TP. El consumidor puede consultar datos adicionales a los servicios correspondientes o recibirlos en el evento, según la estrategia de integración que adopte el equipo.

## 8. Cómo gestionar los canales de envío

El servicio debe mantener separada la lógica de negocio de la tecnología utilizada para enviar mensajes.

Email

Permite enviar mensajes con asunto, texto o HTML. Es el canal principal para bienvenida y comunicaciones detalladas.

Implementación sugerida: `JavaMailSender` o un adaptador de proveedor externo.

SMS

Adecuado para avisos breves, como una entrega próxima o una incidencia logística.

Implementación sugerida: una interfaz `SmsSender` con un proveedor configurable.

WhatsApp

Permite comunicar avisos y enlaces de seguimiento mediante la integración correspondiente.

Implementación sugerida: `WhatsAppSender`, independiente del proveedor y de las plantillas de negocio.

### Primera iteración frente a envíos reales

En la primera iteración del TP, el sistema simula las llamadas a los servicios externos y marca las notificaciones como completadas. En la segunda, se solicita la integración concreta con los medios de comunicación.&#x20;

Cátedra Diseño de Sistemas de Información - TP Anual 2026 (L, M, M) (1).pdf



Por eso, te recomiendo disponer de dos implementaciones intercambiables:

- `SimulatedEmailSender`, `SimulatedSmsSender` y `SimulatedWhatsAppSender`.
- Adaptadores reales para cada proveedor que se incorpore.

La selección puede depender de la configuración de Spring, por ejemplo mediante perfiles o propiedades de configuración. Así podrás probar la lógica completa sin credenciales externas y luego activar los envíos reales sin reescribir el dominio.

No simules que un mensaje llegó realmente a un teléfono: registrá que la operación simulada finalizó correctamente. En producción, diferenciá entre envío aceptado y entrega confirmada.

## 9. Errores, reintentos y duplicaciones

Esta es una de las partes más importantes para que el servicio sea confiable.

### Flujo de fallo

Notificación pendiente

Intentar enviar

Éxito

Registrar resultado y finalizar.

Error

Clasificar el error.

Error recuperable: programar reintento. Error definitivo: registrar fallo y permitir revisión.

### Reglas que debería cumplir

1. Persistencia antes del envío: registrar la notificación para no perder la operación si el proceso se interrumpe.
2. Reintentos limitados: usar una política configurable de espera progresiva para errores transitorios.
3. Idempotencia: procesar el mismo evento más de una vez sin crear duplicados lógicos.
4. Aislamiento por canal: un error en SMS no debe impedir el envío por email.
5. Cola de mensajes fallidos: conservar los eventos que no pueden procesarse y permitir su análisis posterior.
6. Trazabilidad: registrar el evento de origen, el identificador de la notificación y cada intento.
7. Errores seguros: no almacenar contraseñas, tokens ni información sensible innecesaria en los logs.

### ¿Cómo evitar notificaciones duplicadas?

Imaginá que el Servicio de Donaciones publica `DONATION_ASSIGNED`, pero la cola entrega el mismo evento dos veces.

Una solución recomendable es tener un identificador único de evento y una restricción de unicidad para la operación de notificación que se pretende realizar. La clave de idempotencia podría combinar:

- `eventId`
- `recipientId`
- `channel`
- `notificationType`

La clave concreta debe reflejar si el mismo evento puede legítimamente originar varias notificaciones para una misma persona. Por ejemplo, dos canales distintos deben poder generar dos envíos diferentes.

Esto evita duplicaciones lógicas en tu sistema. No garantiza por sí solo que un proveedor externo nunca entregue dos mensajes si se pierde la respuesta de una operación; esa garantía depende también de las capacidades del proveedor.

## 10. Persistencia y estructura de datos

Para el Servicio de Notificaciones recomiendo una base relacional propia, coherente con el requisito del TP de utilizar persistencia relacional en los servicios distintos de Logística.&#x20;

Cátedra Diseño de Sistemas de Información - TP Anual 2026 (L, M, M) (1).pdf



Un esquema inicial podría ser:

```

CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    event_id VARCHAR(150),
    event_type VARCHAR(80) NOT NULL,
    recipient_id VARCHAR(150) NOT NULL,
    recipient_type VARCHAR(30) NOT NULL,
    channel VARCHAR(20) NOT NULL,
    destination VARCHAR(255),
    subject VARCHAR(255),
    message TEXT,
    status VARCHAR(30) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    sent_at TIMESTAMP,
    last_error TEXT
);

CREATE TABLE notification_attempts (
    id UUID PRIMARY KEY,
    notification_id UUID NOT NULL
        REFERENCES notifications(id),
    attempt_number INTEGER NOT NULL,
    attempted_at TIMESTAMP NOT NULL,
    status VARCHAR(30) NOT NULL,
    provider_reference VARCHAR(255),
    error_code VARCHAR(100),
    error_message TEXT
);

CREATE INDEX idx_notifications_recipient
    ON notifications(recipient_id, created_at);

CREATE INDEX idx_notifications_status
    ON notifications(status, created_at);

```

Es un esquema ilustrativo: hay que ajustarlo a tus entidades actuales, al motor de base de datos y a la estrategia de persistencia que ya utilices.

Si la bandeja de notificaciones forma parte de la aplicación, conviene agregar campos como `read_at` y una política de retención. Si se requiere idempotencia, debe agregarse también una clave única adecuada o una tabla de eventos procesados.

Para la integración confiable, el patrón Transactional Outbox que el enunciado propone como bonus en la entrega 6 es especialmente útil: el servicio de dominio guarda el cambio de negocio y el evento en la misma transacción, y un proceso independiente publica el evento en la cola.

## 11. Seguridad y privacidad

El notificador trabaja con direcciones de correo, teléfonos y mensajes que pueden contener información sobre donaciones y entidades beneficiarias. Por eso, debe contemplar:

- Autenticación y autorización para cualquier operación expuesta por REST.
- Validación del origen de los eventos o protección del canal de mensajería.
- Control de acceso para que un donante no pueda leer notificaciones de otra persona.
- Protección de datos de contacto y contenido sensible en la base de datos y los logs.
- Gestión segura de credenciales de los proveedores mediante variables de entorno o un gestor de secretos.
- Validación de enlaces incluidos en los mensajes.
- Control de frecuencia para evitar envíos excesivos.
- Preferencias de contacto y requisitos de consentimiento cuando correspondan al canal utilizado.
- Manejo de bajas y restricciones de comunicaciones no esenciales.

Una distinción útil es que las comunicaciones operativas —por ejemplo, la confirmación de una entrega— no deben confundirse con mensajes promocionales. Las políticas de envío deben tener en cuenta la naturaleza de cada comunicación y los requisitos aplicables.

## 12. Checklist de auditoría del microservicio

Podés utilizar esta lista para revisar tu implementación actual. Los elementos marcados como recomendaciones son mejoras de robustez, no requisitos expresos de todas las entregas.

## Auditoría de implementación

0/20

### Primera iteración

Existe un componente notificador que recibe destinatario, mensaje y canal.

Obligatorio

Se soportan email, SMS y WhatsApp.

Obligatorio

Los envíos externos se simulan y se registran como completados.

Obligatorio

La lógica de negocio está separada de los adaptadores de canal.

Recomendado

### Eventos e integración

Se notifica al donante después de más de 20 días de inactividad.

Entrega 2

Se notifica a la entidad y al donante cuando se asigna una donación.

Entrega 2

Se notifica al donante al completar una misión y cambiar de categoría.

Entrega 2

Se integra con los eventos de inicio de ruta, entrega exitosa y entrega no satisfactoria.

Entrega 3

La integración de dominio con Notificaciones utiliza una cola asíncrona.

Entrega 3

Los eventos se generan desde los servicios de negocio, no desde acciones arbitrarias del navegador.

Diseño

### Persistencia y confiabilidad

Cada envío tiene un estado y una identidad propia.

Recomendado

Se conserva un historial de intentos y errores.

Recomendado

Los eventos repetidos no generan duplicados lógicos.

Recomendado

Los fallos transitorios se pueden reintentar sin bloquear al productor.

Recomendado

Se conserva la trazabilidad entre evento, notificación y operación de negocio.

Diseño

### Frontend y seguridad

Las notificaciones llevan al recurso relacionado, con autorización.

Recomendado

El usuario puede consultar el resultado sin depender del canal externo.

Recomendado

Los datos de contacto están protegidos y no se exponen a otros usuarios.

Seguridad

La interfaz informa errores y estados de procesamiento reales.

UX

Los envíos administrativos y reintentos están protegidos.

Seguridad

Copiar checklist

## 13. Prompt completo para auditar tu microservicio con una IA

Este prompt está pensado para ejecutarlo en la raíz de tu proyecto DonaTrack, donde la IA pueda inspeccionar el repositorio, los servicios Docker, el frontend, las APIs, los modelos de datos y la configuración de mensajería.

# Auditoría integral del Servicio de Notificaciones de DonaTrack

## Contexto del proyecto

Estoy desarrollando DonaTrack, una aplicación web para la gestión y trazabilidad de donaciones, con arquitectura distribuida en microservicios, backend Java con Spring Boot, frontend web, APIs REST, persistencia y despliegue mediante Docker.

Necesito que realices una auditoría técnica y funcional exhaustiva del Servicio de Notificaciones y de todas sus integraciones con el resto del sistema.

No quiero que modifiques código inmediatamente. Primero quiero que inspecciones el repositorio completo, reconstruyas la arquitectura real y produzcas documentación detallada que permita identificar qué está implementado, qué está incompleto, qué está mal implementado y qué falta desarrollar.

## 1. Inspección inicial del repositorio

1. Identifica el Servicio de Notificaciones y sus módulos, paquetes, clases, controladores, servicios, entidades, repositorios, DTOs, excepciones, configuraciones y pruebas.
2. Examina su base de datos, migraciones, Dockerfile, configuración Docker Compose y variables de entorno.
3. Identifica las colas, exchanges, topics, consumidores, productores y mecanismos de integración existentes.
4. Examina el frontend y determina qué pantallas, acciones y flujos están relacionados con las notificaciones.
5. Inspecciona los servicios de Donaciones, Incentivos, Logística y Autenticación para reconstruir los flujos de eventos.
6. Revisa los contratos REST, la documentación, los diagramas y las pruebas disponibles.
7. No presupongas que una funcionalidad existe porque haya una clase, un endpoint o una interfaz. Verifica su implementación real y sus conexiones.

## 2. Especificación funcional que debe verificarse

El Servicio de Notificaciones debe permitir enviar mensajes a un destinatario por email, SMS y WhatsApp.

En la primera iteración, los proveedores externos se simulan y las notificaciones se marcan como completadas.

En la segunda iteración, deben integrarse los envíos reales y generarse notificaciones ante los siguientes eventos:

- Inactividad de una persona donante durante más de 20 días.
- Asignación de una donación a una entidad beneficiaria.
- Asignación de una donación a una persona donante.
- Cumplimiento de una misión.
- Cambio de categoría de una persona donante.

En la integración con Logística deben contemplarse:

- Inicio de ruta: notificar a las entidades y donantes involucrados, incluyendo un enlace al mapa de seguimiento.
- Entrega exitosa: notificar a la entidad y al donante, incluyendo fecha, hora y camión responsable.
- Entrega no satisfactoria: notificar a la entidad, al donante y a las personas administradoras, incluyendo el motivo y el estado correspondiente.

También debe revisarse el envío de bienvenida al registrarse un donante.

La integración entre los servicios de dominio y el Servicio de Notificaciones debe ser asíncrona mediante una cola de mensajes.

Utiliza estos puntos como requisitos de auditoría, distinguiendo las entregas y sin afirmar que todos deben estar implementados en la misma etapa.

## 3. Auditoría del modelo de dominio

Analiza las entidades y relaciones existentes.

Verifica si el diseño permite:

- Representar una notificación individual por destinatario y canal.
- Registrar el evento que originó cada notificación.
- Diferenciar tipo de evento, canal, destinatario y estado.
- Registrar fecha de creación, procesamiento, envío y último error.
- Registrar intentos de envío.
- Evitar duplicaciones ante la recepción repetida de eventos.
- Identificar las notificaciones asociadas a donaciones, misiones y entregas.
- Gestionar plantillas y variables del mensaje.
- Consultar el historial de notificaciones.

Identifica clases innecesarias, responsabilidades mezcladas, duplicaciones y problemas de acoplamiento.

No propongas nuevas entidades si las existentes ya resuelven correctamente la necesidad.

## 4. Auditoría de eventos e integraciones

Para cada evento, documenta:

- Servicio productor.
- Operación que lo origina.
- Momento exacto en que se publica.
- Contrato del evento.
- Identificador único del evento.
- Datos incluidos y datos consultados externamente.
- Cola o mecanismo utilizado.
- Consumidor responsable.
- Destinatarios que deben recibir la notificación.
- Plantilla y canales utilizados.
- Persistencia del resultado.
- Manejo de errores y reintentos.

Verifica especialmente que una notificación no se genere antes de que el cambio de negocio se haya confirmado.

Comprueba si el sistema evita duplicaciones, pérdida de eventos, mensajes fuera de orden y notificaciones inconsistentes.

Evalúa la necesidad de utilizar Transactional Outbox cuando corresponda, sin implementarlo durante esta primera auditoría.

## 5. Auditoría de los flujos del frontend

Recorre el código del frontend y documenta cada flujo existente.

Para cada flujo, especifica:

1. Pantalla inicial.
2. Rol del usuario.
3. Acción realizada.
4. Endpoint invocado.
5. Servicio backend responsable.
6. Cambio de estado de negocio.
7. Evento generado.
8. Notificación creada.
9. Resultado esperado del envío.
10. Respuesta y feedback mostrado en el frontend.
11. Estados de carga, errores y recuperación.
12. Permisos y restricciones.

Revisa los flujos de registro, asignación de donaciones, cumplimiento de misiones, cambio de categoría, inicio de ruta, entrega exitosa y entrega fallida.

Comprueba que las notificaciones se generen desde los servicios de negocio y no dependan de que el navegador realice una segunda llamada para provocar el envío.

Si existe una bandeja de notificaciones, revisa su paginación, filtros, estados de lectura y autorización. Si no existe, documenta esa ausencia como una posible mejora y no como un requisito explícito del enunciado.

## 6. Auditoría técnica

Inspecciona:

- Calidad de los controladores y servicios.
- Validaciones y manejo global de excepciones.
- Separación entre dominio, aplicación e infraestructura.
- Interfaces y adaptadores de email, SMS y WhatsApp.
- Configuración de proveedores reales y simulados.
- Persistencia y migraciones.
- Transacciones.
- Concurrencia y consumo de mensajes.
- Idempotencia.
- Reintentos con espera progresiva.
- Gestión de mensajes fallidos.
- Timeouts y errores de proveedores.
- Seguridad, autorización y protección de datos.
- Logs, métricas, trazas y correlation IDs.
- Configuración Docker y disponibilidad del servicio.
- Calidad y cobertura de pruebas.

Diferencia los errores comprobados de los riesgos potenciales y de las mejoras opcionales.

## 7. Pruebas necesarias

Determina si existen pruebas suficientes y cuáles faltan.

Incluye casos de éxito, validaciones, errores de proveedores, mensajes duplicados, indisponibilidad de la cola, reinicio del consumidor, fallo parcial entre canales, persistencia, reintentos y autorización.

Comprueba que el envío simulado funcione sin credenciales externas y que los envíos reales no se activen accidentalmente en entornos de prueba.

Cuando sea posible, ejecuta las pruebas existentes y documenta los comandos utilizados, resultados y limitaciones.

No modifiques datos de producción ni realices envíos reales durante la auditoría.

## 8. Documentos que debes producir

Genera dos documentos separados en Markdown.

### Documento A: AUDITORIA_SERVICIO_NOTIFICACIONES.md

Debe contener:

- Resumen ejecutivo.
- Arquitectura real encontrada.
- Inventario de componentes.
- Modelo de dominio y persistencia.
- Contratos REST y eventos.
- Flujos completos del frontend al proveedor de envío.
- Comparación requisito por requisito.
- Errores y defectos comprobados, con archivos y ubicaciones exactas.
- Riesgos de consistencia, seguridad y disponibilidad.
- Pruebas ejecutadas y resultados.
- Diagrama de arquitectura y diagramas de secuencia en Mermaid.
- Recomendaciones priorizadas.
- Criterios de aceptación para considerar correcto el servicio.

### Documento B: PENDIENTES_SERVICIO_NOTIFICACIONES.md

Debe contener una lista priorizada de todo lo que falta o requiere corrección.

Para cada elemento indica:

- Identificador.
- Requisito relacionado.
- Estado: implementado, parcial, ausente, defectuoso o no verificable.
- Evidencia en el repositorio.
- Problema concreto.
- Impacto funcional o técnico.
- Archivos y módulos afectados.
- Solución propuesta.
- Dependencias con otros servicios.
- Pruebas necesarias.
- Criterios de aceptación.
- Prioridad: crítica, alta, media o baja.
- Orden recomendado de implementación.

Separa las correcciones obligatorias del TP de las mejoras arquitectónicas opcionales.

## 9. Reglas de trabajo

- No modifiques código durante la auditoría.
- No inventes clases, endpoints, eventos ni funcionalidades como si ya existieran.
- No marques un requisito como cumplido solo porque existe una interfaz o una clase con el nombre esperado.
- Si no puedes verificar algo, decláralo explícitamente.
- Usa referencias concretas a archivos, métodos, rutas y pruebas.
- Mantén la terminología y los requisitos del TP Anual 2026.
- Distingue hechos comprobados, inferencias técnicas y recomendaciones.
- No dupliques hallazgos entre documentos: utiliza referencias cruzadas.
- No te limites al Servicio de Notificaciones aislado; revisa las integraciones reales con los servicios productores y el frontend.

Primero inspecciona el proyecto, después reconstruye los flujos reales, contrasta cada requisito y finalmente genera los dos documentos.

Al terminar, presenta un resumen de los principales hallazgos y las cinco tareas más importantes que deberían implementarse primero.

## 14. Orden recomendado para trabajar después de la auditoría

Una vez que tengas los dos documentos, te conviene implementar las correcciones en este orden:

1. Corregir errores funcionales y contratos de integración: garantizar que los eventos y destinatarios sean correctos.
2. Asegurar persistencia e idempotencia: evitar pérdida de notificaciones y duplicados.
3. Completar los flujos de negocio: inactividad, asignación, incentivos y eventos logísticos.
4. Completar la integración con los canales reales: validar credenciales, plantillas y errores de los proveedores.
5. Integrar el frontend y la observabilidad: mostrar los estados correctos y poder diagnosticar fallos.

Mi recomendación principal es auditar primero los eventos y sus relaciones con Donaciones, Incentivos y Logística. Un servicio puede enviar correctamente un email y aun así estar mal implementado si lo envía a la persona equivocada, lo envía antes de confirmar la operación o lo envía dos veces.

La calidad del Servicio de Notificaciones de DonaTrack depende tanto de cómo envía los mensajes como de cuándo los envía, a quién y a partir de qué hecho de negocio.
