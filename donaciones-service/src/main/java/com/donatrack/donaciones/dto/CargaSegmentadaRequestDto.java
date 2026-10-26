package com.donatrack.donaciones.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CargaSegmentadaRequestDto(
    @NotNull(message = "El identificador del donante es obligatorio")
    Long donanteId,

    @NotEmpty(message = "Debe consignar al menos un bien en la carga")
    @Valid
    List<BienInputDto> bienes
) {}
