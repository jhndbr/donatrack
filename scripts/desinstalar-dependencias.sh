#!/usr/bin/env bash
# ==============================================================================
# DonaTrack - Script para desinstalar dependencias en Debian
# ==============================================================================
set -euo pipefail

# Validar permisos de root
if [ "$(id -u)" -ne 0 ]; then
  echo "❌ Por favor, ejecuta este script con permisos de superusuario: sudo ./desinstalar-dependencias.sh"
  exit 1
fi

REAL_USER="${SUDO_USER:-$(logname 2>/dev/null || echo "$USER")}"

echo "=========================================================="
echo "🧹 Desinstalación de Dependencias - DonaTrack"
echo "=========================================================="

# 1. Preguntar por Docker
REMOVE_DOCKER=false
REMOVE_DOCKER_DATA=false

read -p "¿Deseas desinstalar Docker y Docker Compose? (s/N): " -r RESP_DOCKER
if [[ "$RESP_DOCKER" =~ ^[sS]$ ]]; then
    REMOVE_DOCKER=true
    read -p "  ⚠️ ¿Deseas borrar también todos los volúmenes, imágenes y datos de Docker (/var/lib/docker)? (s/N): " -r RESP_DATA
    if [[ "$RESP_DATA" =~ ^[sS]$ ]]; then
        REMOVE_DOCKER_DATA=true
    fi
fi

# 2. Desinstalar Java 21 y repositorio Temurin
echo ""
echo "☕ 1/3. Desinstalando JDK 21 y repositorios asociados..."
apt-get purge -y \
    openjdk-21-jdk \
    openjdk-21-jre \
    openjdk-21-jre-headless \
    temurin-21-jdk 2>/dev/null || true

rm -f /etc/apt/sources.list.d/adoptium.list
rm -f /etc/apt/keyrings/adoptium.gpg

# 3. Desinstalar Docker si se confirmó
if [ "$REMOVE_DOCKER" = true ]; then
    echo ""
    echo "🐳 2/3. Deteniendo y desinstalando Docker..."
    systemctl stop docker docker.socket containerd 2>/dev/null || true

    apt-get purge -y \
        docker-ce \
        docker-ce-cli \
        containerd.io \
        docker-buildx-plugin \
        docker-compose-plugin \
        docker-ce-rootless-extras 2>/dev/null || true

    rm -f /etc/apt/sources.list.d/docker.list /etc/apt/sources.list.d/docker.sources
    rm -f /etc/apt/keyrings/docker.gpg /etc/apt/keyrings/docker.asc

    if [ "$REMOVE_DOCKER_DATA" = true ]; then
        echo "🗑️ Eliminando datos de Docker (/var/lib/docker, /var/lib/containerd)..."
        rm -rf /var/lib/docker /var/lib/containerd /etc/docker
    fi

    # Quitar usuario del grupo docker si existe
    if [ -n "$REAL_USER" ] && [ "$REAL_USER" != "root" ]; then
        gpasswd -d "$REAL_USER" docker 2>/dev/null || true
    fi
else
    echo ""
    echo "⏩ 2/3. Omitiendo desinstalación de Docker a petición del usuario."
fi

# 4. Limpieza general de paquetes huérfanos
echo ""
echo "🧹 3/3. Limpiando paquetes y dependencias huérfanas..."
apt-get autoremove -y --purge
apt-get update -y

echo ""
echo "=========================================================="
echo "✅ Desinstalación finalizada con éxito."
echo "=========================================================="
