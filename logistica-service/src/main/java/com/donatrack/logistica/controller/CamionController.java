package com.donatrack.logistica.controller;

import com.donatrack.logistica.domain.Camion;
import com.donatrack.logistica.dto.CamionRequestDto;
import com.donatrack.logistica.service.FlotaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping({"/api/v1/logistica/camiones", "/api/v1/camiones"})
public class CamionController {

    private final FlotaService flotaService;

    public CamionController(FlotaService flotaService) {
        this.flotaService = flotaService;
    }

    @GetMapping
    public ResponseEntity<List<Camion>> listarCamiones(@RequestParam(value = "activo", required = false) Boolean activo) {
        return ResponseEntity.ok(flotaService.listarCamiones(activo));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Camion> obtenerPorId(@PathVariable("id") String id) {
        return ResponseEntity.ok(flotaService.obtenerPorId(id));
    }

    @PostMapping
    public ResponseEntity<Camion> registrarCamion(@Valid @RequestBody CamionRequestDto dto) {
        Camion creado = flotaService.registrarCamion(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Camion> actualizarCamion(@PathVariable("id") String id,
                                                   @Valid @RequestBody CamionRequestDto dto) {
        Camion actualizado = flotaService.actualizarCamion(id, dto);
        return ResponseEntity.ok(actualizado);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> darDeBajaCamion(@PathVariable("id") String id,
                                                @RequestParam(value = "motivo", required = false, defaultValue = "Baja solicitada por administración") String motivo) {
        flotaService.darDeBajaCamion(id, motivo);
        return ResponseEntity.noContent().build();
    }
}
