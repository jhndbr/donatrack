package com.donatrack.logistica.controller;

import com.donatrack.logistica.domain.Entrega;
import com.donatrack.logistica.domain.Ruta;
import com.donatrack.logistica.dto.PlanificadorCallbackDto;
import com.donatrack.logistica.repository.EntregaRepository;
import com.donatrack.logistica.repository.RutaRepository;
import com.donatrack.logistica.service.EntregaService;
import com.donatrack.logistica.service.LogisticaService;
import com.donatrack.logistica.service.PlanificacionRutasService;
import com.donatrack.logistica.service.RecorridoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/logistica/rutas")
public class RutaController {

    private final RutaRepository rutaRepository;
    private final EntregaRepository entregaRepository;
    private final LogisticaService logisticaService;
    private final PlanificacionRutasService planificacionRutasService;
    private final RecorridoService recorridoService;
    private final EntregaService entregaService;

    public RutaController(RutaRepository rutaRepository,
                           EntregaRepository entregaRepository,
                           LogisticaService logisticaService,
                           @Autowired(required = false) PlanificacionRutasService planificacionRutasService,
                           @Autowired(required = false) RecorridoService recorridoService,
                           @Autowired(required = false) EntregaService entregaService) {
        this.rutaRepository = rutaRepository;
        this.entregaRepository = entregaRepository;
        this.logisticaService = logisticaService;
        this.planificacionRutasService = planificacionRutasService;
        this.recorridoService = recorridoService;
        this.entregaService = entregaService;
    }

    @GetMapping
    public ResponseEntity<List<Ruta>> listarRutas() {
        return ResponseEntity.ok(rutaRepository.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Ruta> obtenerRuta(@PathVariable("id") String id) {
        return rutaRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Disparo bajo demanda de la planificación automática de rutas sobre todas las donaciones en ASIGNACION_REALIZADA (Modo Demo / Operativo / REST Spec).
     */
    @PostMapping({"/planificar-pendientes", "/planificaciones"})
    public ResponseEntity<Map<String, Object>> planificarPendientesAhora() {
        if (planificacionRutasService != null) {
            planificacionRutasService.ejecutarPlanificacionNocturna();
        }
        return ResponseEntity.ok(Map.of(
                "status", "EJECUTADO",
                "totalRutas", rutaRepository.count()
        ));
    }

    @GetMapping("/planificaciones/{id}")
    public ResponseEntity<Map<String, Object>> consultarPlanificacion(@PathVariable("id") String id) {
        return rutaRepository.findById(id)
                .map(r -> ResponseEntity.ok(Map.<String, Object>of(
                        "status", "COMPLETADO",
                        "idRuta", r.getId(),
                        "estado", r.getEstado().name(),
                        "patenteCamion", r.getPatenteCamion() != null ? r.getPatenteCamion() : "",
                        "paradasCount", r.getParadas() != null ? r.getParadas().size() : 0
                )))
                .orElse(ResponseEntity.ok(Map.of("status", "NO_ENCONTRADO", "id", id)));
    }

    /**
     * Planificación de lotes de hasta 100 donaciones (CU-37).
     */
    @PostMapping("/planificar-lote")
    public ResponseEntity<List<PlanificadorCallbackDto>> planificarLote(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Number> idsRaw = (List<Number>) body.get("donacionIds");
        if (idsRaw == null || idsRaw.isEmpty()) {
            idsRaw = (List<Number>) body.get("idsDonaciones");
        }
        if (idsRaw == null || idsRaw.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        List<Long> donacionIds = idsRaw.stream().map(Number::longValue).toList();
        String idCamion = (String) body.get("idCamion");

        List<PlanificadorCallbackDto> lotes = logisticaService.planificarLotes(donacionIds, idCamion);
        return ResponseEntity.ok(lotes);
    }

    /**
     * Webhook de callback invocado por el optimizador VRP (CU-38).
     */
    @PostMapping("/callback")
    public ResponseEntity<Map<String, Object>> callbackPlanificador(@RequestBody PlanificadorCallbackDto callbackDto) {
        if (planificacionRutasService != null) {
            Map<String, Object> resultado = planificacionRutasService.procesarCallback(callbackDto);
            return ResponseEntity.ok(resultado);
        }
        return ResponseEntity.ok(Map.of("status", "PROCESADO", "loteId", callbackDto.getLoteId() != null ? callbackDto.getLoteId() : "N/A"));
    }

    /**
     * Inicio de recorrido por el chofer (CU-40).
     */
    @PostMapping("/{id}/iniciar")
    public ResponseEntity<Ruta> iniciarRuta(@PathVariable("id") String id) {
        return ResponseEntity.ok(logisticaService.iniciarRuta(id));
    }

    /**
     * Fin de recorrido por el chofer.
     */
    @PostMapping("/{id}/finalizar")
    public ResponseEntity<Ruta> finalizarRuta(@PathVariable("id") String id) {
        if (recorridoService != null) {
            return ResponseEntity.ok(recorridoService.finalizarRuta(id));
        }
        return rutaRepository.findById(id)
                .map(r -> {
                    r.finalizarRuta();
                    return ResponseEntity.ok(rutaRepository.save(r));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/entregas/{idEntrega}/confirmar")
    public ResponseEntity<Entrega> confirmarEntrega(@PathVariable("idEntrega") String idEntrega,
                                                    @RequestBody(required = false) Map<String, List<String>> body) {
        List<String> fotos = (body != null && body.containsKey("fotos")) ? body.get("fotos") : List.of();
        return ResponseEntity.ok(logisticaService.confirmarEntrega(idEntrega, fotos));
    }

    @PostMapping("/entregas/{idEntrega}/fallida")
    public ResponseEntity<Entrega> entregaFallida(@PathVariable("idEntrega") String idEntrega,
                                                  @RequestBody Map<String, Object> body) {
        String motivo = (String) body.get("motivo");
        boolean replanificable = body.containsKey("replanificable") && (boolean) body.get("replanificable");
        return ResponseEntity.ok(logisticaService.registrarEntregaFallida(idEntrega, motivo, replanificable));
    }
}
