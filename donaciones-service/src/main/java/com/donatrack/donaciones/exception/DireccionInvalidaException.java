package com.donatrack.donaciones.exception;

/**
 * Excepción lanzada cuando una dirección ingresada no existe, es apócrifa
 * o no cumple los requisitos catastrales mínimos (calle y altura comprobable).
 */
public class DireccionInvalidaException extends RuntimeException {

    private final String direccionRechazada;

    public DireccionInvalidaException(String direccionRechazada, String detalle) {
        super(String.format("La dirección '%s' no pudo ser validada: %s", direccionRechazada, detalle));
        this.direccionRechazada = direccionRechazada;
    }

    public String getDireccionRechazada() {
        return direccionRechazada;
    }
}
