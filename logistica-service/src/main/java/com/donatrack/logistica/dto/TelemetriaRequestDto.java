package com.donatrack.logistica.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;

public record TelemetriaRequestDto(
        @NotBlank(message = "El id o identificador del camión es obligatorio")
        String idCamion,

        String patente,

        double latitud,

        double longitud,

        double velocidad,

        LocalDateTime timestamp,

        String direccion
) {}
