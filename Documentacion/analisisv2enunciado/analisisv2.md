El proyecto **DonaTrack (TP Anual 2026 - Diseño de Sistemas de Información UTN.BA)** plantea una arquitectura distribuida basada en servicios (evolucionando hacia microservicios en la Entrega 6) con persistencia políglota (relacional mediante ORM y documental mediante ODM para Logística), comunicación sincrónica (REST / gRPC / GraphQL) y asincrónica (colas de mensajes, webhooks/callbacks y procesos batch/cron).

---

## 1. Análisis Profundo de la Arquitectura y Reglas de Negocio

### A. Arquitectura de Servicios (Bounded Contexts)

El sistema se divide en 6 componentes principales:

1. **Servicio de Donaciones (Gestión de Donantes, Donaciones y Entidades Beneficiarias):** Núcleo transaccional. Maneja el alta/baja/modificación (ABM) de actores, la recepción en depósito, la segmentación automática de bienes, la trazabilidad de estados de las donaciones, el registro de necesidades materiales y los algoritmos de *matchmaking*. Persistencia: Relacional (ORM).


2. **Servicio de Logística (Gestión de Camiones, Rutas y Entregas):** Administra la flota, se integra con un planificador externo de rutas mediante lotes y *callbacks*, gestiona el estado de los repartos y recibe telemetría GPS en tiempo real. Persistencia: No Relacional / Documental (ODM).


3. **Servicio de Incentivos (Analítica, Misiones y Recompensas):** Procesa la actividad de los donantes para calcular métricas, gestiona la ludificación (misiones secuenciales, rachas, insignias, categorías), elabora el ranking mensual y dispara flujos de difusión en redes sociales (*low-code* como n8n). Persistencia: Relacional (ORM).


4. **Servicio de Notificaciones:** Componente desacoplado consumido vía **cola de mensajes** asincrónica para despachar avisos por Email, SMS y WhatsApp. Persistencia: Relacional (ORM).


5. **Servicio de Autenticación:** Gestiona credenciales, sesiones, emisión/validación de tokens de autorización y opcionalmente SSO / Social-Login.


6. **Cliente Liviano (Frontend SSR MVC):** Aplicación web independiente que renderiza vistas desde el servidor (*Server-Side Rendering*) mediante un motor de plantillas consumiendo las APIs REST/GraphQL de los servicios.



### B. Reglas de Negocio Críticas ("Trampas" de Diseño a tener en cuenta)

* **Regla de Segmentación Automática (Unidad Mínima):** El donante entrega un "lote" o conjunto de bienes en una única carga administrativa, pero el sistema **no** guarda eso como una única donación asignable. Automáticamente lo divide en **múltiples donaciones independientes** donde cada una tiene una única **Subcategoría**. Si son bienes perecederos con distinta fecha de vencimiento (aunque sean de la misma subcategoría), se separan en donaciones distintas. Si son muebles o vestimenta, se distingue si son nuevos o usados.


* **Polimorfismo o Composición en Donantes y Necesidades:**
* *Donantes:* Pueden ser **Personas Humanas** (DNI, edad, género, etc.) o **Personas Jurídicas** (CUIT, razón social, tipo de entidad, rubro y lista de representantes). Nota de diseño: el Requerimiento 7 menciona incentivos para persona humana, mientras que en la Entrega 2 (pág. 14) aclara que tanto la persona humana como el representante de una jurídica pueden ver analíticas en su perfil. Conviene modelar el sujeto de analítica/incentivos de forma flexible (asociado al perfil donante).


* *Necesidades:* Pueden ser **Extraordinarias** (emergencias puntuales, se satisfacen por acumulación parcial hasta llegar al total) o **Recurrentes** (se satisfacen periódicamente, ej. 100 paquetes por semana, reiniciando el objetivo en cada período).




* **Extensibilidad de Matchmaking (Patrón Strategy):** Se piden 2 algoritmos iniciales (*Compatibilidad Semántica* con distancia de Levenshtein + cobertura de volumen + urgencia, y *Prioridad a Sub-atendidos*). Ambos normalizan puntajes en $[0, 1]$ y evalúan solo donaciones en estado `En depósito` contra necesidades de la **misma subcategoría**. Si hay intersección entre el Top 10 de ambos, se filtra esa intersección; si es vacía, se muestran ambos rankings por separado al administrador.


* **Máquina de Estados Dual (Donación vs. Entrega):**
* *Estados de la Donación (Servicio de Donaciones):* `En depósito` $\rightarrow$ `Asignación realizada` $\rightarrow$ `Lista para entregar` $\rightarrow$ `En traslado` $\rightarrow$ `Entregada`. Ramas de excepción: `Entrega fallida` (vuelve a `En depósito` con justificación) y `Vencida` (baja lógica en depósito por el administrador).


* *Estados de la Entrega (Servicio de Logística):* `Pendiente` $\rightarrow$ `En traslado` $\rightarrow$ `Entregada` o `No recibida` (revisada por el administrador; si el bien retorna al depósito, vuelve a `Pendiente`).





---

## 2. Catálogo Completo de Actores del Sistema

### Actores Humanos

1. **Visitante (Usuario Anónimo):** Navega la landing page pública, visualiza el mapa interactivo de donaciones entregadas, fotos e información legal.


2. **Persona Donante (Humana o Representante de Jurídica):** Consulta sus donaciones, filtra por estados/categorías, explora entidades beneficiarias, sigue camiones en el mapa en tiempo real y consulta su panel de analítica, misiones e insignias.


3. **Entidad Beneficiaria (Representante):** Publica y gestiona necesidades materiales (recurrentes y extraordinarias), consulta donaciones asignadas, sigue en el mapa los camiones en camino y confirma o rechaza la recepción de entregas subiendo fotos.


4. **Persona Administradora:** Registra donantes en el depósito, importa donantes por CSV (+10.000 / 20.000 filas), carga donaciones recibidas, marca donaciones como vencidas, configura pesos de algoritmos, selecciona la entidad beneficiaria final para una donación, administra la flota de camiones, audita entregas fallidas, monitorea el dashboard de logística en tiempo real y consulta rankings históricos.


5. **Chofer de Camión:** Consulta su hoja de ruta del día en su aplicación e informa el inicio del recorrido (y opcionalmente emite geolocalización desde su app móvil).



### Actores de Sistema / Externos y Temporales

6. **Planificador de Rutas Externo:** Sistema externo que recibe lotes de hasta 100 donaciones asignadas y camiones disponibles, y devuelve las hojas de ruta óptimas mediante un *callback* asincrónico.


7. **Dispositivo GPS / App Móvil de Telemetría:** Emite periódicamente ubicación y velocidad de cada camión en ruta.


8. **Motor de Automatización / Red Social (ej. n8n):** Recibe el evento de nueva insignia, genera la imagen y publica el hito en redes sociales mencionando al donante.


9. **Proveedores de Mensajería (Email / SMS / WhatsApp):** Gateways externos consumidos por el Servicio de Notificaciones.


10. **Reloj del Sistema (Scheduler / Cron):** Dispara procesos batch en horarios de baja carga o cierres de período (matchmaking nocturno, planificación de rutas, control de inactividad de 20 días, control de pérdida de rachas y cierre de ranking mensual).



---

## 3. Catálogo Exhaustivo de Casos de Uso (Agrupados por Módulo/Servicio)

| ID | Caso de Uso | Actor Primario | Servicio Principal | Entregas |
| --- | --- | --- | --- | --- |
| **ACCESO Y PÚBLICO** |  |  |  |  |
| **CU-01** | Visualizar landing page y donaciones destacadas del último mes | Visitante | Frontend SSR / Donaciones | E1, E4, E5

 |
| **CU-02** | Explorar mapa interactivo y fotos de donaciones entregadas | Visitante | Frontend SSR / Donaciones | E1, E4, E5

 |
| **CU-03** | Registrarse como Persona Donante (Humana o Jurídica) | Visitante | Autenticación / Donaciones | E1, E5

 |
| **CU-04** | Registrarse como Entidad Beneficiaria | Visitante | Autenticación / Donaciones | E1, E5

 |
| **CU-05** | Iniciar sesión / Cerrar sesión (Login tradicional o SSO Social-Login) | Donante, Beneficiaria, Admin, Chofer | Autenticación | E1, E5

 |
| **SERVICIO DE DONACIONES** |  |  |  |  |
| **CU-06** | Gestionar Personas Donantes (ABM y configuración de medio de contacto preferido) | Persona Administradora / Donante | Servicio de Donaciones | E1, E2

 |
| **CU-07** | Importar masivamente Personas Donantes vía CSV (+10.000 filas) | Persona Administradora | Servicio de Donaciones | E1

 |
| **CU-08** | Registrar ingreso de donación en depósito y segmentar bienes por subcategoría/vencimiento | Persona Administradora | Servicio de Donaciones | E1, E2

 |
| **CU-09** | Consultar y filtrar donaciones propias (por estado, categoría y subcategoría) | Persona Donante | Servicio de Donaciones | E1, E4

 |
| **CU-10** | Actualizar estado de donación en depósito a "Vencida" | Persona Administradora | Servicio de Donaciones | E1, E2

 |
| **CU-11** | Gestionar Entidades Beneficiarias (ABM) y explorar catálogo de entidades | Admin / Persona Donante | Servicio de Donaciones | E1, E2

 |
| **CU-12** | Gestionar Necesidades Materiales (ABM de Recurrentes y Extraordinarias) | Entidad Beneficiaria | Servicio de Donaciones | E1, E2

 |
| **CU-13** | Configurar parámetros y pesos ($a_1, a_2, a_3, \alpha$) de algoritmos de matchmaking | Persona Administradora | Servicio de Donaciones | E2

 |
| **CU-14** | Ejecutar algoritmos de matchmaking (Batch programado o a demanda) | Scheduler (Cron) / Admin | Servicio de Donaciones | E2

 |
| **CU-15** | Seleccionar Entidad Beneficiaria final a partir del ranking de matchmaking | Persona Administradora | Servicio de Donaciones | E1, E2

 |
| **CU-16** | Consultar estado de donaciones asignadas a la entidad | Entidad Beneficiaria | Servicio de Donaciones | E1, E2

 |
| **CU-17** | Detectar inactividad de donantes (> 20 días) y disparar recordatorio | Scheduler (Cron) | Servicio de Donaciones | E2

 |
| **SERVICIO DE INCENTIVOS** |  |  |  |  |
| **CU-18** | Procesar actividad de donación, actualizar progreso de misiones, otorgar insignias y ascender categoría | Sistema (Evento desde Donaciones/Logística) | Servicio de Incentivos | E2, E3

 |
| **CU-19** | Evaluar pérdida de progreso en misiones condicionales (ej. fin de mes sin donar en misión "Racha") | Scheduler (Cron) | Servicio de Incentivos | E2

 |
| **CU-20** | Consultar dashboard de analítica personal, progreso de misiones e insignias | Persona Donante | Servicio de Incentivos | E1, E2

 |
| **CU-21** | Configurar visibilidad pública de insignias en el perfil | Persona Donante | Servicio de Incentivos | E2

 |
| **CU-22** | Difundir automáticamente insignia obtenida en redes sociales (vía n8n) | Servicio de Incentivos | Servicio de Incentivos | E2

 |
| **CU-23** | Generar y persistir el ranking mensual de donantes más activos | Scheduler (Cron) | Servicio de Incentivos | E2

 |
| **CU-24** | Visualizar ranking mensual actual e historial de rankings | Admin / Persona Donante | Servicio de Incentivos | E1, E2

 |
| **SERVICIO DE LOGÍSTICA** |  |  |  |  |
| **CU-25** | Gestionar flota de camiones disponibles (ABM: patente, volumen $m^3$, altura, carga kg) | Persona Administradora | Servicio de Logística | E1, E3

 |
| **CU-26** | Solicitar planificación de rutas en lotes (máx. 100 donaciones por lote) al proveedor externo | Scheduler (Cron) | Servicio de Logística | E3

 |
| **CU-27** | Recibir resultado de planificación de rutas (Callback), registrar rutas y encolar no asignadas | Planificador Externo | Servicio de Logística | E3

 |
| **CU-28** | Informar inicio de ruta de reparto | Chofer de Camión | Servicio de Logística | E3

 |
| **CU-29** | Recibir, validar y procesar telemetría de ubicación de camiones en tiempo real | Dispositivo GPS / App Móvil | Servicio de Logística | E3

 |
| **CU-30** | Monitorear ubicación de camiones y entregas activas en mapa en tiempo real | Donante, Beneficiaria, Admin | Servicio de Logística | E1, E3

 |
| **CU-31** | Confirmar recepción exitosa de donación y cargar fotos comprobatorias | Entidad Beneficiaria | Servicio de Logística / Donaciones | E1, E2, E3

 |
| **CU-32** | Reportar entrega fallida / no recibida con justificación | Entidad Beneficiaria / Chofer | Servicio de Logística / Donaciones | E2, E3

 |
| **CU-33** | Revisar entrega no recibida, registrar reingreso al depósito y habilitar replanificación | Persona Administradora | Servicio de Logística / Donaciones | E2, E3

 |
| **SERVICIO DE NOTIFICACIONES** |  |  |  |  |
| **CU-34** | Procesar evento de la cola de mensajes y enviar notificación multicanal (Email, SMS, WhatsApp) | Servicios de Dominio (vía Cola) | Servicio de Notificaciones | E1, E2, E3

 |

---

## 4. Todos los Flujos del Proyecto (End-to-End y Asincrónicos)

A continuación se detallan todos los flujos operativos y de integración que ocurren dentro del sistema, paso a paso, incluyendo sus caminos alternativos y de excepción.

### Flujo 1: Alta de Donantes (Individual en Depósito vs. Migración Masiva CSV)

* **1A. Alta Presencial en Depósito:**
1. El donante (o representante de persona jurídica) se acerca físicamente al depósito con bienes para donar.


2. El Administrador consulta si posee usuario en DonaTrack.


3. *Camino alternativo (No posee usuario):* El Administrador solicita los datos obligatorios (Humana: nombre, apellido, edad, documento, género, dirección, email obligatorio, tel/WhatsApp opcional y medio preferido; Jurídica: razón social, CUIT, tipo, rubro, contacto y representantes).


4. El Servicio de Donaciones crea el perfil, coordina con el Servicio de Autenticación la creación de credenciales y publica un evento hacia el Servicio de Notificaciones para enviar el correo de bienvenida con el acceso inicial.




* **1B. Migración Masiva por CSV (+10.000 / 20.000 filas):**
1. El Administrador sube el archivo `.csv` (`Tipo Persona`, `TipoDoc`, `Documento`, `Nombre/Razón Social`, `Email`, `Teléfono`).


2. El sistema procesa el archivo de forma eficiente (lectura en *streaming* o *batch processing* para no saturar la memoria RAM).
3. Por cada fila, verifica si el `Email` ya existe en la base de datos:


* *Si existe:* Actualiza los datos identificatorios y de contacto del donante.


* *Si no existe:* Da de alta al donante, genera su usuario y encola el envío de credenciales de acceso por correo electrónico.







### Flujo 2: Recepción de Bienes en Depósito y Segmentación Automática

1. Con el donante identificado, el Administrador completa en el sistema el formulario único de carga ingresando una descripción general y el listado de bienes entregados.


2. Por cada ítem del listado se ingresa: categoría, subcategoría, descripción, unidad de medida, cantidad, foto (opcional), estado (`Nuevo`/`Usado` si aplica a la categoría como mobiliario o vestimenta) y fecha de vencimiento (si es perecedero).


3. **Motor de Segmentación Interna:** El Servicio de Donaciones toma la carga única y la particiona automáticamente en $N$ objetos `Donacion` independientes bajo las siguientes reglas:


* Agrupación estricta por `Subcategoría` (unidad mínima de asignación).


* Si dentro de una misma subcategoría de perecederos hay distintas fechas de vencimiento, se separan en donaciones distintas.


* Si dentro de una misma subcategoría de no perecederos hay artículos nuevos y usados, se separan según su estado.




4. Cada `Donacion` resultante se persiste con estado inicial **`En depósito`**, registrando la auditoría de cambio de estado (fecha, hora, usuario).


5. **Integración con Incentivos:** El Servicio de Donaciones notifica la nueva actividad al Servicio de Incentivos (sincróncamente vía REST/gRPC o asincrónicamente) para recalcular métricas y progreso de misiones basadas en recepción de donaciones (ej. *Hábil Donador*, *Completitud*, *Racha*).


6. *Excepción en Depósito (Vencimiento):* Si una donación perecedera supera su fecha de vencimiento estando en el depósito, el Administrador actualiza manualmente su estado a **`Vencida`**, sacándola del circuito de asignación.



### Flujo 3: Matchmaking (Evaluación) y Asignación a Entidad Beneficiaria

1. **Disparo:** En horario de baja carga (ejecución batch programada al día siguiente del ingreso) o mediante ejecución manual a demanda del Administrador, se inicia el motor de *matchmaking*.


2. **Filtrado Duro:** Por cada donación en estado `En depósito`, el sistema busca todas las necesidades activas (Extraordinarias no satisfechas o Recurrentes con cupo pendiente en el período actual) que soliciten **exactamente la misma Subcategoría**.


3. **Cálculo de Puntajes (Polimorfismo de Algoritmos):**
* *Algoritmo 1 (Compatibilidad Semántica):* Calcula similitud textual por distancia de Levenshtein ($\chi_1$), ratio de cobertura de volumen penalizando excesos o faltantes ($\chi_2$) y urgencia por tipo de necesidad ($\chi_3 = 1$ si es Extraordinaria, $0.5$ si es Recurrente). Evalúa $S_{sem} = a_1 \cdot \chi_1^2 + a_2 \cdot \chi_2 + a_3 \cdot \chi_3$. Genera el **Ranking Semántico (Top 10)**.


* *Algoritmo 2 (Prioridad a Sub-atendidos):* Consulta la cantidad de donaciones recibidas por cada entidad en el último trimestre y evalúa $S_{sub} = \frac{1}{1 + \alpha \cdot \text{CantDonacionesTrimestre}}$. Genera el **Ranking de Sub-atendidos (Top 10)**.




4. **Consolidación de Propuesta:**
* El sistema calcula la intersección entre ambos Top 10.


* *Si hay coincidencias:* Filtra y presenta únicamente las necesidades que figuran en ambos rankings.


* *Si no hay coincidencias:* Presenta en la interfaz administrativa ambos rankings por separado.




5. **Confirmación Humana:** La Persona Administradora revisa los candidatos y selecciona la Entidad Beneficiaria (y necesidad) final.


6. **Transición y Notificación:**
* La donación cambia su estado a **`Asignación realizada`** (guardando traza de auditoría).


* Se publican dos eventos en la cola de mensajes hacia el Servicio de Notificaciones: uno para avisar a la **Persona Donante** que su donación fue asignada, y otro para avisar a la **Entidad Beneficiaria** adjudicada.





### Flujo 4: Planificación Nocturna de Rutas por Lotes y Callback

1. **Disparo Batch:** En horario de baja carga, el Servicio de Logística inicia la planificación de entregas para la jornada siguiente.


2. **Recolección y Loteo:** Obtiene todas las donaciones en estado `Asignación realizada` (consultando al Servicio de Donaciones) y la flota de camiones disponibles. Como el proveedor externo soporta un máximo de **100 donaciones por ejecución**, Logística particiona el conjunto en lotes de hasta 100 elementos.


3. **Solicitud Externa:** Por cada lote, invoca al Componente Externo de Planificación enviando las donaciones (con dirección de destino, peso/volumen) y los camiones disponibles, adjuntando la **URL de Callback** de nuestro sistema.


4. **Procesamiento de Callback:** Cuando el proveedor externo termina de calcular, hace un `POST` a la URL de Callback de Logística:


* Para las donaciones incluidas en una hoja de ruta: Logística persiste el documento de `Ruta` (con la lista ordenada de paradas/entregas por camión) con las entregas en estado `Pendiente`, e informa al Servicio de Donaciones para que cambie el estado de esas donaciones a **`Lista para entregar`**.


* *Camino alternativo (Donaciones sin asignar):* Si el proveedor devuelve donaciones en el campo de "no asignadas" (por falta de capacidad de flota o tiempo), el sistema las mantiene en cola (`Asignación realizada`) para reintentar su planificación en otro lote o en la jornada siguiente.





### Flujo 5: Ejecución de Ruta, Telemetría en Tiempo Real y Recepción de Entrega

1. **Inicio de Recorrido:** El Chofer ingresa a su vista/aplicación, visualiza su ruta asignada del día y presiona "Iniciar Ruta".


* El Servicio de Logística pasa las entregas de esa ruta a estado **`En traslado`** y coordina con el Servicio de Donaciones para que las donaciones asociadas pasen también a **`En traslado`**.


* Logística publica eventos en la cola de notificaciones para avisar a todas las **Personas Donantes** y **Entidades Beneficiarias** involucradas en esa ruta, incluyendo el enlace directo al mapa interactivo de seguimiento en vivo.




2. **Monitoreo en Tiempo Real (Streaming de Ubicación):**
* Mientras la ruta está activa, el Dispositivo GPS del camión (o la App Móvil del conductor) envía periódicamente las coordenadas (latitud, longitud, velocidad, timestamp) al Servicio de Logística.


* Logística valida, procesa y actualiza la posición del camión.


* El Administrador (en su dashboard), el Donante y la Entidad Beneficiaria visualizan el marcador del camión moviéndose en tiempo real y la hora de última actualización.




3. **Desenlace en Destino (3 Caminos Posibles):**
* **Camino 3A — Entrega Exitosa:**
1. El camión llega a la sede y entrega los bienes.


2. La Entidad Beneficiaria ingresa a la plataforma, confirma la recepción y **carga fotos de la donación recibida**.


3. La entrega y la donación pasan al estado **`Entregada`** (registrando fecha, hora y camión responsable).


4. Se actualiza el grado de satisfacción de la `Necesidad Material` (sumando la cantidad entregada al acumulado de la emergencia o del período recurrente).


5. Se envía notificación con el comprobante de entrega (fecha, hora, camión) tanto al Donante como a la Entidad Beneficiaria.


6. Se notifica al Servicio de Incentivos para destrabar o avanzar misiones sujetas a recepción efectiva (ej. misión *Donaciones Exitosas*).


7. Las fotos y la ubicación pasan a estar disponibles en el mapa público de la *landing page*.




* **Camino 3B — Entrega Fallida / No Recibida (Replanificable):**
1. No se puede concretar la entrega en el día estipulado (ej. nadie respondió al timbre o la entidad informa que no recibió el camión).


2. Se registra una justificación obligatoria. En Logística la entrega pasa a **`No recibida`** y en Donaciones a **`Entrega fallida`**.


3. Se envía notificación de entrega no satisfactoria al Donante, a la Entidad Beneficiaria y a las Personas Administradoras.


4. El Administrador revisa el caso: cuando el camión regresa con la mercadería física al depósito, la donación vuelve al estado **`En depósito`** (o lista para replanificar ruta manteniendo su asignación según criterio de negocio) y la entrega vuelve a **`Pendiente`** para ser incluida en una nueva planificación de ruta.




* **Camino 3C — Bienes Vencidos antes de Entregar:**
1. Si la entrega falla porque los bienes perecederos vencieron antes de concretarse, se notifica a las tres partes (Donante, Beneficiaria, Admin) y el Administrador pasa la donación al estado terminal **`Vencida`**.







### Flujo 6: Ludificación, Difusión en Redes Sociales y Ranking Mensual

1. **Evaluación de Misiones y Categorías:**
* Cada vez que el Servicio de Incentivos recibe un evento de donación ingresada (Flujo 2) o donación entregada (Flujo 5.3A), actualiza los indicadores del donante y evalúa la misión activa actual de su categoría (`Colaborador` $\rightarrow$ `Sostenedor` $\rightarrow$ `Transformador`).


* Como las misiones son **secuenciales**, si la misión actual alcanza el 100% de su objetivo:
1. Se marca la misión como completada y se desbloquea la siguiente en la secuencia.


2. Se le otorga la **Insignia** correspondiente.


3. Se publica en la cola de mensajes una notificación de "Misión cumplida" para el donante.


4. **Disparo a Redes Sociales (Webhook n8n):** El Servicio de Incentivos invoca automáticamente al flujo de automatización externo enviando el texto del logro y datos del usuario; el flujo genera una imagen de la insignia y la publica en la red social mencionando al donante.


5. *Ascenso de Categoría:* Si la misión completada era la última de su categoría actual, el donante asciende a la categoría superior y se dispara una segunda notificación felicitándolo por el nuevo rango.






2. **Control de Penalización de Rachas:**
* Un proceso programado verifica periódicamente las misiones condicionales como *"Racha"* (donar durante $X$ meses consecutivos). Si finaliza un mes calendario sin que el donante haya registrado ninguna donación, su progreso acumulado en esa misión se reinicia a cero.




3. **Cierre de Ranking Mensual:**
* Al cierre de cada mes, un job programado en el Servicio de Incentivos contabiliza la cantidad de misiones cumplidas por cada donante en ese mes (independientemente de su categoría), ordena los resultados, persiste el **Ranking Mensual** histórico y actualiza la vista destacada con el Top 3 de donantes.





### Flujo 7: Retención de Donantes Inactivos y Despacho de Notificaciones (Transactional Outbox)

1. **Detección de Inactividad (> 20 días):**
* Diariamente, un job en el Servicio de Donaciones busca a las personas donantes cuya última interacción o donación supere los 20 días.


* Por cada donante detectado, genera un evento de incentivo/recordatorio.




2. **Despacho Asincrónico Confiable (Cola + Outbox):**
* En cualquier servicio de dominio (Donaciones, Logística, Incentivos), al ocurrir un evento notificable (bienvenida, inactividad > 20 días, donación asignada, misión cumplida, ascenso de categoría, inicio de ruta, entrega exitosa, entrega fallida):


* *(Con Bonus Entrega 6 - Transactional Outbox):* El servicio guarda el evento en una tabla local `outbox_events` dentro de la misma transacción ACID de la operación de negocio, y un *relay* lo publica en el broker de mensajería (ej. RabbitMQ / Kafka).


* El **Servicio de Notificaciones** consume el mensaje de la cola, consulta el medio de contacto preferido del destinatario (Email, SMS o WhatsApp), invoca al adaptador (*Strategy/Adapter*) del proveedor externo correspondiente y registra el envío como completado.
