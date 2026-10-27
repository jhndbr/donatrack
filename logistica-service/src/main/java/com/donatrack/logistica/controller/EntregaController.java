package com.donatrack.logistica.controller;

import com.donatrack.logistica.domain.Entrega;
import com.donatrack.logistica.dto.ComprobanteEntregaDto;
import com.donatrack.logistica.dto.ConfirmarEntregaRequestDto;
import com.donatrack.logistica.dto.EntregaFallidaRequestDto;
import com.donatrack.logistica.dto.ReplanificarRequestDto;
import com.donatrack.logistica.service.EntregaService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/logistica/entregas")
public class EntregaController {

    private final EntregaService entregaService;

    public EntregaController(EntregaService entregaService) {
        this.entregaService = entregaService;
    }

    @GetMapping
    public ResponseEntity<List<Entrega>> listarTodas() {
        return ResponseEntity.ok(entregaService.listarTodas());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Entrega> obtenerPorId(@PathVariable("id") String id) {
        return ResponseEntity.ok(entregaService.obtenerPorId(id));
    }

    @GetMapping("/donacion/{idDonacion}")
    public ResponseEntity<Entrega> obtenerPorDonacion(@PathVariable("idDonacion") Long idDonacion) {
        return ResponseEntity.ok(entregaService.obtenerPorDonacion(idDonacion));
    }

    @GetMapping("/ruta/{idRuta}")
    public ResponseEntity<List<Entrega>> listarPorRuta(@PathVariable("idRuta") String idRuta) {
        return ResponseEntity.ok(entregaService.listarEntregasPorRuta(idRuta));
    }

    /**
     * Confirmación de recepción física en sede con carga obligatoria de fotos (CU-43).
     */
    @PostMapping("/{id}/confirmar")
    public ResponseEntity<Entrega> confirmarEntrega(@PathVariable("id") String id,
                                                    @Valid @RequestBody ConfirmarEntregaRequestDto request) {
        return ResponseEntity.ok(entregaService.confirmarEntrega(id, request));
    }

    /**
     * Registro de entrega no recibida o fallida con justificación obligatoria (CU-44).
     */
    @PostMapping({"/{id}/fallida", "/{id}/no-recibida"})
    public ResponseEntity<Entrega> registrarEntregaFallida(@PathVariable("id") String id,
                                                          @Valid @RequestBody EntregaFallidaRequestDto request) {
        return ResponseEntity.ok(entregaService.registrarEntregaFallida(id, request));
    }

    @PostMapping("/donacion/{idDonacion}/confirmar")
    public ResponseEntity<Entrega> confirmarEntregaPorDonacion(@PathVariable("idDonacion") Long idDonacion,
                                                               @Valid @RequestBody ConfirmarEntregaRequestDto request) {
        return ResponseEntity.ok(entregaService.confirmarEntregaPorDonacion(idDonacion, request));
    }

    @PostMapping({"/donacion/{idDonacion}/fallida", "/donacion/{idDonacion}/no-recibida"})
    public ResponseEntity<Entrega> registrarEntregaFallidaPorDonacion(@PathVariable("idDonacion") Long idDonacion,
                                                                      @Valid @RequestBody EntregaFallidaRequestDto request) {
        return ResponseEntity.ok(entregaService.registrarEntregaFallidaPorDonacion(idDonacion, request));
    }

    @PostMapping("/donacion/{idDonacion}/arribo")
    public ResponseEntity<Entrega> registrarArriboPorDonacion(@PathVariable("idDonacion") Long idDonacion,
                                                              @RequestBody(required = false) java.util.Map<String, Object> body) {
        String obs = (body != null && body.get("observaciones") != null) ? String.valueOf(body.get("observaciones")) : null;
        return ResponseEntity.ok(entregaService.registrarArriboSedePorDonacion(idDonacion, obs));
    }

    @PostMapping("/{id}/arribo")
    public ResponseEntity<Entrega> registrarArribo(@PathVariable("id") String id,
                                                   @RequestBody(required = false) java.util.Map<String, Object> body) {
        String obs = (body != null && body.get("observaciones") != null) ? String.valueOf(body.get("observaciones")) : null;
        return ResponseEntity.ok(entregaService.registrarArriboSede(id, obs));
    }

    /**
     * Flujo administrativo de replanificación si la carga regresó al depósito (CU-45).
     */
    @PostMapping("/{id}/replanificar")
    public ResponseEntity<Entrega> replanificarEntrega(@PathVariable("id") String id,
                                                       @RequestBody ReplanificarRequestDto request) {
        return ResponseEntity.ok(entregaService.replanificarEntrega(id, request));
    }

    @PostMapping("/donacion/{idDonacion}/replanificar")
    public ResponseEntity<Entrega> replanificarEntregaPorDonacion(@PathVariable("idDonacion") Long idDonacion,
                                                                 @RequestBody ReplanificarRequestDto request) {
        return ResponseEntity.ok(entregaService.replanificarEntregaPorDonacion(idDonacion, request));
    }

    /**
     * Carga de evidencia fotográfica adicional para trazabilidad (CU-43 / CU-46).
     */
    @PostMapping("/{id}/evidencias")
    public ResponseEntity<Entrega> adjuntarEvidencias(@PathVariable("id") String id,
                                                      @RequestBody(required = false) java.util.Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> fotos = (body != null && body.get("fotos") instanceof List<?> list)
                ? (List<String>) list
                : (body != null && body.get("fotoUrl") != null ? List.of(String.valueOf(body.get("fotoUrl"))) : List.of());
        return ResponseEntity.ok(entregaService.agregarEvidencias(id, fotos));
    }

    @PostMapping("/donacion/{idDonacion}/evidencias")
    public ResponseEntity<Entrega> adjuntarEvidenciasPorDonacion(@PathVariable("idDonacion") Long idDonacion,
                                                                 @RequestBody(required = false) java.util.Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> fotos = (body != null && body.get("fotos") instanceof List<?> list)
                ? (List<String>) list
                : (body != null && body.get("fotoUrl") != null ? List.of(String.valueOf(body.get("fotoUrl"))) : List.of());
        return ResponseEntity.ok(entregaService.agregarEvidenciasPorDonacion(idDonacion, fotos));
    }

    /**
     * Consulta del comprobante formal digital de entrega (CU-46).
     */
    @GetMapping("/{id}/comprobante")
    public ResponseEntity<ComprobanteEntregaDto> obtenerComprobante(@PathVariable("id") String id) {
        return ResponseEntity.ok(entregaService.obtenerComprobante(id));
    }

    @GetMapping("/donacion/{idDonacion}/comprobante")
    public ResponseEntity<ComprobanteEntregaDto> obtenerComprobantePorDonacion(@PathVariable("idDonacion") Long idDonacion) {
        return ResponseEntity.ok(entregaService.obtenerComprobantePorDonacion(idDonacion));
    }
}
