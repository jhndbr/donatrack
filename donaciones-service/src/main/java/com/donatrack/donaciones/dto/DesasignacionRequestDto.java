package com.donatrack.donaciones.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DesasignacionRequestDto(
    @NotBlank(message = "El motivo de la desasignación es obligatorio")
    @Size(min = 5, max = 500, message = "El motivo debe contener entre 5 y 500 caracteres")
    String motivo,

    String usuario
) {}
