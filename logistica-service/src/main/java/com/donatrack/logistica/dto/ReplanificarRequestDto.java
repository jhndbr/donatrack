package com.donatrack.logistica.dto;

public record ReplanificarRequestDto(
        boolean regresoAlDeposito,
        String motivo
) {}
