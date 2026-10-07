# Especificación Técnica: API Gateway y Seguridad Keycloak (`api-gateway` y `keycloak`)

El **API Gateway** actúa como la única puerta de entrada (Single Point of Entry) para todo el tráfico externo hacia el ecosistema de **DonaTrack**. En conjunto con **Keycloak**, conforma el perímetro de seguridad del sistema, centralizando la autenticación, la validación de tokens JWT, el control de acceso por roles y las políticas de enrutamiento y CORS.

---

## 1. Ficha Técnica

* **Puerto API Gateway**: `8080` (Spring Cloud Gateway reactivo basado en Project Reactor y Netty)
* **Puerto Keycloak**: `8081` (Keycloak 24.0.5 en contenedor Docker sobre PostgreSQL `keycloak_db`)
* **Protocolo de Seguridad**: OpenID Connect (OIDC) / OAuth2 con tokens firmados RSA (JWT Bearer)

---

## 2. Reglas de Enrutamiento en `api-gateway`

El Gateway intercepta las solicitudes de los clientes y las redirige hacia los microservicios correspondientes, ocultando la topología interna:

```yaml
spring:
  cloud:
    gateway:
      routes:
        # 1. Enrutamiento hacia Donaciones
        - id: donaciones-service
          uri: http://localhost:8082
          predicates:
            - Path=/api/v1/donaciones/**, /api/v1/donantes/**, /api/v1/beneficiarias/**

        # 2. Enrutamiento hacia Logística
        - id: logistica-service
          uri: http://localhost:8083
          predicates:
            - Path=/api/v1/logistica/**

        # 3. Enrutamiento hacia Incentivos
        - id: incentivos-service
          uri: http://localhost:8084
          predicates:
            - Path=/api/v1/incentivos/**
```

---

## 3. Matriz de Roles y Control de Acceso

Keycloak gestiona el Realm `donatrack` con los siguientes roles asignables a los usuarios del sistema:

| Rol | Descripción | Permisos Clave |
| :--- | :--- | :--- |
| `ROLE_ADMIN` | Personal directivo o administradores del sistema | Acceso total a todas las APIs, configuración de la cátedra, auditoría y forzado de schedulers. |
| `ROLE_DONANTE` | Personas humanas o representantes jurídicos donantes | Carga de donaciones segmentadas, consulta de perfil de puntos, historial y avance en misiones. |
| `ROLE_CHOFER` | Conductores de la flota logística | Envío de telemetría GPS, inicio/fin de ruta, confirmación de entregas y registro de fallos justificados. |
| `ROLE_BENEFICIARIA` | Representantes de comedores, ONGs u hospitales | Registro y actualización de necesidades materiales, consulta del estado de donaciones asignadas y rutas en camino. |

---

## 4. Validación de Tokens JWT (Decodificación Criptográfica)

1. El usuario inicia sesión contra Keycloak en `http://localhost:8081/realms/donatrack/protocol/openid-connect/token` y obtiene un `access_token` JWT.
2. Cada solicitud enviada al Gateway incluye la cabecera:
   ```http
   Authorization: Bearer <access_token>
   ```
3. El Gateway valida la firma criptográfica del token consultando la clave pública expuesta en el endpoint JWKS de Keycloak:
   `http://localhost:8081/realms/donatrack/protocol/openid-connect/certs`.
4. El Gateway extrae los claims del usuario (`sub`, `preferred_username`, `realm_access.roles`) y los inyecta como cabeceras internas (`X-User-Id`, `X-User-Roles`) hacia los microservicios aguas abajo.

---

## 5. Configuración de CORS y Rate Limiting

* **CORS**: Se habilita el acceso desde los orígenes del Frontend (ej. `http://localhost:3000`, `http://localhost:5173`) permitiendo los verbos `GET, POST, PUT, DELETE, OPTIONS` y las cabeceras `Authorization, Content-Type, Accept`.
* **Rate Limiting**: El Gateway puede incorporar un filtro `RequestRateLimiter` con algoritmo Token Bucket para proteger los microservicios contra ataques de denegación de servicio (DoS) o saturación indebida de endpoints de cómputo intensivo (ej. importación CSV o matchmaking).
