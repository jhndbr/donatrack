package com.donatrack.logistica.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;

public record EntregaFallidaRequestDto(
        @NotBlank(message = "La justificación o motivo de falla es estrictamente obligatoria (CU-44)")
        @JsonAlias({"motivoFalla", "observaciones", "descripcion"})
        String motivo,

        boolean replanificable
) {}
