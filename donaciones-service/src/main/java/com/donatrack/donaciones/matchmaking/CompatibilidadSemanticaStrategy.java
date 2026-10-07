package com.donatrack.donaciones.matchmaking;

import com.donatrack.common.enums.TipoNecesidad;
import com.donatrack.donaciones.domain.Donacion;
import com.donatrack.donaciones.domain.NecesidadMaterial;
import org.springframework.stereotype.Component;

@Component
public class CompatibilidadSemanticaStrategy implements MatchmakingStrategy {

    // Pesos configurables de las constantes que suman 1.0 (100%)
    private final double a1 = 0.40; // Peso textual
    private final double a2 = 0.40; // Peso del volumen
    private final double a3 = 0.20; // Peso de urgencia / tipo

    @Override
    public String getNombre() {
        return "Compatibilidad Semantica";
    }

    @Override
    public double calcularScore(Donacion donacion, NecesidadMaterial necesidad) {
        // chi1: Similitud textual basada en distancia de Levenshtein
        String str1 = donacion.getSubcategoria().toLowerCase().trim();
        String str2 = necesidad.getDescripcion().toLowerCase().trim();
        int dist = calcularLevenshtein(str1, str2);
        int maxLen = Math.max(str1.length(), str2.length());
        double chi1 = maxLen == 0 ? 1.0 : (1.0 - ((double) dist / maxLen));
        chi1 = Math.max(0.0, Math.min(1.0, chi1));

        // chi2: Cobertura de volumen
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
                chi2 = 1.0 / r; // Penaliza desperdicio / sobre-asignacion
            }
        }
        chi2 = Math.max(0.0, Math.min(1.0, chi2));

        // chi3: Tipo de necesidad (Urgencia)
        double chi3 = (necesidad.getTipo() == TipoNecesidad.EXTRAORDINARIA) ? 1.0 : 0.5;

        // Polinomio normalizado en [0, 1]
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
}
