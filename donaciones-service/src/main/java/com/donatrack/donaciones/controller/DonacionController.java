package com.donatrack.donaciones.controller;

import com.donatrack.common.enums.EstadoDonacion;
import com.donatrack.donaciones.domain.BienDonado;
import com.donatrack.donaciones.domain.Donacion;
import com.donatrack.donaciones.matchmaking.MatchmakingRankingResult;
import com.donatrack.donaciones.repository.DonacionRepository;
import com.donatrack.donaciones.service.DonacionService;
import com.donatrack.donaciones.service.MatchmakingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/donaciones")
public class DonacionController {

    private final DonacionService donacionService;
    private final MatchmakingService matchmakingService;
    private final DonacionRepository donacionRepository;

    public DonacionController(DonacionService donacionService,
                              MatchmakingService matchmakingService,
                              DonacionRepository donacionRepository) {
        this.donacionService = donacionService;
        this.matchmakingService = matchmakingService;
        this.donacionRepository = donacionRepository;
    }

    /**
     * Requerimiento 7 de Entrega 1:
     * El Servicio de Donaciones debera exponer un endpoint web simple que al invocarlo mediante un GET retorne:
     * "Hola desde el servicio de Donaciones."
     */
    @GetMapping("/saludo")
    public ResponseEntity<String> saludo() {
        return ResponseEntity.ok("Hola desde el servicio de Donaciones.");
    }

    @GetMapping
    public ResponseEntity<List<Donacion>> listarDonaciones(@RequestParam(value = "estado", required = false) EstadoDonacion estado) {
        if (estado != null) {
            return ResponseEntity.ok(donacionRepository.findByEstado(estado));
        }
        return ResponseEntity.ok(donacionRepository.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Donacion> obtenerPorId(@PathVariable("id") Long id) {
        return donacionRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/carga-segmentada")
    public ResponseEntity<List<Donacion>> registrarDonacionSegmentada(@RequestParam("donanteId") Long donanteId,
                                                                      @RequestBody List<BienDonado> bienes) {
        List<Donacion> donaciones = donacionService.registrarCargaSegmentada(donanteId, bienes);
        return ResponseEntity.ok(donaciones);
    }

    @GetMapping("/{id}/matchmaking")
    public ResponseEntity<MatchmakingRankingResult> ejecutarMatchmaking(@PathVariable("id") Long id) {
        return ResponseEntity.ok(matchmakingService.evaluarDonacion(id));
    }

    @PostMapping("/{id}/asignar")
    public ResponseEntity<Donacion> asignarDonacion(@PathVariable("id") Long id, @RequestParam("entidadId") Long entidadId) {
        Donacion asignada = donacionService.asignarDonacion(id, entidadId);
        return ResponseEntity.ok(asignada);
    }

    @PatchMapping("/{id}/estado")
    public ResponseEntity<Donacion> actualizarEstado(@PathVariable("id") Long id,
                                                     @RequestBody Map<String, String> request) {
        EstadoDonacion nuevoEstado = EstadoDonacion.valueOf(request.get("nuevoEstado"));
        String justificacion = request.get("justificacion");
        Donacion actualizada = donacionService.actualizarEstado(id, nuevoEstado, justificacion);
        return ResponseEntity.ok(actualizada);
    }

    /**
     * Requerimiento del TP: Actualizacion masiva de estado para lotes logisticos
     */
    @PutMapping("/bulk-estado")
    public ResponseEntity<List<Donacion>> actualizarEstadoLote(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Number> idsRaw = (List<Number>) body.get("donacionIds");
        if (idsRaw == null || idsRaw.isEmpty()) {
            idsRaw = (List<Number>) body.get("idsDonaciones");
        }
        if (idsRaw == null || idsRaw.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        List<Long> ids = idsRaw.stream().map(Number::longValue).toList();
        EstadoDonacion nuevoEstado = EstadoDonacion.valueOf((String) body.get("nuevoEstado"));
        String justificacion = (String) body.get("justificacion");
        return ResponseEntity.ok(donacionService.actualizarEstadoLote(ids, nuevoEstado, justificacion));
    }

    /**
     * Endpoint para que logistica-service consulte donaciones listas para armar rutas en lotes
     */
    @GetMapping("/asignadas-logistica")
    public ResponseEntity<List<Donacion>> listarAsignadasParaLogistica() {
        return ResponseEntity.ok(donacionRepository.findAsignadasListasParaLogistica());
    }
}
