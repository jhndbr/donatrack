# Scripts de DonaTrack para Windows (PowerShell)

Todos los scripts están escritos en **PowerShell (`.ps1`)** nativo y utilizan las herramientas oficiales (`mvnw.cmd`, `docker compose`, `winget`).
Están ubicados en: `scripts/windows/`

---

### 1. Gestión de Dependencias del Sistema
> **Nota:** Estos dos scripts deben ejecutarse abriendo **PowerShell como Administrador**.

- **`.\scripts\windows\instalar-dependencias.ps1`**:
  Instala automáticamente mediante `winget`:
  - **Git**
  - **Eclipse Temurin OpenJDK 21**
  - **Docker Desktop**
- **`.\scripts\windows\desinstalar-dependencias.ps1`**:
  Pregunta de forma interactiva si deseas desinstalar Docker Desktop y/o Java 21, removiéndolos limpiamente.

---

### 2. Ciclo de Vida del Proyecto

Puedes ejecutarlos desde la raíz del proyecto o dentro de `scripts/windows/`:

- **Compilar microservicios:**
  ```powershell
  .\scripts\windows\build.ps1
  ```
- **Levantar solo infraestructura (PostgreSQL, MongoDB, RabbitMQ, Mailpit, Keycloak):**
  ```powershell
  .\scripts\windows\infra-up.ps1
  ```
- **Detener infraestructura:**
  ```powershell
  .\scripts\windows\infra-down.ps1
  ```
- **Limpiar infraestructura (borra bases de datos y volúmenes para reiniciar de cero):**
  ```powershell
  .\scripts\windows\infra-clean.ps1
  ```
- **Levantar microservicios localmente (JARs en segundo plano con logs en `logs\`):**
  ```powershell
  .\scripts\windows\services-up.ps1
  ```
- **Detener microservicios locales:**
  ```powershell
  .\scripts\windows\services-down.ps1
  ```
- **Despliegue integral en Docker (Infraestructura + Microservicios en contenedores):**
  ```powershell
  .\scripts\windows\docker-all.ps1
  ```
