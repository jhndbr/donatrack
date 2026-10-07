package com.donatrack.logistica.controller;

import com.donatrack.logistica.domain.Camion;
import com.donatrack.logistica.repository.CamionRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/logistica/camiones")
public class CamionController {

    private final CamionRepository camionRepository;

    public CamionController(CamionRepository camionRepository) {
        this.camionRepository = camionRepository;
    }

    @GetMapping
    public ResponseEntity<List<Camion>> listarCamiones() {
        return ResponseEntity.ok(camionRepository.findAll());
    }

    @PostMapping
    public ResponseEntity<Camion> registrarCamion(@RequestBody Camion camion) {
        return ResponseEntity.ok(camionRepository.save(camion));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Camion> obtenerPorId(@PathVariable String id) {
        return camionRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
