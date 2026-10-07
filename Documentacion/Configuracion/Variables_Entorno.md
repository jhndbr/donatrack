# Matriz de Variables de Entorno y Configuración - DonaTrack

Este documento consolida las variables de entorno utilizadas en el despliegue de **Docker Compose** y en los archivos de configuración `application.yml` de cada microservicio en sus distintos perfiles (`default`, `docker`, `prod`).

---

## 1. Variables de Infraestructura Docker (`docker-compose.yml`)

| Variable | Valor por Defecto Local | Descripción |
| :--- | :--- | :--- |
| `POSTGRES_USER` | `donatrack` | Usuario maestro de PostgreSQL |
| `POSTGRES_PASSWORD` | `donatrack_password` | Contraseña maestra de PostgreSQL |
| `POSTGRES_DB` | `postgres` | Base de datos por defecto para el bootstrap |
| `RABBITMQ_DEFAULT_USER` | `donatrack` | Usuario administrador de RabbitMQ |
| `RABBITMQ_DEFAULT_PASS` | `donatrack_password` | Contraseña de RabbitMQ |
| `KEYCLOAK_ADMIN` | `admin` | Usuario administrador maestro de Keycloak |
| `KEYCLOAK_ADMIN_PASSWORD` | `admin` | Contraseña de Keycloak |
| `KC_DB` | `postgres` | Driver de base de datos de Keycloak |
| `KC_DB_URL` | `jdbc:postgresql://postgres:5432/keycloak_db` | Cadena de conexión para Keycloak |
| `KC_DB_USERNAME` | `donatrack` | Usuario de BD para Keycloak |
| `KC_DB_PASSWORD` | `donatrack_password` | Contraseña de BD para Keycloak |
| `KC_HTTP_PORT` | `8081` | Puerto HTTP expuesto por Keycloak |

---

## 2. Variables de Configuración por Microservicio

### 2.1. `donaciones-service` (:8082)
| Propiedad / Variable | Valor Local (`default`) | Perfil Docker (`docker`) |
| :--- | :--- | :--- |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/donaciones_db` | `jdbc:postgresql://postgres:5432/donaciones_db` |
| `SPRING_DATASOURCE_USERNAME` | `donatrack` | `donatrack` |
| `SPRING_DATASOURCE_PASSWORD` | `donatrack_password` | `donatrack_password` |
| `SPRING_RABBITMQ_HOST` | `localhost` | `rabbitmq` |
| `SPRING_RABBITMQ_PORT` | `5672` | `5672` |
| `SPRING_RABBITMQ_USERNAME` | `donatrack` | `donatrack` |
| `SPRING_RABBITMQ_PASSWORD` | `donatrack_password` | `donatrack_password` |

### 2.2. `logistica-service` (:8083)
| Propiedad / Variable | Valor Local (`default`) | Perfil Docker (`docker`) |
| :--- | :--- | :--- |
| `SPRING_DATA_MONGODB_URI` | `mongodb://localhost:27017/logistica_db` | `mongodb://mongo:27017/logistica_db` |
| `SPRING_RABBITMQ_HOST` | `localhost` | `rabbitmq` |
| `DONACIONES_SERVICE_URL` | `http://localhost:8082` | `http://donaciones-service:8082` |
| `ROUTING_OPTIMIZER_URL` | `http://localhost:9000/api/optimize` | `http://route-optimizer:9000/api/optimize` |

### 2.3. `incentivos-service` (:8084)
| Propiedad / Variable | Valor Local (`default`) | Perfil Docker (`docker`) |
| :--- | :--- | :--- |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/incentivos_db` | `jdbc:postgresql://postgres:5432/incentivos_db` |
| `SPRING_DATASOURCE_USERNAME` | `donatrack` | `donatrack` |
| `SPRING_DATASOURCE_PASSWORD` | `donatrack_password` | `donatrack_password` |
| `SPRING_RABBITMQ_HOST` | `localhost` | `rabbitmq` |
| `N8N_WEBHOOK_URL` | `http://localhost:5678/webhook/difusion` | `http://n8n:5678/webhook/difusion` |

### 2.4. `notificaciones-service` (:8085)
| Propiedad / Variable | Valor Local (`default`) | Perfil Docker (`docker`) |
| :--- | :--- | :--- |
| `SPRING_RABBITMQ_HOST` | `localhost` | `rabbitmq` |
| `SPRING_MAIL_HOST` | `localhost` | `mailpit` |
| `SPRING_MAIL_PORT` | `1025` | `1025` |

### 2.5. `api-gateway` (:8080)
| Propiedad / Variable | Valor Local (`default`) | Perfil Docker (`docker`) |
| :--- | :--- | :--- |
| `KEYCLOAK_JWKS_URL` | `http://localhost:8081/realms/donatrack/protocol/openid-connect/certs` | `http://keycloak:8081/realms/donatrack/protocol/openid-connect/certs` |
| `DONACIONES_SVC_URI` | `http://localhost:8082` | `http://donaciones-service:8082` |
| `LOGISTICA_SVC_URI` | `http://localhost:8083` | `http://logistica-service:8083` |
| `INCENTIVOS_SVC_URI` | `http://localhost:8084` | `http://incentivos-service:8084` |
