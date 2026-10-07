package com.donatrack.logistica.controller;

import com.donatrack.logistica.domain.Entrega;
import com.donatrack.logistica.domain.Parada;
import com.donatrack.logistica.domain.Ruta;
import com.donatrack.logistica.dto.PlanificadorCallbackDto;
import com.donatrack.logistica.repository.EntregaRepository;
import com.donatrack.logistica.repository.RutaRepository;
import com.donatrack.logistica.service.LogisticaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/logistica/rutas")
public class RutaController {

    private final RutaRepository rutaRepository;
    private final EntregaRepository entregaRepository;
    private final LogisticaService logisticaService;

    public RutaController(RutaRepository rutaRepository,
                           EntregaRepository entregaRepository,
                           LogisticaService logisticaService) {
        this.rutaRepository = rutaRepository;
        this.entregaRepository = entregaRepository;
        this.logisticaService = logisticaService;
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
     * Requerimiento del TP: Lotes de maximo 100 donaciones hacia el ruteador externo
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
     * Requerimiento del TP: URL de callback donde el componente externo notifica el resultado de la planificacion
     */
    @PostMapping("/callback")
    public ResponseEntity<Map<String, Object>> callbackPlanificador(@RequestBody PlanificadorCallbackDto callbackDto) {
        List<Ruta> rutasGuardadas = new ArrayList<>();

        if (callbackDto.getRutasGeneradas() != null) {
            for (PlanificadorCallbackDto.RutaPropuestaDto propuesta : callbackDto.getRutasGeneradas()) {
                Ruta ruta = new Ruta(propuesta.getIdCamion(), propuesta.getPatenteCamion(), LocalDate.now().plusDays(1));
                if (propuesta.getParadas() != null) {
                    for (PlanificadorCallbackDto.ParadaPropuestaDto paradaDto : propuesta.getParadas()) {
                        Parada p = new Parada(
                                paradaDto.getOrden(),
                                paradaDto.getIdEntidad(),
                                paradaDto.getNombreEntidad(),
                                paradaDto.getDireccion(),
                                paradaDto.getLatitud(),
                                paradaDto.getLongitud(),
                                paradaDto.getIdDonacion()
                        );
                        ruta.agregarParada(p);
                    }
                }
                Ruta salvada = rutaRepository.save(ruta);
                rutasGuardadas.add(salvada);

                // Crear entregas asociadas a la ruta
                for (Parada p : ruta.getParadas()) {
                    Entrega entrega = new Entrega(
                            salvada.getId(),
                            p.getIdDonacion(),
                            p.getIdEntidadBeneficiaria(),
                            p.getNombreEntidad(),
                            "", // email se completa con servicio
                            null,
                            "",
                            salvada.getPatenteCamion()
                    );
                    entregaRepository.save(entrega);
                }
            }
        }

        return ResponseEntity.ok(Map.of(
                "status", "PROCESADO",
                "loteId", callbackDto.getLoteId() != null ? callbackDto.getLoteId() : "N/A",
                "rutasCreadas", rutasGuardadas.size(),
                "donacionesSinAsignarParaReintento", callbackDto.getDonacionesSinAsignar() != null ? callbackDto.getDonacionesSinAsignar().size() : 0
        ));
    }

    @PostMapping("/{id}/iniciar")
    public ResponseEntity<Ruta> iniciarRuta(@PathVariable("id") String id) {
        return ResponseEntity.ok(logisticaService.iniciarRuta(id));
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
