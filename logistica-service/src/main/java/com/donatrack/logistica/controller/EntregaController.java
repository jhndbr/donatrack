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

    @GetMapping("/{id}")
    public ResponseEntity<Entrega> obtenerPorId(@PathVariable("id") String id) {
        return ResponseEntity.ok(entregaService.obtenerPorId(id));
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
    @PostMapping("/{id}/fallida")
    public ResponseEntity<Entrega> registrarEntregaFallida(@PathVariable("id") String id,
                                                          @Valid @RequestBody EntregaFallidaRequestDto request) {
        return ResponseEntity.ok(entregaService.registrarEntregaFallida(id, request));
    }

    @PostMapping("/donacion/{idDonacion}/confirmar")
    public ResponseEntity<Entrega> confirmarEntregaPorDonacion(@PathVariable("idDonacion") Long idDonacion,
                                                               @Valid @RequestBody ConfirmarEntregaRequestDto request) {
        return ResponseEntity.ok(entregaService.confirmarEntregaPorDonacion(idDonacion, request));
    }

    @PostMapping("/donacion/{idDonacion}/fallida")
    public ResponseEntity<Entrega> registrarEntregaFallidaPorDonacion(@PathVariable("idDonacion") Long idDonacion,
                                                                      @Valid @RequestBody EntregaFallidaRequestDto request) {
        return ResponseEntity.ok(entregaService.registrarEntregaFallidaPorDonacion(idDonacion, request));
    }

    /**
     * Flujo administrativo de replanificación si la carga regresó al depósito (CU-45).
     */
    @PostMapping("/{id}/replanificar")
    public ResponseEntity<Entrega> replanificarEntrega(@PathVariable("id") String id,
                                                       @RequestBody ReplanificarRequestDto request) {
        return ResponseEntity.ok(entregaService.replanificarEntrega(id, request));
    }

    /**
     * Consulta del comprobante formal digital de entrega (CU-46).
     */
    @GetMapping("/{id}/comprobante")
    public ResponseEntity<ComprobanteEntregaDto> obtenerComprobante(@PathVariable("id") String id) {
        return ResponseEntity.ok(entregaService.obtenerComprobante(id));
    }
}
