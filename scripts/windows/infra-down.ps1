# ==============================================================================
# DonaTrack - Detener Infraestructura Docker en Windows
# ==============================================================================
$ErrorActionPreference = "Stop"

$RootDir = (Resolve-Path "$PSScriptRoot\..\..").Path
Set-Location $RootDir

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " Deteniendo Infraestructura Docker..." -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

docker compose -f docker/docker-compose.yml --profile full down @args

Write-Host "`nContenedores detenidos correctamente." -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Cyan
