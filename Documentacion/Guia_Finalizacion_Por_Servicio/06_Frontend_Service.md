# 06. Guía de Finalización: Cliente Liviano Web MVC (`frontend-service`)

## 1. Resumen de Estado Actual
El módulo [`frontend-service`](file:///home/jhon/Documentos/Github/TP-DDSI/frontend-service/pom.xml) (puerto `8086`) implementa el **Cliente Liviano desacoplado con Server-Side Rendering (SSR MVC)** exigido en la Entrega 5 con Spring Web MVC, Thymeleaf y Leaflet.
Posee controladores especializados:
* [`HomeController.java`](file:///home/jhon/Documentos/Github/TP-DDSI/frontend-service/src/main/java/com/donatrack/frontend/controller/HomeController.java): Landing pública, login y registro.
* [`DonanteWebController.java`](file:///home/jhon/Documentos/Github/TP-DDSI/frontend-service/src/main/java/com/donatrack/frontend/controller/DonanteWebController.java): Dashboard, catálogo de donaciones, entidades, incentivos y tracking.
* [`BeneficiariaWebController.java`](file:///home/jhon/Documentos/Github/TP-DDSI/frontend-service/src/main/java/com/donatrack/frontend/controller/BeneficiariaWebController.java): Dashboard, registro de necesidades, confirmación de entregas con fotos y tracking.
* [`AdminWebController.java`](file:///home/jhon/Documentos/Github/TP-DDSI/frontend-service/src/main/java/com/donatrack/frontend/controller/AdminWebController.java): Flota de camiones, carga segmentada, matchmaking interactivo, rankings e importación CSV.
* [`ApiClientService.java`](file:///home/jhon/Documentos/Github/TP-DDSI/frontend-service/src/main/java/com/donatrack/frontend/service/ApiClientService.java): Cliente REST hacia `api-gateway` (`:8080`).

---

## 2. Cosas que FALLAN (Bugs Críticos y Contratos Rotos)

### 2.1. BUG HTTP 400: Visibilidad de Insignias
* **Ubicación**: [`ApiClientService.java:627-639`](file:///home/jhon/Documentos/Github/TP-DDSI/frontend-service/src/main/java/com/donatrack/frontend/service/ApiClientService.java#L627-L639) vs [`IncentivosController.java:119-121`](file:///home/jhon/Documentos/Github/TP-DDSI/incentivos-service/src/main/java/com/donatrack/incentivos/controller/IncentivosController.java#L119-L121).
* **Fallo**: `ApiClientService.alternarVisibilidadInsignia` envía un JSON `{"visible": visible}` en el body de `PATCH /api/v1/incentivos/insignias/{id}/visibilidad`. El backend exige `@RequestParam("visible") boolean visible`.
* **Solución**: Enviar `uri = gatewayUrl + "/api/v1/incentivos/insignias/" + idInsignia + "/visibilidad?visible=" + visible` con body nulo.

### 2.2. BUG HTTP 400: Enviar Notificación de Prueba desde Admin
* **Ubicación**: [`ApiClientService.java:665-682`](file:///home/jhon/Documentos/Github/TP-DDSI/frontend-service/src/main/java/com/donatrack/frontend/service/ApiClientService.java#L665-L682) vs [`NotificacionesController.java:75-80`](file:///home/jhon/Documentos/Github/TP-DDSI/notificaciones-service/src/main/java/com/donatrack/notificaciones/controller/NotificacionesController.java#L75-L80).
* **Fallo**: `ApiClientService.enviarNotificacionPrueba` envía un body JSON a `POST /api/v1/notificaciones/test`, pero el controller espera 4 `@RequestParam` (`canal`, `destinatario`, `asunto`, `mensaje`).
* **Solución**: Enviar las 4 variables en la query string o como `application/x-www-form-urlencoded`.

### 2.3. BUG EN HISTORIAL DE NOTIFICACIONES BENEFICIARIA: Tabla siempre vacía
* **Ubicación**: [`BeneficiariaWebController.java:84-94`](file:///home/jhon/Documentos/Github/TP-DDSI/frontend-service/src/main/java/com/donatrack/frontend/controller/BeneficiariaWebController.java#L84-L94).
* **Fallo**: Busca `e.get("emailPrincipal")`, pero el campo en `EntidadBeneficiaria` es `emailsRepresentantes` (`List<String>`). `emailEntidad` queda `""` y la vista `/beneficiaria/notificaciones` nunca muestra nada.
* **Solución**: Obtener el primer email de `emailsRepresentantes` (o el email de la sesión activa).

### 2.4. ERROR JAVASCRIPT: `actualizarSubcategorias()` no existe
* **Ubicación**: [`donante/nueva_donacion.html:52`](file:///home/jhon/Documentos/Github/TP-DDSI/frontend-service/src/main/resources/templates/donante/nueva_donacion.html#L52).
* **Fallo**: El select de categoría ejecuta `onchange="actualizarSubcategorias()"`, pero la función JS no existe en ningún archivo, arrojando error en la consola del navegador.
* **Solución**: Definir la función JS en `donante/nueva_donacion.html` mapeando categorías a subcategorías válidas (`Alimentos -> arroz, fideos; Indumentaria -> remeras, camperas; Mobiliario -> sillas, mesas`).

### 2.5. MAPAS LEAFLET SIMULADOS / HARDCODEADOS
* **Ubicación**:
  * [`index.html:224-265`](file:///home/jhon/Documentos/Github/TP-DDSI/frontend-service/src/main/resources/templates/index.html#L224-L265) (Landing)
  * [`donante/tracking.html:98-150`](file:///home/jhon/Documentos/Github/TP-DDSI/frontend-service/src/main/resources/templates/donante/tracking.html#L98-L150) (Tracking Donante)
  * [`beneficiaria/tracking.html:22-42`](file:///home/jhon/Documentos/Github/TP-DDSI/frontend-service/src/main/resources/templates/beneficiaria/tracking.html#L22-L42) (Tracking Beneficiaria)
* **Fallo**: Los 3 mapas ignoran los atributos del modelo Thymeleaf (`donacionesPublicasMapa`, `flotaEnVivo`, `rutas`) y pintan arrays JavaScript hardcodeados con comedores y posiciones fijas.
* **Solución**: Agregar `th:inline="javascript"` a los bloques `<script>` e iterar sobre los objetos reales pasados por el controlador.

---

## 3. Cosas que FALTAN según los 20 Requerimientos de UI/UX

1. **Falta Vista de Chofer (`ROLE_CHOFER`)**:
   - Requerido en: Entrega 3 (pág. 16, líneas 779-784 y pág. 18, Req. 3).
   - Crear `ChoferWebController.java` (`/chofer`), vista `templates/chofer/ruta.html` para consultar paradas, botón para `POST /api/v1/logistica/rutas/{id}/iniciar`, botón para finalizar recorrido y formulario para emitir telemetría GPS (`/api/v1/logistica/telemetria/posicion`).
   - Agregar `isChofer()` en `UserSession.java` y habilitar el rol en `AuthInterceptor.java`.
2. **Falta Registro de Personas Jurídicas Donantes**:
   - Requerido en: Requerimiento 3 de UI/UX y Entrega 1 (pág. 6, líneas 247-249).
   - Agregar en `templates/registro.html` una pestaña o formulario para Persona Jurídica (razón social, CUIT, tipo ONG/Empresa/Institución, rubro y representantes) consumiendo `apiClient.crearDonanteJuridica()`.
3. **Falta Botón para marcar Donación como `VENCIDA`**:
   - Requerido en: Requerimiento 16 de UI/UX y Entrega 2 (pág. 11, líneas 481-482).
   - Agregar en `templates/admin/dashboard.html` una acción en la tabla para donaciones en `EN_DEPOSITO` que invoque `POST /admin/donaciones/{id}/marcar-vencida` con motivo.
4. **Falta CRUD Completo de Flota de Camiones**:
   - Requerido en: Requerimiento 18 de UI/UX.
   - En `templates/admin/camiones.html`, añadir botones para editar capacidades y dar de baja lógica camiones (`DELETE /api/v1/logistica/camiones/{id}`).
5. **Completar Panel de Analítica en `/donante/incentivos`**:
   - Requerido en: Requerimiento 7 de UI/UX y Entrega 2 (pág. 13, líneas 600-605).
   - Consumir `GET /api/v1/incentivos/donantes/{id}/analitica` y renderizar gráficos o tarjetas con totales históricos, comparativa mes anterior y organizaciones ayudadas.

---

## 4. Checklist de Finalización para `frontend-service`
- [ ] Corregir la llamada a visibilidad de insignia en `ApiClientService.java` (`?visible={bool}`).
- [ ] Corregir envío de notificación de prueba en `ApiClientService.java`.
- [ ] Corregir lectura de emails de la entidad en `BeneficiariaWebController.java`.
- [ ] Agregar función JS `actualizarSubcategorias()` en `donante/nueva_donacion.html`.
- [ ] Conectar mapas Leaflet dinámicamente con los datos de Thymeleaf (`th:inline="javascript"`).
- [ ] Crear `ChoferWebController.java` y vista `templates/chofer/ruta.html`.
- [ ] Agregar formulario de registro para Persona Jurídica en `registro.html`.
- [ ] Agregar botón "Marcar Vencida" en `admin/dashboard.html`.
- [ ] Agregar botones de edición y baja en `admin/camiones.html`.
