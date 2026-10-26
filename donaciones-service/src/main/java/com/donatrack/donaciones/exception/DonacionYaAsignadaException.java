package com.donatrack.donaciones.exception;

/**
 * Excepción lanzada cuando se produce una condición de carrera o intento concurrente
 * de asignar una donación que ya fue asignada o no se encuentra en estado EN_DEPOSITO (FT-09).
 */
public class DonacionYaAsignadaException extends RuntimeException {

    public DonacionYaAsignadaException(String message) {
        super(message);
    }

    public DonacionYaAsignadaException(Long donacionId) {
        super("La donación con ID " + donacionId + " ya fue asignada previamente o no está disponible en depósito.");
    }
}
