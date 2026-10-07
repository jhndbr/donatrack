# Guía de Configuración: Keycloak Identity Provider (`keycloak`)

Este documento detalla el aprovisionamiento, configuración del Realm, creación de clientes OIDC, definición de roles y usuarios de prueba en **Keycloak 24** para el ecosistema **DonaTrack**.

---

## 1. Acceso a la Instancia Local de Keycloak

El contenedor Docker de Keycloak corre en:
* **Consola de Administración**: [http://localhost:8081](http://localhost:8081)
* **Credenciales Maestras**:
  - **Usuario**: `admin`
  - **Contraseña**: `admin`
* **Base de Datos de Respaldo**: PostgreSQL en puerto `5432` (`keycloak_db`).

---

## 2. Configuración del Realm `donatrack`

1. Iniciar sesión en la consola de Keycloak.
2. En el selector superior izquierdo, hacer clic en el menú desplegable y seleccionar **"Create Realm"**.
3. Ingresar:
   - **Realm name**: `donatrack`
   - **Enabled**: `ON`
4. Hacer clic en **"Create"**.

---

## 3. Configuración del Cliente OIDC (`donatrack-client`)

Dentro del realm `donatrack`:
1. Ir a **Clients** -> **Create client**.
2. **Paso 1: General Settings**:
   - **Client type**: `OpenID Connect`
   - **Client ID**: `donatrack-client`
   - **Name**: `DonaTrack API & Frontend Client`
3. **Paso 2: Capability config**:
   - **Client authentication**: `Off` (Cliente público para SPA / Mobile) o `On` si se usa Client Secret.
   - **Standard flow**: Marcado (para login interactivo con redirección).
   - **Direct access grants**: Marcado (para autenticación directa con usuario y contraseña vía API / cURL).
4. **Paso 3: Login settings**:
   - **Valid redirect URIs**: `http://localhost:3000/*`, `http://localhost:8080/*`
   - **Valid post logout redirect URIs**: `http://localhost:3000/*`
   - **Web origins**: `*` (o `http://localhost:3000`)
5. Hacer clic en **"Save"**.

---

## 4. Definición de Roles del Sistema (Realm Roles)

Ir a **Realm Roles** -> **Create role** y crear los siguientes roles:

| Nombre del Rol | Descripción |
| :--- | :--- |
| `ROLE_ADMIN` | Administrador general de DonaTrack |
| `ROLE_DONANTE` | Donante individual o empresa |
| `ROLE_CHOFER` | Chofer y operador de flota logística |
| `ROLE_BENEFICIARIA` | Representante de entidad comunitaria o comedor |

---

## 5. Usuarios de Prueba Preconfigurados

Ir a **Users** -> **Add user** para dar de alta los perfiles de prueba:

| Usuario (`username`) | Email | Contraseña Temporal: NO | Roles Asignados |
| :--- | :--- | :--- | :--- |
| `admin` | `admin@donatrack.org` | `admin123` | `ROLE_ADMIN` |
| `donante_juan` | `juan.donante@example.com` | `donante123` | `ROLE_DONANTE` |
| `chofer_carlos`| `carlos.chofer@example.com` | `chofer123` | `ROLE_CHOFER` |
| `comedor_esperanza`| `esperanza@comedor.org` | `comedor123` | `ROLE_BENEFICIARIA` |

*Para fijar la contraseña*: Tras crear el usuario, ir a la pestaña **Credentials**, hacer clic en **Set password**, desmarcar **Temporary** y confirmar.

---

## 6. Obtención y Verificación de Tokens JWT con cURL

### 6.1. Obtener Token Bearer (Direct Access Grant)
```bash
curl -X POST "http://localhost:8081/realms/donatrack/protocol/openid-connect/token" \
     -H "Content-Type: application/x-www-form-urlencoded" \
     -d "client_id=donatrack-client" \
     -d "grant_type=password" \
     -d "username=donante_juan" \
     -d "password=donante123"
```

Respuesta esperada:
```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIi...",
  "expires_in": 300,
  "refresh_expires_in": 1800,
  "token_type": "Bearer",
  "not-before-policy": 0,
  "scope": "openid profile email"
}
```

### 6.2. Consumir una API del Gateway utilizando el Token
```bash
TOKEN="<access_token_obtenido>"

curl -X GET "http://localhost:8080/api/v1/donaciones" \
     -H "Authorization: Bearer $TOKEN"
```
