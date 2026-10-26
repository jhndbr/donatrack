package com.donatrack.logistica.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record ConfirmarEntregaRequestDto(
        @NotEmpty(message = "Es obligatorio adjuntar al menos una fotografía de los bienes recibidos (CU-43)")
        List<String> fotos,

        String receptorNombre,
        String receptorDni,
        String firmaDigitalUrl
) {}
