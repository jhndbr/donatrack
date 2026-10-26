package com.donatrack.donaciones.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

public record BienInputDto(
    @NotBlank(message = "La descripción del bien es obligatoria")
    String descripcion,

    @NotBlank(message = "La categoría es obligatoria")
    String categoria,

    @NotBlank(message = "La subcategoría es la unidad mínima y es obligatoria")
    String subcategoria,

    @Positive(message = "La cantidad debe ser mayor a cero")
    double cantidad,

    @NotBlank(message = "La unidad de medida es obligatoria")
    String unidadMedida,

    Boolean esUsado,

    LocalDate fechaVencimiento,

    String fotoUrl
) {}
