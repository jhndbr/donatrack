package com.donatrack.donaciones.matchmaking;

import com.donatrack.common.enums.TipoNecesidad;
import com.donatrack.donaciones.domain.Donacion;
import com.donatrack.donaciones.domain.NecesidadMaterial;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Estrategia de Matchmaking basada en Compatibilidad Semántica (CU-31).
 * Combina similitud textual de Levenshtein (chi1), ratio de cobertura de volumen (chi2)
 * y urgencia por tipo de necesidad (chi3), ponderados por constantes parametrizables (a1, a2, a3).
 * Invariante: a1 + a2 + a3 == 1.0 (100%).
 */
@Component
public class CompatibilidadSemanticaStrategy implements MatchmakingStrategy {

    @Value("${donatrack.matchmaking.semantica.peso-textual:0.40}")
    private double a1 = 0.40; // Peso textual (chi1)

    @Value("${donatrack.matchmaking.semantica.peso-volumen:0.40}")
    private double a2 = 0.40; // Peso del volumen (chi2)

    @Value("${donatrack.matchmaking.semantica.peso-urgencia:0.20}")
    private double a3 = 0.20; // Peso de urgencia / tipo (chi3)

    public CompatibilidadSemanticaStrategy() {
        this(0.40, 0.40, 0.20);
    }

    public CompatibilidadSemanticaStrategy(double a1, double a2, double a3) {
        this.a1 = a1;
        this.a2 = a2;
        this.a3 = a3;
        validarPesos();
    }

    @PostConstruct
    public void validarPesos() {
        double suma = a1 + a2 + a3;
        if (Math.abs(suma - 1.0) > 0.001) {
            throw new IllegalStateException("La suma de los coeficientes de afinidad semántica (a1 + a2 + a3) debe ser exactamente 1.0. Suma actual: " + suma);
        }
    }

    public void configurarPesos(double a1, double a2, double a3) {
        this.a1 = a1;
        this.a2 = a2;
        this.a3 = a3;
        validarPesos();
    }

    @Override
    public String getNombre() {
        return "Compatibilidad Semantica";
    }

    @Override
    public double calcularScore(Donacion donacion, NecesidadMaterial necesidad) {
        // chi1: Similitud textual basada en distancia de Levenshtein normalizada (Nombre/descripción del bien vs descripción necesidad)
        String descBienes = (donacion.getBienes() != null && !donacion.getBienes().isEmpty())
                ? donacion.getBienes().stream()
                      .map(b -> b.getDescripcion() != null ? b.getDescripcion() : "")
                      .filter(s -> !s.isBlank())
                      .collect(java.util.stream.Collectors.joining(" "))
                : donacion.getSubcategoria();
        String str1 = (descBienes != null && !descBienes.isBlank()) ? descBienes.toLowerCase().trim() : donacion.getSubcategoria().toLowerCase().trim();
        String str2 = (necesidad.getDescripcion() != null) ? necesidad.getDescripcion().toLowerCase().trim() : "";
        int dist = calcularLevenshtein(str1, str2);
        int maxLen = Math.max(str1.length(), str2.length());
        double chi1 = maxLen == 0 ? 1.0 : (1.0 - ((double) dist / maxLen));
        chi1 = Math.max(0.0, Math.min(1.0, chi1));

        // chi2: Cobertura de volumen / cantidad
        double cantDonada = donacion.getCantidad();
        double cantRequerida = necesidad.getCantidadPendiente();
        double chi2;
        if (cantRequerida <= 0) {
            chi2 = 0.0;
        } else {
            double r = cantDonada / cantRequerida;
            if (r <= 1.0) {
                chi2 = r;
            } else {
                chi2 = 1.0 / r; // Penaliza desperdicio / sobre-asignación excesiva
            }
        }
        chi2 = Math.max(0.0, Math.min(1.0, chi2));

        // chi3: Tipo de necesidad (Urgencia)
        double chi3 = (necesidad.getTipo() == TipoNecesidad.EXTRAORDINARIA) ? 1.0 : 0.5;

        // Polinomio ponderado normalizado en [0, 1]
        double score = (a1 * chi1) + (a2 * chi2) + (a3 * chi3);
        return Math.round(score * 10000.0) / 10000.0;
    }

    private int calcularLevenshtein(String s1, String s2) {
        int[] prev = new int[s2.length() + 1];
        for (int j = 0; j <= s2.length(); j++) {
            prev[j] = j;
        }

        for (int i = 1; i <= s1.length(); i++) {
            int[] curr = new int[s2.length() + 1];
            curr[0] = i;
            for (int j = 1; j <= s2.length(); j++) {
                int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            prev = curr;
        }
        return prev[s2.length()];
    }

    public double getA1() { return a1; }
    public double getA2() { return a2; }
    public double getA3() { return a3; }
}
