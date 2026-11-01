# ==============================================================================
# DonaTrack - Iniciar Microservicios Localmente en Windows
# ==============================================================================
$ErrorActionPreference = "Stop"

$RootDir = (Resolve-Path "$PSScriptRoot\..\..").Path
Set-Location $RootDir

if (-not (Test-Path "logs")) {
    New-Item -ItemType Directory -Path "logs" | Out-Null
}

$PidFile = "$RootDir\.services.pid"

if (Test-Path $PidFile) {
    Write-Host "Ya existen microservicios iniciados (registrados en .services.pid)." -ForegroundColor Yellow
    Write-Host "Ejecute .\scripts\windows\services-down.ps1 primero si desea reiniciarlos." -ForegroundColor Yellow
    exit 1
}

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " Iniciando Microservicios DonaTrack en Windows" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

$services = @(
    @{ Name = "donaciones-service";     Port = 8082; Jar = "donaciones-service\target\donaciones-service-1.0.0-SNAPSHOT.jar" },
    @{ Name = "logistica-service";      Port = 8083; Jar = "logistica-service\target\logistica-service-1.0.0-SNAPSHOT.jar" },
    @{ Name = "incentivos-service";     Port = 8084; Jar = "incentivos-service\target\incentivos-service-1.0.0-SNAPSHOT.jar" },
    @{ Name = "notificaciones-service"; Port = 8085; Jar = "notificaciones-service\target\notificaciones-service-1.0.0-SNAPSHOT.jar" },
    @{ Name = "api-gateway";            Port = 8080; Jar = "api-gateway\target\api-gateway-1.0.0-SNAPSHOT.jar" },
    @{ Name = "frontend-service";       Port = 8086; Jar = "frontend-service\target\frontend-service-1.0.0-SNAPSHOT.jar" }
)

# Verificar JARs
$faltanJars = $false
foreach ($svc in $services) {
    if (-not (Test-Path $svc.Jar)) {
        Write-Host "Falta compilar: $($svc.Jar)" -ForegroundColor Yellow
        $faltanJars = $true
    }
}

if ($faltanJars) {
    Write-Host "`nCompilando microservicios con .\mvnw.cmd package -DskipTests..." -ForegroundColor Yellow
    & .\mvnw.cmd package -DskipTests
}

$javaOpts = "-Xms64m -Xmx256m"

foreach ($svc in $services) {
    Write-Host " -> Levantando $($svc.Name) en puerto $($svc.Port)..." -ForegroundColor Cyan
    $logFile = "logs\$($svc.Name).log"
    
    $proc = Start-Process -FilePath "java" `
        -ArgumentList "$javaOpts -jar $($svc.Jar)" `
        -RedirectStandardOutput $logFile `
        -RedirectStandardError $logFile `
        -WindowStyle Hidden `
        -PassThru
        
    "$($proc.Id) $($svc.Name)" | Out-File -FilePath $PidFile -Append -Encoding ascii
}

Write-Host "`nEsperando que los servicios respondan..." -ForegroundColor Yellow
Start-Sleep -Seconds 5

Write-Host "`n==========================================================" -ForegroundColor Cyan
Write-Host "✅ Microservicios iniciados en background!" -ForegroundColor Green
Write-Host " - Frontend Portal:   http://localhost:8086"
Write-Host " - API Gateway:       http://localhost:8080"
Write-Host " - Donaciones:        http://localhost:8082"
Write-Host " - Logística:         http://localhost:8083"
Write-Host " - Incentivos:        http://localhost:8084"
Write-Host " - Notificaciones:    http://localhost:8085"
Write-Host ""
Write-Host "Logs disponibles en la carpeta logs\" -ForegroundColor Gray
Write-Host "Para detenerlos ejecute: .\scripts\windows\services-down.ps1" -ForegroundColor Yellow
Write-Host "==========================================================" -ForegroundColor Cyan
