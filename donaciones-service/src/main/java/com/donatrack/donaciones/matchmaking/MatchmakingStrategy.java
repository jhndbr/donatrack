package com.donatrack.donaciones.matchmaking;

import com.donatrack.donaciones.domain.Donacion;
import com.donatrack.donaciones.domain.NecesidadMaterial;

/**
 * Patron Strategy para evaluar la afinidad entre una Donacion y una Necesidad Material.
 */
public interface MatchmakingStrategy {
    String getNombre();
    double calcularScore(Donacion donacion, NecesidadMaterial necesidad);
}
