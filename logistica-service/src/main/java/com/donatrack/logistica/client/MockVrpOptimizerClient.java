package com.donatrack.logistica.client;

import com.donatrack.logistica.domain.Camion;
import com.donatrack.logistica.dto.PlanificadorCallbackDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementación local simulada (Fallback) del planificador de rutas VRP.
 */
@Component("mockVrpOptimizerClient")
public class MockVrpOptimizerClient implements RutaOptimizerClient {

    private static final Logger log = LoggerFactory.getLogger(MockVrpOptimizerClient.class);

    @Override
    public PlanificadorCallbackDto optimizarLote(String loteId, List<Long> donacionIds, Camion camion) {
        log.info("[MockVRP] Optimizando lote {} con {} donaciones para camión {}",
                loteId, donacionIds.size(), camion != null ? camion.getPatente() : "DEFAULT");

        PlanificadorCallbackDto dto = new PlanificadorCallbackDto();
        dto.setLoteId(loteId);

        PlanificadorCallbackDto.RutaPropuestaDto rutaProp = new PlanificadorCallbackDto.RutaPropuestaDto();
        if (camion != null) {
            rutaProp.setIdCamion(camion.getId());
            rutaProp.setPatenteCamion(camion.getPatente());
        } else {
            rutaProp.setIdCamion("CAMION-01");
            rutaProp.setPatenteCamion("AE789XY");
        }

        List<PlanificadorCallbackDto.ParadaPropuestaDto> paradas = new ArrayList<>();
        int orden = 1;
        for (Long donacionId : donacionIds) {
            PlanificadorCallbackDto.ParadaPropuestaDto p = new PlanificadorCallbackDto.ParadaPropuestaDto();
            p.setOrden(orden++);
            p.setIdDonacion(donacionId);
            p.setIdEntidad(1L);
            p.setNombreEntidad("Entidad Beneficiaria Destino #" + (orden % 5 + 1));
            p.setDireccion("Av. Medrano " + (900 + orden * 15) + ", CABA");
            p.setLatitud(-34.5985 + (orden * 0.0012));
            p.setLongitud(-58.4201 + (orden * 0.0015));
            paradas.add(p);
        }

        rutaProp.setParadas(paradas);
        dto.setRutasGeneradas(List.of(rutaProp));
        dto.setDonacionesSinAsignar(List.of());

        return dto;
    }
}
