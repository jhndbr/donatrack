package com.donatrack.logistica.dto;

import java.time.LocalDateTime;

public record CamionEnVivoDto(
        String idCamion,
        String patente,
        String marcaModelo,
        double latitud,
        double longitud,
        double velocidadKmH,
        LocalDateTime ultimoTimestamp,
        Long antiguedadMinutos,
        String direccionAproximada,
        boolean activo,
        String estadoRuta
) {}
