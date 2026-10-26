package com.donatrack.logistica.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record CamionRequestDto(
        @NotBlank(message = "La patente es obligatoria")
        @Pattern(regexp = "^[A-Z0-9-]{6,10}$", message = "Formato de patente inválido (ej: AA123BB, AE789XY)")
        String patente,

        String marcaModelo,

        @Positive(message = "La capacidad volumétrica (m3) debe ser mayor a cero")
        double capacidadVolumenM3,

        @Positive(message = "La altura (m) debe ser mayor a cero")
        double alturaM,

        @Positive(message = "La capacidad de peso (kg) debe ser mayor a cero")
        double capacidadPesoKg
) {}
