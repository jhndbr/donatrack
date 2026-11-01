# ==============================================================================
# DonaTrack - Instalación de dependencias para Windows (PowerShell Administrador)
# Utiliza winget para instalar JDK 21, Docker Desktop y Git
# ==============================================================================

#Requires -RunAsAdministrator

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " Instalando Dependencias para DonaTrack en Windows" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# 1. Comprobar / Instalar Git
if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
    Write-Host "📦 Instalando Git..." -ForegroundColor Yellow
    winget install --id Git.Git -e --source winget --accept-package-agreements --accept-source-agreements
} else {
    Write-Host "✅ Git ya se encuentra instalado." -ForegroundColor Green
}

# 2. Comprobar / Instalar Java JDK 21 (Eclipse Temurin / Microsoft OpenJDK)
$javaInstalled = $false
try {
    $javaVer = java -version 2>&1 | Select-Object -First 1
    if ($javaVer -match '21\.') {
        $javaInstalled = $true
        Write-Host "✅ Java 21 ya está instalado ($javaVer)." -ForegroundColor Green
    }
} catch {}

if (-not $javaInstalled) {
    Write-Host "☕ Instalando Eclipse Temurin JDK 21..." -ForegroundColor Yellow
    winget install --id EclipseAdoptium.Temurin.21.JDK -e --source winget --accept-package-agreements --accept-source-agreements
}

# 3. Comprobar / Instalar Docker Desktop
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Host "🐳 Instalando Docker Desktop..." -ForegroundColor Yellow
    winget install --id Docker.DockerDesktop -e --source winget --accept-package-agreements --accept-source-agreements
    Write-Host "⚠️ Recuerda iniciar Docker Desktop y reiniciar el equipo si es la primera vez que se activa WSL2/Hyper-V." -ForegroundColor Yellow
} else {
    Write-Host "✅ Docker ya se encuentra instalado." -ForegroundColor Green
}

Write-Host "`n==========================================================" -ForegroundColor Cyan
Write-Host "✅ Proceso de instalación finalizado." -ForegroundColor Green
Write-Host "Por favor, cierra y vuelve a abrir tu terminal (PowerShell) para refrescar el PATH." -ForegroundColor Yellow
Write-Host "==========================================================" -ForegroundColor Cyan
