package com.donatrack.donaciones.matchmaking;

import com.donatrack.donaciones.domain.Donacion;
import com.donatrack.donaciones.domain.NecesidadMaterial;
import org.springframework.stereotype.Component;

@Component
public class PrioridadSubatendidosStrategy implements MatchmakingStrategy {

    // Factor de suavizado alfa
    private final double alpha = 0.1;

    @Override
    public String getNombre() {
        return "Prioridad a Subatendidos";
    }

    @Override
    public double calcularScore(Donacion donacion, NecesidadMaterial necesidad) {
        int donacionesTrimestre = 0;
        if (necesidad.getEntidadBeneficiaria() != null) {
            donacionesTrimestre = necesidad.getEntidadBeneficiaria().getDonacionesRecibidasTrimestre();
        }

        // S_sub = 1 / (1 + alpha * CantDonacionesTrimestre)
        double score = 1.0 / (1.0 + (alpha * donacionesTrimestre));
        return Math.round(score * 10000.0) / 10000.0;
    }
}
