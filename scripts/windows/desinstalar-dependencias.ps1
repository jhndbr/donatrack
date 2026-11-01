# ==============================================================================
# DonaTrack - Desinstalación de dependencias en Windows (PowerShell Administrador)
# ==============================================================================

#Requires -RunAsAdministrator

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " Desinstalación de Dependencias - DonaTrack" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# Preguntar por Docker
$desinstalarDocker = Read-Host "¿Deseas desinstalar Docker Desktop? (s/N)"
if ($desinstalarDocker -match '^[sS]$') {
    Write-Host "🐳 Desinstalando Docker Desktop..." -ForegroundColor Yellow
    winget uninstall --id Docker.DockerDesktop -e --source winget
} else {
    Write-Host "⏩ Omitiendo desinstalación de Docker Desktop." -ForegroundColor Gray
}

# Preguntar por JDK 21
$desinstalarJava = Read-Host "¿Deseas desinstalar Java 21 (Temurin)? (s/N)"
if ($desinstalarJava -match '^[sS]$') {
    Write-Host "☕ Desinstalando JDK 21..." -ForegroundColor Yellow
    winget uninstall --id EclipseAdoptium.Temurin.21.JDK -e --source winget
} else {
    Write-Host "⏩ Omitiendo desinstalación de Java 21." -ForegroundColor Gray
}

Write-Host "`n==========================================================" -ForegroundColor Cyan
Write-Host "✅ Proceso de desinstalación completado." -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Cyan
