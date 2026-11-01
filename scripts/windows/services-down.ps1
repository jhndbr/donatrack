# ==============================================================================
# DonaTrack - Detener Microservicios en Windows
# ==============================================================================
$ErrorActionPreference = "SilentlyContinue"

$RootDir = (Resolve-Path "$PSScriptRoot\..\..").Path
Set-Location $RootDir

$PidFile = "$RootDir\.services.pid"

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " Deteniendo Microservicios DonaTrack..." -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

if (Test-Path $PidFile) {
    $lines = Get-Content $PidFile
    foreach ($line in $lines) {
        if ($line -match '^(\d+)\s+(.+)$') {
            $pidToKill = [int]$matches[1]
            $svcName = $matches[2]
            Write-Host " - Deteniendo $svcName (PID $pidToKill)..." -ForegroundColor Yellow
            Stop-Process -Id $pidToKill -Force -ErrorAction SilentlyContinue
        }
    }
    Remove-Item -Path $PidFile -Force -ErrorAction SilentlyContinue
}

# Detener cualquier proceso java residual con los jars de donatrack
Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" | Where-Object {
    $_.CommandLine -match "donatrack|donaciones-service|logistica-service|incentivos-service|notificaciones-service|api-gateway|frontend-service"
} | ForEach-Object {
    Write-Host " - Terminando proceso java residual (PID $($_.ProcessId))..." -ForegroundColor Yellow
    Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
}

Write-Host "`n✅ Todos los microservicios han sido detenidos correctamente." -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Cyan
