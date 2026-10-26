package com.donatrack.donaciones.dto;

import com.donatrack.common.enums.EstadoDonacion;
import jakarta.validation.constraints.NotNull;

public record CambioEstadoRequestDto(
    @NotNull(message = "El nuevo estado es obligatorio")
    EstadoDonacion nuevoEstado,

    String justificacion,

    String usuario
) {}
