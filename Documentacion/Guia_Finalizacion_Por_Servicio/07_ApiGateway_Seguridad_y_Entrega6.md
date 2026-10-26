# 07. Guía de Finalización: API Gateway, Keycloak e Infraestructura (Entrega 5 y 6)

## 1. Resumen de Estado Actual
* **`api-gateway`** ([puerto `8080`](file:///home/jhon/Documentos/Github/TP-DDSI/api-gateway/src/main/resources/application.yml)): Enruta limpiamente hacia `donaciones-service` (`:8082`), `logistica-service` (`:8083`), `incentivos-service` (`:8084`) y `notificaciones-service` (`:8085`).
* **`docker/docker-compose.yml`**: Levanta PostgreSQL 16 (`5432`), MongoDB 7.0 (`27017`), RabbitMQ 3.13 (`5672`/`15672`), Mailpit (`1025`/`8025`) y Keycloak 24 (`8081`).
* **Scripts de automatización**: [`infra-up.sh`](file:///home/jhon/Documentos/Github/TP-DDSI/scripts/infra-up.sh), [`services-up.sh`](file:///home/jhon/Documentos/Github/TP-DDSI/scripts/services-up.sh), [`test-e2e.sh`](file:///home/jhon/Documentos/Github/TP-DDSI/scripts/test-e2e.sh).

---

## 2. Cosas que FALLAN o están Desconectadas

### 2.1. Keycloak levanta vacío (sin Realm `donatrack`)
* **Ubicación**: [`docker/docker-compose.yml:62-75`](file:///home/jhon/Documentos/Github/TP-DDSI/docker/docker-compose.yml#L62-L75).
* **Fallo**: Keycloak se inicia con `start-dev --db=postgres ...` pero **no tiene `--import-realm`** ni monta un archivo `realm-export.json`.
* **Solución**:
  1. Crear `docker/keycloak/realm-donatrack.json` con el realm `donatrack`, los clientes `donatrack-frontend` y `donatrack-gateway`, y los roles `ROLE_ADMIN`, `ROLE_DONANTE`, `ROLE_BENEFICIARIA`, `ROLE_CHOFER`.
  2. Modificar el comando en `docker-compose.yml`:
     ```yaml
     keycloak:
       ...
       command: start-dev --db=postgres --db-url=jdbc:postgresql://postgres:5432/keycloak_db --db-username=donatrack --db-password=donatrack_password --http-port=8081 --import-realm
       volumes:
         - ./keycloak/realm-donatrack.json:/opt/keycloak/data/import/realm.json:ro
     ```

### 2.2. `api-gateway` NO valida tokens JWT ni aplica RBAC
* **Ubicación**: [`api-gateway/pom.xml`](file:///home/jhon/Documentos/Github/TP-DDSI/api-gateway/pom.xml) y [`application.yml`](file:///home/jhon/Documentos/Github/TP-DDSI/api-gateway/src/main/resources/application.yml).
* **Fallo**: La documentación en `Documentacion/Servicios/ApiGateway_Keycloak.md` describe validación criptográfica JWKS y filtrado de roles en el Gateway, pero `api-gateway` solo tiene `spring-cloud-starter-gateway` sin `spring-boot-starter-oauth2-resource-server`.
* **Solución**:
  1. Agregar en `api-gateway/pom.xml`:
     ```xml
     <dependency>
         <groupId>org.springframework.boot</groupId>
         <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
     </dependency>
     <dependency>
         <groupId>org.springframework.boot</groupId>
         <artifactId>spring-boot-starter-security</artifactId>
     </dependency>
     ```
  2. Configurar en `api-gateway/src/main/resources/application.yml`:
     ```yaml
     spring:
       security:
         oauth2:
           resourceserver:
             jwt:
               jwk-set-uri: ${KEYCLOAK_JWKS_URI:http://localhost:8081/realms/donatrack/protocol/openid-connect/certs}
     ```
  3. Configurar un `SecurityWebFilterChain` reactivo que permita endpoints públicos (saludo, mapa público, callback de logística) y exija Bearer token para el resto, inyectando cabeceras `X-User-Id` y `X-User-Roles`.

### 2.3. No existe Rate Limiting en el Gateway (Entrega 6 - Requerimiento 4)
* **Requerimiento del enunciado (pág. 22)**:
  *"4. Se deberá implementar un sistema de limitación de solicitudes (Rate Limiting) que regule el tráfico entrante, protegiendo los recursos del sistema frente a abusos o sobrecarga."*
* **Solución**:
  Configurar en `api-gateway` el filtro `RequestRateLimiter` (con `RedisRateLimiter` o filtro en memoria Bucket4j) para limitar a 10 req/s en endpoints intensivos como `/api/v1/donaciones/importar-csv` o `/api/v1/donaciones/*/matchmaking`.

---

## 3. Requerimientos de Entrega 6 (Despliegue y Observabilidad)

1. **Observabilidad: Actuator + Prometheus + Grafana**:
   - Agregar en el POM padre o en cada servicio:
     ```xml
     <dependency>
         <groupId>org.springframework.boot</groupId>
         <artifactId>spring-boot-starter-actuator</artifactId>
     </dependency>
     <dependency>
         <groupId>io.micrometer</groupId>
         <artifactId>micrometer-registry-prometheus</artifactId>
     </dependency>
     ```
   - Exponer en `application.yml`:
     ```yaml
     management:
       endpoints:
         web:
           exposure:
             include: health,info,metrics,prometheus
     ```
   - Agregar contenedores de Prometheus y Grafana en `docker/docker-compose.yml` para monitorear el consumo de CPU, heap JVM y tiempos de respuesta de cada microservicio.
2. **Dockerfiles y Despliegue en la Nube**:
   - Crear un `Dockerfile` multi-stage ligero para cada microservicio:
     ```dockerfile
     FROM eclipse-temurin:21-jre-alpine
     WORKDIR /app
     COPY target/*.jar app.jar
     ENTRYPOINT ["java", "-Xms64m", "-Xmx256m", "-jar", "app.jar"]
     ```
   - Añadir un perfil `docker-compose.full.yml` que pueda levantar todos los servicios juntos para un despliegue local o en VM cloud (AWS/GCP/Render).

---

## 4. Checklist de Finalización para Infraestructura y Gateway
- [ ] Exportar y montar el realm `donatrack` en `docker/keycloak/realm-donatrack.json`.
- [ ] Incorporar Spring Security Reactive + OAuth2 Resource Server en `api-gateway`.
- [ ] Implementar filtro de Rate Limiting en `api-gateway`.
- [ ] Incorporar `spring-boot-starter-actuator` y `micrometer-registry-prometheus` en todos los servicios.
- [ ] Crear los `Dockerfile`s para cada módulo.
