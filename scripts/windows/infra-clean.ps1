# ==============================================================================
# DonaTrack - Limpiar Infraestructura y Volúmenes en Windows
# ==============================================================================
$ErrorActionPreference = "Stop"

$RootDir = (Resolve-Path "$PSScriptRoot\..\..").Path
Set-Location $RootDir

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " Limpiando Infraestructura y Volúmenes de Datos..." -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

docker compose -f docker/docker-compose.yml --profile full down -v @args

Write-Host "`nInfraestructura y volúmenes persistentes eliminados." -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Cyan
