# DonaTrack — Casos de uso y flujos completos del sistema

Tomando como base el TP Anual 2026 de Diseño de Sistemas de Información de la UTN, te voy a organizar todos los casos de uso que se desprenden del proyecto, sus actores y los flujos principales, alternativos y de excepción.

El sistema DonaTrack abarca la gestión de donantes, donaciones, entidades beneficiarias, necesidades materiales, asignaciones automáticas, logística, seguimiento de camiones, incentivos, notificaciones y administración. También contempla autenticación, importación masiva y operaciones de integración entre servicios.

Cátedra Diseño de Sistemas de Información - TP Anual 2026 (L, M, M) .pdf

Una aclaración importante: el documento pide un diagrama general de casos de uso y define funcionalidades a lo largo de seis entregas. Para no dejar funcionalidades afuera, voy a separar los casos de uso por módulo y distinguir los que son acciones de los usuarios de los procesos automáticos del sistema. Algunos procesos automáticos se representan mejor como casos de uso internos o subcasos incluidos.

## 1. Actores del sistema

1. Persona donante humana

Registra y consulta sus donaciones, sigue entregas, consulta sus métricas, misiones, insignias y categoría.

2. Persona donante jurídica / representante

Opera en nombre de una organización donante y consulta sus aportes, asignaciones y entregas. El acceso a incentivos debe contemplarse según las reglas del sistema.

3. Entidad beneficiaria

Registra necesidades, consulta donaciones asignadas, sigue camiones y confirma o rechaza la recepción de entregas.

4. Persona administradora

Gestiona donantes, donaciones, vencimientos, asignaciones, flota, importaciones, incidencias y rankings.

5. Chofer

Consulta su ruta y comunica el inicio del recorrido. El TP exige informar el comienzo de la ruta.

6. Visitante público

Accede a la página pública, consulta información de DonaTrack, observa donaciones entregadas y ve sus fotos sin iniciar sesión.

7. Planificador externo de rutas

Recibe lotes de donaciones y camiones, calcula recorridos y devuelve las rutas generadas mediante un callback.

8. Fuente de geolocalización

GPS del camión o aplicación móvil del chofer. Es una integración externa que informa ubicación y, según la alternativa elegida, velocidad.

9. Proveedores externos

Servicios de correo electrónico, SMS, WhatsApp y red social; intervienen en notificaciones y difusión de insignias.

10. Planificador temporal / scheduler

Dispara procesos programados: asignación de donaciones, generación de rutas, detección de inactividad y ranking mensual.

Actores secundarios adicionales: el servicio de autenticación, los servicios internos de Donaciones, Logística, Incentivos y Notificaciones, y la herramienta de automatización de flujos. En UML, los servicios internos no siempre necesitan dibujarse como actores; depende de si estás representando el sistema completo o un servicio particular.

## 2. Inventario general de casos de uso

Este es el inventario maestro, agrupado por módulos. Después desarrollo los flujos uno por uno.

A. Acceso y consulta pública

CU-01 a CU-06

CU-01 Mostrar landing page · CU-02 Consultar información legal y privacidad · CU-03 Visualizar donaciones entregadas · CU-04 Consultar detalle de una donación en el mapa · CU-05 Registrarse · CU-06 Iniciar sesión.

B. Gestión de personas donantes

CU-07 a CU-12

CU-07 Registrar donante humano · CU-08 Registrar donante jurídico · CU-09 Modificar datos de donante · CU-10 Consultar donante · CU-11 Eliminar o dar de baja donante · CU-12 Importar donantes mediante CSV.

C. Gestión de donaciones

CU-13 a CU-21

CU-13 Registrar una donación recibida · CU-14 Registrar los bienes donados · CU-15 Segmentar automáticamente la donación · CU-16 Consultar donaciones · CU-17 Filtrar donaciones · CU-18 Modificar donación · CU-19 Cambiar estado de donación · CU-20 Marcar donación vencida · CU-21 Consultar historial de estados.

D. Entidades beneficiarias y necesidades

CU-22 a CU-29

CU-22 Registrar entidad beneficiaria · CU-23 Modificar entidad · CU-24 Consultar entidades · CU-25 Dar de baja entidad · CU-26 Registrar necesidad · CU-27 Modificar necesidad · CU-28 Consultar necesidades · CU-29 Cancelar o cerrar necesidad.

E. Asignación automática de donaciones

CU-30 a CU-35

CU-30 Ejecutar algoritmos de asignación · CU-31 Calcular compatibilidad semántica · CU-32 Calcular prioridad a entidades subatendidas · CU-33 Consultar ranking de necesidades · CU-34 Confirmar entidad beneficiaria final · CU-35 Registrar asignación y notificar.

F. Logística y seguimiento

CU-36 a CU-46

CU-36 Administrar camiones · CU-37 Planificar rutas · CU-38 Recibir resultado del planificador · CU-39 Consultar ruta asignada · CU-40 Iniciar recorrido · CU-41 Actualizar ubicación del camión · CU-42 Seguir entrega en mapa · CU-43 Confirmar recepción · CU-44 Registrar entrega no recibida · CU-45 Revisar incidencia y replanificar · CU-46 Consultar comprobante de entrega.

G. Incentivos y analítica

CU-47 a CU-55

CU-47 Consultar métricas de actividad · CU-48 Consultar progreso de misiones · CU-49 Completar misión · CU-50 Otorgar insignia · CU-51 Consultar insignias · CU-52 Configurar visibilidad de insignias · CU-53 Actualizar categoría · CU-54 Consultar ranking mensual e histórico · CU-55 Publicar insignia en red social.

H. Notificaciones

CU-56 a CU-61

CU-56 Enviar notificación · CU-57 Notificar inactividad · CU-58 Notificar asignación · CU-59 Notificar misión cumplida · CU-60 Notificar cambio de categoría · CU-61 Notificar inicio, éxito o fallo de entrega.

I. Administración, integración y operación técnica

CU-62 a CU-69

CU-62 Consultar dashboard administrativo · CU-63 Consultar posición y avance de flota · CU-64 Ejecutar tareas programadas · CU-65 Gestionar autenticación y autorización · CU-66 Consumir API REST · CU-67 Consultar salud, métricas, trazas y logs · CU-68 Limitar solicitudes · CU-69 Recuperar servicios ante fallos.

Importante para el diagrama UML: los CU-31 y CU-32 pueden modelarse como subcasos incluidos por CU-30; los CU-57 a CU-61 especializan el envío de notificaciones de CU-56. Las operaciones CRUD también se pueden agrupar si el diagrama queda demasiado grande, pero sus flujos se detallan por separado más abajo.

# 3. Flujos de casos de uso

## A. Acceso y consulta pública

### CU-01 — Mostrar landing page

Actor principal: visitante público.

Flujo principal

1. El visitante accede a la dirección web de DonaTrack.

2. El sistema muestra el propósito y los objetivos de la organización.

3. El sistema presenta ejemplos destacados de donaciones realizadas durante el último mes.

4. El sistema ofrece accesos a la galería de donaciones entregadas, registro, inicio de sesión e información legal.

5. El visitante elige la sección que desea consultar.

Flujos alternativos

* A1. No hay donaciones destacadas: el sistema muestra la información institucional sin ejemplos, o un mensaje de que no hay datos disponibles.

* A2. Falla la carga de datos: se presenta el contenido institucional disponible y un mensaje de error para la sección afectada.

### CU-02 — Consultar información legal y privacidad

Actor principal: visitante público o usuario autenticado.

Flujo principal

1. El usuario accede al enlace de información legal y privacidad.

2. El sistema presenta las condiciones y la información de privacidad disponibles.

3. El usuario consulta el contenido.

4. El usuario regresa a la página anterior o continúa navegando.

Alternativo

- A1. La información no está disponible: se informa que no pudo cargarse.

### CU-03 — Visualizar donaciones entregadas

Actor principal: visitante público.

Flujo principal

1. El visitante ingresa a la galería o al mapa público.

2. El sistema consulta las donaciones con estado «Entregada».

3. El sistema muestra sus marcadores geográficos o sus fotos disponibles.

4. El visitante selecciona una donación para conocer más detalles.

Alternativos

* A1. No existen donaciones entregadas: se muestra el mapa vacío o un mensaje informativo.

* A2. Una donación no tiene fotos: se muestran los datos disponibles sin fotografía.

* A3. El mapa no puede cargarse: se informa el error sin impedir otras consultas disponibles.

### CU-04 — Consultar detalle de una donación en el mapa

Actor principal: visitante público.

Precondición: existe una donación entregada visible en el mapa.

Flujo principal

1. El visitante selecciona un marcador.

2. El sistema identifica la donación asociada.

3. El sistema presenta los detalles públicos disponibles de la entrega.

4. El visitante consulta las fotos y la información mostrada.

5. El visitante cierra el detalle y vuelve al mapa.

Alternativos

* A1. El marcador ya no corresponde a una donación disponible: el sistema informa que el contenido no está disponible.

* A2. No hay imágenes asociadas: el detalle presenta el resto de la información.

### CU-05 — Registrarse

Actores principales: persona donante o entidad beneficiaria.

Flujo principal

1. El usuario ingresa a la pantalla de registro.

2. Selecciona el tipo de registro correspondiente.

3. Completa los datos requeridos.

4. El sistema valida los campos obligatorios y el formato de los datos.

5. El sistema verifica si existe un registro incompatible o duplicado.

6. El sistema crea el registro correspondiente.

7. Si corresponde, inicia el proceso de habilitación de acceso.

8. El sistema confirma el resultado.

Alternativos

* A1. Faltan campos obligatorios: se indican los campos que deben completarse.

* A2. Los datos tienen un formato inválido: se solicita corregirlos.

* A3. Ya existe el registro: se informa la situación y se orienta al usuario a iniciar sesión o recuperar el acceso.

* A4. Falla el guardado: se informa que no pudo completarse el registro.

Observación: el documento define expresamente el registro público de donantes y entidades beneficiarias. El registro asistido por una persona administradora para una donación recibida se detalla en CU-07, CU-08 y CU-13.

### CU-06 — Iniciar sesión

Actores principales: donante, entidad beneficiaria y persona administradora.

Flujo principal

1. El usuario accede al formulario de inicio de sesión.

2. Ingresa sus credenciales.

3. El sistema valida las credenciales mediante el servicio de autenticación.

4. El sistema determina la identidad y los permisos del usuario.

5. El sistema habilita el acceso a las funcionalidades de su rol.

6. El usuario ingresa a su área de trabajo.

Alternativos

* A1. Credenciales incorrectas: se informa que no pudo autenticarse.

* A2. Cuenta inexistente o no habilitada: se informa que no puede acceder.

* A3. El servicio de autenticación no responde: se informa una indisponibilidad temporal.

* A4. El usuario intenta ingresar a una funcionalidad no autorizada: se deniega el acceso.

Extensión de seguridad: el TP también contempla tokens de autorización entre el frontend y los servicios de aplicación. La recuperación de contraseña no está especificada como requisito funcional explícito.

## B. Gestión de personas donantes

### CU-07 — Registrar donante humano

Actor principal: persona administradora. Actor secundario: persona donante.

Precondición: la persona se presenta en el depósito para donar y no tiene usuario registrado.

Flujo principal

1. La persona administradora solicita los datos de la persona donante.

2. Registra nombre, apellido, edad, documento, género y dirección.

3. Registra el correo electrónico obligatorio.

4. Si corresponde, agrega teléfono y/o WhatsApp.

5. Selecciona el medio de contacto predeterminado para notificaciones.

6. El sistema valida los datos.

7. El sistema registra a la persona donante y habilita su acceso.

8. El sistema envía un correo de bienvenida.

9. La persona administradora continúa con el registro de la donación.

Alternativos

* A1. Falta el correo electrónico: el sistema no permite completar el registro.

* A2. Los datos obligatorios son inválidos: se solicitan correcciones.

* A3. Ya existe un usuario para ese correo: se consulta el registro existente para evitar duplicarlo.

* A4. Falla el envío del correo: el sistema conserva el registro si ya fue guardado y deja pendiente o informa el fallo de notificación, según la implementación.

### CU-08 — Registrar donante jurídico

Actor principal: persona administradora. Actor secundario: representante de la persona jurídica.

Flujo principal

1. La persona administradora solicita los datos de la organización.

2. Registra razón social.

3. Selecciona el tipo: gubernamental, ONG, empresa o institución.

4. Registra el rubro.

5. Registra al menos un medio de contacto.

6. Identifica a las personas representantes habilitadas para operar en nombre de la organización.

7. El sistema valida y guarda el registro.

8. Se envía el correo de bienvenida o la comunicación de acceso correspondiente.

9. La persona administradora continúa con el registro de la donación.

Alternativos

* A1. Falta la razón social, el tipo, el rubro o los medios de contacto requeridos: se solicita completar los datos.

* A2. No se identifica un representante habilitado cuando es necesario: no se habilita la operación en nombre de la organización hasta resolverlo.

* A3. El correo ya está registrado: se verifica si corresponde actualizar el registro o asociar el acceso existente.

### CU-09 — Modificar datos de donante

Actor principal: persona administradora.

Flujo principal

1. La persona administradora busca al donante.

2. El sistema muestra su información.

3. La persona administradora modifica los campos permitidos.

4. El sistema valida los cambios.

5. El sistema guarda la información actualizada.

6. El sistema confirma la operación.

Alternativos

* A1. No se encuentra el donante: se informa el resultado de la búsqueda.

* A2. Los datos son inválidos: se rechazan los cambios.

* A3. Se intenta registrar un correo ya utilizado por otro usuario: se impide la duplicación.

* A4. Falla la persistencia: se informa que los cambios no fueron guardados.

### CU-10 — Consultar donante

Actor principal: persona administradora.

Flujo principal

1. La persona administradora accede al módulo de donantes.

2. Busca por los datos identificatorios disponibles.

3. El sistema presenta los resultados coincidentes.

4. La persona administradora selecciona un registro.

5. El sistema muestra los datos del donante y la información de sus donaciones accesible desde el módulo.

Alternativos

* A1. No hay coincidencias: se informa que no se encontraron resultados.

* A2. Hay varias coincidencias: el usuario refina la búsqueda.

### CU-11 — Eliminar o dar de baja donante

Actor principal: persona administradora.

Flujo principal propuesto

1. La persona administradora selecciona un donante.

2. El sistema muestra el registro.

3. La persona administradora solicita la baja.

4. El sistema verifica las restricciones de integridad.

5. Se realiza la baja permitida.

6. El sistema informa el resultado.

Alternativos

* A1. El registro tiene relaciones que impiden eliminarlo físicamente: se rechaza la eliminación o se aplica una baja lógica, si así se diseña.

* A2. La persona administradora cancela la operación: no se modifica el registro.

Nota de alcance: el TP exige alta, baja y modificación, pero no especifica si la baja debe ser lógica o física. Conviene justificar esa decisión en el diseño.

### CU-12 — Importar donantes mediante CSV

Actor principal: persona administradora.

Precondición: existe un archivo CSV con los campos requeridos para identificar y contactar a las personas donantes.

Flujo principal

1. La persona administradora ingresa al módulo de importación.

2. Selecciona el archivo CSV.

3. El sistema lee las filas del archivo.

4. Valida la estructura y los campos necesarios.

5. Por cada fila, busca si ya existe un registro con el correo electrónico informado.

6. Si existe, actualiza la información del donante.

7. Si no existe, crea el donante y genera sus credenciales de acceso.

8. Para los nuevos usuarios, solicita o dispara el envío de las credenciales correspondientes.

9. El sistema continúa hasta procesar las filas.

10. Presenta un resumen de registros creados, actualizados y rechazados.

Alternativos

* A1. El archivo no es CSV o no puede abrirse: se rechaza la importación.

* A2. El encabezado o las columnas son incorrectos: se informa el formato esperado.

* A3. Una fila contiene campos inválidos: se rechaza esa fila y se continúa con las restantes, si la estrategia elegida permite procesamiento parcial.

* A4. El correo ya existe: se actualiza el registro, no se crea un duplicado.

* A5. Falla el envío de credenciales: se registra la incidencia sin asumir que el envío fue exitoso.

* A6. El archivo contiene más de 10.000 filas: el procesamiento debe contemplar el volumen indicado por el TP.

Resultado: los registros procesados quedan identificados por su resultado individual y la importación dispone de un resumen.

## C. Gestión de donaciones

### CU-13 — Registrar una donación recibida

Actor principal: persona administradora. Actor secundario: persona donante o representante.

Precondiciones: el donante está registrado o puede registrarse durante la operación.

Flujo principal

1. La persona donante se presenta en el depósito.

2. La persona administradora identifica al donante.

3. Si no existe, ejecuta CU-07 o CU-08.

4. La persona administradora inicia una nueva carga de donación.

5. Introduce la descripción general.

6. Carga la totalidad de los bienes incluidos.

7. Para cada bien, informa descripción, subcategoría, cantidad y unidad.

8. Si corresponde, agrega una fotografía.

9. Si la categoría requiere estado, indica si el artículo es nuevo o usado.

10. Si el bien es perecedero, informa su fecha de vencimiento.

11. El sistema valida los datos.

12. El sistema registra la carga y ejecuta CU-15.

13. Cada donación resultante queda inicialmente en estado «En depósito».

14. El sistema confirma el registro.

Alternativos

* A1. No se puede identificar al donante: se solicita corregir los datos o completar el registro.

* A2. Faltan bienes o cantidades: no se confirma la carga.

* A3. Falta el estado de un bien para el que es obligatorio: se solicita completarlo.

* A4. Un bien perecedero no tiene vencimiento: se solicita la fecha.

* A5. Falla el guardado: se informa el error y no se debe comunicar un registro exitoso inexistente.

### CU-14 — Registrar los bienes donados

Actor principal: persona administradora.

Flujo principal

1. Dentro de la carga de una donación, el usuario agrega un bien.

2. Introduce su descripción.

3. Selecciona la categoría y subcategoría.

4. Indica la cantidad y su unidad de medida.

5. Adjunta una foto, si dispone de ella.

6. Completa el estado del artículo cuando corresponde.

7. Completa la fecha de vencimiento para bienes perecederos.

8. El sistema valida el bien y lo incorpora a la carga.

9. El usuario repite el proceso para los restantes bienes.

10. Al finalizar, confirma la carga completa.

Alternativos

* A1. Se desea corregir un bien antes de confirmar: se modifica la información ingresada.

* A2. La categoría requiere atributos adicionales: el sistema solicita esos campos.

* A3. El usuario cancela la carga: el sistema no debe registrar como confirmada una donación incompleta.

### CU-15 — Segmentar automáticamente la donación

Actor principal: sistema DonaTrack. Disparador: confirmación de la carga completa de bienes.

Flujo principal

1. El sistema recibe la carga completa de bienes.

2. Agrupa los bienes según su subcategoría.

3. Separa las donaciones que corresponden a diferentes subcategorías.

4. Si son bienes perecederos con fechas de vencimiento diferentes, genera grupos separados cuando corresponda.

5. Para los bienes cuyo estado sea relevante, mantiene separados los artículos nuevos y usados cuando corresponda.

6. Genera las donaciones independientes resultantes.

7. Asocia cada resultado con el donante.

8. Establece el estado inicial «En depósito».

9. Deja los resultados disponibles para los procesos posteriores de asignación.

Alternativos

* A1. La carga contiene datos inconsistentes: se rechaza o se solicita corregir la información antes de confirmar la segmentación.

* A2. Un bien no tiene subcategoría: no puede generarse correctamente la unidad mínima de asignación y se solicita resolverlo.

* A3. Falla el proceso: se registra el error y se evita dejar una segmentación parcial presentada como completa.

Regla de negocio: la subcategoría es la unidad mínima de asignación. No debe tratarse como una única donación asignable un conjunto de bienes que corresponden a subcategorías incompatibles.

### CU-16 — Consultar donaciones

Actores principales: persona donante y persona administradora.

Flujo principal del donante

1. El donante inicia sesión.

2. Accede a sus donaciones.

3. El sistema recupera los registros asociados a su identidad.

4. Muestra la información y el estado de cada donación.

5. El donante selecciona una para ver su detalle.

Flujo principal de administración

1. La persona administradora abre el módulo de donaciones.

2. Busca o selecciona una donación.

3. El sistema muestra los datos del donante, los bienes, el estado y la información de trazabilidad disponible.

Alternativos

* A1. No existen donaciones asociadas: se muestra una lista vacía.

* A2. El usuario no tiene permiso para consultar la donación: se deniega el acceso.

### CU-17 — Filtrar donaciones

Actor principal: persona donante.

Flujo principal

1. El donante abre su listado de donaciones.

2. Selecciona uno o varios estados.

3. Selecciona una categoría o subcategoría de bienes.

4. El sistema aplica los filtros.

5. Presenta las donaciones que cumplen los criterios.

6. El donante puede modificar o limpiar los filtros.

Alternativos

* A1. Ninguna donación coincide: el sistema informa que no hay resultados.

* A2. El donante elimina los filtros: se vuelve al listado completo permitido.

### CU-18 — Modificar una donación

Actor principal: persona administradora.

Flujo principal propuesto

1. La persona administradora selecciona una donación.

2. El sistema muestra su información.

3. La persona administradora modifica los campos permitidos.

4. El sistema valida las modificaciones.

5. El sistema guarda los cambios y conserva la consistencia de la segmentación y la trazabilidad.

6. Confirma la operación.

Alternativos

* A1. La donación no existe: se informa el problema.

* A2. La modificación contradice su estado o asignación: se rechaza o requiere un procedimiento específico.

* A3. Falla la persistencia: se informa el error.

Nota: el TP pide operaciones CRUD sobre donaciones, pero no define en detalle qué campos se pueden modificar una vez asignadas o entregadas. Eso debe resolverse como regla de diseño.

### CU-19 — Cambiar estado de donación

Actor principal: persona administradora o proceso autorizado, según el estado.

Flujo principal

1. Se identifica la donación.

2. Se determina el evento que habilita el cambio.

3. El sistema valida que la transición sea válida.

4. Actualiza el estado.

5. Registra el cambio para garantizar trazabilidad y auditoría.

6. Si el evento requiere notificaciones, publica el evento correspondiente.

7. Informa o refleja el nuevo estado.

Transiciones definidas en el TP

* «En depósito» → «Asignación realizada».

* «Asignación realizada» → «Lista para entregar».

* «Lista para entregar» → «En traslado».

* «En traslado» → «Entregada».

* «En traslado» → «Entrega fallida», si no se concreta en la jornada.

* Una entrega fallida puede regresar al depósito y ser planificada nuevamente.

* Una donación en depósito puede pasar a «Vencida» por acción administrativa.

Alternativos

* A1. La transición no está permitida: se rechaza y conserva el estado anterior.

* A2. Falta la justificación de un fallo de entrega: se solicita antes de registrar la incidencia.

* A3. Falla el registro de auditoría: no debe informarse una transición completamente registrada si no se pudo guardar su trazabilidad.

### CU-20 — Marcar donación vencida

Actor principal: persona administradora.

Flujo principal

1. La persona administradora busca la donación.

2. Verifica que corresponde actualizar su estado por vencimiento.

3. Solicita marcarla como «Vencida».

4. El sistema valida la transición.

5. Registra el nuevo estado y su trazabilidad.

6. Confirma la operación.

Alternativos

* A1. La donación no se encuentra en el depósito o la transición no corresponde: se rechaza el cambio.

* A2. La donación ya está vencida: se informa que no es necesario repetir la operación.

* A3. El registro no puede guardarse: se informa el error.

### CU-21 — Consultar historial de estados

Actor principal: persona administradora. Actores secundarios: donante y entidad beneficiaria, dentro de la información que tienen autorizada para consultar.

Flujo principal

1. El usuario selecciona una donación.

2. El sistema recupera los eventos de su ciclo de vida.

3. Muestra los estados registrados y la secuencia temporal disponible.

4. Si corresponde, presenta la justificación de una entrega fallida.

5. El usuario consulta la trazabilidad.

Alternativos

* A1. No hay historial disponible: se informa que no existen eventos registrados.

* A2. El usuario no está autorizado: se limita o deniega la consulta.

## D. Gestión de entidades beneficiarias y necesidades

### CU-22 — Registrar entidad beneficiaria

Actor principal: entidad beneficiaria o persona administradora.

Flujo principal

1. La entidad accede al registro.

2. Informa la razón social.

3. Informa la dirección completa.

4. Informa el teléfono de contacto.

5. Registra los correos de las personas representantes designadas.

6. El sistema valida los datos.

7. El sistema registra la entidad y habilita su acceso.

8. Confirma el registro.

Alternativos

* A1. Faltan datos obligatorios: se solicitan correcciones.

* A2. La entidad ya existe: se informa el registro existente.

* A3. Falla el guardado: se informa el error.

### CU-23 — Modificar entidad beneficiaria

Actor principal: persona administradora o representante autorizado.

Flujo principal

1. El actor selecciona la entidad.

2. El sistema presenta los datos existentes.

3. El actor modifica los campos permitidos.

4. El sistema valida la información.

5. Guarda los cambios.

6. Confirma la actualización.

Alternativos

* A1. La entidad no existe: se informa.

* A2. El actor no está autorizado para modificarla: se deniega la operación.

* A3. Los datos no son válidos: se solicita corregirlos.

### CU-24 — Consultar entidades beneficiarias

Actores principales: persona donante y persona administradora.

Flujo principal

1. El actor accede al listado de entidades.

2. El sistema recupera las entidades registradas.

3. Presenta los datos públicos disponibles.

4. El actor selecciona una entidad.

5. El sistema presenta su información y, cuando corresponda, las necesidades registradas.

Alternativos

* A1. No hay entidades registradas: se muestra un listado vacío.

* A2. Una entidad no tiene necesidades activas: se presenta la entidad sin necesidades activas.

### CU-25 — Dar de baja entidad beneficiaria

Actor principal: persona administradora.

Flujo principal propuesto

1. La persona administradora selecciona la entidad.

2. Solicita la baja.

3. El sistema verifica las donaciones, necesidades y entregas vinculadas.

4. Si se cumplen las condiciones, aplica la baja permitida.

5. Informa el resultado.

Alternativos

* A1. La entidad tiene entregas pendientes: se impide una baja que deje operaciones inconsistentes.

* A2. La persona administradora cancela: no se modifica el registro.

Nota: el TP pide operaciones CRUD sobre entidades, pero no determina la política exacta para eliminar entidades con historial.

### CU-26 — Registrar necesidad material

Actor principal: entidad beneficiaria.

Flujo principal

1. La entidad inicia sesión.

2. Ingresa al módulo de necesidades.

3. Selecciona la subcategoría del bien requerido.

4. Describe la necesidad.

5. Indica si es recurrente o extraordinaria.

6. Informa la cantidad objetivo o requerida.

7. Si es recurrente, define el período de cumplimiento.

8. El sistema valida los datos.

9. Registra la necesidad.

10. La necesidad queda disponible para el proceso de asignación.

Alternativos

* A1. Falta la subcategoría: no se permite guardar.

* A2. Falta una cantidad válida: se solicita corregirla.

* A3. Una necesidad recurrente no tiene período: se solicita completar la periodicidad.

* A4. La entidad cancela antes de confirmar: no se crea la necesidad.

Reglas específicas

* Las necesidades extraordinarias responden a situaciones puntuales.

* Las recurrentes se satisfacen durante períodos definidos, como una semana o un mes.

* Las necesidades extraordinarias pueden satisfacerse mediante varias donaciones parciales.

* La subcategoría debe coincidir exactamente con la subcategoría de la donación evaluada.

### CU-27 — Modificar necesidad material

Actor principal: entidad beneficiaria.

Flujo principal

1. La entidad selecciona una necesidad propia.

2. El sistema presenta sus datos.

3. La entidad modifica descripción, cantidad, período u otros campos permitidos.

4. El sistema valida los cambios.

5. Guarda la nueva información.

6. Confirma la modificación.

Alternativos

* A1. La necesidad no existe o no pertenece a la entidad: se rechaza la operación.

* A2. La necesidad ya fue satisfecha o tiene asignaciones en curso: el sistema aplica las restricciones correspondientes.

* A3. Los datos son inválidos: se solicita corregirlos.

### CU-28 — Consultar necesidades

Actor principal: entidad beneficiaria. Actor secundario: persona administradora.

Flujo principal

1. El actor abre el módulo de necesidades.

2. El sistema recupera las necesidades de la entidad o las disponibles para administración.

3. Presenta la descripción, subcategoría, tipo, cantidad y período, cuando corresponda.

4. El actor selecciona una necesidad.

5. El sistema presenta su detalle y estado de satisfacción disponible.

Alternativos

* A1. No hay necesidades registradas: se muestra un listado vacío.

* A2. No hay resultados para los criterios elegidos: se informa que no hay coincidencias.

### CU-29 — Cancelar o cerrar necesidad

Actor principal: entidad beneficiaria o persona administradora, según los permisos definidos.

Flujo principal propuesto

1. El actor selecciona una necesidad.

2. Solicita cerrarla o cancelarla.

3. El sistema verifica su estado y las asignaciones relacionadas.

4. Si corresponde, registra el cierre.

5. La necesidad deja de participar en nuevas asignaciones.

6. Se confirma la operación.

Alternativos

* A1. Existen asignaciones en curso: se impide el cierre inmediato o se requiere resolver las asignaciones.

* A2. La necesidad ya está satisfecha: se informa que no requiere nuevas donaciones.

* A3. La operación es cancelada: se conserva el estado actual.

Nota: el cierre y la cancelación son una inferencia funcional útil para completar el ciclo de vida. El documento no define un flujo específico de cancelación de necesidades.

## E. Asignación automática de donaciones

### CU-30 — Ejecutar algoritmos de asignación

Actor principal: scheduler o persona administradora mediante ejecución a demanda.

Precondiciones: existen donaciones «En depósito» y necesidades compatibles registradas.

Flujo principal

1. El proceso se inicia de manera programada en horario de baja carga o por solicitud administrativa.

2. El sistema recupera las donaciones que están «En depósito».

3. Recupera las necesidades candidatas.

4. Descarta las necesidades cuya subcategoría no coincide exactamente con la de la donación.

5. Ejecuta el algoritmo de compatibilidad semántica (CU-31).

6. Ejecuta el algoritmo de prioridad a entidades subatendidas (CU-32).

7. Calcula los puntajes de las combinaciones válidas.

8. Ordena los resultados de cada algoritmo.

9. Genera hasta diez necesidades candidatas por donación para cada ranking.

10. Si existen coincidencias en ambos Top 10, identifica las coincidencias comunes.

11. Presenta los rankings y las propuestas a la persona administradora.

12. La persona administradora selecciona el destino final mediante CU-34.

13. El sistema registra la asignación y actualiza el estado a «Asignación realizada».

14. Se generan los eventos correspondientes para notificaciones.

Alternativos

* A1. No existen donaciones «En depósito»: el proceso termina sin asignaciones.

* A2. No existen necesidades con subcategoría coincidente: la donación queda sin asignar y permanece en depósito.

* A3. Los algoritmos no tienen coincidencias en sus Top 10: se muestran ambos rankings por separado.

* A4. No hay necesidades con datos suficientes para evaluar: se excluyen las candidatas inválidas y se informa la ausencia de resultados válidos.

* A5. Falla la ejecución de un algoritmo: se registra el error; no se debe presentar un ranking incompleto como resultado definitivo.

* A6. El proceso devuelve varias candidatas válidas: la decisión final corresponde a la persona administradora.

### CU-31 — Calcular compatibilidad semántica

Actor principal: subsistema de asignación.

Flujo principal

1. Recibe una donación y una necesidad de la misma subcategoría.

2. Compara el nombre o la descripción del bien con la descripción de la necesidad.

3. Calcula la similitud textual mediante la distancia de Levenshtein normalizada.

4. Calcula el indicador de cobertura de volumen según las cantidades donadas y requeridas.

5. Determina el indicador del tipo de necesidad: extraordinaria o recurrente.

6. Combina los indicadores mediante los pesos configurados.

7. Obtiene un puntaje normalizado.

8. Devuelve el puntaje para ordenar las candidatas.

Alternativos

* A1. Las subcategorías no coinciden: la combinación no se evalúa.

* A2. Falta un dato necesario para calcular un indicador: se excluye la combinación o se gestiona el error según las reglas de implementación.

* A3. Los pesos configurados no suman 1: el sistema debe rechazar la configuración inválida o impedir ejecutar el algoritmo con ella.

Reglas de negocio

* Los pesos a1,a2,a3a_1, a_2, a_3a1,a2,a3 son configurables.

* La suma de los pesos debe ser 1.

* El algoritmo combina similitud textual, cobertura de volumen y tipo de necesidad.

### CU-32 — Calcular prioridad a entidades subatendidas

Actor principal: subsistema de asignación.

Flujo principal

1. Recibe una necesidad candidata y su entidad beneficiaria.

2. Consulta la cantidad de donaciones recibidas por la entidad durante el último trimestre.

3. Obtiene el factor de suavizado configurado.

4. Calcula el puntaje de prioridad.

5. Devuelve el puntaje al proceso de asignación.

6. El sistema ordena las candidatas por el resultado calculado.

Alternativos

* A1. No existe información histórica: se aplica la regla que se defina para datos faltantes, sin inventar un historial.

* A2. Falla la consulta histórica: se registra el error y se evita presentar un resultado incorrecto como definitivo.

### CU-33 — Consultar ranking de necesidades

Actor principal: persona administradora.

Flujo principal

1. La persona administradora accede al resultado de una ejecución.

2. El sistema recupera los rankings generados.

3. Presenta las necesidades candidatas, sus entidades y los puntajes.

4. Si existen coincidencias entre ambos algoritmos, muestra las candidatas coincidentes.

5. Si no existen, presenta ambos rankings por separado.

6. La persona administradora examina las alternativas.

Alternativos

* A1. Todavía no se ejecutaron los algoritmos: se informa que no hay resultados.

* A2. Una donación no tiene candidatas: se muestra sin propuestas de asignación.

### CU-34 — Confirmar entidad beneficiaria final

Actor principal: persona administradora.

Precondición: existe una donación pendiente de asignación y se generaron propuestas.

Flujo principal

1. La persona administradora selecciona la donación.

2. Consulta las necesidades propuestas por los algoritmos.

3. Evalúa las alternativas.

4. Selecciona la entidad beneficiaria y la necesidad de destino.

5. El sistema verifica que la donación siga pendiente de asignación.

6. Verifica que la necesidad corresponda a la misma subcategoría.

7. Registra la asignación.

8. Actualiza el estado de la donación a «Asignación realizada».

9. Dispara las notificaciones que correspondan.

Alternativos

* A1. La donación fue asignada por otra operación: se rechaza la confirmación duplicada.

* A2. La necesidad ya no está disponible: se solicita seleccionar otra candidata.

* A3. No hay propuestas válidas: la donación permanece en depósito.

* A4. La persona administradora cancela la selección: no se confirma la asignación.

### CU-35 — Registrar asignación y notificar

Actor principal: sistema DonaTrack.

Flujo principal

1. El sistema recibe una asignación confirmada.

2. Guarda la relación entre donación y entidad beneficiaria.

3. Registra el cambio de estado y su trazabilidad.

4. Emite un evento de asignación.

5. El servicio de Notificaciones recibe el evento de forma asincrónica.

6. Se prepara una notificación para la entidad beneficiaria.

7. Se prepara una notificación para la persona donante.

8. El sistema registra los resultados de envío disponibles.

Alternativos

* A1. Falla la publicación del evento: se registra la incidencia y se contempla un mecanismo de recuperación.

* A2. Falla la entrega de una notificación: la asignación no debe revertirse solo por ese motivo.

* A3. No hay un medio de contacto disponible: se informa o registra la imposibilidad de notificar.

## F. Logística y seguimiento de camiones

### CU-36 — Administrar camiones

Actor principal: persona administradora.

Flujo principal

1. La persona administradora abre el módulo de flota.

2. Registra un camión nuevo o selecciona uno existente.

3. Informa o modifica patente, capacidad volumétrica, altura y capacidad de carga.

4. Guarda los datos.

5. El sistema valida la información.

6. El camión queda disponible para ser considerado en la planificación cuando corresponda.

Alternativos

* A1. La patente ya está registrada: se impide crear un duplicado.

* A2. Las capacidades no son válidas: se solicita corregirlas.

* A3. Se intenta modificar un camión comprometido en una ruta: se aplican restricciones para preservar la consistencia operativa.

* A4. La persona administradora elimina o da de baja un camión: se verifica que no tenga rutas pendientes.

Operaciones incluidas: alta, consulta, modificación y baja, conforme a la gestión de flota requerida.

### CU-37 — Planificar rutas

Actor principal: scheduler. Actor secundario: planificador externo.

Precondiciones: existen donaciones asignadas, camiones disponibles y datos de destino.

Flujo principal

1. El scheduler inicia la planificación de la siguiente jornada operativa.

2. El sistema recupera las donaciones en estado «Asignación realizada».

3. Obtiene los datos de los camiones disponibles.

4. Divide las solicitudes en lotes de hasta 100 donaciones.

5. Envía cada lote al planificador externo.

6. El planificador calcula las rutas y ordena los destinos de cada camión.

7. El planificador devuelve el resultado mediante el callback del sistema.

8. El sistema registra las rutas y sus entregas.

9. Las donaciones efectivamente incluidas en las rutas pasan a «Lista para entregar».

10. Las donaciones no asignadas por el planificador se conservan para una nueva planificación.

11. Las rutas quedan disponibles para los choferes.

Alternativos

* A1. No hay donaciones asignadas: no se generan rutas.

* A2. El proveedor no responde: se registra la falla y se contempla reintentar la solicitud.

* A3. El proveedor no asigna todas las donaciones: el sistema vuelve a planificar las pendientes.

* A4. La respuesta contiene datos inválidos: se rechaza o pone en revisión el resultado afectado.

* A5. Una donación ya no es elegible: se excluye de la planificación correspondiente.

### CU-38 — Recibir resultado del planificador

Actor principal: planificador externo.

Flujo principal

1. El planificador finaliza el cálculo.

2. Invoca la URL de callback de DonaTrack.

3. Envía las rutas generadas, los camiones, los destinos y las donaciones asignadas.

4. Informa las donaciones que quedaron sin asignar.

5. DonaTrack valida la respuesta.

6. Registra las rutas y sus entregas.

7. Actualiza los estados de las donaciones efectivamente planificadas.

8. Devuelve o registra el resultado del procesamiento.

Alternativos

* A1. El callback no puede ser procesado: se registra el error y se contempla reintentar.

* A2. La respuesta es duplicada: el sistema debe evitar duplicar rutas o entregas.

* A3. Hay donaciones sin asignar: se dejan pendientes para otra ejecución.

* A4. Hay referencias a camiones o donaciones inexistentes: se rechaza o aísla la parte inválida.

### CU-39 — Consultar ruta asignada

Actor principal: chofer.

Flujo principal

1. El chofer ingresa a su aplicación.

2. El sistema identifica las rutas asignadas.

3. El chofer selecciona la ruta correspondiente a la jornada.

4. El sistema presenta el recorrido, el orden de destinos y las entregas.

5. El chofer consulta la información necesaria para iniciar el recorrido.

Alternativos

* A1. No tiene una ruta asignada: el sistema informa que no hay recorridos disponibles.

* A2. La ruta no está disponible por un problema de planificación: se informa la situación.

### CU-40 — Iniciar recorrido

Actor principal: chofer.

Precondición: el chofer tiene una ruta asignada.

Flujo principal

1. El chofer consulta la ruta.

2. Confirma el inicio del recorrido.

3. El sistema registra la fecha y hora de inicio.

4. Las entregas asignadas pasan a «En traslado».

5. Se activa el seguimiento de la ubicación.

6. Se emiten los eventos de inicio de ruta.

7. Se notifican las entidades beneficiarias y las personas donantes cuyas entregas forman parte de la ruta.

8. Las notificaciones incluyen un enlace al mapa interactivo.

Alternativos

* A1. La ruta ya fue iniciada: el sistema evita registrar un segundo inicio.

* A2. La ruta fue cancelada o no está disponible: se impide iniciarla.

* A3. No se puede activar el seguimiento: se registra la incidencia y se aplica la política operativa definida.

### CU-41 — Actualizar ubicación del camión

Actor principal: dispositivo GPS o aplicación móvil del chofer.

Flujo principal

1. La fuente de geolocalización obtiene la posición del camión.

2. Envía las coordenadas y, cuando corresponda, la velocidad.

3. El sistema recibe los datos.

4. Valida el formato y la relación con el camión y la ruta activa.

5. Registra la ubicación y su fecha/hora de actualización.

6. Actualiza la información que utiliza el dashboard y el mapa de seguimiento.

7. El proceso se repite mientras corresponda monitorear la ruta.

Alternativos

* A1. Los datos están mal formados: se rechazan.

* A2. El camión no está asociado a una ruta activa: no se presenta como una entrega en curso.

* A3. No llegan nuevas posiciones: se conserva la última posición conocida y se informa su antigüedad.

* A4. La fuente envía posiciones fuera de secuencia: se valida la marca temporal para evitar que una ubicación antigua sobrescriba una más reciente.

Decisión de diseño pendiente: el TP permite optar por GPS dedicado o aplicación móvil. La elección debe quedar documentada.

### CU-42 — Seguir entrega en mapa

Actores principales: persona donante y entidad beneficiaria.

Precondición: la entrega está activa y existe una ruta asociada.

Flujo principal

1. El usuario inicia sesión.

2. Accede a sus donaciones o entregas activas.

3. Selecciona una entrega.

4. El sistema recupera el camión o los camiones asociados.

5. Presenta un mapa con su ubicación.

6. Muestra la última actualización conocida.

7. Actualiza la visualización a medida que llegan nuevas posiciones.

8. El usuario consulta el avance hasta la finalización de la entrega.

Alternativos

* A1. La entrega aún no tiene ruta: se informa que no existe seguimiento activo.

* A2. No hay posiciones recientes: se muestra la última ubicación con su fecha/hora.

* A3. La entrega ya finalizó: se muestra el estado final y la información disponible, en lugar de un seguimiento activo.

* A4. El usuario intenta consultar una entrega ajena sin autorización: se deniega el acceso.

### CU-43 — Confirmar recepción de donación

Actor principal: entidad beneficiaria.

Precondición: la donación fue llevada a la entidad.

Flujo principal

1. La entidad abre la entrega correspondiente.

2. Verifica la recepción de los bienes.

3. Confirma la recepción.

4. Carga las fotografías de la donación recibida.

5. El sistema valida la operación y los archivos.

6. Registra la confirmación, la fecha y hora.

7. Registra qué camión realizó la entrega.

8. Actualiza la donación al estado «Entregada».

9. Genera el comprobante de entrega.

10. Notifica a la entidad y a la persona donante.

Alternativos

* A1. No se pueden cargar las fotos: se informa el error y se permite corregir o reintentar.

* A2. La entrega no corresponde a esa entidad: se rechaza la confirmación.

* A3. La entrega ya está confirmada: se evita una segunda confirmación inconsistente.

* A4. La recepción es parcial o presenta diferencias: el TP no define el flujo exacto; se deberá acordar una regla de negocio antes de implementarlo.

### CU-44 — Registrar entrega no recibida

Actor principal: entidad beneficiaria.

Flujo principal

1. La entidad consulta una entrega esperada.

2. Informa que no recibió la donación en el día correspondiente.

3. El sistema registra la incidencia.

4. La entrega queda identificada como «No recibida» en el proceso logístico.

5. El sistema notifica a la persona donante, a la entidad beneficiaria y a la administración.

6. La administración recibe el caso para su revisión.

Alternativos

* A1. La entrega ya fue confirmada como recibida: se impide marcarla simultáneamente como no recibida.

* A2. La entrega no corresponde a la entidad: se rechaza la operación.

* A3. La entidad no informa suficiente información para identificar la entrega: se solicita seleccionar la entrega correcta.

Importante: «No recibida» es el estado descrito en el flujo logístico del TP. No debe confundirse automáticamente con «Entrega fallida», que aparece en el ciclo de estados de donaciones. La relación entre ambos estados debe establecerse en el diseño.

### CU-45 — Revisar incidencia y replanificar entrega

Actor principal: persona administradora.

Flujo principal

1. La persona administradora consulta las entregas no recibidas o fallidas.

2. Selecciona la incidencia.

3. Revisa la justificación y la información logística disponible.

4. Determina si la donación regresó al depósito.

5. Si regresó, registra el estado «Pendiente» de la entrega.

6. Actualiza la situación de la donación de acuerdo con el ciclo de estados definido.

7. Si corresponde, incluye la donación en una nueva planificación.

8. El sistema registra los cambios y emite los eventos de notificación necesarios.

Alternativos

* A1. La donación no regresó al depósito: el caso queda en revisión y no se asume que está disponible para otra ruta.

* A2. Los bienes vencieron: se debe evaluar el estado de la donación antes de volver a planificarla.

* A3. No hay camiones o rutas disponibles: la entrega permanece pendiente.

* A4. La incidencia se resuelve sin replanificar: se registra la resolución y se conserva la trazabilidad.

### CU-46 — Consultar comprobante de entrega

Actores principales: persona donante y entidad beneficiaria. Actor secundario: persona administradora.

Flujo principal

1. El usuario selecciona una entrega realizada.

2. El sistema verifica sus permisos.

3. Recupera la confirmación de recepción.

4. Presenta fecha, hora, camión responsable y demás datos disponibles.

5. Permite consultar las fotos asociadas a la recepción.

6. El usuario verifica el comprobante.

Alternativos

* A1. La entrega todavía no fue confirmada: no hay comprobante final.

* A2. Faltan datos del comprobante: se presenta la información disponible y se informa la ausencia de datos.

* A3. El usuario no tiene permisos: se deniega la consulta.

## G. Incentivos, misiones y analítica

### CU-47 — Consultar métricas de actividad

Actor principal: persona donante.

Flujo principal

1. El donante inicia sesión.

2. Accede a su perfil o sección de estadísticas.

3. El sistema recupera el total histórico de donaciones.

4. Calcula o consulta la evolución por período.

5. Presenta comparaciones mensuales.

6. Muestra el número de organizaciones ayudadas.

7. Presenta los indicadores de impacto y la posición en el ranking disponible.

Alternativos

* A1. No hay actividad registrada: se presentan métricas en cero o mensajes informativos.

* A2. No hay información para un período: se muestra ese período sin datos.

* A3. El servicio de Incentivos no responde: se informa la indisponibilidad temporal.

### CU-48 — Consultar progreso de misiones

Actor principal: persona donante humana.

Flujo principal

1. El donante accede a la sección de Incentivos.

2. El sistema recupera la categoría actual.

3. Recupera la misión activa y las misiones completadas.

4. Presenta el objetivo de la misión.

5. Muestra el progreso acumulado y cuánto falta para completarla.

6. Presenta la siguiente misión cuando se desbloquea.

7. El donante consulta su avance.

Alternativos

* A1. No hay misión activa disponible: se informa el estado actual del sistema de misiones.

* A2. El progreso se reinicia por una condición de la misión: se actualiza el indicador correspondiente.

* A3. No se pueden recuperar las métricas: se informa el error.

### CU-49 — Completar misión

Actor principal: sistema de Incentivos. Actor que origina la actividad: persona donante.

Flujo principal

1. El servicio de Donaciones informa una nueva actividad relevante.

2. Incentivos recibe el evento de donación.

3. Identifica al donante y recupera su progreso.

4. Evalúa las condiciones de las misiones activas.

5. Actualiza los contadores o indicadores de progreso.

6. Verifica si se alcanzó el objetivo de alguna misión.

7. Marca la misión como completada.

8. Desbloquea la siguiente misión de la secuencia, si corresponde.

9. Dispara el proceso de otorgamiento de insignia.

10. Emite el evento para notificar la misión cumplida.

Alternativos

* A1. La actividad ya fue procesada: se evita contabilizarla dos veces.

* A2. La donación no satisface las condiciones: se actualiza el progreso sin completar la misión.

* A3. Una misión requiere que la donación haya sido recibida exitosamente: no se completa antes de confirmar la entrega.

* A4. La actividad no puede vincularse con el donante: se registra la incidencia.

Tipos de misiones previstos en el TP

* Racha de donaciones durante meses consecutivos.

* Donaciones de distintas categorías.

* Donación que supera una cantidad de bienes.

* Cantidad de donaciones recibidas exitosamente por entidades beneficiarias.

### CU-50 — Otorgar insignia

Actor principal: sistema de Incentivos.

Flujo principal

1. El sistema confirma que una misión fue completada.

2. Identifica la insignia asociada.

3. Registra la insignia obtenida por el donante.

4. La agrega a su perfil.

5. Actualiza la información que se muestra según la configuración de visibilidad.

6. Emite el evento de insignia obtenida.

7. Inicia el flujo automatizado de difusión, si corresponde.

Alternativos

* A1. La insignia ya fue otorgada por el mismo logro: se evita duplicarla.

* A2. No existe una insignia asociada a la misión: se registra la configuración faltante.

* A3. Falla el guardado: no se comunica el otorgamiento como exitoso hasta confirmar la operación.

### CU-51 — Consultar insignias

Actor principal: persona donante.

Flujo principal

1. El donante abre su perfil de Incentivos.

2. El sistema recupera las insignias obtenidas.

3. Muestra sus nombres y los datos descriptivos disponibles.

4. El donante consulta sus logros.

Alternativos

* A1. No tiene insignias: se presenta el estado inicial.

* A2. No se puede cargar el listado: se informa el error.

### CU-52 — Configurar visibilidad de insignias

Actor principal: persona donante.

Flujo principal propuesto

1. El donante abre la configuración de su perfil.

2. Selecciona una insignia.

3. Cambia su visibilidad pública.

4. El sistema guarda la preferencia.

5. El sistema refleja el cambio en el perfil.

Alternativos

* A1. La insignia no pertenece al donante: se rechaza el cambio.

* A2. Falla la actualización: se informa que la preferencia no pudo guardarse.

Nota: el TP indica que las insignias se muestran en el perfil si el donante las configura como visibles. Este caso de uso es necesario para modelar esa regla.

### CU-53 — Actualizar categoría de donante

Actor principal: sistema de Incentivos.

Flujo principal

1. El sistema recibe la confirmación de una misión.

2. Verifica si se completaron las misiones requeridas de la categoría actual.

3. Comprueba la secuencia de misiones.

4. Si corresponde, actualiza la categoría.

5. Registra la nueva categoría.

6. La nueva categoría queda visible junto al nombre de usuario.

7. Emite el evento de cambio de categoría.

8. Se dispara la notificación correspondiente.

Alternativos

* A1. Quedan misiones pendientes: la categoría no cambia.

* A2. El evento se procesa nuevamente: se evita duplicar el ascenso.

* A3. Falla la actualización: se registra el error y no se comunica el cambio como definitivo.

Categorías definidas: Colaborador, Sostenedor y Transformador.

### CU-54 — Consultar ranking mensual e histórico

Actores principales: persona administradora y usuarios que consultan la sección pública habilitada.

Flujo principal

1. El sistema obtiene la actividad de los donantes durante el mes.

2. Cuenta las misiones completadas en el período, independientemente de la categoría.

3. Ordena a los donantes por la métrica definida.

4. Persiste el ranking del mes.

5. Destaca a los tres primeros en la aplicación.

6. La persona administradora puede consultar los rankings anteriores.

7. El usuario consulta el ranking disponible.

Alternativos

* A1. El ranking del mes aún no fue generado: se informa que no está disponible.

* A2. No hay actividad suficiente: se presenta el resultado según los datos existentes.

* A3. No se puede recuperar el histórico: se informa el error.

### CU-55 — Publicar insignia en red social

Actor principal: automatización externa, por ejemplo n8n. Actores secundarios: sistema de Incentivos y proveedor de red social.

Flujo principal

1. El sistema detecta que un donante obtuvo una insignia.

2. Emite el evento con la información necesaria.

3. La herramienta de automatización recibe el evento.

4. Obtiene el texto descriptivo del logro.

5. Genera una imagen asociada a la insignia.

6. Prepara la publicación con la mención al usuario.

7. Envía el contenido a la red social.

8. Registra el resultado de la publicación.

Alternativos

* A1. No se puede generar la imagen: se registra el fallo y se contempla reintentar.

* A2. La red social no responde o rechaza la publicación: se registra el error.

* A3. El evento se recibe dos veces: se evita publicar duplicados, si la estrategia de idempotencia lo contempla.

* A4. Faltan los datos necesarios para la publicación: se detiene el flujo y se registra la incidencia.

Aclaración: la difusión automatizada está contemplada en el TP. Las reglas concretas de consentimiento, privacidad y selección de publicaciones deben definirse antes de publicar información identificable.

## H. Notificaciones

### CU-56 — Enviar notificación por un medio de contacto

Actor principal: servicio de Notificaciones. Actor secundario: proveedor de correo electrónico, SMS o WhatsApp.

Flujo principal

1. Un servicio de DonaTrack produce un evento relevante.

2. Publica el evento para que sea procesado de manera asincrónica.

3. El servicio de Notificaciones recibe el mensaje.

4. Identifica al destinatario.

5. Obtiene el contenido de la notificación y el medio correspondiente.

6. Valida los datos necesarios para el envío.

7. Invoca al proveedor de correo, SMS o WhatsApp.

8. Registra el resultado del intento.

9. Marca la notificación como completada si la operación simulada o real termina correctamente.

Alternativos

* A1. El mensaje no contiene destinatario: se rechaza o se registra como inválido.

* A2. No hay medio de contacto disponible: se registra la imposibilidad de envío.

* A3. El proveedor no responde: se registra el fallo y se puede reintentar.

* A4. El proveedor rechaza el mensaje: se registra el resultado sin asumir entrega.

* A5. El mensaje se procesa dos veces: se evita la duplicación cuando la implementación lo permita.

Diferencia entre entregas: en la primera entrega se simula el envío; en la segunda se solicitan integraciones reales con los distintos medios.

### CU-57 — Notificar inactividad del donante

Actor principal: scheduler. Actor secundario: servicio de Notificaciones.

Flujo principal

1. Una tarea programada consulta la actividad de los donantes.

2. Identifica a quienes llevan más de 20 días sin interactuar con la plataforma.

3. Verifica que corresponda enviar un recordatorio.

4. Genera el mensaje para incentivar una nueva donación.

5. Envía el evento al servicio de Notificaciones.

6. El servicio determina el medio de contacto.

7. Envía el recordatorio y registra el resultado.

Alternativos

* A1. El donante tuvo actividad reciente: no se envía la notificación.

* A2. El donante no tiene contacto disponible: se registra la imposibilidad de notificar.

* A3. El proceso se ejecuta varias veces: se debe evitar el envío repetido para el mismo período de inactividad, según una regla de negocio definida.

### CU-58 — Notificar asignación de donación

Actores principales: servicio de Donaciones y servicio de Notificaciones.

Flujo principal

1. Se confirma una asignación.

2. El servicio de Donaciones emite el evento.

3. El servicio de Notificaciones identifica a la entidad beneficiaria.

4. Prepara y envía el mensaje a la entidad.

5. Identifica a la persona donante asociada.

6. Prepara y envía el mensaje al donante.

7. Registra ambos resultados.

Alternativos

* A1. La asignación se revierte o no se confirma: no se notifica como definitiva.

* A2. Uno de los destinatarios no tiene un contacto válido: se registra el fallo individual.

* A3. Un envío falla y el otro resulta exitoso: se conservan los resultados independientes.

### CU-59 — Notificar misión cumplida

Actor principal: servicio de Incentivos.

Flujo principal

1. Se marca una misión como completada.

2. Se genera un evento con la identidad del donante y la misión.

3. El servicio de Notificaciones recibe el evento.

4. Construye el mensaje de felicitación.

5. Selecciona el medio de contacto.

6. Envía la notificación.

7. Registra el resultado.

Alternativos

* A1. La misión no se completó: no se envía el mensaje.

* A2. El evento está duplicado: se evita notificar dos veces el mismo logro cuando la implementación dispone de control de duplicados.

* A3. Falla el envío: se registra el error.

### CU-60 — Notificar cambio de categoría

Actor principal: servicio de Incentivos.

Flujo principal

1. Se confirma el ascenso de categoría.

2. El servicio de Incentivos genera el evento.

3. Notificaciones identifica al donante.

4. Prepara el mensaje indicando la nueva categoría.

5. Envía la notificación.

6. Registra el resultado.

Alternativos

* A1. El ascenso no fue confirmado: no se notifica el cambio.

* A2. La notificación no puede enviarse: se registra el fallo.

* A3. El evento es duplicado: se evita repetir la notificación si existe control de idempotencia.

### CU-61 — Notificar inicio, éxito o fallo de entrega

Actores principales: servicio de Logística y servicio de Notificaciones. Destinatarios: entidad beneficiaria, persona donante y, en caso de fallo, administración.

Flujo principal para inicio de ruta

1. El chofer inicia la ruta.

2. Logística registra el evento.

3. Identifica todas las entregas incluidas.

4. Obtiene las entidades y los donantes involucrados.

5. Genera las notificaciones con el enlace al mapa interactivo.

6. Envía los mensajes y registra sus resultados.

Flujo principal para entrega exitosa

1. La entidad confirma la recepción.

2. Logística registra el éxito.

3. Se obtiene la fecha, hora y camión responsable.

4. Se genera el comprobante de entrega.

5. Se notifica a la entidad y a la persona donante.

6. Se registra el resultado de los envíos.

Flujo principal para entrega fallida o no recibida

1. Se detecta que la entrega no pudo concretarse.

2. Se registra la incidencia y su justificación disponible.

3. Se identifica a la entidad, al donante y a la administración.

4. Se emiten los eventos de notificación.

5. Se informa la situación y, si corresponde, que la entrega podrá replanificarse.

6. Se registran los resultados.

Alternativos

* A1. No se inicia realmente la ruta: no se emite la notificación de inicio.

* A2. La recepción no está confirmada: no se envía el comprobante como si fuera definitivo.

* A3. Falla un envío: se conserva el estado real de la entrega y se registra el problema de notificación.

* A4. La entrega puede replanificarse: se informa el resultado y se deriva el caso al proceso logístico.

## I. Administración, integración y operación técnica

Estos casos completan la visión del sistema. Algunos corresponden a requerimientos transversales de implementación y no necesariamente aparecerán como óvalos independientes en el diagrama general de negocio.

### CU-62 — Consultar dashboard administrativo

Actor principal: persona administradora.

Flujo principal

1. La persona administradora inicia sesión.

2. Accede al dashboard.

3. El sistema recupera las métricas y los datos operativos disponibles.

4. Presenta el estado de las donaciones, las entregas y la información de flota.

5. La persona administradora selecciona un elemento para consultar su detalle.

6. El sistema muestra la información autorizada.

Alternativos

* A1. No hay datos: se muestran indicadores vacíos o en cero.

* A2. Un servicio no responde: se indica qué información no pudo recuperarse.

### CU-63 — Consultar posición y avance de flota

Actor principal: persona administradora.

Flujo principal

1. La persona administradora abre el panel de seguimiento.

2. El sistema recupera las posiciones más recientes.

3. Presenta los camiones en un mapa.

4. Muestra las rutas asociadas y su avance.

5. Indica la fecha y hora de la última actualización.

6. La visualización se actualiza cuando llegan nuevas posiciones.

Alternativos

* A1. Un camión no tiene ubicación disponible: se indica que no hay una posición actualizada.

* A2. Una ruta terminó: se refleja el estado final.

* A3. El servicio de geolocalización falla: se conserva la última información conocida, indicando su antigüedad.

### CU-64 — Ejecutar tareas programadas

Actor principal: scheduler.

Flujo principal

1. Llega el horario programado o la condición de ejecución.

2. El scheduler identifica la tarea correspondiente.

3. Inicia el proceso de asignación, planificación logística, detección de inactividad o ranking mensual.

4. Registra el inicio de la ejecución.

5. Ejecuta el proceso correspondiente.

6. Registra el resultado, la duración y los errores.

7. Programa o espera la siguiente ejecución.

Alternativos

* A1. Ya hay una ejecución activa: se evita una concurrencia no permitida.

* A2. Falla una dependencia: se registra la ejecución fallida y se contempla recuperación.

* A3. No hay datos para procesar: la tarea termina sin cambios.

### CU-65 — Gestionar autenticación y autorización

Actores principales: usuarios y servicios internos.

Flujo principal

1. Un usuario inicia sesión o un servicio necesita acceder a otro servicio.

2. El componente de autenticación valida las credenciales o la identidad técnica.

3. Emite o valida el token correspondiente.

4. El frontend presenta la autorización necesaria para consumir los servicios.

5. El servicio de destino valida el token y los permisos.

6. Autoriza la operación o la rechaza.

Alternativos

* A1. El token es inválido o expiró: se deniega el acceso.

* A2. El usuario no tiene permisos: se rechaza la operación.

* A3. El componente de autenticación no responde: se informa una indisponibilidad temporal.

### CU-66 — Consumir API REST

Actor principal: frontend, integración autorizada o consumidor externo habilitado.

Flujo principal

1. El consumidor construye una solicitud al endpoint correspondiente.

2. Envía la solicitud HTTP.

3. El servicio valida la autenticación, los parámetros y los datos.

4. Ejecuta la operación solicitada.

5. Persiste o recupera los datos según corresponda.

6. Devuelve una respuesta con el resultado.

Alternativos

* A1. El endpoint no existe: se devuelve un error de recurso no encontrado.

* A2. Los datos de entrada son inválidos: se devuelve un error de validación.

* A3. No hay autorización: se deniega la solicitud.

* A4. Ocurre un error interno: se devuelve un error y se registra para su diagnóstico.

Operaciones funcionales incluidas

* CRUD de personas donantes.

* CRUD de donaciones.

* Cambios de estado con trazabilidad.

* CRUD de entidades y necesidades.

* Consulta de rankings y selección de destino.

* Ejecución a demanda de los algoritmos.

* Obtención de métricas, misiones e insignias.

* Gestión de camiones, rutas y entregas.

### CU-67 — Consultar salud, métricas, trazas y logs

Actor principal: responsable operativo o herramienta de observabilidad.

Flujo principal

1. La herramienta de observabilidad recolecta métricas, trazas y logs.

2. Consolida la información de los servicios.

3. Presenta indicadores de disponibilidad y comportamiento.

4. El responsable operativo consulta el estado.

5. Identifica errores o degradaciones.

6. Investiga las causas utilizando los registros disponibles.

Alternativos

* A1. Un servicio deja de emitir métricas: se identifica la ausencia de datos.

* A2. No se pueden recolectar logs o trazas: se registra la falla de observabilidad.

* A3. Se detecta una degradación: se genera una alerta según las reglas configuradas.

### CU-68 — Limitar solicitudes

Actor principal: middleware o componente de control de tráfico.

Flujo principal

1. Llega una solicitud al sistema.

2. El componente identifica la política aplicable.

3. Evalúa el volumen de solicitudes dentro de la ventana configurada.

4. Si el límite no se superó, permite continuar.

5. Si se superó, rechaza o demora la solicitud según la política definida.

6. Registra la información necesaria para monitorear el tráfico.

Alternativos

* A1. Se supera el límite: se devuelve la respuesta de limitación correspondiente.

* A2. El componente de control falla: se aplica la política de contingencia definida para evitar decisiones inseguras o una caída innecesaria.

### CU-69 — Recuperar servicios ante fallos

Actor principal: herramienta de monitoreo y administración de infraestructura.

Flujo principal

1. El monitor comprueba la salud de los servicios.

2. Detecta un servicio no disponible.

3. Registra el fallo.

4. Ejecuta la política de recuperación configurada, como un reinicio automático.

5. Vuelve a comprobar la salud del servicio.

6. Registra el resultado de la recuperación.

7. Genera una alerta si el servicio no vuelve a estar disponible.

Alternativos

* A1. El servicio se recupera por sí mismo: se registra la recuperación sin reiniciarlo.

* A2. El reinicio falla: se registra la incidencia y se genera una alerta.

* A3. El servicio vuelve a fallar repetidamente: se aplica la política de escalamiento que se defina.

# 4. Flujos transversales que también tenés que contemplar

Para que el trabajo quede realmente completo, no alcanza con documentar el camino exitoso de cada caso de uso. Hay situaciones que se repiten en varios módulos y conviene especificarlas como reglas generales.

| Código | Flujo transversal       | Comportamiento esperado                                                                |
| ------ | ----------------------- | -------------------------------------------------------------------------------------- |
| FT-01  | Validación de datos     | Rechazar campos obligatorios faltantes o formatos inválidos.                           |
| FT-02  | Falta de permisos       | Impedir operaciones fuera del rol autorizado.                                          |
| FT-03  | Registro duplicado      | Evitar duplicaciones de usuarios, donaciones o entregas.                               |
| FT-04  | Error de persistencia   | Informar el fallo y no confirmar operaciones no guardadas.                             |
| FT-05  | Fallo de integración    | Registrar la incidencia y aplicar reintentos cuando corresponda.                       |
| FT-06  | Evento duplicado        | Evitar contabilizar dos veces una misión o repetir una operación logística.            |
| FT-07  | Notificación fallida    | Registrar el resultado del envío sin revertir automáticamente la operación de negocio. |
| FT-08  | Datos desactualizados   | Indicar la antigüedad de las ubicaciones y no presentarlas como actuales.              |
| FT-09  | Operación concurrente   | Verificar que otra operación no haya cambiado el estado antes de confirmar.            |
| FT-10  | Proceso incompleto      | Permitir identificar y recuperar tareas que quedaron pendientes o fallaron.            |
| FT-11  | Consulta sin resultados | Informar que no hay coincidencias sin presentar un error innecesario.                  |
| FT-12  | Operación cancelada     | No confirmar cambios que el usuario canceló antes de guardar.                          |

Estos flujos son especialmente importantes en las asignaciones, las entregas, las importaciones CSV y las integraciones asincrónicas.

# 5. Casos de uso adicionales para el diagrama técnico

Hay funcionalidades del documento que no son necesariamente casos de uso independientes para los actores de negocio, pero conviene identificarlas en los diagramas de arquitectura o de servicios.

| Caso o función                              | Qué representa                                                                   | Dónde conviene modelarlo                                 |
| ------------------------------------------- | -------------------------------------------------------------------------------- | -------------------------------------------------------- |
| Exponer endpoint de prueba                  | Responder al GET con «Hola desde el servicio de Donaciones.»                     | Diagrama del Servicio de Donaciones o especificación API |
| Persistir datos                             | Guardar donantes, donaciones, necesidades, misiones y otros registros            | Diagrama de componentes y modelo de datos                |
| Ejecutar matchmaking en segundo plano       | Ejecutar algoritmos asincrónicos en horarios de baja carga                       | Diagrama de actividades o secuencia                      |
| Procesar callback de rutas                  | Recibir las rutas devueltas por el proveedor externo                             | Diagrama de secuencia de Logística                       |
| Procesar eventos mediante cola              | Comunicar servicios de dominio con Notificaciones de manera asincrónica          | Diagrama de componentes o secuencia                      |
| Generar imágenes de insignias               | Producir imágenes para la difusión automatizada                                  | Diagrama de actividades de Incentivos                    |
| Renderizar vistas del frontend              | Obtener datos de las APIs y mostrar las páginas dinámicamente                    | Diagrama de componentes MVC                              |
| Integrar gRPC o GraphQL                     | Habilitar la alternativa de comunicación avanzada requerida en la última entrega | Diagrama de arquitectura                                 |
| Registrar servicios y API Gateway           | Incorporar el bonus de arquitectura de microservicios                            | Diagrama de despliegue                                   |
| Garantizar eventos con Transactional Outbox | Incorporar el bonus de persistencia confiable de eventos                         | Diagrama de componentes y secuencia                      |

La diferencia fundamental es que un caso de uso describe un objetivo de un actor, mientras que una función técnica puede ser un componente interno que permite realizar ese objetivo.

# 6. Cómo organizar el diagrama general en StarUML

No te recomiendo colocar los 69 casos de uso en una sola imagen: resultaría difícil de leer. Lo más conveniente es hacer un diagrama general y varios diagramas de detalle.

## Diagrama general

Sistema: DonaTrack

Acceso y consulta

Registrarse · Iniciar sesión · Consultar donaciones públicas

Donaciones

Gestionar donantes · Registrar donación · Segmentar · Consultar trazabilidad

Beneficiarios y asignación

Gestionar necesidades · Ejecutar asignación · Consultar ranking · Confirmar destino

Logística

Gestionar flota · Planificar rutas · Iniciar recorrido · Seguir camiones · Confirmar entrega

Incentivos y comunicación

Consultar métricas · Gestionar misiones · Consultar insignias · Consultar ranking · Enviar notificaciones

Administración y operación

Importar CSV · Administrar entidades · Gestionar autenticación · Supervisar servicios

## Relaciones UML recomendadas

* `Registrar una donación` include `Registrar los bienes donados`.

* `Registrar una donación` include `Segmentar automáticamente la donación`.

* `Ejecutar algoritmos de asignación` include `Calcular compatibilidad semántica`.

* `Ejecutar algoritmos de asignación` include `Calcular prioridad a entidades subatendidas`.

* `Confirmar entidad beneficiaria final` include `Registrar asignación`.

* `Iniciar recorrido` include `Actualizar estado de las entregas`.

* `Confirmar recepción` include `Registrar comprobante de entrega`.

* `Completar misión` puede include `Otorgar insignia` cuando el logro lo requiere.

* `Enviar notificación` puede especializarse en las notificaciones de asignación, misión, categoría e incidencias logísticas.

* `Publicar insignia` se dispara cuando se produce el evento correspondiente; la relación con `Otorgar insignia` puede representarse con una dependencia o en un diagrama de actividades, en lugar de forzar un `include`.

Recordá que `include` significa que el comportamiento incluido forma parte obligatoria del caso base. `extend` corresponde a un comportamiento adicional condicionado, no simplemente a cualquier paso que ocurra después.

# 7. Qué está definido y qué falta decidir

El TP permite identificar las funcionalidades anteriores, pero hay decisiones que no están cerradas en el documento y no conviene presentarlas como requisitos explícitos:

* Si la baja de donantes y entidades será lógica o física.  -> Logica

* Qué modificaciones se permiten sobre una donación ya asignada o entregada.  -> elegir mejor solucion

* Cómo se cancela una necesidad y qué ocurre con sus asignaciones pendientes.

* Qué tratamiento tiene una recepción parcial o una discrepancia en las cantidades.

* Cómo se vinculan exactamente los estados «No recibida», «Pendiente» y «Entrega fallida».

* Qué reglas se aplican para reintentos de notificaciones y procesamiento de eventos duplicados.

* Qué mecanismo de autenticación se utiliza para cada tipo de usuario y cómo se recuperan las credenciales.

* Si la difusión de insignias requiere una autorización específica del donante.

* Qué tecnología se elige para el seguimiento de camiones: GPS dedicado o aplicación móvil.

Estas cuestiones deberían aparecer como decisiones de diseño, reglas de negocio pendientes o flujos alternativos por definir.

Mi recomendación para la entrega de Diseño de Sistemas: usá el inventario como base y prepará un diagrama general más diagramas detallados de Donaciones, Beneficiarios y Asignaciones, Logística, Incentivos y Administración. Así vas a poder mostrar todos los objetivos del sistema sin convertir el diagrama UML en una maraña de líneas.



# DonaTrack: decisiones funcionales recomendadas

Para que el proyecto quede consistente desde el punto de vista de análisis, diseño de sistemas y casos de uso, conviene responder estas nueve cuestiones como reglas oficiales del negocio. Las siguientes son propuestas recomendadas para DonaTrack, no decisiones que ya estén confirmadas en el documento original.

La idea es que cada regla sea clara, implementable y fácil de defender ante la cátedra: definir qué hace el sistema, qué puede hacer cada actor y qué pasa cuando ocurre una excepción.

## 1. ¿La baja de donantes y entidades será lógica o física?

Decisión recomendada: baja lógica

DonaTrack debe implementar la baja lógica tanto para donantes como para entidades beneficiarias. No se eliminarán físicamente sus registros de la base de datos, sino que se marcarán como inactivos.

Reglas de negocio

* Un donante dado de baja no podrá registrar nuevas donaciones.

* Una entidad dada de baja no podrá registrar nuevas necesidades ni recibir nuevas asignaciones.

* Se conservarán el historial de donaciones, entregas, asignaciones, comprobantes y auditorías.

* Si existen entregas o asignaciones pendientes, el sistema deberá advertirlo y exigir que se resuelvan o se reasignen antes de completar la baja.

* La baja deberá registrar fecha, usuario responsable y motivo.

* Si la baja fue un error, un administrador autorizado podrá reactivar el registro.

Flujo de baja

1. El administrador selecciona el donante o la entidad.

2. El sistema consulta si existen asignaciones o entregas pendientes.

3. Si existen, informa cuáles son y bloquea la baja definitiva hasta resolverlas.

4. Si no existen, solicita confirmación y motivo.

5. El sistema cambia el estado a `Inactivo`.

6. Registra la operación en la auditoría.

7. El registro deja de aparecer en las búsquedas operativas, pero permanece disponible para consultas históricas autorizadas.

Justificación: la baja física podría romper las relaciones entre donantes, donaciones, entidades y entregas. La baja lógica preserva la trazabilidad y permite auditar el destino de cada bien donado.

## 2. ¿Qué modificaciones se permiten sobre una donación ya asignada o entregada?

Decisión recomendada: restricciones según estado

La posibilidad de modificar una donación dependerá de su estado actual. Cuanto más avanzado esté el proceso, menos modificaciones directas se permitirán.

| Estado de la donación | Modificaciones permitidas                                                                                                                                         |
| --------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| En depósito           | Datos descriptivos, cantidades y vencimientos, con validaciones.                                                                                                  |
| Asignación realizada  | Datos descriptivos sin impacto en la asignación. Si cambia la cantidad, categoría o vencimiento, se deberá revisar la asignación.                                 |
| Lista para entregar   | Solo correcciones administrativas que no alteren la carga, la compatibilidad ni la ruta. Los cambios relevantes requieren cancelar y recalcular la planificación. |
| En traslado           | No se permiten modificaciones de cantidad, categoría, destinatario ni ruta mediante edición directa. Debe gestionarse una incidencia.                             |
| Entregada             | No se permiten cambios operativos directos. Las correcciones se registran mediante una rectificación auditada, sin borrar los datos originales.                   |
| Vencida               | No se permite asignarla ni entregarla. Una corrección excepcional requiere autorización y una nueva validación de vigencia.                                       |

Regla fundamental: una modificación nunca debe alterar silenciosamente una asignación, una ruta ya iniciada o una entrega confirmada.

Flujo para modificar una donación asignada

1. El administrador solicita modificar la donación.

2. El sistema consulta su estado.

3. Valida los permisos y los campos que se quieren cambiar.

4. Si el cambio no afecta la asignación, lo guarda y registra en auditoría.

5. Si afecta la cantidad, subcategoría o vencimiento, bloquea la edición directa y solicita revisar la asignación.

6. Si la asignación todavía no fue entregada, se cancela o revierte de manera controlada y la donación vuelve al proceso de asignación.

7. El sistema ejecuta nuevamente las validaciones y registra los cambios.

Para donaciones entregadas: si se detectó un error, se genera una rectificación con el valor anterior, el valor corregido, el motivo, la fecha y el responsable. La entrega original y su comprobante se conservan.

Justificación: permite corregir errores sin perder la historia del proceso ni comprometer la integridad de los registros logísticos.

## 3. ¿Cómo se cancela una necesidad y qué ocurre con sus asignaciones pendientes?

Decisión recomendada: cancelación con reasignación controlada

Una necesidad podrá cancelarse únicamente por un usuario autorizado de la entidad beneficiaria o por un administrador. La cancelación deberá incluir un motivo y no implicará borrar la necesidad.

Reglas de negocio

* Una necesidad satisfecha se marca como `Cerrada`, no como cancelada.

* Una necesidad cancelada deja de participar en el algoritmo de asignación.

* Las asignaciones pendientes deben liberarse y las donaciones deben regresar al proceso de asignación, siempre que todavía no estén en traslado.

* Si la donación ya está en traslado, se deberá gestionar una incidencia logística; no se cambiará el destinatario sin una intervención autorizada.

* Si la donación ya fue entregada, la cancelación no puede revertir la entrega histórica.

* En las necesidades extraordinarias, las asignaciones parciales ya realizadas deben revisarse individualmente.

Flujo de cancelación

1. El usuario autorizado selecciona la necesidad y especifica el motivo.

2. El sistema verifica que la necesidad no esté cerrada ni cancelada previamente.

3. Consulta las donaciones asociadas y sus estados.

4. Para cada asignación pendiente, determina si puede liberarse automáticamente.

5. Libera las asignaciones que todavía no ingresaron en traslado.

6. Devuelve las donaciones liberadas al proceso de asignación y recalcula candidatos.

7. Si hay donaciones en traslado, genera una incidencia para revisión logística.

8. Marca la necesidad como `Cancelada`.

9. Registra la operación y notifica a los usuarios involucrados.

Justificación: evita que una necesidad inexistente siga recibiendo bienes y, al mismo tiempo, impide que la cancelación destruya información sobre donaciones ya comprometidas.

## 4. ¿Qué tratamiento tiene una recepción parcial o una discrepancia en las cantidades?

Decisión recomendada: recepción con control de cantidades

DonaTrack deberá permitir que la entidad beneficiaria confirme la recepción de una donación de forma total o parcial. La cantidad recibida se registrará por separado de la cantidad enviada, para mantener la trazabilidad.

Reglas de negocio

* La cantidad enviada y la cantidad recibida nunca se sobrescriben entre sí.

* Si se recibe todo correctamente, la donación pasa a `Entregada`.

* Si se recibe una parte, el sistema registra una recepción parcial y genera una incidencia.

* La cantidad faltante no se considerará entregada.

* Si hay bienes dañados, sobrantes o de una subcategoría incorrecta, se deberá registrar la discrepancia.

* Un administrador resolverá la incidencia y determinará si corresponde una nueva entrega, una devolución o el cierre del caso.

* Cada recepción y rectificación debe quedar asociada al usuario que la registró, con fecha y hora.

Flujo de recepción

1. El destinatario abre la entrega asignada.

2. El sistema muestra los bienes y las cantidades declaradas para esa entrega.

3. El destinatario registra las cantidades efectivamente recibidas.

4. Adjunta fotografías y observaciones si corresponde.

5. El sistema compara las cantidades enviadas con las recibidas.

6. Si coinciden y no existen discrepancias, confirma la entrega y genera el comprobante.

7. Si no coinciden, registra la recepción parcial o la discrepancia y genera una incidencia.

8. El administrador revisa la evidencia y decide cómo resolverla.

9. El sistema conserva el resultado original y registra la resolución.

Ejemplo

Se enviaron 100 unidades y la entidad declara haber recibido 85.

* Cantidad enviada: 100.

* Cantidad recibida: 85.

* Cantidad faltante: 15.

* Resultado: recepción parcial con incidencia pendiente de resolución.

No se debe marcar como entregada la totalidad de las 100 unidades. Si el modelo de datos permite dividir una donación en entregas o partidas, las 85 unidades recibidas podrán quedar confirmadas mientras las 15 restantes permanecen bajo revisión.

Justificación: el control separado de cantidades evita dar por entregados bienes que no llegaron y permite investigar diferencias sin perder el historial logístico.

## 5. ¿Cómo se vinculan los estados «No recibida», «Pendiente» y «Entrega fallida»?

Decisión recomendada: separar estado logístico de confirmación

Para evitar ambigüedades, recomiendo que `Pendiente` no sea un estado de entrega equivalente a los otros dos, sino una condición que indique que todavía falta una acción o confirmación.

Los estados principales de una donación pueden seguir el flujo ya definido para DonaTrack:

`En depósito` → `Asignación realizada` → `Lista para entregar` → `En traslado` → `Entregada`

Cuando ocurre un problema, se registra una incidencia y, si corresponde, un estado de excepción.

| Término           | Significado                                                                                   | Consecuencia                                                                   |
| ----------------- | --------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------ |
| `Pendiente`       | Todavía falta realizar una acción o confirmar un resultado.                                   | No implica por sí solo que la entrega haya fallado.                            |
| `No recibida`     | El destinatario declara que no recibió la entrega o que no puede confirmar su recepción.      | Se registra una declaración y se requiere revisión.                            |
| `Entrega fallida` | La entrega no pudo completarse durante el recorrido o dentro del plazo operativo establecido. | Se registra el fallo y se habilita la revisión logística o la replanificación. |
| `Entregada`       | La recepción fue confirmada y registrada.                                                     | Se genera el comprobante correspondiente.                                      |

### Flujo recomendado

1. La donación se encuentra en `En traslado`.

2. Si la entrega se completa, pasa a `Entregada`.

3. Si termina la jornada sin confirmación, queda pendiente de conciliación.

4. Si la entidad declara que no recibió los bienes, se registra `No recibida` como resultado de recepción.

5. Si se confirma que el intento no pudo completarse, se registra `Entrega fallida` como resultado logístico.

6. El administrador revisa el caso y decide si se devuelve la donación al depósito, se replanifica o se adopta otra medida.

7. El sistema registra el resultado final y notifica a los usuarios involucrados.

Recomendación de diseño: usar un campo para el estado de la donación, otro para el estado de la entrega y otro para la incidencia. Por ejemplo:

* `estado_donacion`: `En traslado`.

* `estado_entrega`: `Pendiente de confirmación`.

* `tipo_incidencia`: `No recibida`.

Así se evita mezclar el estado general de los bienes con el resultado de un intento de entrega. Si se decide que `Entrega fallida` también debe ser un estado de la donación, la transición deberá estar explícitamente definida y documentada.

Justificación: un intento fallido, una declaración de no recepción y una confirmación todavía pendiente representan situaciones distintas y requieren acciones diferentes.

## 6. ¿Qué reglas se aplican para reintentos de notificaciones y procesamiento de eventos duplicados?

Decisión recomendada: procesamiento asíncrono e idempotente

DonaTrack debería utilizar una cola de eventos para procesar notificaciones de forma asíncrona. De esa manera, una caída del servicio de correo, WhatsApp o SMS no bloquea el registro de una donación, asignación o entrega.

### Reglas para notificaciones

* Cada notificación tendrá un identificador único.

* Los errores temporales se reintentarán automáticamente.

* Los errores permanentes, como un destinatario inválido, no se reintentarán indefinidamente.

* Se propone un máximo de cinco intentos, con esperas crecientes entre ellos.

* Al agotar los intentos, la notificación pasará a `Fallida` y se generará un registro para su revisión.

* Los reintentos no deberán generar mensajes duplicados al destinatario cuando el proveedor permita controlar la idempotencia.

* Se registrarán fecha, canal, destinatario, cantidad de intentos y resultado de cada envío.

Como política inicial, los reintentos pueden realizarse después de 1, 5, 15 y 60 minutos, con un quinto intento posterior sujeto a la configuración operativa. Estos intervalos son una propuesta y deberán confirmarse en los requisitos.

### Reglas para eventos duplicados

Cada evento tendrá un `event_id` único. El sistema deberá registrar los eventos procesados para reconocer si ya fueron ejecutados.

Flujo

1. Se genera un evento, por ejemplo, `EntregaConfirmada`.

2. El evento se publica en la cola.

3. El consumidor verifica su identificador.

4. Si ya fue procesado, lo descarta sin repetir efectos de negocio.

5. Si es nuevo, valida el contenido y ejecuta la operación.

6. Registra el resultado y marca el evento como procesado.

7. Si ocurre un error temporal, permite reintentar la operación de forma segura.

Para evitar que se genere una entrega sin publicar su evento —o viceversa— se recomienda evaluar el patrón Transactional Outbox, que guarda la modificación de negocio y el evento pendiente de publicación dentro de una misma transacción.

Importante: una cola puede entregar un evento más de una vez. Por eso, no alcanza con confiar en que la infraestructura no duplicará mensajes; la aplicación debe estar preparada para procesarlos de forma idempotente.

Justificación: estas medidas evitan notificaciones repetidas, insignias otorgadas varias veces, entregas duplicadas y otros errores provocados por reintentos o fallos de red.

## 7. ¿Qué mecanismo de autenticación se utiliza para cada tipo de usuario y cómo se recuperan las credenciales?

Decisión recomendada: autenticación centralizada y roles

DonaTrack debería utilizar autenticación centralizada con correo electrónico y contraseña para los usuarios humanos, junto con autorización basada en roles. La API y las integraciones técnicas deberán autenticarse mediante mecanismos específicos para sistemas.

| Tipo de usuario                   | Autenticación recomendada                                                                  | Permisos generales                                                              |
| --------------------------------- | ------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------- |
| Donante humano                    | Correo y contraseña.                                                                       | Gestionar sus datos, consultar sus donaciones y sus incentivos.                 |
| Representante de donante jurídico | Cuenta individual asociada a la organización.                                              | Operar dentro de los permisos concedidos a su organización.                     |
| Usuario de entidad beneficiaria   | Cuenta individual asociada a la entidad.                                                   | Gestionar necesidades y confirmar recepciones autorizadas.                      |
| Administrador                     | Correo y contraseña con segundo factor (MFA).                                              | Administrar registros, asignaciones, incidencias y configuraciones autorizadas. |
| Servicio o integración externa    | Credenciales de servicio, como OAuth 2.0 Client Credentials o claves específicas rotables. | Acceso limitado a las operaciones técnicas necesarias.                          |

Cada persona deberá tener su propia cuenta; no se recomienda compartir una cuenta entre todos los miembros de una organización. Los permisos deben validarse en el servidor, no solamente ocultando opciones en la interfaz.

### Recuperación de credenciales

1. El usuario selecciona «Olvidé mi contraseña».

2. Ingresa el correo asociado a su cuenta.

3. El sistema muestra una respuesta genérica para no revelar si ese correo está registrado.

4. Si corresponde, envía un enlace o código de recuperación de un solo uso, con vencimiento.

5. El usuario verifica el enlace o código y define una contraseña nueva.

6. El sistema invalida el token utilizado y, como medida recomendada, revoca las sesiones existentes.

7. Registra el evento de seguridad y notifica el cambio de contraseña.

Reglas de seguridad

* Las contraseñas se almacenan mediante un algoritmo de hash diseñado para contraseñas, como Argon2id, nunca en texto plano.

* Los tokens de recuperación son aleatorios, de un solo uso y tienen una vigencia limitada.

* Se aplican límites de intentos para prevenir ataques automatizados.

* El personal administrativo no puede consultar la contraseña de un usuario.

* Para integraciones, las credenciales se revocan y rotan sin depender de una recuperación de contraseña humana.

Justificación: la autenticación centralizada simplifica el control de acceso y la recuperación, mientras que los roles permiten separar las responsabilidades de donantes, beneficiarios, administradores y servicios externos.

## 8. ¿La difusión de insignias requiere una autorización específica del donante?

Decisión recomendada: autorización explícita y revocable

Sí. La publicación de una insignia en redes sociales debe requerir una autorización explícita del donante. Obtener una insignia por completar una misión no equivale a autorizar su publicación.

DonaTrack deberá distinguir entre:

* Otorgamiento: el sistema concede la insignia cuando se cumplen las condiciones de la misión.

* Visualización interna: el donante consulta sus insignias dentro de DonaTrack.

* Publicación externa: el donante autoriza que una insignia se comparta fuera del sistema.

### Flujo de publicación

1. El sistema otorga la insignia.

2. La insignia aparece en el perfil del donante.

3. El donante selecciona «Compartir insignia».

4. El sistema informa qué contenido se publicará, en qué plataforma y con qué datos.

5. El donante autoriza la operación y, cuando corresponda, conecta su cuenta con la plataforma externa.

6. El sistema publica únicamente el contenido autorizado.

7. Registra el resultado de la operación.

8. Si no existe autorización o falla la integración, la insignia permanece disponible dentro de DonaTrack, sin publicación externa.

La autorización debe poder revocarse para futuras publicaciones. La revocación no garantiza que desaparezcan contenidos que ya fueron publicados y copiados en plataformas externas.

Reglas adicionales

* La publicación no será automática por defecto.

* No se incluirán datos personales o detalles de las donaciones sin autorización específica.

* La configuración de visibilidad interna será independiente del permiso para publicar en redes.

* Si se implementa publicación automática, deberá existir una autorización previa clara y específica para esa modalidad.

Justificación: permite mantener el sistema de incentivos sin convertir una recompensa interna en una difusión pública no deseada.

## 9. ¿Qué tecnología se elige para el seguimiento de camiones: GPS dedicado o aplicación móvil?

Decisión recomendada: aplicación móvil con GPS

Para la primera versión de DonaTrack, recomiendo utilizar una aplicación móvil instalada en el teléfono del conductor, que obtenga la ubicación mediante el GPS del dispositivo. Es una solución más simple de desplegar que instalar un dispositivo GPS dedicado en cada camión.

![Episode 33: Our mobile world: Enabling intelligent transport systems](https://images.openai.com/static-rsc-4/7Kw-H1j3nL95BB-uoVKwAsQAowp2ImrBKumfp0CAcIC42Q3K05F5i-HkkKpo4OFErmorHFUw0aSP8tRhRXuCGOnqG3-aUadoqrERIfdz5q3a0W3f9-lRZXzImO96XKI7thO9Tuo92G23Mi4lkHFSiHK_EvvkYKpR3Df9IqmivVw?purpose=inline)

Agregar a Favoritos

Opción A — Aplicación móvil con GPS

Recomendada para el MVP

* Menor inversión inicial.

* Permite enviar ubicación, identificar recorridos y registrar el inicio y fin de las entregas.

* Puede reutilizarse para confirmar paradas y registrar incidencias.

* Depende de la batería, los permisos de ubicación, la conectividad y el comportamiento del teléfono.

![UScellular Business Selects Geotab To Implement Fleet Management Solutions | UScellular for Business](https://images.openai.com/static-rsc-4/Vhh4a9IaraLWNquXarn3MryGb7ems-5CtsdDjrlKRVmQLraRaeXDEFJu8tCovHgsZYpuK09etRPFGl8q3L8z6cV_RtY0iMOPQANXzw9wqGGbw0olXc0HngjsS1ejj1I9l4UPNd93RxrmrTqsBl45DMwih0I66mftiGANTE5cojM?purpose=inline)

Agregar a Favoritos

Opción B — GPS dedicado

* Puede ofrecer seguimiento permanente y alimentación eléctrica del vehículo.

* Resulta apropiado cuando se requiere alta disponibilidad o control de flota independiente del conductor.

* Implica comprar e instalar equipos y gestionar su conectividad.

* Puede requerir integración con el proveedor de telemetría.

### Flujo de seguimiento recomendado

1. El administrador planifica las rutas y asigna un camión.

2. El conductor inicia sesión en la aplicación y selecciona la ruta asignada.

3. La aplicación solicita los permisos de ubicación necesarios.

4. Al iniciar el recorrido, DonaTrack registra la hora de salida y cambia las donaciones correspondientes a `En traslado`.

5. La aplicación envía posiciones periódicas al backend, siempre que tenga los permisos y la conectividad necesarios.

6. El backend valida el usuario, el camión y la ruta asociados, y registra la última posición recibida.

7. Los usuarios autorizados visualizan el avance en el mapa.

8. Al llegar al destino, el conductor o el destinatario registra el resultado de la entrega.

9. El sistema detiene el seguimiento al finalizar la ruta y registra la hora de cierre.

### Reglas técnicas

* La frecuencia de actualización será configurable; como punto de partida, puede evaluarse una actualización cada 15–30 segundos durante el recorrido.

* Si se pierde la conectividad, la aplicación intentará enviar las posiciones pendientes al recuperar la conexión, identificándolas con su fecha y hora originales.

* Si una posición está desactualizada, el mapa debe indicarlo y no presentarla como ubicación en tiempo real.

* El seguimiento se activa durante recorridos autorizados y no debe implicar rastrear al conductor fuera del servicio.

* El acceso al historial de ubicaciones se limitará según los roles y las políticas de conservación de datos.

Justificación: la aplicación móvil reduce los costos y permite integrar el seguimiento con la confirmación de entregas. Si más adelante se requiere monitoreo permanente, se podrá incorporar GPS dedicado mediante una interfaz común de seguimiento.

# Resumen de las nueve decisiones

| N.º | Decisión propuesta                                                                                                                  |
| --- | ----------------------------------------------------------------------------------------------------------------------------------- |
| 1   | Baja lógica, con auditoría y conservación del historial.                                                                            |
| 2   | Modificaciones restringidas según estado; rectificación auditada para entregas cerradas.                                            |
| 3   | Cancelación con motivo, liberación de asignaciones pendientes y revisión de entregas en tránsito.                                   |
| 4   | Recepción parcial registrada por separado, con discrepancias e incidencias.                                                         |
| 5   | `Pendiente` como condición de seguimiento; `No recibida` como declaración de recepción; `Entrega fallida` como resultado logístico. |
| 6   | Cola asíncrona, reintentos limitados, identificadores únicos e idempotencia.                                                        |
| 7   | Autenticación centralizada, roles, MFA para administradores y credenciales específicas para integraciones.                          |
| 8   | Autorización explícita y revocable para difundir insignias.                                                                         |
| 9   | Aplicación móvil con GPS para la primera versión, con posibilidad de incorporar dispositivos dedicados.                             |

## Cómo incorporarlo a la documentación de DonaTrack

Estas decisiones deberían reflejarse en cuatro partes del proyecto:

1. Reglas de negocio: registrar las nueve decisiones como reglas numeradas y verificables.

2. Casos de uso: actualizar los flujos de baja de donantes y entidades, modificación de donaciones, cancelación de necesidades, confirmación de recepción, notificaciones, autenticación, difusión de insignias y seguimiento de rutas.

3. Modelo de datos: incorporar, cuando corresponda, campos de estado, motivo, cantidades enviadas y recibidas, incidencias, autorizaciones, identificadores de eventos y auditoría.

4. Pruebas de aceptación: comprobar tanto el camino normal como los casos de excepción: una baja con entregas pendientes, una necesidad cancelada con asignaciones, una recepción parcial, un evento duplicado, una contraseña recuperada y una insignia sin permiso de publicación.

La clave para defender estas respuestas es distinguir entre requisitos ya establecidos en el proyecto y decisiones de diseño propuestas para cerrar ambigüedades. Una vez aprobadas, estas últimas pueden incorporarse formalmente como reglas de negocio y utilizarse como base para los diagramas UML y los casos de prueba.
