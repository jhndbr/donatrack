# 02. Guía de Finalización: Servicio de Donaciones (`donaciones-service`)

## 1. Resumen de Estado Actual
El módulo [`donaciones-service`](file:///home/jhon/Documentos/Github/TP-DDSI/donaciones-service/pom.xml) (puerto `8082`, PostgreSQL `donaciones_db`) tiene una base sólida:
* **Modelo de Dominio Rico**: Herencia `JOINED` en [`PersonaDonante.java`](file:///home/jhon/Documentos/Github/TP-DDSI/donaciones-service/src/main/java/com/donatrack/donaciones/domain/PersonaDonante.java) (`PersonaHumana` y `PersonaJuridica`), máquina de estados con bloqueo optimista (`@Version`) y trazabilidad de auditoría en [`Donacion.java`](file:///home/jhon/Documentos/Github/TP-DDSI/donaciones-service/src/main/java/com/donatrack/donaciones/domain/Donacion.java) e [`HistorialEstadoDonacion.java`](file:///home/jhon/Documentos/Github/TP-DDSI/donaciones-service/src/main/java/com/donatrack/donaciones/domain/HistorialEstadoDonacion.java).
* **Patrón Strategy + Registry en Matchmaking**: [`MatchmakingStrategyRegistry.java`](file:///home/jhon/Documentos/Github/TP-DDSI/donaciones-service/src/main/java/com/donatrack/donaciones/matchmaking/MatchmakingStrategyRegistry.java), [`CompatibilidadSemanticaStrategy.java`](file:///home/jhon/Documentos/Github/TP-DDSI/donaciones-service/src/main/java/com/donatrack/donaciones/matchmaking/CompatibilidadSemanticaStrategy.java) y [`PrioridadSubatendidosStrategy.java`](file:///home/jhon/Documentos/Github/TP-DDSI/donaciones-service/src/main/java/com/donatrack/donaciones/matchmaking/PrioridadSubatendidosStrategy.java).
* **Importación CSV en Streaming**: [`CsvDonanteImporterService.java`](file:///home/jhon/Documentos/Github/TP-DDSI/donaciones-service/src/main/java/com/donatrack/donaciones/service/CsvDonanteImporterService.java) con lotes de 500 filas (`flush()` y `clear()`).
* **Transactional Outbox**: [`OutboxEvent.java`](file:///home/jhon/Documentos/Github/TP-DDSI/donaciones-service/src/main/java/com/donatrack/donaciones/domain/OutboxEvent.java) y [`OutboxPublisher.java`](file:///home/jhon/Documentos/Github/TP-DDSI/donaciones-service/src/main/java/com/donatrack/donaciones/service/OutboxPublisher.java).

Sin embargo, existen **bugs críticos de lógica de negocio** y **requerimientos faltantes del enunciado** que deben corregirse.

---

## 2. Cosas que FALLAN (Bugs Críticos a Corregir)

### 2.1. BUG CRÍTICO: `DonacionesScheduler` auto-asigna todas las donaciones cada 5 minutos (incluso sin necesidad compatible)
* **Ubicación**: [`DonacionesScheduler.java`](file:///home/jhon/Documentos/Github/TP-DDSI/donaciones-service/src/main/java/com/donatrack/donaciones/scheduler/DonacionesScheduler.java#L67-L120) y [`application.yml`](file:///home/jhon/Documentos/Github/TP-DDSI/donaciones-service/src/main/resources/application.yml#L54-L57).
* **Qué ocurre hoy**:
  1. `ejecutarMatchmakingPeriodico()` corre automáticamente cada 5 minutos (`fixed-delay: 300000`, `initial-delay: 10000`).
  2. Dentro de `procesarMatchmakingDonaciones()`, en la línea 107 invoca directamente `donacionService.asignarDonacion(...)`, quitando las donaciones del estado `EN_DEPOSITO` sin que la Persona Administradora seleccione la entidad beneficiaria final (contradice el Requerimiento 17 de UI/UX y la pág. 12 del enunciado: *"para que una persona administradora confirme el destino final"*).
  3. Peor aún, en las líneas 94-104:
     ```java
     if (entidadIdDestino == null && entidadRepository != null) {
         List<EntidadBeneficiaria> entidadesActivas = entidadRepository.findByActivoTrue();
         // ¡Le asigna la donación a cualquier entidad aunque no tenga ninguna necesidad de esa subcategoría!
     ```
     Esto contradice directamente la regla de la Entrega 2 (pág. 10, línea 465): *"si la donación es asignada a una entidad beneficiaria quedará en estado 'Asignación realizada', **en caso contrario permanece en el depósito**"*.
* **Cómo corregirlo**:
  1. **Eliminar el bloque fallback de las líneas 94-104** de `DonacionesScheduler.java`: jamás se debe asignar una donación a una entidad que no tiene una necesidad activa de la misma subcategoría.
  2. El proceso batch de matchmaking debe **precalcular y dejar disponibles las propuestas de ranking** para que el Administrador confirme la asignación en `/admin/matchmaking` (o bien solo auto-asignar si se configura explícitamente una bandera `donatrack.matchmaking.auto-assign=false` por defecto, y únicamente cuando exista intersección en el Top 10).

### 2.2. BUG EN SEGMENTACIÓN: No separa artículos `Nuevos` de `Usados` dentro de la misma subcategoría
* **Ubicación**: [`DonacionService.java`](file:///home/jhon/Documentos/Github/TP-DDSI/donaciones-service/src/main/java/com/donatrack/donaciones/service/DonacionService.java#L74-L92).
* **Qué ocurre hoy**:
  El enunciado (pág. 7, líneas 285-288) establece que la segmentación debe separar por subcategoría, por fecha de vencimiento (en perecederos) y por estado nuevo/usado (en bienes como mobiliario o vestimenta). Actualmente el `groupingBy` en las líneas 75-76 hace:
  ```java
  Map<String, List<BienDonado>> bienesPorSubcategoriaYVencimiento = bienesRecibidos.stream()
          .collect(Collectors.groupingBy(b -> b.getSubcategoria() + "_" +
                  (b.getFechaVencimiento() != null ? b.getFechaVencimiento().toString() : "NO_VENCE")));
  ```
  Si en una misma carga se ingresan 4 sillas nuevas y 2 sillas usadas, las junta en una sola `Donacion` y marca todas como usadas (`anyMatch(BienDonado::isEsUsado)`).
* **Cómo corregirlo**:
  Incluir `b.isEsUsado()` y normalizar `subcategoria` en la clave de agrupación:
  ```java
  Map<String, List<BienDonado>> bienesSegmentados = bienesRecibidos.stream()
          .collect(Collectors.groupingBy(b ->
                  b.getSubcategoria().trim().toLowerCase() + "|" +
                  (b.getFechaVencimiento() != null ? b.getFechaVencimiento().toString() : "NO_VENCE") + "|" +
                  (b.isEsUsado() ? "USADO" : "NUEVO")
          ));
  ```

### 2.3. BUG EN MATCHMAKING SEMÁNTICO: Distancia de Levenshtein compara `subcategoria` contra `descripcion`
* **Ubicación**: [`CompatibilidadSemanticaStrategy.java`](file:///home/jhon/Documentos/Github/TP-DDSI/donaciones-service/src/main/java/com/donatrack/donaciones/matchmaking/CompatibilidadSemanticaStrategy.java#L62-L66).
* **Qué ocurre hoy**:
  El enunciado (pág. 11, líneas 511-522) define $\chi_1$ como la Distancia de Levenshtein entre el **nombre/descripción del bien** y la **descripción de la necesidad**:
  $$\chi_1 = 1 - \frac{\text{Dist Levenshtein}(\text{Nombre}, \text{Desc})}{\max(\text{Longitud}(\text{Nombre}), \text{Longitud}(\text{Desc}))}$$
  En la línea 62 se compara `donacion.getSubcategoria()` contra `necesidad.getDescripcion()`. Además, verificar en la cátedra si el polinomio lleva $\chi_1$ lineal o $\chi_1^2$ (en la página 11 del PDF aparece un `2` sobre la línea de la ecuación $S_{sem} = a_1 \cdot \chi_1^2 + a_2 \cdot \chi_2 + a_3 \cdot \chi_3$).
* **Cómo corregirlo**:
  Obtener la descripción concatenada de los bienes de la donación (o usar `subcategoria` como fallback si la lista de bienes está vacía):
  ```java
  String textoBien = (donacion.getBienes() != null && !donacion.getBienes().isEmpty())
          ? donacion.getBienes().stream()
                .map(BienDonado::getDescripcion)
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" "))
          : donacion.getSubcategoria();
  double chi1 = calcularSimilitudLevenshtein(textoBien, necesidad.getDescripcion());
  ```

### 2.4. BUG EN MAPA PÚBLICO: `DonacionMapaPublicoDto` no incluye `latitud` ni `longitud`
* **Ubicación**: [`DonacionMapaPublicoDto.java`](file:///home/jhon/Documentos/Github/TP-DDSI/donaciones-service/src/main/java/com/donatrack/donaciones/dto/DonacionMapaPublicoDto.java#L9-L19) y [`DonacionController.java`](file:///home/jhon/Documentos/Github/TP-DDSI/donaciones-service/src/main/java/com/donatrack/donaciones/controller/DonacionController.java#L113-L128).
* **Qué ocurre hoy**:
  `EntidadBeneficiaria` ya posee `latitud` y `longitud` en la base de datos (`EntidadBeneficiaria.java:24-26`), pero `DonacionMapaPublicoDto` no devuelve esos campos, impidiendo que el mapa interactivo de la Landing Page coloque los marcadores reales.
* **Cómo corregirlo**:
  Agregar `Double latitud` y `Double longitud` al record `DonacionMapaPublicoDto` y poblarlos en `DonacionController.obtenerDonacionesMapaPublico()`:
  ```java
  d.getEntidadAsignada() != null && d.getEntidadAsignada().getLatitud() != null
      ? d.getEntidadAsignada().getLatitud() : -34.6037,
  d.getEntidadAsignada() != null && d.getEntidadAsignada().getLongitud() != null
      ? d.getEntidadAsignada().getLongitud() : -58.3816
  ```

### 2.5. BUG DE ACOPLAMIENTO: Eventos falsos de Logística emitidos desde `DonacionService.actualizarEstadoDonacion`
* **Ubicación**: [`DonacionService.java`](file:///home/jhon/Documentos/Github/TP-DDSI/donaciones-service/src/main/java/com/donatrack/donaciones/service/DonacionService.java#L275-L351).
* **Qué ocurre hoy**:
  Cuando `frontend-service` llama a `PATCH /api/v1/donaciones/{id}/estado` con `usuario = "OPERADOR"`, `DonacionService` ejecuta `publicarEventoTransicionOutbox()`, que fabrica un `RutaIniciadaEvent` falso (con patente `"AE789XY"` hardcodeada) y un `EntregaConfirmadaEvent` duplicado, chocando con los eventos reales que emite `logistica-service`.
* **Cómo corregirlo**:
  Los eventos `ruta.iniciada`, `entrega.confirmada` y `entrega.fallida` son responsabilidad del **Servicio de Logística** (`LogisticaEventPublisher`). Si se mantiene un fallback en `DonacionService` para pruebas sin logística, solo debe dispararse si se pasa un flag explícito o si la donación no tiene entrega logística asociada, y usando URLs configuradas por `@Value` en vez de `http://localhost:8086/...`.

---

## 3. Cosas que FALTAN según el Enunciado (Entregas 1 a 6)

### 3.1. Reinicio periódico de Necesidades `RECURRENTE` y momento de satisfacción (Entrega 1 y 3)
* **Archivos**: [`NecesidadMaterial.java`](file:///home/jhon/Documentos/Github/TP-DDSI/donaciones-service/src/main/java/com/donatrack/donaciones/domain/NecesidadMaterial.java#L75-L96) y [`DonacionesScheduler.java`](file:///home/jhon/Documentos/Github/TP-DDSI/donaciones-service/src/main/java/com/donatrack/donaciones/scheduler/DonacionesScheduler.java).
* **Qué falta**:
  1. Hoy `NecesidadMaterial.registrarCobertura()` cierra definitivamente (`this.activa = false`) tanto las necesidades `EXTRAORDINARIA` como las `RECURRENTE` apenas se asigna una donación.
  2. Agregar en `NecesidadMaterial.java` un método `reiniciarPeriodoRecurrente()`:
     ```java
     public void reiniciarPeriodoRecurrente() {
         if (this.tipo == TipoNecesidad.RECURRENTE && this.motivoCancelacion == null) {
             this.cantidadCubierta = 0.0;
             this.activa = true;
             this.fechaCierre = null;
         }
     }
     ```
  3. En `DonacionesScheduler.java`, agregar dos tareas `@Scheduled` (una semanal `0 0 0 * * MON` para `periodo = "SEMANAL"` y otra mensual `0 0 0 1 * *` para `periodo = "MENSUAL"`) que reinicien el cupo de las necesidades recurrentes no canceladas.

### 3.2. Endpoints REST para configurar pesos $a_1, a_2, a_3$ y $\alpha$ del Matchmaking (Entrega 2 - CU-13)
* **Archivo**: [`DonacionController.java`](file:///home/jhon/Documentos/Github/TP-DDSI/donaciones-service/src/main/java/com/donatrack/donaciones/controller/DonacionController.java).
* **Qué falta**: El administrador debe poder configurar las constantes $a_1, a_2, a_3$ (verificando que $a_1 + a_2 + a_3 = 1.0$) y el factor de suavizado $\alpha$. Los métodos ya existen en `CompatibilidadSemanticaStrategy.configurarPesos(a1, a2, a3)` y `PrioridadSubatendidosStrategy.setAlpha(alpha)`, pero no están expuestos por REST.
* **Implementación guía**:
  Exponer en `DonacionController`:
  * `GET /api/v1/donaciones/matchmaking/config` -> retorna `{"a1": ..., "a2": ..., "a3": ..., "alpha": ...}`.
  * `PUT /api/v1/donaciones/matchmaking/config` -> recibe DTO con `a1, a2, a3, alpha`, invoca `semanticaStrategy.configurarPesos(a1, a2, a3)` y `subatendidosStrategy.setAlpha(alpha)`.

### 3.3. Emitir `DonanteRegistradoEvent` (Bienvenida / Credenciales CSV) y `DonacionRegistradaEvent` (Incentivos)
* **Archivos**: [`DonanteService.java`](file:///home/jhon/Documentos/Github/TP-DDSI/donaciones-service/src/main/java/com/donatrack/donaciones/service/DonanteService.java#L54-L98), [`CsvDonanteImporterService.java`](file:///home/jhon/Documentos/Github/TP-DDSI/donaciones-service/src/main/java/com/donatrack/donaciones/service/CsvDonanteImporterService.java#L174-L195) y [`DonacionService.java`](file:///home/jhon/Documentos/Github/TP-DDSI/donaciones-service/src/main/java/com/donatrack/donaciones/service/DonacionService.java#L61-L109).
* **Implementación guía**:
  1. En `DonanteService.registrarDonanteHumano()` y `registrarDonanteJuridico()`: persistir en `outbox_events` un `DonanteRegistradoEvent` con routing key `donante.registrado`.
  2. En `CsvDonanteImporterService.procesarFila()`: cuando se crea un nuevo donante (`existenteOpt.isEmpty()`), generar un `DonanteRegistradoEvent` en `outbox_events` para que `notificaciones-service` le envíe sus credenciales iniciales.
  3. En `DonacionService.registrarCargaSegmentada()`: por cada `Donacion` segmentada creada en estado `EN_DEPOSITO`, persistir en `outbox_events` un `DonacionRegistradaEvent` (routing key `donacion.registrada`) con `idDonante`, `categoria`, `subcategoria` y `cantidad` para que `incentivos-service` actualice inmediatamente las métricas y misiones de donación.

### 3.4. Endpoints CRUD faltantes (`PUT` para Donantes y Entidades) y gestión de Representantes
* **Archivos**: [`DonanteController.java`](file:///home/jhon/Documentos/Github/TP-DDSI/donaciones-service/src/main/java/com/donatrack/donaciones/controller/DonanteController.java) y [`EntidadBeneficiariaController.java`](file:///home/jhon/Documentos/Github/TP-DDSI/donaciones-service/src/main/java/com/donatrack/donaciones/controller/EntidadBeneficiariaController.java).
* **Qué falta**:
  * `PUT /api/v1/donantes/humana/{id}` y `PUT /api/v1/donantes/juridica/{id}` (incluyendo actualización de medios de contacto y medio predeterminado, y lista de representantes de la persona jurídica).
  * `PUT /api/v1/entidades/{id}` (para modificar razón social, dirección, teléfono y correos de representantes).

### 3.5. Implementar Interfaz GraphQL en `donaciones-service` (Entrega 6 - Requerimiento 5.b)
* **Requerimiento del enunciado (pág. 22)**:
  *"Opción 2: una interfaz GraphQL en el Servicio de Donaciones o en el Servicio de Logística, con el objetivo de dar soporte a filtros dinámicos y selección de campos específicos, proporcionando flexibilidad y eficiencia en las consultas."*
* **Implementación paso a paso**:
  1. Agregar en `donaciones-service/pom.xml`:
     ```xml
     <dependency>
         <groupId>org.springframework.boot</groupId>
         <artifactId>spring-boot-starter-graphql</artifactId>
     </dependency>
     ```
  2. Crear `donaciones-service/src/main/resources/graphql/schema.graphqls`:
     ```graphql
     type Query {
         donaciones(estado: String, subcategoria: String, donanteId: ID, entidadId: ID): [Donacion!]!
         donacionPorId(id: ID!): Donacion
         entidadesBeneficiarias(soloActivas: Boolean): [EntidadBeneficiaria!]!
     }

     type Donacion {
         id: ID!
         subcategoria: String!
         cantidad: Float!
         unidadMedida: String!
         esUsado: Boolean!
         fechaVencimiento: String
         estado: String!
         donante: PersonaDonante
         entidadAsignada: EntidadBeneficiaria
         bienes: [BienDonado!]
     }

     type BienDonado {
         id: ID!
         descripcion: String!
         categoria: String!
         subcategoria: String!
         cantidad: Float!
         unidadMedida: String!
     }

     type PersonaDonante {
         id: ID!
         nombreCompletoORazonSocial: String!
         emailPrincipal: String
     }

     type EntidadBeneficiaria {
         id: ID!
         razonSocial: String!
         direccion: String!
         telefono: String
         donacionesRecibidasTrimestre: Int!
     }
     ```
  3. Crear `DonacionGraphqlController.java` con `@QueryMapping` delegando en `DonacionService.filtrarDonaciones(...)`.
  4. Habilitar en `application.yml`: `spring.graphql.graphiql.enabled: true` y agregar la ruta `/graphql/**` en `api-gateway`.

---

## 4. Checklist de Finalización para `donaciones-service`
- [ ] Eliminar la auto-asignación sin necesidad compatible en `DonacionesScheduler.java:94-104` y desactivar la auto-asignación forzada cada 5 minutos.
- [ ] Corregir la clave de agrupación en `DonacionService.registrarCargaSegmentada()` para separar bienes nuevos y usados (`esUsado`).
- [ ] Corregir `CompatibilidadSemanticaStrategy` para calcular Levenshtein usando la descripción de los bienes donados.
- [ ] Añadir `latitud` y `longitud` a `DonacionMapaPublicoDto`.
- [ ] Agregar reinicio periódico de necesidades `RECURRENTE` en `NecesidadMaterial` y `DonacionesScheduler`.
- [ ] Exponer `GET/PUT /api/v1/donaciones/matchmaking/config` para configurar $a_1, a_2, a_3$ y $\alpha$.
- [ ] Emitir `DonanteRegistradoEvent` y `DonacionRegistradaEvent` vía Outbox.
- [ ] Agregar `spring-boot-starter-graphql`, `schema.graphqls` y `DonacionGraphqlController` (Entrega 6).
- [ ] Agregar `spring-boot-starter-actuator` y `micrometer-registry-prometheus` en `pom.xml` (Entrega 6).
