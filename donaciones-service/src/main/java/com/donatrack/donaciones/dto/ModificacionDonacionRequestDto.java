package com.donatrack.donaciones.dto;

import jakarta.validation.constraints.Positive;

public record ModificacionDonacionRequestDto(
    String subcategoria,

    @Positive(message = "La cantidad debe ser positiva")
    Double cantidad,

    String unidadMedida,

    Boolean esUsado
) {}
