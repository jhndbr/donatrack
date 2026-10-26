package com.donatrack.logistica.client;

import com.donatrack.logistica.domain.Camion;
import com.donatrack.logistica.dto.PlanificadorCallbackDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Strategy resiliente con patrón Circuit Fallback:
 * Intenta invocar al servicio HTTP externo VRP y conmuta automáticamente al simulador local
 * si el optimizador externo no responde o no está disponible.
 */
@Component
@Primary
public class ResilientRutaOptimizerClient implements RutaOptimizerClient {

    private static final Logger log = LoggerFactory.getLogger(ResilientRutaOptimizerClient.class);

    private final HttpVrpOptimizerClient httpClient;
    private final MockVrpOptimizerClient mockClient;

    @Value("${donatrack.vrp.http-enabled:false}")
    private boolean httpEnabled;

    public ResilientRutaOptimizerClient(HttpVrpOptimizerClient httpClient, MockVrpOptimizerClient mockClient) {
        this.httpClient = httpClient;
        this.mockClient = mockClient;
    }

    @Override
    public PlanificadorCallbackDto optimizarLote(String loteId, List<Long> donacionIds, Camion camion) {
        if (httpEnabled) {
            try {
                log.info("Despachando solicitud VRP por HTTP al proveedor externo...");
                return httpClient.optimizarLote(loteId, donacionIds, camion);
            } catch (Exception e) {
                log.warn("El proveedor externo VRP falló o no respondió ({}). Activando contingencia con Mock local...", e.getMessage());
            }
        }
        return mockClient.optimizarLote(loteId, donacionIds, camion);
    }
}
