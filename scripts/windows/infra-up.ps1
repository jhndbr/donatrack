# ==============================================================================
# DonaTrack - Levantar Infraestructura Docker en Windows
# ==============================================================================
$ErrorActionPreference = "Stop"

$RootDir = (Resolve-Path "$PSScriptRoot\..\..").Path
Set-Location $RootDir

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " Levantar Infraestructura Docker - DonaTrack" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

docker compose -f docker/docker-compose.yml up -d @args

Write-Host "`nEsperando que PostgreSQL alcance estado saludable..." -ForegroundColor Yellow
Start-Sleep -Seconds 3

for ($i = 1; $i -le 20; $i++) {
    $ready = docker exec donatrack-postgres pg_isready -U donatrack 2>$null
    if ($LASTEXITCODE -eq 0) {
        Get-Content docker/init-scripts/01-init-databases.sql | docker exec -i donatrack-postgres psql -U donatrack -d postgres -q
        Get-Content docker/init-scripts/02-schema-updates.sql | docker exec -i donatrack-postgres psql -U donatrack -d donaciones_db -q
        Write-Host "Bases de datos y migraciones verificadas (donaciones_db, incentivos_db, notificaciones_db, keycloak_db)." -ForegroundColor Green
        break
    }
    Start-Sleep -Seconds 1
}

Write-Host ""
docker compose -f docker/docker-compose.yml ps

Write-Host "`nInfraestructura iniciada correctamente:" -ForegroundColor Cyan
Write-Host " - PostgreSQL:     localhost:5432 (donaciones_db, incentivos_db, notificaciones_db, keycloak_db)"
Write-Host " - MongoDB:        localhost:27017 (logistica_db)"
Write-Host " - RabbitMQ:       localhost:5672 (AMQP) | UI: http://localhost:15672 (donatrack/donatrack_password)"
Write-Host " - Mailpit Web:    http://localhost:8025 (SMTP :1025)"
Write-Host " - Keycloak Admin: http://localhost:8081 (admin/admin)"
Write-Host "==========================================================" -ForegroundColor Cyan
