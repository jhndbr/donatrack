package com.donatrack.logistica.controller;

import com.donatrack.logistica.domain.UbicacionGPS;
import com.donatrack.logistica.dto.CamionEnVivoDto;
import com.donatrack.logistica.dto.TelemetriaRequestDto;
import com.donatrack.logistica.repository.UbicacionGPSRepository;
import com.donatrack.logistica.service.TelemetriaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/logistica/telemetria")
public class TelemetriaController {

    private final TelemetriaService telemetriaService;
    private final UbicacionGPSRepository gpsRepository;

    public TelemetriaController(TelemetriaService telemetriaService,
                                @Autowired(required = false) UbicacionGPSRepository gpsRepository) {
        this.telemetriaService = telemetriaService;
        this.gpsRepository = gpsRepository;
    }

    /**
     * Reporte de posición GPS desde el camión o la app móvil del chofer (CU-41).
     */
    @PostMapping("/posicion")
    public ResponseEntity<UbicacionGPS> reportarPosicion(@RequestBody Map<String, Object> body) {
        String idCamion = (String) body.get("idCamion");
        String patente = (String) body.get("patente");
        double latitud = ((Number) body.get("latitud")).doubleValue();
        double longitud = ((Number) body.get("longitud")).doubleValue();
        double velocidad = body.containsKey("velocidad") ? ((Number) body.get("velocidad")).doubleValue() : 0.0;
        String direccion = (String) body.get("direccion");

        TelemetriaRequestDto dto = new TelemetriaRequestDto(
                idCamion, patente, latitud, longitud, velocidad, LocalDateTime.now(), direccion
        );

        UbicacionGPS pos = telemetriaService.registrarTelemetria(dto);
        return ResponseEntity.ok(pos);
    }

    /**
     * Consulta de la última posición reportada de un camión por su patente.
     */
    @GetMapping("/camion/{patente}/ultima")
    public ResponseEntity<UbicacionGPS> obtenerUltimaPosicion(@PathVariable("patente") String patente) {
        return telemetriaService.obtenerUltimaPosicion(patente)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Dashboard en tiempo real con monitoreo de flota y antigüedad de señal (CU-63, CU-42).
     */
    @GetMapping("/flota-en-vivo")
    public ResponseEntity<List<CamionEnVivoDto>> obtenerFlotaEnVivo() {
        return ResponseEntity.ok(telemetriaService.obtenerFlotaEnVivo());
    }
}
