package com.donatrack.donaciones.dto;

/**
 * DTO que encapsula el resultado de la validación y normalización oficial
 * de un domicilio en la República Argentina mediante el servicio Georef AR.
 */
public record DireccionNormalizadaDto(
        String direccionOriginal,
        String nomenclatura,
        String calle,
        Integer altura,
        String departamento,
        String localidad,
        String provincia,
        Double latitud,
        Double longitud,
        boolean esValida,
        String mensajeValidacion
) {
    public static DireccionNormalizadaDto invalida(String original, String motivo) {
        return new DireccionNormalizadaDto(
                original,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                motivo
        );
    }

    public static DireccionNormalizadaDto valida(
            String original,
            String nomenclatura,
            String calle,
            Integer altura,
            String departamento,
            String localidad,
            String provincia,
            Double latitud,
            Double longitud
    ) {
        return new DireccionNormalizadaDto(
                original,
                nomenclatura,
                calle,
                altura,
                departamento,
                localidad,
                provincia,
                latitud,
                longitud,
                true,
                "Dirección validada exitosamente en el registro geográfico oficial."
        );
    }
}
