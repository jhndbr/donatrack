package com.donatrack.donaciones.dto;

import jakarta.validation.constraints.NotBlank;

public record BajaLogicaRequestDto(
    @NotBlank(message = "El motivo de baja es obligatorio")
    String motivo
) {}
