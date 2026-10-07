package com.donatrack.donaciones.controller;

import com.donatrack.donaciones.domain.EntidadBeneficiaria;
import com.donatrack.donaciones.domain.NecesidadMaterial;
import com.donatrack.donaciones.repository.EntidadBeneficiariaRepository;
import com.donatrack.donaciones.repository.NecesidadMaterialRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/entidades")
public class EntidadBeneficiariaController {

    private final EntidadBeneficiariaRepository entidadRepository;
    private final NecesidadMaterialRepository necesidadRepository;

    public EntidadBeneficiariaController(EntidadBeneficiariaRepository entidadRepository,
                                         NecesidadMaterialRepository necesidadRepository) {
        this.entidadRepository = entidadRepository;
        this.necesidadRepository = necesidadRepository;
    }

    @GetMapping
    public ResponseEntity<List<EntidadBeneficiaria>> listarEntidades() {
        return ResponseEntity.ok(entidadRepository.findAll());
    }

    @PostMapping
    public ResponseEntity<EntidadBeneficiaria> crearEntidad(@RequestBody EntidadBeneficiaria entidad) {
        return ResponseEntity.ok(entidadRepository.save(entidad));
    }

    @PostMapping("/{entidadId}/necesidades")
    public ResponseEntity<NecesidadMaterial> registrarNecesidad(@PathVariable("entidadId") Long entidadId,
                                                                @RequestBody NecesidadMaterial necesidad) {
        EntidadBeneficiaria entidad = entidadRepository.findById(entidadId)
                .orElseThrow(() -> new IllegalArgumentException("Entidad no encontrada: " + entidadId));
        entidad.agregarNecesidad(necesidad);
        necesidad.setEntidadBeneficiaria(entidad);
        return ResponseEntity.ok(necesidadRepository.save(necesidad));
    }

    @GetMapping("/necesidades/pendientes")
    public ResponseEntity<List<NecesidadMaterial>> listarPendientes(@RequestParam("subcategoria") String subcategoria) {
        return ResponseEntity.ok(necesidadRepository.findPendientesPorSubcategoria(subcategoria));
    }
}
