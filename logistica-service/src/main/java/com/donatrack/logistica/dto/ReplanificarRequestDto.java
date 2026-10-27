package com.donatrack.logistica.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

public record ReplanificarRequestDto(
        @JsonAlias({"donacionRegresoBuenEstado", "regresoAlDeposito"})
        boolean regresoAlDeposito,
        String motivo
) {}
