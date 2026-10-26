package com.donatrack.logistica.service;

import com.donatrack.logistica.dto.PlanificadorCallbackDto;

import java.util.List;
import java.util.Map;

public interface PlanificacionRutasService {
    int TAMANIO_MAXIMO_LOTE = 100;

    List<PlanificadorCallbackDto> planificarLotes(List<Long> donacionIds, String idCamion);
    Map<String, Object> procesarCallback(PlanificadorCallbackDto callbackDto);
    void ejecutarPlanificacionNocturna();
}
