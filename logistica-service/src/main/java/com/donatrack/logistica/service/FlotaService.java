package com.donatrack.logistica.service;

import com.donatrack.logistica.domain.Camion;
import com.donatrack.logistica.dto.CamionRequestDto;

import java.util.List;

public interface FlotaService {
    List<Camion> listarCamiones(Boolean activo);
    Camion obtenerPorId(String id);
    Camion obtenerPorPatente(String patente);
    Camion registrarCamion(CamionRequestDto dto);
    Camion actualizarCamion(String id, CamionRequestDto dto);
    void darDeBajaCamion(String id, String motivo);
    Camion obtenerCamionDisponible();
}
