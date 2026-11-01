# ==============================================================================
# DonaTrack - Compilar proyecto en Windows
# ==============================================================================
$ErrorActionPreference = "Stop"

$RootDir = (Resolve-Path "$PSScriptRoot\..\..").Path
Set-Location $RootDir

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " Compilando e Instalando Microservicios DonaTrack" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

& .\mvnw.cmd clean install @args

Write-Host "`nCompilación exitosa. JARs generados en cada submódulo." -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Cyan
