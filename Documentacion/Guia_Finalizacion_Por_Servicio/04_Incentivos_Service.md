# 04. Guía de Finalización: Servicio de Incentivos (`incentivos-service`)

## 1. Resumen de Estado Actual
El módulo [`incentivos-service`](file:///home/jhon/Documentos/Github/TP-DDSI/incentivos-service/pom.xml) (puerto `8084`, PostgreSQL `incentivos_db`) implementa:
* **Modelo de Dominio de Ludificación**: [`PerfilDonanteIncentivo.java`](file:///home/jhon/Documentos/Github/TP-DDSI/incentivos-service/src/main/java/com/donatrack/incentivos/domain/PerfilDonanteIncentivo.java), [`Mision.java`](file:///home/jhon/Documentos/Github/TP-DDSI/incentivos-service/src/main/java/com/donatrack/incentivos/domain/Mision.java), [`ProgresoMision.java`](file:///home/jhon/Documentos/Github/TP-DDSI/incentivos-service/src/main/java/com/donatrack/incentivos/domain/ProgresoMision.java), [`Insignia.java`](file:///home/jhon/Documentos/Github/TP-DDSI/incentivos-service/src/main/java/com/donatrack/incentivos/domain/Insignia.java) y [`RankingMensual.java`](file:///home/jhon/Documentos/Github/TP-DDSI/incentivos-service/src/main/java/com/donatrack/incentivos/domain/RankingMensual.java).
* **Escalafón Oficial**: Categorías `COLABORADOR` $\rightarrow$ `SOSTENEDOR` $\rightarrow$ `TRANSFORMADOR` con 7 misiones precargadas en [`IncentivosDataInitializer.java`](file:///home/jhon/Documentos/Github/TP-DDSI/incentivos-service/src/main/java/com/donatrack/incentivos/config/IncentivosDataInitializer.java).
* **Idempotencia de Eventos**: Deduplicación con [`RegistroEventoProcesado.java`](file:///home/jhon/Documentos/Github/TP-DDSI/incentivos-service/src/main/java/com/donatrack/incentivos/domain/RegistroEventoProcesado.java).

---

## 2. Cosas que FALLAN (Bugs Críticos a Corregir)

### 2.1. BUG EN RANKING MENSUAL: Cuenta todas las misiones históricas en vez de filtrar por el mes/año evaluado y duplica filas
* **Ubicación**: [`IncentivosScheduler.java`](file:///home/jhon/Documentos/Github/TP-DDSI/incentivos-service/src/main/java/com/donatrack/incentivos/scheduler/IncentivosScheduler.java#L48-L80).
* **Qué ocurre hoy**:
  1. El enunciado (Entrega 2, pág. 14, líneas 657-660) dice: *"un flujo programado ejecutará un proceso de ranking mensual de donantes más activos, calculado en función de la cantidad de misiones cumplidas **durante ese período** (sin importar la categoría)"*.
  2. En `IncentivosScheduler.java:58`, el código hace:
     ```java
     int misionesCompletadas = progresoRepository.findByIdDonanteAndCompletadaTrue(p.getIdDonante()).size();
     ```
     ¡Esto cuenta **todas las misiones completadas en toda la historia** del donante sin filtrar por `anio` y `mes` de `fechaCompletada`!
  3. Además, si se ejecuta `forzar-calculo` dos veces para el mismo `(anio, mes)`, inserta filas duplicadas en `rankings_mensuales` porque no borra el cálculo previo de ese período.
* **Cómo corregirlo**:
  En `IncentivosScheduler.calcularRankingParaMes(int anio, int mes)`:
  ```java
  // 1. Limpiar cálculo previo del mismo período para evitar duplicados
  List<RankingMensual> previos = rankingRepository.findByAnioAndMesOrderByPosicionAsc(anio, mes);
  if (!previos.isEmpty()) {
      rankingRepository.deleteAll(previos);
  }

  // 2. Contar misiones completadas en el año y mes indicados (con fallback al total si es el mes actual en demo)
  int misionesCompletadas = (int) progresoRepository.findByIdDonanteAndCompletadaTrue(p.getIdDonante())
          .stream()
          .filter(prog -> prog.getFechaCompletada() != null
                  && prog.getFechaCompletada().getYear() == anio
                  && prog.getFechaCompletada().getMonthValue() == mes)
          .count();
  ```

### 2.2. BUG EN MISIÓN `HABIL_DONADOR`: Acumula donaciones chicas en lugar de verificar que una donación supere $X$ cantidad
* **Ubicación**: [`IncentivosService.java`](file:///home/jhon/Documentos/Github/TP-DDSI/incentivos-service/src/main/java/com/donatrack/incentivos/service/IncentivosService.java#L199-L207).
* **Qué ocurre hoy**:
  El enunciado (pág. 13, línea 623) define: *"Hábil Donador: donación que supere X cantidad de bienes"*.
  En las líneas 199-205, si el objetivo es `10` bienes y el donante dona `3`, el bloque `else` hace `prog.agregarProgreso(3)`, permitiendo completar la misión con varias donaciones pequeñas.
* **Cómo corregirlo**:
  Para `HABIL_DONADOR`, verificar si `cantidadBienes >= mision.getObjetivo()` (o guardar `Math.max(prog.getProgresoActual(), cantidadBienes)` como mejor marca alcanzada sin sumar donaciones chicas entre sí).

### 2.3. BUG EN MISIÓN `RACHA` DE CATEGORÍA `SOSTENEDOR`: Bloquea el avance durante meses
* **Ubicación**: [`IncentivosDataInitializer.java`](file:///home/jhon/Documentos/Github/TP-DDSI/incentivos-service/src/main/java/com/donatrack/incentivos/config/IncentivosDataInitializer.java#L60-L67) e [`IncentivosService.java`](file:///home/jhon/Documentos/Github/TP-DDSI/incentivos-service/src/main/java/com/donatrack/incentivos/service/IncentivosService.java#L182-L190).
* **Qué ocurre hoy**:
  En `SOSTENEDOR`, la Misión 1 (`ordenSecuencia = 1`) tiene `objetivo = 3.0` y tipo `RACHA`. Si un donante completa `COLABORADOR` en su primer mes, su `rachaMesesConsecutivos` es `1`. Como `actualizarRachaMensual` solo incrementa cuando cambia el `YearMonth`, el donante queda trabado 2 meses calendario sin poder desbloquear las misiones 2 y 3 de `SOSTENEDOR`.
* **Cómo corregirlo**:
  En `IncentivosDataInitializer.java`, colocar la misión `RACHA` al final de la secuencia de la categoría (o evaluar en `"Racha Activa"` la cantidad de aportes realizados si se está en el mismo mes), permitiendo el flujo demostrativo continuo durante la defensa del TP.

### 2.4. BUG EN `DonacionEventConsumer`: `EntregaConfirmadaEvent` busca solo por email
* **Ubicación**: [`DonacionEventConsumer.java`](file:///home/jhon/Documentos/Github/TP-DDSI/incentivos-service/src/main/java/com/donatrack/incentivos/consumer/DonacionEventConsumer.java#L68-L86) e [`IncentivosService.java`](file:///home/jhon/Documentos/Github/TP-DDSI/incentivos-service/src/main/java/com/donatrack/incentivos/service/IncentivosService.java#L260-L265).
* **Cómo corregirlo**:
  Una vez agregado `idDonante` a `EntregaConfirmadaEvent` (ver documento `01_Common_DTOs_y_Eventos.md`), en `DonacionEventConsumer.procesarEntregaConfirmada()` usar `evento.getIdDonante()` si no es nulo (`incentivosService.registrarEntregaExitosa(evento.getIdDonante())`), dejando la búsqueda por email solo como fallback.

### 2.5. BUG EN DIFUSIÓN EN REDES SOCIALES: Endpoint `/publicar-red-social` es un mock desconectado y `RestTemplate` no tiene timeout
* **Ubicación**: [`IncentivosController.java`](file:///home/jhon/Documentos/Github/TP-DDSI/incentivos-service/src/main/java/com/donatrack/incentivos/controller/IncentivosController.java#L131-L146) e [`IncentivosService.java`](file:///home/jhon/Documentos/Github/TP-DDSI/incentivos-service/src/main/java/com/donatrack/incentivos/service/IncentivosService.java#L100).
* **Cómo corregirlo**:
  1. Configurar timeouts de 3 segundos en el `RestTemplate` de `IncentivosService` (`SimpleClientHttpRequestFactory`).
  2. Hacer que `POST /api/v1/incentivos/insignias/{id}/publicar-red-social` busque la `Insignia` real en base de datos e invoque a `incentivosService.dispararDifusionSocialManual(id)`.

---

## 3. Cosas que FALTAN según el Enunciado

### 3.1. Escuchar `DonacionRegistradaEvent` al ingresar donación en depósito (Entrega 2)
* **Archivos**: [`RabbitMqIncentivosConfig.java`](file:///home/jhon/Documentos/Github/TP-DDSI/incentivos-service/src/main/java/com/donatrack/incentivos/config/RabbitMqIncentivosConfig.java) y [`DonacionEventConsumer.java`](file:///home/jhon/Documentos/Github/TP-DDSI/incentivos-service/src/main/java/com/donatrack/incentivos/consumer/DonacionEventConsumer.java).
* **Qué hacer**:
  Declarar la cola `q.incentivos.donaciones.registradas` bindeada a `donacion.registrada` para que apenas un donante ingrese una donación en el depósito se evalúe su progreso en `IncentivosService` (evitando duplicar el conteo cuando esa misma donación pase luego a `ASIGNACION_REALIZADA`).

### 3.2. Job Programado de Pérdida de Racha por Inactividad Mensual (Entrega 2 - CU-19)
* **Requerimiento del enunciado (pág. 13, líneas 628-630)**:
  *"Se deberá contemplar que ciertas misiones, como 'Racha', pueden perder el progreso acumulado ante determinadas condiciones (por ejemplo, si no se realiza ninguna donación durante un mes completo)."*
* **Qué falta**:
  El método [`ProgresoMision.resetearProgreso()`](file:///home/jhon/Documentos/Github/TP-DDSI/incentivos-service/src/main/java/com/donatrack/incentivos/domain/ProgresoMision.java#L49-L53) ya existe pero nunca se invoca.
* **Implementación guía**:
  En [`IncentivosScheduler.java`](file:///home/jhon/Documentos/Github/TP-DDSI/incentivos-service/src/main/java/com/donatrack/incentivos/scheduler/IncentivosScheduler.java), agregar un método programado `@Scheduled(cron = "0 30 0 1 * ?")` (primer día de cada mes) que:
  1. Busque todos los `PerfilDonanteIncentivo` cuyo `ultimoMesDonacion` sea anterior al mes recién cerrado (`YearMonth.now().minusMonths(1)`).
  2. Ponga su `rachaMesesConsecutivos = 0`.
  3. Busque sus `ProgresoMision` no completados de tipo `TipoMision.RACHA` e invoque `prog.resetearProgreso()`.

### 3.3. Serie Histórica de Evolución por Período e Historial de Rankings
* **Requerimiento del enunciado (pág. 13, líneas 601-603 y pág. 5, Req. 19)**:
  * *"gráficos de evolución por período, comparaciones mensuales y el número total de organizaciones que ha ayudado"*
  * *"visualizar el ranking mensual de donantes más activos y el historial de rankings anteriores"*
* **Implementación guía**:
  1. En `IncentivosService.obtenerAnaliticaDonante(idDonante)`, agregar al mapa de respuesta un objeto `evolucionPorPeriodo` (por ejemplo, mapa de los últimos 6 meses `YYYY-MM` con cantidad de actividades/misiones de ese donante) para que `frontend-service` pueda renderizar un gráfico de barras/líneas en `/donante/incentivos`.
  2. En `IncentivosController`, agregar `GET /api/v1/incentivos/rankings/historico` que devuelva los rankings agrupados por `(anio, mes)` disponibles en `RankingMensualRepository`.

---

## 4. Checklist de Finalización para `incentivos-service`
- [ ] Corregir `IncentivosScheduler.calcularRankingParaMes` para filtrar misiones por año/mes y eliminar registros previos del mismo período antes de recalcular.
- [ ] Agregar job `@Scheduled` en `IncentivosScheduler` para pérdida de progreso en misiones `RACHA` ante mes sin donar.
- [ ] Corregir la evaluación de `HABIL_DONADOR` y el orden de misiones en `SOSTENEDOR`.
- [ ] Consumir `idDonante` en `EntregaConfirmadaEvent` y escuchar `DonacionRegistradaEvent`.
- [ ] Conectar `POST /api/v1/incentivos/insignias/{id}/publicar-red-social` con el método de envío a n8n con timeout configurado.
- [ ] Agregar `GET /api/v1/incentivos/rankings/historico` y serie `evolucionPorPeriodo` en `/analitica`.
- [ ] Agregar `spring-boot-starter-actuator` en `pom.xml`.
