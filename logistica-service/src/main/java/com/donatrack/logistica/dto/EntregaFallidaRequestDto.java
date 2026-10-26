package com.donatrack.logistica.dto;

import jakarta.validation.constraints.NotBlank;

public record EntregaFallidaRequestDto(
        @NotBlank(message = "La justificación o motivo de falla es estrictamente obligatoria (CU-44)")
        String motivo,

        boolean replanificable
) {}
