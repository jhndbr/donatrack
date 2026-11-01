#!/usr/bin/env bash
# ==============================================================================
# DonaTrack - Script de instalación de dependencias para Debian
# ==============================================================================
set -euo pipefail

# Validar permisos de root
if [ "$(id -u)" -ne 0 ]; then
  echo "❌ Por favor, ejecuta este script con permisos de superusuario: sudo ./dependencias.sh"
  exit 1
fi

# Detectar automáticamente el usuario que invocó sudo
REAL_USER="${SUDO_USER:-$(logname 2>/dev/null || echo "$USER")}"

echo "🚀 Iniciando preparación del entorno para DonaTrack en Debian..."
echo "👤 Usuario destino para grupo Docker: $REAL_USER"

# 0. Limpiar repositorios y claves conflictivas de Docker PREVIAMENTE a cualquier apt-get update
echo "🧹 Limpiando configuraciones previas conflictivas de Docker en APT..."
rm -f /etc/apt/sources.list.d/docker.list /etc/apt/sources.list.d/docker.sources
rm -f /etc/apt/keyrings/docker.gpg /etc/apt/keyrings/docker.asc

# 1. Actualizar repositorios e instalar herramientas esenciales
echo "📦 1/4. Instalando paquetes base..."
apt-get update -y
apt-get install -y --no-install-recommends \
    ca-certificates \
    curl \
    gnupg \
    lsb-release \
    git \
    jq \
    netcat-openbsd \
    apt-transport-https

# 2. Instalar OpenJDK 21 (Temurin / Adoptium o Debian Oficial según versión)
echo "☕ 2/4. Configurando Java 21..."
if apt-cache show openjdk-21-jdk >/dev/null 2>&1; then
    apt-get install -y openjdk-21-jdk
else
    echo "Agregando repositorio de Eclipse Adoptium (Temurin JDK 21)..."
    install -m 0755 -d /etc/apt/keyrings
    curl -fsSL https://packages.adoptium.net/artifactory/api/gpg/key/public | gpg --dearmor -o /etc/apt/keyrings/adoptium.gpg --yes
    echo "deb [signed-by=/etc/apt/keyrings/adoptium.gpg] https://packages.adoptium.net/artifactory/deb $(lsb_release -cs) main" > /etc/apt/sources.list.d/adoptium.list
    apt-get update -y
    apt-get install -y temurin-21-jdk
fi

# 3. Instalar Docker Engine y Docker Compose Plugin
echo "🐳 3/4. Instalando Docker y Docker Compose..."
install -m 0755 -d /etc/apt/keyrings

# Descargar la clave oficial de Docker
curl -fsSL https://download.docker.com/linux/debian/gpg -o /etc/apt/keyrings/docker.asc
chmod a+r /etc/apt/keyrings/docker.asc

# Configurar fuente usando deb822 moderno (.sources)
CODENAME="$(. /etc/os-release && echo "$VERSION_CODENAME")"
cat <<EOF > /etc/apt/sources.list.d/docker.sources
Types: deb
URIs: https://download.docker.com/linux/debian
Suites: ${CODENAME}
Components: stable
Architectures: $(dpkg --print-architecture)
Signed-By: /etc/apt/keyrings/docker.asc
EOF

apt-get update -y
apt-get install -y \
    docker-ce \
    docker-ce-cli \
    containerd.io \
    docker-buildx-plugin \
    docker-compose-plugin

# Iniciar y habilitar servicio Docker
systemctl enable --now docker

# Agregar el usuario al grupo docker automáticamente
if [ -n "$REAL_USER" ] && [ "$REAL_USER" != "root" ]; then
    usermod -aG docker "$REAL_USER"
    echo "👤 Usuario '$REAL_USER' agregado al grupo 'docker'."
fi

# 4. Verificación
echo "=========================================================="
echo "✅ Instalación completada con éxito!"
echo "Versión de Java: $(java -version 2>&1 | head -n 1)"
echo "Versión de Docker: $(docker --version)"
echo "Versión de Compose: $(docker compose version)"
echo "=========================================================="
echo "⚠️  NOTA: Debes aplicar el nuevo grupo ejecutando:"
echo "    newgrp docker"
echo "    o cerrando y volviendo a iniciar tu sesión."
