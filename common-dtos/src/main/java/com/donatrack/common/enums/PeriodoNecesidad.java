package com.donatrack.common.enums;

/**
 * Periodicidades estandarizadas para necesidades materiales de tipo RECURRENTE.
 */
public enum PeriodoNecesidad {
    DIARIA("Diaria (Todos los días)"),
    SEMANAL("Semanal (Cada 7 días)"),
    QUINCENAL("Quincenal (Cada 15 días)"),
    MENSUAL("Mensual (Cada 30 días)"),
    BIMESTRAL("Bimestral (Cada 2 meses)"),
    TRIMESTRAL("Trimestral (Cada 3 meses)"),
    SEMESTRAL("Semestral (Cada 6 meses)"),
    ANUAL("Anual (Cada año)");

    private final String etiqueta;

    PeriodoNecesidad(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    /**
     * Normaliza un texto de periodicidad al valor del enum correspondiente.
     * Lanza IllegalArgumentException si no corresponde a ninguna periodicidad válida.
     */
    public static PeriodoNecesidad fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("La periodicidad no puede estar vacía para una necesidad recurrente.");
        }
        String normalized = raw.trim().toUpperCase()
                .replace("Á", "A")
                .replace("É", "E")
                .replace("Í", "I")
                .replace("Ó", "O")
                .replace("Ú", "U");
        for (PeriodoNecesidad p : values()) {
            if (p.name().equals(normalized) || p.etiqueta.toUpperCase().startsWith(normalized)) {
                return p;
            }
        }
        if (normalized.contains("DIARI")) return DIARIA;
        if (normalized.contains("SEMAN")) return SEMANAL;
        if (normalized.contains("QUINCEN")) return QUINCENAL;
        if (normalized.contains("MENSU") || normalized.contains("MES")) return MENSUAL;
        if (normalized.contains("BIMESTR")) return BIMESTRAL;
        if (normalized.contains("TRIMESTR")) return TRIMESTRAL;
        if (normalized.contains("SEMESTR")) return SEMESTRAL;
        if (normalized.contains("ANUAL") || normalized.contains("AÑO") || normalized.contains("ANO")) return ANUAL;

        throw new IllegalArgumentException("Periodicidad inválida: '" + raw + "'. Valores permitidos: DIARIA, SEMANAL, QUINCENAL, MENSUAL, BIMESTRAL, TRIMESTRAL, SEMESTRAL, ANUAL.");
    }
}
