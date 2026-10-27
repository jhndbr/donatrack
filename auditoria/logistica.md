# Especificación completa del Servicio de Logística de DonaTrack

El objetivo es definir qué debe hacer el `logistica-service`, cómo deben funcionar sus procesos, qué vistas del frontend intervienen y cómo debe comunicarse con los demás microservicios.

Voy a separar tres cosas para que puedas usar esto con tu IA de desarrollo:

- Requisitos del enunciado: lo que pide explícitamente el TP Anual 2026.
- Diseño recomendado: cómo implementarlo de forma coherente con una arquitectura de microservicios.
- Puntos a verificar: decisiones que deben confirmarse revisando el código y la documentación de tu repositorio.

El enunciado establece que Logística debe cubrir la planificación de rutas, la gestión de camiones y entregas, la trazabilidad de los estados y el monitoreo de camiones en tiempo real. También exige una API REST para gestionar la flota y realizar operaciones CRUD sobre rutas y entregas.

## 1. Responsabilidad del servicio

El `logistica-service` debe encargarse de coordinar y registrar el traslado físico de las donaciones desde el depósito hasta las entidades beneficiarias.

Sus responsabilidades principales son:

Gestión de flota

Registrar camiones, patentes, capacidades, altura, disponibilidad y datos necesarios para planificar rutas.

Planificación de rutas

Obtener las donaciones listas para ser entregadas, los camiones disponibles y generar rutas ordenadas para el día siguiente mediante un componente de planificación externo.

Gestión de entregas

Controlar el inicio del recorrido, el traslado, la recepción, las entregas no recibidas y el regreso al depósito.

Seguimiento en tiempo real

Recibir, validar y procesar posiciones de los camiones para mostrar dónde están y cuánto avanzaron sobre sus rutas.

Trazabilidad y auditoría

Conservar el historial de estados, las fechas, los responsables, las incidencias y la evidencia de las entregas.

### Qué NO debería hacer Logística

Para mantener los microservicios bien separados:

- No debería registrar ni administrar el ciclo de vida completo de donantes y entidades: eso corresponde a Donaciones.
- No debería decidir qué donaciones necesitan las entidades ni realizar el algoritmo de asignación de donaciones: corresponde al dominio de Donaciones y su proceso de asignación.
- No debería calcular insignias, misiones o categorías de donantes: corresponde a Incentivos.
- No debería enviar directamente correos, SMS o WhatsApp: debe publicar eventos para que Notificaciones se encargue.
- No debería confiar en que el frontend cambie por su cuenta el estado de una donación.
- No debería depender de una llamada síncrona a otro microservicio para que cada movimiento de un camión pueda registrarse.

Una regla importante: Logística es la fuente de verdad sobre las rutas, los camiones, el seguimiento y las entregas; Donaciones sigue siendo la fuente de verdad sobre la donación y su asignación dentro del dominio de donaciones.

## 2. Cómo se comunica con los demás servicios

Esta sería la arquitectura recomendada para DonaTrack, manteniendo el frontend SSR con Spring MVC/Thymeleaf, el API Gateway y la mensajería RabbitMQ que aparecen en la documentación del proyecto.

\#chatgpt-mermaid-\_r_ip\_{font-family:-apple-system-body,ui-sans-serif,-apple-system,system-ui,"Segoe UI",Helvetica,"Apple Color Emoji",Arial,sans-serif,"Segoe UI Emoji","Segoe UI Symbol";font-size:16px;fill:rgb(237, 237, 237);}@keyframes edge-animation-frame{from{stroke-dashoffset:0;}}@keyframes dash{to{stroke-dashoffset:0;}}#chatgpt-mermaid-\_r_ip\_ .edge-animation-slow{stroke-dasharray:9,5!important;stroke-dashoffset:900;animation:dash 50s linear infinite;stroke-linecap:round;}#chatgpt-mermaid-\_r_ip\_ .edge-animation-fast{stroke-dasharray:9,5!important;stroke-dashoffset:900;animation:dash 20s linear infinite;stroke-linecap:round;}#chatgpt-mermaid-\_r_ip\_ .error-icon{fill:rgb(48, 48, 48);}#chatgpt-mermaid-\_r_ip\_ .error-text{fill:rgb(237, 237, 237);stroke:rgb(237, 237, 237);}#chatgpt-mermaid-\_r_ip\_ .edge-thickness-normal{stroke-width:1px;}#chatgpt-mermaid-\_r_ip\_ .edge-thickness-thick{stroke-width:3.5px;}#chatgpt-mermaid-\_r_ip\_ .edge-pattern-solid{stroke-dasharray:0;}#chatgpt-mermaid-\_r_ip\_ .edge-thickness-invisible{stroke-width:0;fill:none;}#chatgpt-mermaid-\_r_ip\_ .edge-pattern-dashed{stroke-dasharray:3;}#chatgpt-mermaid-\_r_ip\_ .edge-pattern-dotted{stroke-dasharray:2;}#chatgpt-mermaid-\_r_ip\_ .marker{fill:rgb(175, 175, 175);stroke:rgb(175, 175, 175);}#chatgpt-mermaid-\_r_ip\_ .marker.cross{stroke:rgb(175, 175, 175);}#chatgpt-mermaid-\_r_ip\_ svg{font-family:-apple-system-body,ui-sans-serif,-apple-system,system-ui,"Segoe UI",Helvetica,"Apple Color Emoji",Arial,sans-serif,"Segoe UI Emoji","Segoe UI Symbol";font-size:16px;}#chatgpt-mermaid-\_r_ip\_ p{margin:0;}#chatgpt-mermaid-\_r_ip\_ .label{font-family:-apple-system-body,ui-sans-serif,-apple-system,system-ui,"Segoe UI",Helvetica,"Apple Color Emoji",Arial,sans-serif,"Segoe UI Emoji","Segoe UI Symbol";color:rgb(237, 237, 237);}#chatgpt-mermaid-\_r_ip\_ .cluster-label text{fill:rgb(237, 237, 237);}#chatgpt-mermaid-\_r_ip\_ .cluster-label span{color:rgb(237, 237, 237);}#chatgpt-mermaid-\_r_ip\_ .cluster-label span p{background-color:transparent;}#chatgpt-mermaid-\_r_ip\_ .label text,#chatgpt-mermaid-\_r_ip\_ span{fill:rgb(237, 237, 237);color:rgb(237, 237, 237);}#chatgpt-mermaid-\_r_ip\_ .node rect,#chatgpt-mermaid-\_r_ip\_ .node circle,#chatgpt-mermaid-\_r_ip\_ .node ellipse,#chatgpt-mermaid-\_r_ip\_ .node polygon,#chatgpt-mermaid-\_r_ip\_ .node path{fill:rgb(9, 23, 44);stroke:rgb(31, 78, 148);stroke-width:1px;}#chatgpt-mermaid-\_r_ip\_ .rough-node .label text,#chatgpt-mermaid-\_r_ip\_ .node .label text,#chatgpt-mermaid-\_r_ip\_ .image-shape .label,#chatgpt-mermaid-\_r_ip\_ .icon-shape .label{text-anchor:middle;}#chatgpt-mermaid-\_r_ip\_ .node .katex path{fill:#000;stroke:#000;stroke-width:1px;}#chatgpt-mermaid-\_r_ip\_ .rough-node .label,#chatgpt-mermaid-\_r_ip\_ .node .label,#chatgpt-mermaid-\_r_ip\_ .image-shape .label,#chatgpt-mermaid-\_r_ip\_ .icon-shape .label{text-align:center;}#chatgpt-mermaid-\_r_ip\_ .node.clickable{cursor:pointer;}#chatgpt-mermaid-\_r_ip\_ .root .anchor path{fill:rgb(175, 175, 175)!important;stroke-width:0;stroke:rgb(175, 175, 175);}#chatgpt-mermaid-\_r_ip\_ .arrowheadPath{fill:rgb(175, 175, 175);}#chatgpt-mermaid-\_r_ip\_ .edgePath .path{stroke:rgb(175, 175, 175);stroke-width:1px;}#chatgpt-mermaid-\_r_ip\_ .flowchart-link{stroke:rgb(175, 175, 175);fill:none;}#chatgpt-mermaid-\_r_ip\_ .edgeLabel{background-color:rgb(0, 0, 0);text-align:center;}#chatgpt-mermaid-\_r_ip\_ .edgeLabel p{background-color:rgb(0, 0, 0);}#chatgpt-mermaid-\_r_ip\_ .edgeLabel rect{opacity:0.5;background-color:rgb(0, 0, 0);fill:rgb(0, 0, 0);}#chatgpt-mermaid-\_r_ip\_ .labelBkg{background-color:rgba(0, 0, 0, 0.5);}#chatgpt-mermaid-\_r_ip\_ .cluster rect{fill:rgb(48, 48, 48);stroke:rgba(255, 255, 255, 0.15);stroke-width:1px;}#chatgpt-mermaid-\_r_ip\_ .cluster text{fill:rgb(237, 237, 237);}#chatgpt-mermaid-\_r_ip\_ .cluster span{color:rgb(237, 237, 237);}#chatgpt-mermaid-\_r_ip\_ div.mermaidTooltip{position:absolute;text-align:center;max-width:200px;padding:2px;font-family:-apple-system-body,ui-sans-serif,-apple-system,system-ui,"Segoe UI",Helvetica,"Apple Color Emoji",Arial,sans-serif,"Segoe UI Emoji","Segoe UI Symbol";font-size:12px;background:rgb(48, 48, 48);border:1px solid rgba(255, 255, 255, 0.15);border-radius:2px;pointer-events:none;z-index:100;}#chatgpt-mermaid-\_r_ip\_ .flowchartTitleText{text-anchor:middle;font-size:18px;fill:rgb(237, 237, 237);}#chatgpt-mermaid-\_r_ip\_ rect.text{fill:none;stroke-width:0;}#chatgpt-mermaid-\_r_ip\_ .icon-shape,#chatgpt-mermaid-\_r_ip\_ .image-shape{background-color:rgb(0, 0, 0);text-align:center;}#chatgpt-mermaid-\_r_ip\_ .icon-shape p,#chatgpt-mermaid-\_r_ip\_ .image-shape p{background-color:rgb(0, 0, 0);padding:2px;}#chatgpt-mermaid-\_r_ip\_ .icon-shape .label rect,#chatgpt-mermaid-\_r_ip\_ .image-shape .label rect{opacity:0.5;background-color:rgb(0, 0, 0);fill:rgb(0, 0, 0);}#chatgpt-mermaid-\_r_ip\_ .label-icon{display:inline-block;height:1em;overflow:visible;vertical-align:-0.125em;}#chatgpt-mermaid-\_r_ip\_ .node .label-icon path{fill:currentColor;stroke:revert;stroke-width:revert;}#chatgpt-mermaid-\_r_ip\_ .node .neo-node{stroke:rgb(31, 78, 148);}#chatgpt-mermaid-\_r_ip\_ [data-look="neo"].node rect,#chatgpt-mermaid-\_r_ip\_ [data-look="neo"].cluster rect,#chatgpt-mermaid-\_r_ip\_ [data-look="neo"].node polygon{stroke:url(#chatgpt-mermaid-\_r_ip\_-gradient);filter:drop-shadow( 1px 2px 2px rgba(185,185,185,1));}#chatgpt-mermaid-\_r_ip\_ [data-look="neo"].swimlane.cluster rect{filter:none;}#chatgpt-mermaid-\_r_ip\_ [data-look="neo"].node path{stroke:url(#chatgpt-mermaid-\_r_ip\_-gradient);stroke-width:1px;}#chatgpt-mermaid-\_r_ip\_ [data-look="neo"].node .outer-path{filter:drop-shadow( 1px 2px 2px rgba(185,185,185,1));}#chatgpt-mermaid-\_r_ip\_ [data-look="neo"].node .neo-line path{stroke:rgb(31, 78, 148);filter:none;}#chatgpt-mermaid-\_r_ip\_ [data-look="neo"].node circle{stroke:url(#chatgpt-mermaid-\_r_ip\_-gradient);filter:drop-shadow( 1px 2px 2px rgba(185,185,185,1));}#chatgpt-mermaid-\_r_ip\_ [data-look="neo"].node circle .state-start{fill:#000000;}#chatgpt-mermaid-\_r_ip\_ [data-look="neo"].icon-shape .icon{fill:url(#chatgpt-mermaid-\_r_ip\_-gradient);filter:drop-shadow( 1px 2px 2px rgba(185,185,185,1));}#chatgpt-mermaid-\_r_ip\_ [data-look="neo"].icon-shape .icon-neo path{stroke:url(#chatgpt-mermaid-\_r_ip\_-gradient);filter:drop-shadow( 1px 2px 2px rgba(185,185,185,1));}#chatgpt-mermaid-\_r_ip\_ .node text{font-size:14px;font-weight:600;letter-spacing:normal;fill:rgb(153, 206, 255);}#chatgpt-mermaid-\_r_ip\_ .edgeLabels text{font-size:13px;font-weight:600;letter-spacing:-0.08px;fill:rgb(153, 206, 255);}#chatgpt-mermaid-\_r_ip\_ .node tspan[font-weight="normal"],#chatgpt-mermaid-\_r_ip\_ .edgeLabels tspan[font-weight="normal"]{font-weight:600;}#chatgpt-mermaid-\_r_ip\_ .edgeLabel .label rect{opacity:1;rx:13px;ry:13px;fill:rgb(0, 14, 26);stroke:rgb(26, 62, 95);stroke-width:1px;}#chatgpt-mermaid-\_r_ip\_ .node rect,#chatgpt-mermaid-\_r_ip\_ .node circle,#chatgpt-mermaid-\_r_ip\_ .node ellipse,#chatgpt-mermaid-\_r_ip\_ .node polygon,#chatgpt-mermaid-\_r_ip\_ .node path{fill:rgb(0, 40, 77);stroke:rgba(255, 255, 255, 0.1);stroke-width:1px;}#chatgpt-mermaid-\_r_ip\_ .node rect{rx:16px;ry:16px;}#chatgpt-mermaid-\_r_ip\_ .node.mermaid-decision .label-container{fill:rgb(0, 14, 26);stroke:rgb(26, 62, 95);stroke-dasharray:2,2;}#chatgpt-mermaid-\_r_ip\_ .edgePaths .flowchart-link{stroke:rgb(175, 175, 175);stroke-width:1px;stroke-linecap:round;stroke-linejoin:round;}#chatgpt-mermaid-\_r_ip\_ .marker{fill:rgb(175, 175, 175);stroke:rgb(175, 175, 175);}#chatgpt-mermaid-\_r_ip\_ :root{--mermaid-font-family:-apple-system-body,ui-sans-serif,-apple-system,system-ui,"Segoe UI",Helvetica,"Apple Color Emoji",Arial,sans-serif,"Segoe UI Emoji","Segoe UI Symbol";}Donante / Entidad / Chofer /Administradorfrontend-serviceSpring MVC + Thymeleafapi-gatewaylogistica-servicedonaciones-serviceincentivos-servicenotificaciones-serviceRabbitMQdonatrack.eventsComponente externode planificación de rutasGPS del camióno app móvil del choferBase de datos de Logística

El diagrama muestra las relaciones principales, no obliga a que todas las flechas sean llamadas HTTP. En particular, las actualizaciones de negocio y las notificaciones deberían usar eventos cuando sea apropiado.

### Integraciones y responsabilidades

| Componente                  | Qué intercambia con Logística                                                                                                     | Forma recomendada                                       |
| --------------------------- | --------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------- |
| `frontend-service`          | Alta y consulta de camiones, rutas, entregas, confirmaciones y seguimiento                                                        | REST mediante el API Gateway                            |
| `api-gateway`               | Autenticación, autorización, enrutamiento y entrada a la API                                                                      | HTTP/REST                                               |
| `donaciones-service`        | Donaciones en estado `Asignación Realizada`, datos necesarios para las entregas y cambios de estado que corresponden a su dominio | REST para consultas puntuales o integración por eventos |
| `notificaciones-service`    | Inicio de ruta, entrega exitosa y entrega no satisfactoria                                                                        | RabbitMQ, mediante eventos                              |
| `incentivos-service`        | Información de actividad o entrega, solo si alguna regla de incentivos la necesita                                                | Eventos de negocio                                      |
| Componente externo de rutas | Camiones disponibles, entregas y destinos; devuelve rutas ordenadas por camión                                                    | Contrato de integración definido por el equipo          |
| GPS o aplicación móvil      | Posición, velocidad y momento de captura del vehículo                                                                             | API de telemetría autenticada o protocolo acordado      |
| Base de datos de Logística  | Camiones, rutas, paradas, entregas, posiciones e historial                                                                        | Persistencia propia del servicio                        |

Importante: el enunciado exige integrar un componente externo que genere las rutas y elegir entre un GPS instalado en el camión o la geolocalización de la aplicación del chofer. No impone una tecnología concreta para esos dos contratos. La elección y el formato de integración deben quedar documentados.

### Regla de comunicación entre microservicios

El frontend no debería llamar directamente a `notificaciones-service` para anunciar que una entrega se realizó. El flujo correcto es:

1. El usuario ejecuta la operación desde el frontend.
2. El Gateway deriva la solicitud a Logística.
3. Logística valida la operación y registra el cambio.
4. Logística publica un evento de negocio.
5. Notificaciones consume el evento y avisa a los destinatarios.
6. El frontend consulta el estado actualizado desde la API.

Así, si Notificaciones está temporalmente fuera de servicio, una entrega ya registrada no debería perderse ni quedar bloqueada esperando el envío de un correo.

## 3. Qué vistas del frontend deben intervenir

Según la documentación del proyecto, el frontend contempla portales para donantes, entidades beneficiarias y administradores, además de tracking de camiones, gestión de flota, confirmación de recepción y carga de fotografías. La siguiente propuesta distribuye las funciones entre esas vistas y agrega la interfaz del chofer, que el enunciado necesita para iniciar una ruta.

[Unified Backoffice for Fleet Management SaaS by Fernando Marins on Dribbble](https://images.openai.com/static-rsc-4/jXM-pZhekAAx2kawWQHEz0ODKbtLZbZtwMTQ-ahUiv9B9RQy6b9rv-iezfr8KfzS3lSLquVJvHlPGet8V4yzPHVLdhk3tvBFam1rR-jHScGACyujkBJK8vzj7hRy0p0N0B-zUgXJbLSC_p_rx7wUq63U37oFi30gdqfrvlx7GCE?purpose=inline)

1\. Administración de camiones

Rol: administrador

- Tabla con patente, capacidad en m³, altura en metros y capacidad de carga en kg.
- Alta, edición, consulta y baja lógica o desactivación, según las reglas definidas.
- Estado operativo y disponibilidad para planificar.
- Validaciones de capacidad, patente y datos obligatorios.
- Confirmación antes de desactivar un camión asignado a una ruta.

[Software y soluciones de rastreo de flotas por GPS | Samsara](https://images.openai.com/static-rsc-4/RxqrE_NWJuMGYq4__NF8q92QFBhsN2R7YOSiQaNCS9jsc324672tJZ3SG_wLbGVxZ56UsYfS8wARHOSfQqWUWlCl9YhsDEQrdq93Gcgigqz3do_ZY9gAi-25D0mCZX7AI4yuoa3t1Gd9A2nr41VcsCz0YYOXnCz03vfw2K3Xdrw?purpose=inline)

2\. Planificación de rutas

Rol: administrador u operador logístico

- Consultar las donaciones elegibles para planificar.
- Seleccionar o verificar los camiones disponibles.
- Ejecutar la planificación para el día siguiente.
- Mostrar estado del proceso: pendiente, procesando, completado o fallido.
- Ver rutas por camión, orden de destinos, direcciones y entregas asociadas.
- Revisar resultados y gestionar las donaciones que no pudieron incluirse.

[Technology | Jitsu](https://images.openai.com/static-rsc-4/zttO_DrAIvbLjoHBOJR0J_VkXq1xAoOiLjzQ9uUq9YoTIZMRayaJiO82L5qY74wscDHc9Fb5KvlZOjCOk9Cecb3EptCEuTwjejh_JOQOSgdlGDmp6ZyizugL0K5d0qRVPeDdWxUmCY5wIcbxHBE0Cb5uNy5Cv4_nn-TrVb_rpGQ?purpose=inline)

3\. Panel del chofer

Rol: chofer autenticado

- Ver las rutas asignadas al camión y al día correspondiente.
- Consultar la lista ordenada de destinos.
- Ver dirección, entregas previstas y detalles necesarios para la visita.
- Iniciar la ruta con una acción explícita.
- Ver el estado activo de la ruta y los destinos restantes.
- Informar incidencias si el proceso lo contempla.

[Transportation App Development: Features, Cost, and More](https://images.openai.com/static-rsc-4/KPYL9LyzKrqjuvcwrjZnHXiKiPRoGSmizCKs1JlA-qEkJ6SHTGUDcASA7vEUNdQ5D9ibDDoxKhCN_m2o_21b70eIFLG99q63lJmvWzvDglkronjoWTxa4PL8NeY9qj_3pNfmB9zRGPLqKit1vEISIJuhlNBJH7R0_H_YvLLugBc?purpose=inline)

4\. Portal de la entidad beneficiaria

Rol: usuario de la entidad

- Consultar entregas propias, destino y estado.
- Confirmar recepción de una donación.
- Cargar fotos de la donación recibida.
- Informar que la entrega no fue recibida en el día correspondiente.
- Consultar el comprobante de entrega, cuando exista.
- Ver la información de seguimiento de una ruta relacionada.

[Haul - Fleet Tracking Dashboard by Reza Moradi on Dribbble](https://images.openai.com/static-rsc-4/kQw2vmoB44Tabk6O6k_vwRIK7J9jSUsIMRvW9SvbLMJgimK8ja6fjQG-s-PiWdEGvVqcHUZz52jVAfdAXLzKXqn4L4li5R9bQvBQ4doxIGrJL9j-eZEXQVBrEQBO1QiljIkHHTk3xQT0UyfXtKqY_kzWCVpf34UzxqBtYJ0wOG0?purpose=inline)

5\. Dashboard de seguimiento en tiempo real

Rol: administrador; acceso adicional según permisos

- Mapa con la posición actual de cada camión en ruta.
- Ruta planificada y avance sobre sus destinos.
- Última actualización de posición y velocidad, si está disponible.
- Identificación del camión, ruta y estado.
- Indicador de posición desactualizada o vehículo sin señal.
- Acceso al detalle de la ruta y sus entregas.

[Delivery Software - ePOD, Tracking & Route Optimization](https://images.openai.com/static-rsc-4/UOvRA-2QgvGxv9YElcoZNfYjK7vaGbwtf581ZoEwcq-DgTxu8iEd1BmyRibkkt4fMq6itNvqd4FWEm-a3RmE06X4jLpFm92Z0vkwjkYBk_0y9D59DNy7gSdSpTHyb752mKUKCIw9rytFuoodMk85Oe0XVNRcLqJVn7tVLQidQXI?purpose=inline)

6\. Gestión de entregas e incidencias

Rol: administrador u operador autorizado

- Buscar entregas por fecha, estado, entidad, camión o ruta.
- Revisar entregas `No recibida`.
- Consultar motivos de entrega no satisfactoria.
- Registrar la revisión administrativa y el resultado.
- Cuando la donación regresa al depósito, devolver la entrega al estado `Pendiente`.
- Permitir su inclusión en una nueva planificación, sin duplicar asignaciones activas.

Las imágenes son referencias visuales orientativas, no capturas del frontend actual de DonaTrack. Las vistas exactas deben contrastarse con las plantillas y controladores que ya existan.

### ¿Qué debería mostrarse en cada vista?

| Vista              | Datos principales                                                   | Acciones permitidas                                  |
| ------------------ | ------------------------------------------------------------------- | ---------------------------------------------------- |
| Camiones           | Patente, dimensiones, capacidades, disponibilidad                   | Crear, editar, desactivar, consultar                 |
| Planificación      | Fecha, camiones disponibles, entregas elegibles, estado del proceso | Generar planificación, consultar resultados          |
| Detalle de ruta    | Camión, chofer, destinos ordenados, entregas y estado               | Consultar; iniciar desde la interfaz del chofer      |
| Panel del chofer   | Ruta asignada, paradas y entregas                                   | Iniciar ruta, consultar el recorrido                 |
| Recepción          | Entrega esperada, fecha, estado, fotos                              | Confirmar, adjuntar evidencia, informar no recepción |
| Tracking           | Posición, última actualización, ruta, avance                        | Consultar mapa y detalles autorizados                |
| Incidencias        | Motivo, historial, estado, evidencia                                | Revisar y registrar resolución                       |
| Detalle de entrega | Estado actual, historial, camión y fechas                           | Consultar y realizar solo transiciones autorizadas   |

Cada vista debe contemplar estados de carga, ausencia de datos, error de API, permisos insuficientes y confirmación de operaciones exitosas. No alcanza con que el botón ejecute una petición: la pantalla tiene que reflejar el estado persistido por el backend.

## 4. Flujo completo de planificación de rutas

Este es uno de los procesos centrales del servicio.

El enunciado establece que el componente externo debe recibir las donaciones en estado `Asignación Realizada` y los camiones disponibles, para devolver por cada camión una lista ordenada de destinos con las entregas correspondientes. Las rutas resultantes deben quedar disponibles para los choferes.

BD LogísticaPlanificador externoDonacionesLogísticaAPI GatewayFrontendBD LogísticaPlanificador externoDonacionesLogísticaAPI GatewayFrontend#chatgpt-mermaid-\_r_kc\_{font-family:-apple-system-body,ui-sans-serif,-apple-system,system-ui,"Segoe UI",Helvetica,"Apple Color Emoji",Arial,sans-serif,"Segoe UI Emoji","Segoe UI Symbol";font-size:16px;fill:rgb(237, 237, 237);}@keyframes edge-animation-frame{from{stroke-dashoffset:0;}}@keyframes dash{to{stroke-dashoffset:0;}}#chatgpt-mermaid-\_r_kc\_ .edge-animation-slow{stroke-dasharray:9,5!important;stroke-dashoffset:900;animation:dash 50s linear infinite;stroke-linecap:round;}#chatgpt-mermaid-\_r_kc\_ .edge-animation-fast{stroke-dasharray:9,5!important;stroke-dashoffset:900;animation:dash 20s linear infinite;stroke-linecap:round;}#chatgpt-mermaid-\_r_kc\_ .error-icon{fill:rgb(48, 48, 48);}#chatgpt-mermaid-\_r_kc\_ .error-text{fill:rgb(237, 237, 237);stroke:rgb(237, 237, 237);}#chatgpt-mermaid-\_r_kc\_ .edge-thickness-normal{stroke-width:1px;}#chatgpt-mermaid-\_r_kc\_ .edge-thickness-thick{stroke-width:3.5px;}#chatgpt-mermaid-\_r_kc\_ .edge-pattern-solid{stroke-dasharray:0;}#chatgpt-mermaid-\_r_kc\_ .edge-thickness-invisible{stroke-width:0;fill:none;}#chatgpt-mermaid-\_r_kc\_ .edge-pattern-dashed{stroke-dasharray:3;}#chatgpt-mermaid-\_r_kc\_ .edge-pattern-dotted{stroke-dasharray:2;}#chatgpt-mermaid-\_r_kc\_ .marker{fill:rgb(175, 175, 175);stroke:rgb(175, 175, 175);}#chatgpt-mermaid-\_r_kc\_ .marker.cross{stroke:rgb(175, 175, 175);}#chatgpt-mermaid-\_r_kc\_ svg{font-family:-apple-system-body,ui-sans-serif,-apple-system,system-ui,"Segoe UI",Helvetica,"Apple Color Emoji",Arial,sans-serif,"Segoe UI Emoji","Segoe UI Symbol";font-size:16px;}#chatgpt-mermaid-\_r_kc\_ p{margin:0;}#chatgpt-mermaid-\_r_kc\_ .actor{stroke:rgb(31, 78, 148);fill:rgb(9, 23, 44);stroke-width:1;}#chatgpt-mermaid-\_r_kc\_ rect.actor.outer-path[data-look="neo"]{filter:drop-shadow( 1px 2px 2px rgba(185,185,185,1));}#chatgpt-mermaid-\_r_kc\_ rect.note[data-look="neo"]{stroke:rgb(58, 132, 63);fill:rgb(48, 48, 48);filter:drop-shadow( 1px 2px 2px rgba(185,185,185,1));}#chatgpt-mermaid-\_r_kc\_ text.actor>tspan{fill:rgb(237, 237, 237);stroke:none;}#chatgpt-mermaid-\_r_kc\_ .actor-line{stroke:rgb(175, 175, 175);}#chatgpt-mermaid-\_r_kc\_ .innerArc{stroke-width:1.5;stroke-dasharray:none;}#chatgpt-mermaid-\_r_kc\_ .messageLine0{stroke-width:1.5;stroke-dasharray:none;stroke:rgb(175, 175, 175);}#chatgpt-mermaid-\_r_kc\_ .messageLine1{stroke-width:1.5;stroke-dasharray:2,2;stroke:rgb(175, 175, 175);}#chatgpt-mermaid-\_r_kc\_ [id$="-arrowhead"] path{fill:rgb(175, 175, 175);stroke:rgb(175, 175, 175);}#chatgpt-mermaid-\_r_kc\_ .sequenceNumber{fill:#505050;}#chatgpt-mermaid-\_r_kc\_ [id$="-sequencenumber"]{fill:rgb(175, 175, 175);}#chatgpt-mermaid-\_r_kc\_ [id$="-crosshead"] path{fill:rgb(175, 175, 175);stroke:rgb(175, 175, 175);}#chatgpt-mermaid-\_r_kc\_ .messageText{fill:rgb(237, 237, 237);stroke:none;}#chatgpt-mermaid-\_r_kc\_ .labelBox{stroke:rgba(255, 255, 255, 0.15);fill:rgb(0, 0, 0);filter:none;}#chatgpt-mermaid-\_r_kc\_ .labelText,#chatgpt-mermaid-\_r_kc\_ .labelText>tspan{fill:rgb(237, 237, 237);stroke:none;}#chatgpt-mermaid-\_r_kc\_ .loopText,#chatgpt-mermaid-\_r_kc\_ .loopText>tspan{fill:rgb(237, 237, 237);stroke:none;}#chatgpt-mermaid-\_r_kc\_ .sectionTitle,#chatgpt-mermaid-\_r_kc\_ .sectionTitle>tspan{fill:rgb(237, 237, 237);stroke:none;}#chatgpt-mermaid-\_r_kc\_ .loopLine{stroke-width:2px;stroke-dasharray:2,2;stroke:rgba(255, 255, 255, 0.15);fill:rgba(255, 255, 255, 0.15);}#chatgpt-mermaid-\_r_kc\_ .note{stroke:rgb(58, 132, 63);fill:rgb(48, 48, 48);}#chatgpt-mermaid-\_r_kc\_ .noteText,#chatgpt-mermaid-\_r_kc\_ .noteText>tspan{fill:rgb(237, 237, 237);stroke:none;font-weight:normal;}#chatgpt-mermaid-\_r_kc\_ .activation0{fill:rgb(48, 48, 48);stroke:hsl(0, 0%, 8.8235294118%);}#chatgpt-mermaid-\_r_kc\_ .activation1{fill:rgb(48, 48, 48);stroke:hsl(0, 0%, 8.8235294118%);}#chatgpt-mermaid-\_r_kc\_ .activation2{fill:rgb(48, 48, 48);stroke:hsl(0, 0%, 8.8235294118%);}#chatgpt-mermaid-\_r_kc\_ .actorPopupMenu{position:absolute;}#chatgpt-mermaid-\_r_kc\_ .actorPopupMenuPanel{position:absolute;fill:rgb(9, 23, 44);box-shadow:0px 8px 16px 0px rgba(0,0,0,0.2);filter:drop-shadow(3px 5px 2px rgb(0 0 0 / 0.4));}#chatgpt-mermaid-\_r_kc\_ .actor-man circle,#chatgpt-mermaid-\_r_kc\_ line{fill:rgb(9, 23, 44);stroke-width:2px;}#chatgpt-mermaid-\_r_kc\_ g rect.rect{filter:drop-shadow( 1px 2px 2px rgba(185,185,185,1));stroke:rgb(31, 78, 148);}#chatgpt-mermaid-\_r_kc\_ .node .neo-node{stroke:rgb(31, 78, 148);}#chatgpt-mermaid-\_r_kc\_ [data-look="neo"].node rect,#chatgpt-mermaid-\_r_kc\_ [data-look="neo"].cluster rect,#chatgpt-mermaid-\_r_kc\_ [data-look="neo"].node polygon{stroke:url(#chatgpt-mermaid-\_r_kc\_-gradient);filter:drop-shadow( 1px 2px 2px rgba(185,185,185,1));}#chatgpt-mermaid-\_r_kc\_ [data-look="neo"].swimlane.cluster rect{filter:none;}#chatgpt-mermaid-\_r_kc\_ [data-look="neo"].node path{stroke:url(#chatgpt-mermaid-\_r_kc\_-gradient);stroke-width:1px;}#chatgpt-mermaid-\_r_kc\_ [data-look="neo"].node .outer-path{filter:drop-shadow( 1px 2px 2px rgba(185,185,185,1));}#chatgpt-mermaid-\_r_kc\_ [data-look="neo"].node .neo-line path{stroke:rgb(31, 78, 148);filter:none;}#chatgpt-mermaid-\_r_kc\_ [data-look="neo"].node circle{stroke:url(#chatgpt-mermaid-\_r_kc\_-gradient);filter:drop-shadow( 1px 2px 2px rgba(185,185,185,1));}#chatgpt-mermaid-\_r_kc\_ [data-look="neo"].node circle .state-start{fill:#000000;}#chatgpt-mermaid-\_r_kc\_ [data-look="neo"].icon-shape .icon{fill:url(#chatgpt-mermaid-\_r_kc\_-gradient);filter:drop-shadow( 1px 2px 2px rgba(185,185,185,1));}#chatgpt-mermaid-\_r_kc\_ [data-look="neo"].icon-shape .icon-neo path{stroke:url(#chatgpt-mermaid-\_r_kc\_-gradient);filter:drop-shadow( 1px 2px 2px rgba(185,185,185,1));}#chatgpt-mermaid-\_r_kc\_ :root{--mermaid-font-family:-apple-system-body,ui-sans-serif,-apple-system,system-ui,"Segoe UI",Helvetica,"Apple Color Emoji",Arial,sans-serif,"Segoe UI Emoji","Segoe UI Symbol";}AdministradorSolicitar planificación del día siguientePOST solicitud de planificaciónReenviar peticiónConsultar donaciones elegiblesDonaciones en Asignación RealizadaConsultar camiones disponiblesFlota elegibleRegistrar planificación pendienteAceptar solicitud / devolver IDEnviar donaciones y camionesRutas y destinos ordenadosValidar resultadoGuardar rutas y paradasResultado disponible al consultarMostrar rutas generadasAdministrador

### Pasos que deben implementarse

1. Iniciar la planificación. El administrador solicita generar las rutas del día siguiente.
2. Obtener las donaciones elegibles. Logística consulta a Donaciones cuáles están en `Asignación Realizada` y dispone de los datos necesarios para entregarlas.
3. Obtener la flota. Se seleccionan los camiones disponibles y se respetan sus capacidades.
4. Enviar la solicitud al planificador. Se define un contrato con las donaciones, direcciones de destino y datos de cada camión.
5. Procesar de forma asíncrona. Si el cálculo tarda, el frontend no debe quedarse esperando indefinidamente. La API puede devolver un identificador de planificación para consultar su progreso.
6. Validar la respuesta. Comprobar que las rutas referencien camiones válidos, que los destinos correspondan a las entregas solicitadas y que no haya asignaciones duplicadas.
7. Persistir el resultado. Guardar la ruta, el camión, sus paradas ordenadas y las entregas asociadas.
8. Publicar las rutas. Dejarlas visibles para los choferes autorizados.
9. Mostrar el resultado. La interfaz distingue entre una planificación exitosa, parcial, vacía o fallida.

### Validaciones indispensables

- Un camión no puede superar su capacidad de volumen ni de carga.
- La altura del camión debe conservarse como atributo de la flota y utilizarse si las restricciones de circulación o el planificador lo requieren.
- Una misma entrega no puede quedar asignada simultáneamente a dos rutas activas.
- Un resultado externo incompleto o inconsistente no debe publicarse como planificación válida.
- Si el planificador falla, debe registrarse el error y permitir un reintento controlado.
- Una nueva ejecución no debe borrar rutas que ya comenzaron o tienen entregas en curso.
- Las rutas deben corresponder a la fecha de reparto adecuada.

Decisión de diseño: el algoritmo puede ejecutarse en un componente externo, como exige el enunciado. Logística conserva la responsabilidad de validar, persistir y exponer el resultado. No es necesario implementar el algoritmo de optimización dentro de Logística si el contrato externo ya resuelve esa tarea.

## 5. Flujo de inicio de ruta

Cuando el chofer está listo para salir del depósito, debe iniciar explícitamente la ruta desde su panel.

NotificacionesRabbitMQBD LogísticaLogísticaFrontendNotificacionesRabbitMQBD LogísticaLogísticaFrontend#chatgpt-mermaid-\_r_kj\_{font-family:-apple-system-body,ui-sans-serif,-apple-system,system-ui,"Segoe UI",Helvetica,"Apple Color Emoji",Arial,sans-serif,"Segoe UI Emoji","Segoe UI Symbol";font-size:16px;fill:rgb(237, 237, 237);}@keyframes edge-animation-frame{from{stroke-dashoffset:0;}}@keyframes dash{to{stroke-dashoffset:0;}}#chatgpt-mermaid-\_r_kj\_ .edge-animation-slow{stroke-dasharray:9,5!important;stroke-dashoffset:900;animation:dash 50s linear infinite;stroke-linecap:round;}#chatgpt-mermaid-\_r_kj\_ .edge-animation-fast{stroke-dasharray:9,5!important;stroke-dashoffset:900;animation:dash 20s linear infinite;stroke-linecap:round;}#chatgpt-mermaid-\_r_kj\_ .error-icon{fill:rgb(48, 48, 48);}#chatgpt-mermaid-\_r_kj\_ .error-text{fill:rgb(237, 237, 237);stroke:rgb(237, 237, 237);}#chatgpt-mermaid-\_r_kj\_ .edge-thickness-normal{stroke-width:1px;}#chatgpt-mermaid-\_r_kj\_ .edge-thickness-thick{stroke-width:3.5px;}#chatgpt-mermaid-\_r_kj\_ .edge-pattern-solid{stroke-dasharray:0;}#chatgpt-mermaid-\_r_kj\_ .edge-thickness-invisible{stroke-width:0;fill:none;}#chatgpt-mermaid-\_r_kj\_ .edge-pattern-dashed{stroke-dasharray:3;}#chatgpt-mermaid-\_r_kj\_ .edge-pattern-dotted{stroke-dasharray:2;}#chatgpt-mermaid-\_r_kj\_ .marker{fill:rgb(175, 175, 175);stroke:rgb(175, 175, 175);}#chatgpt-mermaid-\_r_kj\_ .marker.cross{stroke:rgb(175, 175, 175);}#chatgpt-mermaid-\_r_kj\_ svg{font-family:-apple-system-body,ui-sans-serif,-apple-system,system-ui,"Segoe UI",Helvetica,"Apple Color Emoji",Arial,sans-serif,"Segoe UI Emoji","Segoe UI Symbol";font-size:16px;}#chatgpt-mermaid-\_r_kj\_ p{margin:0;}#chatgpt-mermaid-\_r_kj\_ .actor{stroke:rgb(31, 78, 148);fill:rgb(9, 23, 44);stroke-width:1;}#chatgpt-mermaid-\_r_kj\_ rect.actor.outer-path[data-look="neo"]{filter:drop-shadow( 1px 2px 2px rgba(185,185,185,1));}#chatgpt-mermaid-\_r_kj\_ rect.note[data-look="neo"]{stroke:rgb(58, 132, 63);fill:rgb(48, 48, 48);filter:drop-shadow( 1px 2px 2px rgba(185,185,185,1));}#chatgpt-mermaid-\_r_kj\_ text.actor>tspan{fill:rgb(237, 237, 237);stroke:none;}#chatgpt-mermaid-\_r_kj\_ .actor-line{stroke:rgb(175, 175, 175);}#chatgpt-mermaid-\_r_kj\_ .innerArc{stroke-width:1.5;stroke-dasharray:none;}#chatgpt-mermaid-\_r_kj\_ .messageLine0{stroke-width:1.5;stroke-dasharray:none;stroke:rgb(175, 175, 175);}#chatgpt-mermaid-\_r_kj\_ .messageLine1{stroke-width:1.5;stroke-dasharray:2,2;stroke:rgb(175, 175, 175);}#chatgpt-mermaid-\_r_kj\_ [id$="-arrowhead"] path{fill:rgb(175, 175, 175);stroke:rgb(175, 175, 175);}#chatgpt-mermaid-\_r_kj\_ .sequenceNumber{fill:#505050;}#chatgpt-mermaid-\_r_kj\_ [id$="-sequencenumber"]{fill:rgb(175, 175, 175);}#chatgpt-mermaid-\_r_kj\_ [id$="-crosshead"] path{fill:rgb(175, 175, 175);stroke:rgb(175, 175, 175);}#chatgpt-mermaid-\_r_kj\_ .messageText{fill:rgb(237, 237, 237);stroke:none;}#chatgpt-mermaid-\_r_kj\_ .labelBox{stroke:rgba(255, 255, 255, 0.15);fill:rgb(0, 0, 0);filter:none;}#chatgpt-mermaid-\_r_kj\_ .labelText,#chatgpt-mermaid-\_r_kj\_ .labelText>tspan{fill:rgb(237, 237, 237);stroke:none;}#chatgpt-mermaid-\_r_kj\_ .loopText,#chatgpt-mermaid-\_r_kj\_ .loopText>tspan{fill:rgb(237, 237, 237);stroke:none;}#chatgpt-mermaid-\_r_kj\_ .sectionTitle,#chatgpt-mermaid-\_r_kj\_ .sectionTitle>tspan{fill:rgb(237, 237, 237);stroke:none;}#chatgpt-mermaid-\_r_kj\_ .loopLine{stroke-width:2px;stroke-dasharray:2,2;stroke:rgba(255, 255, 255, 0.15);fill:rgba(255, 255, 255, 0.15);}#chatgpt-mermaid-\_r_kj\_ .note{stroke:rgb(58, 132, 63);fill:rgb(48, 48, 48);}#chatgpt-mermaid-\_r_kj\_ .noteText,#chatgpt-mermaid-\_r_kj\_ .noteText>tspan{fill:rgb(237, 237, 237);stroke:none;font-weight:normal;}#chatgpt-mermaid-\_r_kj\_ .activation0{fill:rgb(48, 48, 48);stroke:hsl(0, 0%, 8.8235294118%);}#chatgpt-mermaid-\_r_kj\_ .activation1{fill:rgb(48, 48, 48);stroke:hsl(0, 0%, 8.8235294118%);}#chatgpt-mermaid-\_r_kj\_ .activation2{fill:rgb(48, 48, 48);stroke:hsl(0, 0%, 8.8235294118%);}#chatgpt-mermaid-\_r_kj\_ .actorPopupMenu{position:absolute;}#chatgpt-mermaid-\_r_kj\_ .actorPopupMenuPanel{position:absolute;fill:rgb(9, 23, 44);box-shadow:0px 8px 16px 0px rgba(0,0,0,0.2);filter:drop-shadow(3px 5px 2px rgb(0 0 0 / 0.4));}#chatgpt-mermaid-\_r_kj\_ .actor-man circle,#chatgpt-mermaid-\_r_kj\_ line{fill:rgb(9, 23, 44);stroke-width:2px;}#chatgpt-mermaid-\_r_kj\_ g rect.rect{filter:drop-shadow( 1px 2px 2px rgba(185,185,185,1));stroke:rgb(31, 78, 148);}#chatgpt-mermaid-\_r_kj\_ .node .neo-node{stroke:rgb(31, 78, 148);}#chatgpt-mermaid-\_r_kj\_ [data-look="neo"].node rect,#chatgpt-mermaid-\_r_kj\_ [data-look="neo"].cluster rect,#chatgpt-mermaid-\_r_kj\_ [data-look="neo"].node polygon{stroke:url(#chatgpt-mermaid-\_r_kj\_-gradient);filter:drop-shadow( 1px 2px 2px rgba(185,185,185,1));}#chatgpt-mermaid-\_r_kj\_ [data-look="neo"].swimlane.cluster rect{filter:none;}#chatgpt-mermaid-\_r_kj\_ [data-look="neo"].node path{stroke:url(#chatgpt-mermaid-\_r_kj\_-gradient);stroke-width:1px;}#chatgpt-mermaid-\_r_kj\_ [data-look="neo"].node .outer-path{filter:drop-shadow( 1px 2px 2px rgba(185,185,185,1));}#chatgpt-mermaid-\_r_kj\_ [data-look="neo"].node .neo-line path{stroke:rgb(31, 78, 148);filter:none;}#chatgpt-mermaid-\_r_kj\_ [data-look="neo"].node circle{stroke:url(#chatgpt-mermaid-\_r_kj\_-gradient);filter:drop-shadow( 1px 2px 2px rgba(185,185,185,1));}#chatgpt-mermaid-\_r_kj\_ [data-look="neo"].node circle .state-start{fill:#000000;}#chatgpt-mermaid-\_r_kj\_ [data-look="neo"].icon-shape .icon{fill:url(#chatgpt-mermaid-\_r_kj\_-gradient);filter:drop-shadow( 1px 2px 2px rgba(185,185,185,1));}#chatgpt-mermaid-\_r_kj\_ [data-look="neo"].icon-shape .icon-neo path{stroke:url(#chatgpt-mermaid-\_r_kj\_-gradient);filter:drop-shadow( 1px 2px 2px rgba(185,185,185,1));}#chatgpt-mermaid-\_r_kj\_ :root{--mermaid-font-family:-apple-system-body,ui-sans-serif,-apple-system,system-ui,"Segoe UI",Helvetica,"Apple Color Emoji",Arial,sans-serif,"Segoe UI Emoji","Segoe UI Symbol";}ChoferPulsar "Iniciar ruta"Solicitud de inicioValidar chofer, camión, fecha y estadoGuardar inicio y activar rutaPasar entregas de la ruta a En trasladoRuta iniciadaPublicar ruta.iniciadaConsumir eventoNotificar a entidades y donantesMostrar ruta activaChofer

El backend debe validar que:

- La ruta exista y esté planificada.
- El chofer esté autorizado para iniciar esa ruta.
- El camión coincida con el asignado.
- La ruta no esté ya iniciada, finalizada o cancelada.
- El inicio quede registrado con fecha y hora.
- Las entregas asociadas pasen a `En traslado`, según el modelo del proyecto.
- El evento de inicio se publique una sola vez desde la perspectiva del negocio, aunque la infraestructura de mensajería pueda entregar duplicados.

Al iniciar la ruta, Notificaciones debe avisar a las entidades beneficiarias y a los donantes cuyas entregas forman parte de ella. La notificación debe incluir un enlace al mapa interactivo que permita seguir la entrega en tiempo real.

La vista del chofer debe cambiar a estado activo y la vista administrativa debe reflejar el nuevo estado. La actualización puede hacerse mediante consultas periódicas o un canal en tiempo real, según lo implementado.

## 6. Flujo de seguimiento en tiempo real

El TP plantea dos alternativas válidas:

- Un dispositivo GPS instalado en el camión que informa periódicamente la ubicación y velocidad.
- Una aplicación móvil del chofer que reporta la geolocalización mientras la ruta está activa.

El equipo debe elegir una y definir el contrato de integración. La plataforma recibe y procesa esos datos; la configuración del dispositivo o de la aplicación de campo queda a cargo del equipo externo.

### Procesamiento recomendado

1. Recibir la posición del camión.
2. Autenticar el dispositivo o cliente que la envía.
3. Validar que la ruta esté activa y que el camión corresponda a ella.
4. Validar latitud, longitud, fecha de captura y, si se informa, velocidad.
5. Rechazar o ignorar datos inválidos o demasiado antiguos.
6. Persistir la última posición y, si se necesita auditoría, conservar un historial de ubicaciones.
7. Calcular o actualizar el avance sobre la ruta asignada.
8. Exponer la información al dashboard administrativo.
9. Indicar si la última posición está desactualizada.

### Qué debe mostrar el mapa

- Posición del camión.
- Ruta planificada.
- Orden de los destinos y paradas.
- Entregas completadas y pendientes.
- Estado de la ruta.
- Hora de la última posición recibida.
- Velocidad, si el dispositivo la proporciona.
- Indicador de pérdida de señal o datos obsoletos.

No conviene mostrar un camión como si estuviera en vivo cuando no se reciben actualizaciones. La interfaz debe indicar claramente cuándo la última posición dejó de actualizarse.

Importante: Leaflet.js y OpenStreetMap figuran en la documentación del frontend. Son una opción coherente para visualizar el mapa, pero el mapa no sustituye al backend: el navegador no debe inventar posiciones ni ser la fuente de verdad de la ruta.

## 7. Flujo de entrega exitosa

Cuando la entidad recibe una donación, debe confirmar la recepción desde su portal.

NotificacionesRabbitMQBD LogísticaLogísticaFrontendNotificacionesRabbitMQBD LogísticaLogísticaFrontend#chatgpt-mermaid-\_r_l1\_{font-family:-apple-system-body,ui-sans-serif,-apple-system,system-ui,"Segoe UI",Helvetica,"Apple Color Emoji",Arial,sans-serif,"Segoe UI Emoji","Segoe UI Symbol";font-size:16px;fill:rgb(237, 237, 237);}@keyframes edge-animation-frame{from{stroke-dashoffset:0;}}@keyframes dash{to{stroke-dashoffset:0;}}#chatgpt-mermaid-\_r_l1\_ .edge-animation-slow{stroke-dasharray:9,5!important;stroke-dashoffset:900;animation:dash 50s linear infinite;stroke-linecap:round;}#chatgpt-mermaid-\_r_l1\_ .edge-animation-fast{stroke-dasharray:9,5!important;stroke-dashoffset:900;animation:dash 20s linear infinite;stroke-linecap:round;}#chatgpt-mermaid-\_r_l1\_ .error-icon{fill:rgb(48, 48, 48);}#chatgpt-mermaid-\_r_l1\_ .error-text{fill:rgb(237, 237, 237);stroke:rgb(237, 237, 237);}#chatgpt-mermaid-\_r_l1\_ .edge-thickness-normal{stroke-width:1px;}#chatgpt-mermaid-\_r_l1\_ .edge-thickness-thick{stroke-width:3.5px;}#chatgpt-mermaid-\_r_l1\_ .edge-pattern-solid{stroke-dasharray:0;}#chatgpt-mermaid-\_r_l1\_ .edge-thickness-invisible{stroke-width:0;fill:none;}#chatgpt-mermaid-\_r_l1\_ .edge-pattern-dashed{stroke-dasharray:3;}#chatgpt-mermaid-\_r_l1\_ .edge-pattern-dotted{stroke-dasharray:2;}#chatgpt-mermaid-\_r_l1\_ .marker{fill:rgb(175, 175, 175);stroke:rgb(175, 175, 175);}#chatgpt-mermaid-\_r_l1\_ .marker.cross{stroke:rgb(175, 175, 175);}#chatgpt-mermaid-\_r_l1\_ svg{font-family:-apple-system-body,ui-sans-serif,-apple-system,system-ui,"Segoe UI",Helvetica,"Apple Color Emoji",Arial,sans-serif,"Segoe UI Emoji","Segoe UI Symbol";font-size:16px;}#chatgpt-mermaid-\_r_l1\_ p{margin:0;}#chatgpt-mermaid-\_r_l1\_ .actor{stroke:rgb(31, 78, 148);fill:rgb(9, 23, 44);stroke-width:1;}#chatgpt-mermaid-\_r_l1\_ rect.actor.outer-path[data-look="neo"]{filter:drop-shadow( 1px 2px 2px rgba(185,185,185,1));}#chatgpt-mermaid-\_r_l1\_ rect.note[data-look="neo"]{stroke:rgb(58, 132, 63);fill:rgb(48, 48, 48);filter:drop-shadow( 1px 2px 2px rgba(185,185,185,1));}#chatgpt-mermaid-\_r_l1\_ text.actor>tspan{fill:rgb(237, 237, 237);stroke:none;}#chatgpt-mermaid-\_r_l1\_ .actor-line{stroke:rgb(175, 175, 175);}#chatgpt-mermaid-\_r_l1\_ .innerArc{stroke-width:1.5;stroke-dasharray:none;}#chatgpt-mermaid-\_r_l1\_ .messageLine0{stroke-width:1.5;stroke-dasharray:none;stroke:rgb(175, 175, 175);}#chatgpt-mermaid-\_r_l1\_ .messageLine1{stroke-width:1.5;stroke-dasharray:2,2;stroke:rgb(175, 175, 175);}#chatgpt-mermaid-\_r_l1\_ [id$="-arrowhead"] path{fill:rgb(175, 175, 175);stroke:rgb(175, 175, 175);}#chatgpt-mermaid-\_r_l1\_ .sequenceNumber{fill:#505050;}#chatgpt-mermaid-\_r_l1\_ [id$="-sequencenumber"]{fill:rgb(175, 175, 175);}#chatgpt-mermaid-\_r_l1\_ [id$="-crosshead"] path{fill:rgb(175, 175, 175);stroke:rgb(175, 175, 175);}#chatgpt-mermaid-\_r_l1\_ .messageText{fill:rgb(237, 237, 237);stroke:none;}#chatgpt-mermaid-\_r_l1\_ .labelBox{stroke:rgba(255, 255, 255, 0.15);fill:rgb(0, 0, 0);filter:none;}#chatgpt-mermaid-\_r_l1\_ .labelText,#chatgpt-mermaid-\_r_l1\_ .labelText>tspan{fill:rgb(237, 237, 237);stroke:none;}#chatgpt-mermaid-\_r_l1\_ .loopText,#chatgpt-mermaid-\_r_l1\_ .loopText>tspan{fill:rgb(237, 237, 237);stroke:none;}#chatgpt-mermaid-\_r_l1\_ .sectionTitle,#chatgpt-mermaid-\_r_l1\_ .sectionTitle>tspan{fill:rgb(237, 237, 237);stroke:none;}#chatgpt-mermaid-\_r_l1\_ .loopLine{stroke-width:2px;stroke-dasharray:2,2;stroke:rgba(255, 255, 255, 0.15);fill:rgba(255, 255, 255, 0.15);}#chatgpt-mermaid-\_r_l1\_ .note{stroke:rgb(58, 132, 63);fill:rgb(48, 48, 48);}#chatgpt-mermaid-\_r_l1\_ .noteText,#chatgpt-mermaid-\_r_l1\_ .noteText>tspan{fill:rgb(237, 237, 237);stroke:none;font-weight:normal;}#chatgpt-mermaid-\_r_l1\_ .activation0{fill:rgb(48, 48, 48);stroke:hsl(0, 0%, 8.8235294118%);}#chatgpt-mermaid-\_r_l1\_ .activation1{fill:rgb(48, 48, 48);stroke:hsl(0, 0%, 8.8235294118%);}#chatgpt-mermaid-\_r_l1\_ .activation2{fill:rgb(48, 48, 48);stroke:hsl(0, 0%, 8.8235294118%);}#chatgpt-mermaid-\_r_l1\_ .actorPopupMenu{position:absolute;}#chatgpt-mermaid-\_r_l1\_ .actorPopupMenuPanel{position:absolute;fill:rgb(9, 23, 44);box-shadow:0px 8px 16px 0px rgba(0,0,0,0.2);filter:drop-shadow(3px 5px 2px rgb(0 0 0 / 0.4));}#chatgpt-mermaid-\_r_l1\_ .actor-man circle,#chatgpt-mermaid-\_r_l1\_ line{fill:rgb(9, 23, 44);stroke-width:2px;}#chatgpt-mermaid-\_r_l1\_ g rect.rect{filter:drop-shadow( 1px 2px 2px rgba(185,185,185,1));stroke:rgb(31, 78, 148);}#chatgpt-mermaid-\_r_l1\_ .node .neo-node{stroke:rgb(31, 78, 148);}#chatgpt-mermaid-\_r_l1\_ [data-look="neo"].node rect,#chatgpt-mermaid-\_r_l1\_ [data-look="neo"].cluster rect,#chatgpt-mermaid-\_r_l1\_ [data-look="neo"].node polygon{stroke:url(#chatgpt-mermaid-\_r_l1\_-gradient);filter:drop-shadow( 1px 2px 2px rgba(185,185,185,1));}#chatgpt-mermaid-\_r_l1\_ [data-look="neo"].swimlane.cluster rect{filter:none;}#chatgpt-mermaid-\_r_l1\_ [data-look="neo"].node path{stroke:url(#chatgpt-mermaid-\_r_l1\_-gradient);stroke-width:1px;}#chatgpt-mermaid-\_r_l1\_ [data-look="neo"].node .outer-path{filter:drop-shadow( 1px 2px 2px rgba(185,185,185,1));}#chatgpt-mermaid-\_r_l1\_ [data-look="neo"].node .neo-line path{stroke:rgb(31, 78, 148);filter:none;}#chatgpt-mermaid-\_r_l1\_ [data-look="neo"].node circle{stroke:url(#chatgpt-mermaid-\_r_l1\_-gradient);filter:drop-shadow( 1px 2px 2px rgba(185,185,185,1));}#chatgpt-mermaid-\_r_l1\_ [data-look="neo"].node circle .state-start{fill:#000000;}#chatgpt-mermaid-\_r_l1\_ [data-look="neo"].icon-shape .icon{fill:url(#chatgpt-mermaid-\_r_l1\_-gradient);filter:drop-shadow( 1px 2px 2px rgba(185,185,185,1));}#chatgpt-mermaid-\_r_l1\_ [data-look="neo"].icon-shape .icon-neo path{stroke:url(#chatgpt-mermaid-\_r_l1\_-gradient);filter:drop-shadow( 1px 2px 2px rgba(185,185,185,1));}#chatgpt-mermaid-\_r_l1\_ :root{--mermaid-font-family:-apple-system-body,ui-sans-serif,-apple-system,system-ui,"Segoe UI",Helvetica,"Apple Color Emoji",Arial,sans-serif,"Segoe UI Emoji","Segoe UI Symbol";}EntidadAbrir entrega y confirmar recepciónConfirmación + evidencia requeridaValidar entidad, ruta y estadoRegistrar recepción y camiónCambiar estado a EntregadaConfirmación registradaCargar fotos de la donaciónSubir fotografíasGuardar referencias de evidenciaPublicar entrega.confirmadaConsumir eventoNotificar a entidad y donanteMostrar comprobante de entregaEntidad

### Datos que deben quedar registrados

- Donación y entrega relacionadas.
- Entidad beneficiaria que confirma.
- Ruta y camión responsables.
- Fecha y hora de recepción.
- Usuario que realizó la confirmación.
- Fotografías o referencias a ellas.
- Historial de cambios de estado.
- Identificador del evento de negocio para evitar efectos duplicados.

El enunciado indica que la entidad confirma la recepción, la entrega pasa a `Entregada`, queda registrado qué camión realizó el traslado y luego la entidad carga fotografías.

Por lo tanto, conviene definir si la carga de fotos es obligatoria para completar el proceso o si puede realizarse inmediatamente después. Esa decisión debe reflejarse de manera consistente en las reglas de negocio, los estados y la interfaz; no debería quedar implícita.

### Comprobante de entrega

La interfaz debería presentar una confirmación con:

- Identificador de la donación o entrega.
- Entidad receptora.
- Fecha y hora.
- Camión responsable.
- Evidencia cargada.
- Estado final.

Además, Logística publica `entrega.confirmada`. Notificaciones envía el aviso tanto a la entidad como al donante e incluye fecha, hora y camión responsable.

## 8. Flujo de entrega no recibida o no satisfactoria

El enunciado distingue la situación en la que la entidad informa que no recibió la entrega en el día correspondiente. En ese caso se marca `No recibida`, el equipo administrador revisa el caso y, si la donación regresa al depósito, la entrega vuelve a `Pendiente`.

También contempla entregas no satisfactorias por vencimiento de bienes, imposibilidad de recepción u otros incidentes logísticos. Se debe notificar a la entidad, al donante y a las personas administradoras. Si es posible replanificar, se deja constancia del estado y puede generarse una nueva ruta.

### Flujo correcto

1. La entidad informa que no recibió la entrega, o un usuario autorizado registra la incidencia.
2. Logística valida que la entrega corresponda a la entidad y a la ruta esperada.
3. Se registra el motivo, la fecha, el responsable y la evidencia disponible.
4. La entrega pasa al estado `No recibida` o al estado de incidencia que defina el dominio.
5. Se publica un evento `entrega.fallida` o un contrato equivalente.
6. Notificaciones informa a la entidad, al donante y a los administradores.
7. El administrador revisa el caso y registra su resolución.
8. Si la donación regresa físicamente al depósito, se registra ese retorno y la entrega vuelve a `Pendiente`.
9. La donación podrá incorporarse a una planificación posterior si sigue siendo elegible.

No debe volver automáticamente a `Pendiente` solo porque falló una entrega: primero debe conocerse el resultado del caso. Si los bienes no regresaron al depósito, vencieron o ya no pueden entregarse, el flujo requiere otra resolución.

### Qué debe mostrar el frontend

- Estado actual y motivo.
- Historial del recorrido y los intentos de entrega.
- Fecha en la que se esperaba entregar.
- Identidad de quien informó el problema.
- Evidencia o comentarios.
- Estado de revisión administrativa.
- Resolución y, cuando corresponda, indicación de regreso al depósito.
- Disponibilidad para replanificar, si corresponde.

La interfaz no debe tratar `No recibida`, `Pendiente`, `En traslado` y `Entregada` como estados intercambiables. Cada uno tiene un significado operativo distinto.

## 9. Modelo de estados y transiciones

Un servicio de Logística robusto necesita reglas explícitas sobre qué cambios están permitidos. La siguiente es una propuesta para contrastar con los estados que ya usa tu proyecto.

Asignación Realizada

Donaciones informa que la asignación está lista para planificar.

Ruta planificada

Entrega vinculada a una ruta, camión y parada.

En traslado

El chofer inició la ruta.

Entregada

La entidad confirma la recepción; se registra el camión y la fecha.

No recibida

Requiere revisión y resolución administrativa.

Pendiente

Solo cuando se confirma que la donación regresó al depósito y corresponde volver a planificarla.

Este esquema es orientativo, no una máquina de estados definitiva del código. Conviene separar tres conceptos que pueden confundirse:

- Estado de la donación: pertenece al dominio de Donaciones.
- Estado de la entrega: refleja la ejecución logística de una entrega concreta.
- Estado de la ruta: indica si está planificada, activa, finalizada, cancelada o en incidencia.

No hace falta que las tres máquinas tengan exactamente los mismos estados. Por ejemplo, una ruta puede seguir activa mientras una de sus entregas está marcada como no recibida y las demás siguen pendientes de visita.

Antes de programar, hay que definir las transiciones válidas, quién puede ejecutarlas y qué eventos produce cada una. Esto evita que dos pantallas o servicios interpreten de manera distinta una misma situación.

## 10. Eventos que debería publicar Logística

La documentación del proyecto ya menciona RabbitMQ, el exchange `donatrack.events` y nombres como `ruta.iniciada`, `entrega.confirmada` y `entrega.fallida`. Lo importante es que los productores y consumidores compartan contratos estables.

| Evento                   | Cuándo se publica                                       | Consumidores posibles                                           |
| ------------------------ | ------------------------------------------------------- | --------------------------------------------------------------- |
| `ruta.iniciada`          | Se confirma el inicio de una ruta                       | Notificaciones                                                  |
| `entrega.confirmada`     | La entidad confirma la recepción                        | Notificaciones; otros servicios si requieren el hecho           |
| `entrega.fallida`        | Se registra una entrega no satisfactoria                | Notificaciones                                                  |
| `ruta.finalizada`        | Se confirma el cierre de la ruta                        | Otros servicios que necesiten conocerlo                         |
| `entrega.replanificable` | Se determina que la entrega puede volver a planificarse | Proceso de planificación o servicio responsable de asignaciones |

Los últimos dos eventos son recomendaciones, no requisitos explícitos del enunciado. Antes de crearlos, hay que comprobar si ya existe un contrato equivalente.

### Contenido recomendado de un evento

Por ejemplo, `entrega.confirmada` debería contener información suficiente para identificar el hecho sin obligar a Notificaciones a consultar toda la base de datos de Logística.

```
{
  "eventId": "uuid-del-evento",
  "eventType": "entrega.confirmada",
  "eventVersion": 1,
  "occurredAt": "2026-10-27T15:30:00Z",
  "deliveryId": "id-entrega",
  "donationId": "id-donacion",
  "routeId": "id-ruta",
  "truckId": "id-camion",
  "truckPlate": "AB123CD",
  "beneficiaryId": "id-entidad",
  "donorId": "id-donante",
  "deliveredAt": "2026-10-27T15:25:00Z",
  "correlationId": "uuid-de-trazabilidad"
}
```

Los identificadores y nombres son ilustrativos. El contrato definitivo debe ajustarse a los DTO existentes, sin duplicar datos personales innecesarios.

### Reglas para publicar eventos

1. Persistir primero el cambio de negocio.
2. Publicar el evento de forma confiable, idealmente mediante el patrón Transactional Outbox.
3. Incluir un `eventId` único, versión y fecha del hecho.
4. Hacer que los consumidores toleren mensajes duplicados.
5. Configurar reintentos y una Dead Letter Queue para mensajes que no puedan procesarse.
6. No publicar un evento de entrega exitosa si la operación fue rechazada o no quedó registrada.
7. Evitar que un error de notificaciones revierta una entrega confirmada.

## 11. API REST recomendada

El enunciado pide exponer mediante REST la gestión de flota y las operaciones CRUD sobre rutas y entregas. Las siguientes rutas son una propuesta de diseño; deben adaptarse a los endpoints que ya existan en tu repositorio.

| Método                             | Endpoint propuesto                         | Propósito                                                 |
| ---------------------------------- | ------------------------------------------ | --------------------------------------------------------- |
| `GET`                              | `/api/logistica/camiones`                  | Listar y filtrar camiones                                 |
| `POST`                             | `/api/logistica/camiones`                  | Registrar un camión                                       |
| `GET`                              | `/api/logistica/camiones/{id}`             | Consultar un camión                                       |
| `PUT` o `PATCH`                    | `/api/logistica/camiones/{id}`             | Actualizar datos                                          |
| `DELETE` o acción de desactivación | `/api/logistica/camiones/{id}`             | Retirar un camión de la flota, según las reglas acordadas |
| `GET`                              | `/api/logistica/rutas`                     | Listar rutas                                              |
| `POST`                             | `/api/logistica/planificaciones`           | Solicitar una planificación                               |
| `GET`                              | `/api/logistica/planificaciones/{id}`      | Consultar su progreso                                     |
| `GET`                              | `/api/logistica/rutas/{id}`                | Ver detalle de una ruta                                   |
| `POST`                             | `/api/logistica/rutas/{id}/iniciar`        | Iniciar la ruta                                           |
| `GET`                              | `/api/logistica/entregas`                  | Consultar entregas                                        |
| `GET`                              | `/api/logistica/entregas/{id}`             | Ver detalle e historial                                   |
| `POST`                             | `/api/logistica/entregas/{id}/confirmar`   | Confirmar recepción                                       |
| `POST`                             | `/api/logistica/entregas/{id}/no-recibida` | Registrar que no se recibió                               |
| `POST`                             | `/api/logistica/entregas/{id}/evidencias`  | Cargar fotos o referencias a evidencia                    |
| `GET`                              | `/api/logistica/camiones/posiciones`       | Consultar posiciones actuales autorizadas                 |

Podrían existir endpoints específicos para incidencias, replanificación y seguimiento de una ruta. Lo importante es que no se creen dos operaciones diferentes que cambien el mismo estado de forma inconsistente.

### Contrato y respuestas HTTP

- `400 Bad Request`: datos inválidos.
- `401 Unauthorized`: usuario no autenticado.
- `403 Forbidden`: usuario sin permiso.
- `404 Not Found`: recurso inexistente o no accesible.
- `409 Conflict`: transición de estado incompatible, duplicado o ruta ya iniciada.
- `202 Accepted`: planificación aceptada para ejecución asíncrona.
- `200 OK` o `201 Created`: operación completada o recurso creado, según corresponda.

Las respuestas de error deberían incluir un código legible por el frontend, un mensaje seguro y, cuando resulte útil, un identificador de correlación.

## 12. Entidades y persistencia

Una estructura posible para el dominio de Logística sería:

| Entidad             | Datos principales                                                                    |
| ------------------- | ------------------------------------------------------------------------------------ |
| `Camion`            | ID, patente, capacidad de volumen, altura, capacidad de carga, disponibilidad        |
| `Ruta`              | ID, fecha planificada, camión, estado, fecha de inicio y finalización                |
| `ParadaRuta`        | Ruta, orden, dirección o destino, estado de la parada                                |
| `Entrega`           | ID, referencia a donación, entidad, ruta/parada, estado, fechas y camión responsable |
| `EvidenciaEntrega`  | Entrega, referencia de archivo, fecha de carga, usuario que cargó                    |
| `IncidenciaEntrega` | Entrega, motivo, descripción, fecha, responsable y resolución                        |
| `PosicionCamion`    | Camión, latitud, longitud, velocidad opcional, fecha de captura                      |
| `HistorialEstado`   | Recurso, estado anterior/nuevo, fecha, actor y motivo                                |
| `Planificacion`     | ID, fecha objetivo, estado del proceso, resultado, error y marcas temporales         |

No significa que debas crear obligatoriamente nueve tablas. El modelo debe ajustarse al código y a la tecnología de persistencia que ya usa el servicio. Lo importante es que no se pierda la trazabilidad.

### Reglas de persistencia

- La patente debe tener una restricción de unicidad adecuada.
- Las capacidades deben ser positivas.
- Las fechas deben almacenarse con una convención temporal consistente.
- Las rutas deben conservar el orden de las paradas.
- La relación entre entrega y donación debe tener una referencia estable.
- Las evidencias deberían guardarse como archivos o referencias a almacenamiento, no como enormes cadenas dentro de la tabla de entrega.
- Las posiciones GPS requieren una política de retención para no acumular datos indefinidamente.
- Las operaciones de estado deben registrar un historial auditable.
- La desactivación de un camión no debe dejar rutas activas en un estado inconsistente.

## 13. Seguridad y permisos por rol

El frontend debe ocultar las acciones que un rol no puede realizar, pero la autorización real siempre debe aplicarse en el backend.

| Rol                  | Acciones que debería poder realizar                                                                                       |
| -------------------- | ------------------------------------------------------------------------------------------------------------------------- |
| Administrador        | Gestionar camiones, consultar rutas, supervisar el tracking, revisar incidencias y autorizar resoluciones administrativas |
| Operador logístico   | Consultar flota, planificar rutas y gestionar las operaciones logísticas para las que tenga permiso                       |
| Chofer               | Ver sus rutas asignadas, iniciar su recorrido y enviar datos de seguimiento autorizados                                   |
| Entidad beneficiaria | Consultar sus entregas, confirmar recepciones propias, cargar evidencia e informar que no recibió una entrega             |
| Donante              | Consultar el estado y seguimiento de las entregas relacionadas con sus donaciones, según las reglas de acceso             |

Además:

- Validar que una entidad no pueda confirmar la entrega de otra entidad.
- Validar que un chofer no pueda iniciar rutas que no le fueron asignadas.
- No confiar en el `userId`, `donationId`, `truckId` o `beneficiaryId` enviados por el navegador sin comprobar permisos y relaciones.
- Proteger los endpoints que reciben ubicaciones GPS.
- Validar tipos, tamaños y formatos de los archivos de evidencia.
- No exponer ubicaciones de camiones o direcciones de terceros a usuarios no autorizados.
- Mantener secretos, credenciales y datos personales fuera de los logs.
- Si se usa Keycloak, verificar que los roles y los JWT se validen correctamente en el Gateway y en los puntos de acceso que correspondan.

## 14. Pruebas que debería superar el servicio

No basta con comprobar que los endpoints responden. Hay que probar las reglas del dominio y la integración entre servicios.

## Checklist de auditoría

0 de 23

Flota

No se permite registrar una patente duplicada.

Se rechazan capacidades inválidas o negativas.

No se desactiva un camión con una ruta activa sin resolver esa situación.

Planificación

Solo se incluyen donaciones elegibles para el reparto.

Se respetan las capacidades del camión.

Se detectan destinos, rutas o entregas duplicados.

Si el planificador falla, el proceso queda registrado como fallido y puede reintentarse.

Rutas

Solo un chofer autorizado puede iniciar una ruta asignada.

El inicio repetido no genera eventos ni transiciones duplicadas.

Las entregas de la ruta cambian a En traslado al iniciar el recorrido.

Entregas

Una entidad no puede confirmar entregas ajenas.

La confirmación registra fecha, hora y camión responsable.

La carga de evidencia queda relacionada con la entrega correcta.

Una entrega no recibida requiere revisión y no vuelve automáticamente a Pendiente.

El retorno al depósito queda registrado antes de habilitar una nueva planificación.

Seguimiento y eventos

Se rechazan posiciones inválidas o no autorizadas.

El mapa informa si los datos están desactualizados.

Los eventos se publican después de confirmar el cambio de negocio.

Los consumidores toleran eventos duplicados y disponen de reintentos.

Frontend y seguridad

Cada rol ve únicamente los recursos y acciones autorizados.

Todas las vistas manejan carga, errores, permisos y ausencia de datos.

Los cambios de estado visibles reflejan la respuesta real del backend.

Las operaciones críticas muestran confirmación y no dependen de cambios locales ficticios.

Reiniciar checklistCopiar checklist

## 15. Cómo debería quedar organizado el código

Una separación razonable para un proyecto Java con Spring Boot sería:

- `controller`: expone los endpoints REST.
- `application` o `service`: coordina los casos de uso.
- `domain`: contiene las entidades, reglas de estado y validaciones del dominio.
- `repository`: persiste camiones, rutas, entregas, incidencias y posiciones.
- `client`: integra el servicio de Donaciones y el planificador externo.
- `messaging`: publica y consume eventos RabbitMQ.
- `tracking`: recibe y valida posiciones.
- `dto`: contratos REST y de integración.
- `security`: políticas de autorización, o integración con la seguridad compartida.
- `exception`: errores de dominio y respuestas de API.

No hace falta adoptar estos nombres literalmente. Si el repositorio ya tiene una arquitectura definida, es preferible respetarla y corregir las responsabilidades sin duplicar capas.

## 16. Prompt completo para auditar el servicio con tu IA

Este prompt está preparado para utilizarlo con Antigravity u otra IA que pueda leer el repositorio. Está orientado a una auditoría profunda, sin modificar código todavía, y contempla el frontend y las integraciones.

# Auditoría integral del Servicio de Logística — DonaTrack

## 1. Contexto del proyecto

Estoy desarrollando DonaTrack, una aplicación web con arquitectura de microservicios, frontend y backend en Java/Spring Boot, desplegados mediante Docker.

El repositorio incluye servicios como `api-gateway`, `donaciones-service`, `notificaciones-service`, `frontend-service` y módulos compartidos como `common-dtos`. La arquitectura utiliza o contempla RabbitMQ para la comunicación asíncrona. La documentación también menciona un frontend SSR con Spring MVC, Thymeleaf, JavaScript, Leaflet.js y OpenStreetMap.

Debes inspeccionar el repositorio real antes de asumir que esta estructura o estas tecnologías siguen siendo exactamente las mismas.

## 2. Objetivo

Realizar una auditoría técnica y funcional exhaustiva del Servicio de Logística y de todas sus integraciones.

Verificar qué está correctamente implementado, qué está parcialmente implementado, qué funciona incorrectamente y qué falta.

Analizar tanto el backend como las vistas del frontend, los contratos REST, los eventos, la persistencia, la seguridad, las pruebas y los flujos completos entre microservicios.

No modifiques el código durante esta auditoría.

## 3. Requisitos funcionales que debes contrastar con el enunciado

### A. Gestión de camiones

Cada camión debe contemplar:

- Patente.
- Capacidad de volumen en metros cúbicos.
- Altura en metros.
- Capacidad de carga en kilogramos.
- Disponibilidad para la planificación, si corresponde al modelo definido.

Verifica las operaciones de creación, consulta, modificación y baja o desactivación; las validaciones; la unicidad de patente; la persistencia; las relaciones con rutas; y el comportamiento ante camiones asignados a recorridos activos.

### B. Planificación de rutas

El sistema debe integrarse con un componente externo que genere las rutas de reparto del día siguiente.

La solicitud debe incluir:

- Donaciones en estado `Asignación Realizada`.
- Datos necesarios para identificar las entregas.
- Direcciones de las entidades beneficiarias.
- Información de los camiones disponibles y sus capacidades.

El componente externo devuelve, para cada camión, una lista ordenada de destinos y las entregas que corresponden a cada uno.

Verifica:

- Cómo se obtienen las donaciones elegibles desde Donaciones.
- Cómo se determina la disponibilidad de los camiones.
- Cómo se construye el contrato enviado al planificador.
- Cómo se procesa su respuesta.
- Cómo se valida la capacidad y la coherencia del resultado.
- Cómo se evitan entregas duplicadas en rutas activas.
- Cómo se guardan las rutas y el orden de las paradas.
- Cómo se muestran las rutas generadas a los choferes.
- Qué sucede si la planificación falla, devuelve un resultado parcial o no incluye alguna donación.
- Cómo se ejecuta de forma asíncrona cuando el cálculo lo requiere.
- Cómo se controlan los reintentos y las ejecuciones duplicadas.

### C. Inicio de ruta

Antes de iniciar el recorrido, el chofer debe indicar en el sistema que comienza la ruta.

Al iniciar:

- Se valida la ruta, el camión, el chofer y el estado actual.
- Se registra la fecha y hora.
- La ruta pasa al estado correspondiente.
- Las entregas asociadas pasan a `En traslado`.
- Se publica el evento de negocio correspondiente.
- La interfaz refleja el estado persistido.

Verifica que no se pueda iniciar una ruta ajena, finalizada, cancelada o ya iniciada de forma incompatible.

### D. Confirmación de entrega

Cuando la entidad beneficiaria recibe la donación:

- Debe poder confirmar la recepción.
- La entrega pasa a `Entregada`.
- Queda registrado el camión responsable.
- Se conserva la fecha y hora de recepción.
- La entidad debe poder cargar fotografías de la donación recibida.
- Se genera el comprobante de entrega y el evento de negocio correspondiente.

Revisa cómo se gestionan las fotos, sus validaciones, almacenamiento y permisos. Comprueba si el proceso de confirmación y el de carga de evidencia respetan la regla de negocio realmente definida.

### E. Entrega no recibida o no satisfactoria

Si la entidad informa que no recibió la entrega en el día correspondiente, debe quedar registrada como `No recibida` y el caso debe poder revisarse administrativamente.

Si la donación regresa al depósito, la entrega vuelve a `Pendiente`.

En una entrega no satisfactoria:

- Debe registrarse el motivo y la trazabilidad.
- Debe notificarse a la entidad, al donante y a los administradores.
- Debe poder revisarse el caso.
- Si corresponde, la entrega puede volver a planificarse.

Comprueba que `No recibida`, `Pendiente`, `En traslado` y `Entregada` no se utilicen como estados equivalentes. Verifica qué sucede si los bienes no regresan al depósito o ya no son aptos para entregar.

### F. Monitoreo en tiempo real

El sistema debe recibir, validar y procesar la ubicación de los camiones para reflejar su posición y avance sobre la ruta en el dashboard administrativo.

El enunciado permite dos alternativas:

1. Dispositivo GPS instalado en el camión.
2. Aplicación móvil del chofer que reporta geolocalización mientras la ruta está activa.

Identifica cuál está implementada realmente. Si no hay una decisión documentada, señálalo como pendiente.

Verifica:

- Contrato de entrada de posiciones.
- Autenticación del dispositivo o cliente.
- Validación de coordenadas y fechas.
- Asociación entre camión y ruta activa.
- Persistencia de la última posición y, si corresponde, del historial.
- Actualización del mapa.
- Visualización del avance sobre la ruta.
- Detección de datos obsoletos o pérdida de señal.
- Protección de la ubicación frente a usuarios no autorizados.

### G. Eventos y notificaciones

Comprueba la integración con `notificaciones-service`.

Como mínimo, revisa:

- `ruta.iniciada`: entidades y donantes cuyas entregas están en la ruta; debe incluir un enlace al mapa interactivo.
- `entrega.confirmada`: entidad y donante; debe incluir fecha, hora y camión responsable.
- `entrega.fallida`: entidad, donante y administradores; debe incluir el estado o motivo correspondiente.

Verifica los nombres y esquemas reales de los eventos, el exchange, las routing keys, las colas y los consumidores.

No inventes contratos inexistentes. Identifica diferencias entre los DTO compartidos y los eventos que producen o consumen los servicios.

Analiza idempotencia, reintentos, Dead Letter Queue y Transactional Outbox si corresponden a la arquitectura existente.

## 4. Auditoría obligatoria del frontend

Inspecciona todas las plantillas, controladores MVC, formularios, servicios REST, JavaScript y componentes relacionados con Logística.

Verifica específicamente:

### Vista de camiones

- Tabla de flota.
- Formularios de alta y edición.
- Validaciones.
- Disponibilidad y desactivación.
- Manejo de errores de API.
- Acciones restringidas por rol.

### Vista de planificación

- Consulta de donaciones elegibles.
- Selección o consulta de camiones disponibles.
- Solicitud de planificación.
- Estado del proceso asíncrono.
- Presentación de rutas, destinos y paradas ordenadas.
- Manejo de fallos y resultados parciales.

### Vista del chofer

- Rutas asignadas.
- Camión y fecha.
- Lista ordenada de destinos.
- Inicio de ruta.
- Estado activo.
- Errores de autorización o de transición.

### Portal de la entidad beneficiaria

- Consulta de entregas propias.
- Confirmación de recepción.
- Carga de fotos.
- Registro de no recepción.
- Comprobante e historial.

### Dashboard de seguimiento

- Posición actual de camiones.
- Rutas asignadas.
- Avance del recorrido.
- Última actualización de posición.
- Datos desactualizados y errores de conexión.
- Acceso a detalles de entregas.

### Vista de incidencias

- Motivo de la incidencia.
- Estado de la entrega.
- Historial.
- Revisión administrativa.
- Resolución.
- Regreso al depósito y replanificación.

En cada vista verifica:

- Que las acciones se conecten con endpoints reales.
- Que no existan botones decorativos o flujos incompletos.
- Que el frontend no modifique estados de negocio solamente en memoria.
- Que la autorización se aplique también en el backend.
- Que haya estados de carga, error, vacío, éxito y falta de permisos.
- Que las respuestas de la API se representen correctamente.
- Que no se filtren datos de otras entidades, donantes o rutas.

No supongas que una vista existe solo porque aparece en documentación. Busca la plantilla, el controlador, la llamada a la API y el endpoint que la respalda.

## 5. Integración entre microservicios

Inspecciona:

- `api-gateway`.
- `logistica-service`.
- `donaciones-service`.
- `notificaciones-service`.
- `common-dtos`.
- `frontend-service`.
- RabbitMQ y Docker Compose.
- Servicio de autenticación o Keycloak, si corresponde.
- Integración con el planificador externo.
- Integración con GPS o aplicación móvil.

Dibuja el flujo real de las llamadas REST y los eventos.

Identifica:

- Dependencias síncronas innecesarias.
- Acoplamiento entre bases de datos.
- DTO incompatibles.
- Eventos que no se publican o no se consumen.
- Estados de negocio duplicados entre servicios.
- Problemas de consistencia.
- Ausencia de identificadores de correlación.
- Errores que puedan perder entregas o generar notificaciones duplicadas.

Define con claridad qué servicio es responsable de cada dato y transición de estado.

## 6. API, persistencia y reglas de dominio

Revisa:

- Endpoints y métodos HTTP.
- DTO de entrada y salida.
- Validaciones.
- Entidades y repositorios.
- Relaciones y restricciones de base de datos.
- Historial de estados.
- Manejo de transacciones.
- Errores y códigos HTTP.
- Paginación y filtros.
- Reglas de idempotencia.
- Consistencia entre rutas, paradas, camiones y entregas.

El enunciado requiere gestión de flota y operaciones CRUD sobre rutas y entregas. Comprueba la cobertura real de esas operaciones.

## 7. Seguridad

Verifica:

- Autenticación.
- Roles y permisos.
- Protección de endpoints.
- Restricción de acceso a entregas y ubicaciones por usuario.
- Autorización de inicio de ruta.
- Autorización de confirmación de recepción.
- Validación de archivos.
- Protección de la telemetría.
- Exposición de información personal.
- Gestión de secretos.
- Auditoría de operaciones sensibles.

No consideres suficiente ocultar botones en el frontend.

## 8. Pruebas

Inspecciona los tests existentes y ejecuta las pruebas que sea seguro ejecutar en el entorno disponible.

Busca cobertura para:

- CRUD de camiones.
- Validaciones de capacidad.
- Planificación.
- Respuesta inválida del planificador.
- Inicio de ruta.
- Transiciones de estado.
- Confirmación de recepción.
- Entrega no recibida.
- Regreso al depósito.
- Carga de evidencia.
- Seguimiento GPS.
- Autorización por rol.
- Publicación y consumo de eventos.
- Reintentos y duplicados.
- Integración con el frontend.

No ejecutes operaciones contra producción ni envíes notificaciones reales. No alteres datos del usuario.

## 9. Entregables de la auditoría

Crea dos documentos Markdown independientes, sin modificar ningún archivo de código.

### Documento 1: `AUDITORIA_SERVICIO_LOGISTICA.md`

Debe incluir:

1. Resumen ejecutivo.
2. Alcance inspeccionado.
3. Arquitectura real.
4. Componentes y responsabilidades.
5. Diagrama Mermaid de servicios e integraciones.
6. Diagrama de secuencia de planificación.
7. Diagrama de secuencia de inicio de ruta.
8. Diagrama de secuencia de entrega exitosa.
9. Diagrama de secuencia de entrega no recibida.
10. Diagrama de secuencia de actualización GPS.
11. Inventario de endpoints.
12. Inventario de eventos y contratos.
13. Modelo de datos real.
14. Inventario de vistas frontend y sus endpoints.
15. Comparación requisito por requisito.
16. Pruebas ejecutadas y resultados.
17. Hallazgos con evidencia concreta.
18. Riesgos funcionales, arquitectónicos y de seguridad.
19. Recomendaciones de diseño.
20. Criterios de aceptación para considerar el servicio completo.

### Documento 2: `PENDIENTES_SERVICIO_LOGISTICA.md`

Lista todos los problemas y faltantes que deben corregirse.

Para cada punto incluye:

- Identificador.
- Prioridad: crítica, alta, media o baja.
- Tipo: defecto, funcionalidad faltante, riesgo o mejora.
- Requisito relacionado.
- Evidencia del problema.
- Archivo y ubicación aproximada.
- Impacto funcional o técnico.
- Solución propuesta.
- Servicios y vistas afectados.
- Dependencias con otras tareas.
- Pruebas necesarias.
- Criterio de aceptación verificable.

Ordena los pendientes por prioridad y dependencias, para que puedan corregirse progresivamente.

## 10. Reglas de la auditoría

- No modifiques código ni configuración.
- No inventes funcionalidades ni endpoints.
- No declares que algo funciona solo porque existe una clase o plantilla.
- Sigue el flujo completo desde la vista hasta el backend y, cuando corresponda, hasta el evento y su consumidor.
- Distingue claramente entre requisitos del enunciado, comportamiento comprobado, inferencias y recomendaciones.
- Si algo no se puede verificar, indícalo como no verificado.
- No dupliques responsabilidades que pertenecen a Donaciones, Incentivos o Notificaciones.
- No propongas reemplazar la arquitectura completa cuando un cambio localizado sea suficiente.
- No elimines ni sobrescribas documentación existente.
- Ejecuta pruebas únicamente cuando sea seguro hacerlo y documenta los comandos y resultados.
- Al finalizar, presenta un resumen de los dos documentos creados, los principales hallazgos y el orden recomendado para corregirlos.

El resultado debe ser una auditoría profunda, concreta y trazable al código real del repositorio.

## 17. Orden recomendado para corregir los problemas

Si la auditoría detecta varios defectos, te recomiendo abordarlos en este orden:

1. Modelo y estados: definir las reglas de rutas, entregas, incidencias y retorno al depósito.
2. Contratos entre servicios: estabilizar los DTO, las consultas a Donaciones y los eventos.
3. Persistencia y planificación: asegurar que rutas, paradas y entregas queden correctamente vinculadas y no se dupliquen.
4. Flujos operativos: completar inicio de ruta, recepción, evidencia y no recepción.
5. Seguimiento GPS: validar el contrato de telemetría y actualizar el dashboard.
6. Frontend: conectar cada vista con los endpoints reales y aplicar permisos.
7. Notificaciones y resiliencia: eventos, idempotencia, reintentos y manejo de fallos.
8. Pruebas e integración: probar los flujos completos y documentar los criterios de aceptación.

La prioridad no es tener muchas pantallas o muchos endpoints, sino garantizar que una donación no se entregue dos veces por un error de planificación, que sus estados reflejen lo que ocurrió físicamente y que todas las partes involucradas puedan consultar la información correcta con los permisos adecuados.
