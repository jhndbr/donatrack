package com.donatrack.donaciones.dto;

import jakarta.validation.constraints.NotNull;

public record AsignacionDonacionRequestDto(
    @NotNull(message = "El identificador de la entidad beneficiaria es obligatorio")
    Long entidadId,

    Long necesidadId,

    String usuario
) {}
