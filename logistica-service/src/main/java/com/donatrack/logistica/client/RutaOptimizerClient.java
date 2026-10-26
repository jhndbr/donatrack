package com.donatrack.logistica.client;

import com.donatrack.logistica.domain.Camion;
import com.donatrack.logistica.dto.PlanificadorCallbackDto;

import java.util.List;

/**
 * Patrón Strategy para la optimización y ruteo de vehículos (VRP).
 * Permite desacoplar el motor algorítmico externo del servicio de logística.
 */
public interface RutaOptimizerClient {
    PlanificadorCallbackDto optimizarLote(String loteId, List<Long> donacionIds, Camion camion);
}
