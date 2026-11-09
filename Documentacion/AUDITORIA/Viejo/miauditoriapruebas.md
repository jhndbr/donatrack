# Auditoría de Pruebas Operativas y Soluciones Implementadas — DonaTrack

A continuación se detalla el relevamiento técnico completo y las soluciones implementadas en la arquitectura de microservicios para cada uno de los puntos observados durante las pruebas operativas.

---

## 1. Auditoría de Notificaciones y Mails + Indicador Visual Numérico Rojo (`Badge`)

### 1.1. ¿Cómo funciona el flujo de notificaciones y correos electrónicos de punta a punta?
1. **Emisión de Eventos de Dominio (Outbox + RabbitMQ):**
   - `donaciones-service` persiste eventos en su tabla transaccional `outbox_events` (`donacion.registrada`, `donacion.asignada`, `donante.registrado`, `donante.inactivo`) y `OutboxPublisher` los publica cada 5 segundos al exchange `donatrack.events.exchange`.
   - `logistica-service` publica en tiempo real `ruta.iniciada`, `entrega.confirmada` y `entrega.fallida`.
   - `incentivos-service` publica `mision.cumplida` y `categoria.ascendida`.
2. **Consumo e Idempotencia en `notificaciones-service` (`:8085`):**
   - Los consumidores `DonacionNotificacionesConsumer`, `LogisticaNotificacionesConsumer` e `IncentivosNotificacionesConsumer` reciben los mensajes desde sus colas dedicadas (`q.notificaciones.*`).
   - `NotificacionesService.despacharConAuditoria(...)` verifica la clave de idempotencia en `RegistroEventoNotificacion`, formatea la plantilla HTML corporativa (`NotificationTemplateService`), despacha el correo por SMTP hacia **Mailpit (`:1025` SMTP / `:8025` Web UI)** mediante `EmailNotificationSender` (o adaptadores de SMS/WhatsApp según canal preferido), y guarda el registro en PostgreSQL (`auditorias_notificaciones`).
3. **Solución Implementada — Estado de Lectura e Indicador Visual Rojo:**
   - **En `notificaciones-service`:**
     - Se agregaron los campos `leida` (`boolean`, por defecto `false`) y `fechaLectura` (`LocalDateTime`) en `AuditoriaNotificacion`.
     - Se expusieron los endpoints REST:
       - `GET /api/v1/notificaciones/no-leidas/count?destinatario=...`: retorna la cantidad exacta de notificaciones no leídas para el usuario en sesión (o globales para el Administrador).
       - `POST /api/v1/notificaciones/{id}/leer`: marca una notificación puntual como leída.
       - `POST /api/v1/notificaciones/leer-todas?destinatario=...`: marca todas las notificaciones pendientes del usuario como leídas.
     - Además, se incorporó la cola `q.notificaciones.donacion-registrada` (`donacion.registrada`) para que el donante reciba confirmación inmediata apenas registra una donación en depósito (`EN_DEPOSITO`).
   - **En `frontend-service`:**
     - Se creó `GlobalNotificationsAdvice` (`@ControllerAdvice`) que inyecta automáticamente `notificacionesNoLeidasCount` en todas las vistas según el rol autenticado (`ROLE_DONANTE`, `ROLE_BENEFICIARIA`, `ROLE_ADMIN`, `ROLE_CHOFER`).
     - En `layout.html` se agregó el **indicador numérico rojo (`badge rounded-pill bg-danger`)** tanto en los enlaces del menú superior (*Mis Notificaciones*, *Notificaciones*, *Auditoría Notificaciones*, *Buzón Correos*) como en el ícono de campana junto al nombre del usuario.
     - En las vistas `/donante/notificaciones`, `/beneficiaria/notificaciones` y `/admin/notificaciones` cada fila muestra la etiqueta roja **`NUEVA`** (o `Leída`), un modal para visualizar el cuerpo HTML del mensaje y botones para marcar como leída individualmente o en lote.

---

## 2. Mejoras en Registro y Actualización de Necesidades Materiales (`/beneficiaria/necesidades`)

### 2.1. Problemas detectados en la auditoría
- El campo **Periodicidad** era un `<input type="text">` libre que permanecía activo incluso cuando el tipo seleccionado era `EXTRAORDINARIA`.
- El endpoint `PUT /api/v1/entidades/necesidades/{necesidadId}` en `donaciones-service` solo actualizaba `cantidadSolicitada` y `descripcion`, ignorando cambios en `subcategoria`, `tipo`, `unidadMedida` y `periodo`.
- La vista `beneficiaria/necesidades.html` no mostraba alertas de error (`mensajeError`) ni poseía un cuadro de confirmación o modal de edición integral.

### 2.2. Solución Implementada
1. **Nuevo Enum `PeriodoNecesidad` en `common-dtos`:**
   - Valores estandarizados: `DIARIA`, `SEMANAL`, `QUINCENAL`, `MENSUAL`, `BIMESTRAL`, `TRIMESTRAL`, `SEMESTRAL`, `ANUAL` con etiquetas descriptivas y parser normalizador `PeriodoNecesidad.fromString(...)`.
2. **Activación Condicional de Periodicidad (`EXTRAORDINARIA` vs `RECURRENTE`):**
   - En `beneficiaria/necesidades.html`, el campo `periodo` ahora es un menú desplegable `<select>` alimentado por `PeriodoNecesidad.values()`.
   - Cuando el tipo es **`EXTRAORDINARIA`**, el menú de periodicidad permanece **deshabilitado (`disabled`)** con el indicador `Inactivo (Extraordinaria)`, y el backend garantiza `periodo = null`.
   - Únicamente cuando el usuario selecciona **`RECURRENTE`**, el selector de periodicidad se habilita dinámicamente y pasa a ser obligatorio (`required`), validándose tanto en el frontend como en `EntidadBeneficiariaService`.
3. **Coincidencia Semántica de `Subcategoría (Unidad Mínima)`:**
   - Se incorporó un `<datalist id="subcategoriasDatalist">` con sugerencias estandarizadas del catálogo de donaciones (`fideos`, `arroz`, `leche`, `harina`, `aceite`, `azucar`, `lentejas`, `frazadas`, `pañales`, etc.) para facilitar la coincidencia exacta y semántica en el motor de Matchmaking.
4. **Actualización Integral y Cuadro de Confirmación (`CU-27` y `CU-29`):**
   - Cada necesidad registrada cuenta ahora con un botón **"Actualizar"** que abre un **Modal / Cuadro de Confirmación de Edición Completa** (`#modalEditarNecesidad-{id}`) donde se pueden modificar `subcategoria`, `cantidadSolicitada` (respetando la invariante `>= cantidadCubierta`), `unidadMedida`, `tipo`, `periodo` y `descripcion`, solicitando confirmación explícita antes de guardar.
   - Asimismo, el botón de baja abre un **Modal de Confirmación de Cierre** (`#modalEliminarNecesidad-{id}`) con motivo justificado (`CU-29`).

---

## 3. Flujo del Chofer: Geolocalización del Navegador, Hoja de Ruta y Cronjob cada 5 Minutos

### 3.1. ¿Cómo recibe las rutas el Chofer y cómo opera el Cronjob en Modo Demo?
1. **Generación de Rutas (Cronjob cada 5 minutos + Botón Bajo Demanda):**
   - En `logistica-service`, `LogisticaScheduler.dispararPlanificacionRutasNocturna()` fue configurado con la expresión cron `${donatrack.cron.planificacion-rutas:0 */5 * * * *}` para que en **modo demo se ejecute automáticamente cada 5 minutos** (y sea parametrizable por variable de entorno `DONATRACK_CRON_RUTAS` para producción a las `01:00 AM`).
   - Adicionalmente, se agregó el endpoint `POST /api/v1/logistica/rutas/planificar-pendientes` y el botón **"Generar Rutas Pendientes Ahora"** en el encabezado de `/chofer`, permitiendo disparar la planificación inmediatamente durante una demostración sin esperar los 5 minutos.
2. **Enriquecimiento Real de Paradas:**
   - Se corrigió `PlanificacionRutasServiceImpl.procesarCallback(...)` para que, al consultar `donaciones-service`, actualice cada `Parada` de la `Ruta` con la razón social real de la entidad beneficiaria, su dirección catastral normalizada, sus coordenadas (`latitud`, `longitud`) y el detalle de los bienes (`cantidad + unidadMedida + subcategoria`).

### 3.2. ¿Cómo toma el navegador la ubicación GPS y la envía a la aplicación?
1. **Captura en el Navegador (`chofer/ruta.html`):**
   - Se utiliza la API estándar HTML5 **`navigator.geolocation.getCurrentPosition`** (para envío puntual con el botón *"Tomar Mi Ubicación GPS del Navegador y Transmitir"*) y un interruptor de transmisión continua cada 15 segundos (*"Compartir ubicación en vivo automáticamente"*).
   - También se integró un **Mapa Interactivo Leaflet** en `/chofer`: si el navegador de escritorio no tiene sensor GPS físico o el usuario desea simular el avance del camión por la ciudad, basta con **hacer clic en cualquier punto del mapa** o en el botón **"Mover GPS aquí"** de cada parada.
2. **Transmisión sin recarga (`AJAX` -> `API Gateway` -> `logistica-service`):**
   - El navegador envía `POST /chofer/telemetria/ajax` a `ChoferWebController`, el cual invoca `POST /api/v1/logistica/telemetria/posicion` en `logistica-service`.
   - `TelemetriaServiceImpl` guarda el punto histórico en la colección `ubicaciones_gps` de MongoDB y actualiza la posición actual embebida en el documento `Camion`.
   - Tanto el **Donante** (`/donante/tracking`) como la **Entidad Beneficiaria** (`/beneficiaria/tracking`) consultan `GET /api/v1/logistica/telemetria/flota-en-vivo` y ven moverse el camión en tiempo real.

---

## 4. Contrato de Recepción Sincronizado entre Chofer y Receptor de Entidad Beneficiaria

### 4.1. Sincronización Bidireccional y Etapas Operativas
Se modeló y sincronizó el contrato completo entre `Ruta` (`Parada`), `Entrega` (MongoDB) y `Donacion` (PostgreSQL) a través de `EntregaServiceImpl.sincronizarParadaEnRuta(...)`:

| Paso | Etapa Operativa (`Entrega` / `Parada`) | Estado `Donacion` | Actor que ejecuta | Efecto en el Sistema |
| :--- | :--- | :--- | :--- | :--- |
| **1. Planificación** | `PENDIENTE_RETIRO_DEPOSITO` / `PENDIENTE` | `LISTA_PARA_ENTREGAR` | Cronjob VRP (cada 5 min) o Botón Demo | Crea la `Ruta` (`PLANIFICADA`) y las `Entrega`s asociadas. |
| **2. Retiro en Depósito** | `CARGADA_EN_CAMION` / `EN_TRASLADO` | `EN_TRASLADO` | **Chofer** (*Confirmar Retiro en Depósito e Iniciar Ruta*) | Verifica y carga los bultos en el depósito; inicia la ruta (`EN_CURSO`) y envía email con link de seguimiento GPS a Donante y Entidad. |
| **3. Llegada a Sede** | `EN_PUERTA_SEDE` / `EN_PUERTA_SEDE` | `EN_TRASLADO` | **Chofer** (*Botón "En Puerta"*) | Posiciona el GPS del camión en la puerta del comedor y muestra en `/beneficiaria/confirmar` el aviso `🚚 ¡CHOFER EN PUERTA ESPERANDO RECEPCIÓN!`. |
| **4A. Recepción Exitosa** | `RECEPCION_CONFIRMADA` / `ENTREGADA` (`completada=true`) | `ENTREGADA` | **Chofer** (*Acta Entrega*) o **Entidad Beneficiaria** (*Confirmar Recepción*) | Registra `receptorNombre`, `receptorDni`, fotografía comprobante y observaciones. Actualiza simultáneamente la vista del Chofer y de la Entidad, emite `entrega.confirmada` (sumando puntos/misiones en `incentivos-service`) y publica la foto en `/comprobantes`. |
| **4B. Registro de Fallo** | `INCIDENCIA_REGISTRADA` / `FALLIDA` | `ENTREGA_FALLIDA` | **Chofer** (*Incidencia*) o **Entidad Beneficiaria** (*Rechazar Entrega*) | Registra la etapa del fallo (`RETIRO_DEPOSITO`, `EN_TRANSITO`, `EN_PUERTA_SEDE`, `INSPECCION_RECEPTOR`), el motivo obligatorio (`CU-44`) y si es `replanificable`. Dispara alerta prioritaria al Administrador (`CU-45`), Entidad y Donante. |
| **5. Reingreso / Replanificación** | `PENDIENTE_RETIRO_DEPOSITO` / `PENDIENTE` | `EN_DEPOSITO` | **Administrador** (*Reingresar a Depósito CU-45*) | Cuando la mercadería regresa físicamente al depósito en buen estado, vuelve a `EN_DEPOSITO` para reasignación y nueva ruta. |
