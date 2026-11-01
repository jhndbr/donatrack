# ==============================================================================
# DonaTrack - Despliegue Integral Dockerizado en Windows
# ==============================================================================
$ErrorActionPreference = "Stop"

$RootDir = (Resolve-Path "$PSScriptRoot\..\..").Path
Set-Location $RootDir

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " Despliegue Integral Contenedorizado - DonaTrack" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

Write-Host "1. Compilando artefactos JAR..." -ForegroundColor Yellow
& .\mvnw.cmd clean package -DskipTests

Write-Host "`n2. Construyendo imágenes Docker de microservicios..." -ForegroundColor Yellow
docker build -t donatrack/donaciones-service:latest donaciones-service/
docker build -t donatrack/logistica-service:latest logistica-service/
docker build -t donatrack/incentivos-service:latest incentivos-service/
docker build -t donatrack/notificaciones-service:latest notificaciones-service/
docker build -t donatrack/api-gateway:latest api-gateway/
docker build -t donatrack/frontend-service:latest frontend-service/

Write-Host "`n3. Iniciando ecosistema completo con Docker Compose (Profile: full)..." -ForegroundColor Yellow
docker compose -f docker/docker-compose.yml --profile full up -d

Write-Host "`nEsperando inicialización de bases de datos y servicios..." -ForegroundColor Yellow
Start-Sleep -Seconds 4

for ($i = 1; $i -le 20; $i++) {
    $ready = docker exec donatrack-postgres pg_isready -U donatrack 2>$null
    if ($LASTEXITCODE -eq 0) {
        Get-Content docker/init-scripts/01-init-databases.sql | docker exec -i donatrack-postgres psql -U donatrack -d postgres -q
        Get-Content docker/init-scripts/02-schema-updates.sql | docker exec -i donatrack-postgres psql -U donatrack -d donaciones_db -q
        Write-Host "Bases de datos y esquemas verificados." -ForegroundColor Green
        break
    }
    Start-Sleep -Seconds 1
}

Write-Host ""
docker compose -f docker/docker-compose.yml --profile full ps

Write-Host "`n==========================================================" -ForegroundColor Cyan
Write-Host " Stack Completo DonaTrack Operativo en Docker:" -ForegroundColor Green
Write-Host " - Portal Web:            http://localhost:8086"
Write-Host " - API Gateway:           http://localhost:8080"
Write-Host " - Prometheus:            http://localhost:9090"
Write-Host " - Grafana:               http://localhost:3000 (admin/admin)"
Write-Host " - n8n Webhooks:          http://localhost:5678"
Write-Host " - RabbitMQ UI:           http://localhost:15672 (donatrack/donatrack_password)"
Write-Host " - Mailpit Web:           http://localhost:8025"
Write-Host " - Keycloak Admin:        http://localhost:8081 (admin/admin)"
Write-Host ""
Write-Host "Para detener el stack ejecute: .\scripts\windows\infra-down.ps1" -ForegroundColor Yellow
Write-Host "==========================================================" -ForegroundColor Cyan
