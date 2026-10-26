package com.donatrack.logistica.service;

import com.donatrack.logistica.domain.UbicacionGPS;
import com.donatrack.logistica.dto.CamionEnVivoDto;
import com.donatrack.logistica.dto.TelemetriaRequestDto;

import java.util.List;
import java.util.Optional;

public interface TelemetriaService {
    UbicacionGPS registrarTelemetria(TelemetriaRequestDto request);
    Optional<UbicacionGPS> obtenerUltimaPosicion(String patente);
    List<CamionEnVivoDto> obtenerFlotaEnVivo();
}
