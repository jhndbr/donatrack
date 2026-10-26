# 01. Guía de Finalización: Módulo `common-dtos` (Contratos y Eventos Compartidos)

## 1. Propósito del Módulo
El módulo [`common-dtos`](file:///home/jhon/Documentos/Github/TP-DDSI/common-dtos/pom.xml) define el lenguaje publicado (*Published Language*) compartido entre los microservicios de **DonaTrack**: enumeraciones de dominio y contratos de eventos asincrónicos serializados en JSON a través del Topic Exchange `donatrack.events.exchange` de RabbitMQ.

> [!IMPORTANT]
> Cualquier cambio en `common-dtos` requiere ejecutar `./mvnw -pl common-dtos clean install` antes de compilar o levantar el resto de los microservicios.

---

## 2. Qué está implementado y funcionando bien

* **Enumeraciones oficiales del dominio**:
  * [`EstadoDonacion.java`](file:///home/jhon/Documentos/Github/TP-DDSI/common-dtos/src/main/java/com/donatrack/common/enums/EstadoDonacion.java#L6-L14): Define los 7 estados oficiales del diagrama de estados (Entrega 2, pág. 11): `EN_DEPOSITO`, `ASIGNACION_REALIZADA`, `LISTA_PARA_ENTREGAR`, `EN_TRASLADO`, `ENTREGADA`, `ENTREGA_FALLIDA`, `VENCIDA`.
  * [`TipoNecesidad.java`](file:///home/jhon/Documentos/Github/TP-DDSI/common-dtos/src/main/java/com/donatrack/common/enums/TipoNecesidad.java#L3-L6): Define `EXTRAORDINARIA` y `RECURRENTE`.
  * [`CanalNotificacion.java`](file:///home/jhon/Documentos/Github/TP-DDSI/common-dtos/src/main/java/com/donatrack/common/enums/CanalNotificacion.java#L3-L7): Define `EMAIL`, `SMS`, `WHATSAPP`.
  * [`CategoriaDonante.java`](file:///home/jhon/Documentos/Github/TP-DDSI/common-dtos/src/main/java/com/donatrack/common/enums/CategoriaDonante.java#L3-L17): Define el escalafón oficial `COLABORADOR(1)`, `SOSTENEDOR(2)` y `TRANSFORMADOR(3)`.
* **Eventos AMQP base**:
  * [`DonacionAsignadaEvent.java`](file:///home/jhon/Documentos/Github/TP-DDSI/common-dtos/src/main/java/com/donatrack/common/events/DonacionAsignadaEvent.java), [`DonanteInactivoEvent.java`](file:///home/jhon/Documentos/Github/TP-DDSI/common-dtos/src/main/java/com/donatrack/common/events/DonanteInactivoEvent.java), [`RutaIniciadaEvent.java`](file:///home/jhon/Documentos/Github/TP-DDSI/common-dtos/src/main/java/com/donatrack/common/events/RutaIniciadaEvent.java), [`EntregaConfirmadaEvent.java`](file:///home/jhon/Documentos/Github/TP-DDSI/common-dtos/src/main/java/com/donatrack/common/events/EntregaConfirmadaEvent.java), [`EntregaFallidaEvent.java`](file:///home/jhon/Documentos/Github/TP-DDSI/common-dtos/src/main/java/com/donatrack/common/events/EntregaFallidaEvent.java), [`MisionCumplidaEvent.java`](file:///home/jhon/Documentos/Github/TP-DDSI/common-dtos/src/main/java/com/donatrack/common/events/MisionCumplidaEvent.java), [`CategoriaAscendidaEvent.java`](file:///home/jhon/Documentos/Github/TP-DDSI/common-dtos/src/main/java/com/donatrack/common/events/CategoriaAscendidaEvent.java).

---

## 3. Qué FALLA en los contratos actuales (Bugs de Integración Cross-Service)

### 3.1. `EntregaConfirmadaEvent` NO incluye `idDonante`
* **Archivo**: [`EntregaConfirmadaEvent.java`](file:///home/jhon/Documentos/Github/TP-DDSI/common-dtos/src/main/java/com/donatrack/common/events/EntregaConfirmadaEvent.java#L6-L15).
* **Problema**: Cuando `logistica-service` confirma una entrega y emite `EntregaConfirmadaEvent`, solo envía `emailDonante` (sin `idDonante`). En `incentivos-service`, [`DonacionEventConsumer.java`](file:///home/jhon/Documentos/Github/TP-DDSI/incentivos-service/src/main/java/com/donatrack/incentivos/consumer/DonacionEventConsumer.java#L79-L80) intenta buscar el perfil por email (`findByEmail`). Si el donante no tenía el mismo email sincronizado o su perfil aún no existía, la entrega exitosa se pierde y la misión `DONACIONES_EXITOSAS` nunca avanza.
* **Solución**: Agregar `private Long idDonante;` en `EntregaConfirmadaEvent` y poblarlo desde [`Entrega.getIdDonante()`](file:///home/jhon/Documentos/Github/TP-DDSI/logistica-service/src/main/java/com/donatrack/logistica/domain/Entrega.java#L34) en `logistica-service`.

### 3.2. Ningún evento transporta el `CanalNotificacion` preferido ni el contacto alternativo (Teléfono/WhatsApp)
* **Archivos**: Todos los eventos en `common-dtos/src/main/java/com/donatrack/common/events/`.
* **Problema**: El enunciado (Entrega 1, pág. 6) exige que cada donante pueda elegir su **medio de contacto predeterminado** (`EMAIL`, `SMS` o `WHATSAPP`) para recibir notificaciones. Aunque `PersonaDonante` en `donaciones-service` sí modela `getMedioContactoPredeterminado()`, los eventos solo incluyen `String emailDonante`. Como resultado, `notificaciones-service` hardcodea `CanalNotificacion.EMAIL` en todas las notificaciones.
* **Solución**: Incorporar en los eventos dirigidos al donante (`DonacionAsignadaEvent`, `DonanteInactivoEvent`, `MisionCumplidaEvent`, `CategoriaAscendidaEvent`, `EntregaConfirmadaEvent`, `EntregaFallidaEvent`):
  * `private CanalNotificacion canalPreferidoDonante = CanalNotificacion.EMAIL;`
  * `private String contactoPreferidoDonante;` (que contendrá el email o el número de teléfono/WhatsApp según `getMedioContactoPredeterminado()`).

### 3.3. `DonacionAsignadaEvent` no incluye la `categoria` general del bien
* **Archivo**: [`DonacionAsignadaEvent.java`](file:///home/jhon/Documentos/Github/TP-DDSI/common-dtos/src/main/java/com/donatrack/common/events/DonacionAsignadaEvent.java#L6-L17).
* **Problema**: El enunciado (Entrega 2, pág. 13) define la misión **Completitud** como *"realizar donaciones de X categorías distintas"*. Actualmente el evento solo envía `subcategoria`.
* **Solución**: Añadir `private String categoria;` en `DonacionAsignadaEvent` (y en el nuevo `DonacionRegistradaEvent`).

---

## 4. Qué FALTA agregar en `common-dtos`

### 4.1. Crear `DonanteRegistradoEvent` (Bienvenida y Credenciales Iniciales — Entrega 1)
* **Requerimiento del enunciado**:
  * Pág. 7 (Alta en depósito): *"se les enviará un correo electrónico de bienvenida para que accedan por primera vez"*.
  * Pág. 8 (Importación CSV): *"en caso contrario, se lo deberá crear el usuario y enviarle sus credenciales de acceso"*.
* **Implementación guía**:
  Crear `common-dtos/src/main/java/com/donatrack/common/events/DonanteRegistradoEvent.java`:
  ```java
  package com.donatrack.common.events;

  import com.donatrack.common.enums.CanalNotificacion;
  import java.io.Serializable;
  import java.time.LocalDateTime;

  public class DonanteRegistradoEvent implements Serializable {
      private Long idDonante;
      private String nombreDonante;
      private String emailDonante;
      private CanalNotificacion canalPreferido;
      private String contactoPreferido;
      private String usernameGenerado;
      private String passwordTemporal; // null si se registró por su cuenta, poblado si fue alta en depósito o CSV
      private String origenRegistro;   // "AUTOGESTION", "DEPOSITO_ADMIN", "IMPORTACION_CSV"
      private LocalDateTime timestamp;
      // Constructores, getters y setters
  }
  ```

### 4.2. Crear `DonacionRegistradaEvent` (Impacto en Incentivos al ingresar al depósito — Entrega 2)
* **Requerimiento del enunciado**:
  * Pág. 13: *"se solicita realizar la integración entre el Servicio de Donaciones y el Servicio de Incentivos, de modo que las donaciones impacten en el cálculo de progreso y permitan marcar como completadas aquellas misiones cuya condición dependa de la actividad de donación"*.
* **Por qué es necesario**: Hoy `incentivos-service` solo se entera de una donación cuando pasa a `ASIGNACION_REALIZADA`. Si el donante deja bienes en el depósito (`EN_DEPOSITO`), su racha mensual, completitud de categorías y cantidad de bienes (*Hábil Donador*) deben acreditarse al registrarse la recepción en el depósito.
* **Implementación guía**:
  Crear `common-dtos/src/main/java/com/donatrack/common/events/DonacionRegistradaEvent.java`:
  ```java
  package com.donatrack.common.events;

  import com.donatrack.common.enums.CanalNotificacion;
  import java.io.Serializable;
  import java.time.LocalDateTime;

  public class DonacionRegistradaEvent implements Serializable {
      private Long idDonacion;
      private Long idDonante;
      private String nombreDonante;
      private String emailDonante;
      private CanalNotificacion canalPreferido;
      private String contactoPreferido;
      private String categoria;
      private String subcategoria;
      private double cantidad;
      private String unidadMedida;
      private LocalDateTime timestamp;
      // Constructores, getters y setters
  }
  ```

---

## 5. Checklist de Finalización para `common-dtos`
- [ ] Agregar `idDonante` a `EntregaConfirmadaEvent`.
- [ ] Agregar `canalPreferidoDonante` y `contactoPreferidoDonante` a los eventos de notificación.
- [ ] Agregar `categoria` a `DonacionAsignadaEvent`.
- [ ] Crear `DonanteRegistradoEvent` (routing key `donante.registrado`).
- [ ] Crear `DonacionRegistradaEvent` (routing key `donacion.registrada`).
- [ ] Ejecutar `./mvnw -pl common-dtos clean install` y verificar que compile sin errores.
