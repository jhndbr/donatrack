package com.donatrack.logistica.controller;

import com.donatrack.logistica.domain.UbicacionGPS;
import com.donatrack.logistica.repository.UbicacionGPSRepository;
import com.donatrack.logistica.service.LogisticaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/logistica/telemetria")
public class TelemetriaController {

    private final LogisticaService logisticaService;
    private final UbicacionGPSRepository gpsRepository;

    public TelemetriaController(LogisticaService logisticaService, UbicacionGPSRepository gpsRepository) {
        this.logisticaService = logisticaService;
        this.gpsRepository = gpsRepository;
    }

    @PostMapping("/posicion")
    public ResponseEntity<UbicacionGPS> reportarPosicion(@RequestBody Map<String, Object> body) {
        String idCamion = (String) body.get("idCamion");
        String patente = (String) body.get("patente");
        double latitud = ((Number) body.get("latitud")).doubleValue();
        double longitud = ((Number) body.get("longitud")).doubleValue();
        double velocidad = body.containsKey("velocidad") ? ((Number) body.get("velocidad")).doubleValue() : 0.0;

        UbicacionGPS pos = logisticaService.registrarTelemetriaGPS(idCamion, patente, latitud, longitud, velocidad);
        return ResponseEntity.ok(pos);
    }

    @GetMapping("/camion/{patente}/ultima")
    public ResponseEntity<UbicacionGPS> obtenerUltimaPosicion(@PathVariable("patente") String patente) {
        return gpsRepository.findFirstByPatenteOrderByTimestampDesc(patente)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
