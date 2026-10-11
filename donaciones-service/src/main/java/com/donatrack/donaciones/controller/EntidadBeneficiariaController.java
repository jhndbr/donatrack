package com.donatrack.donaciones.controller;

import com.donatrack.donaciones.domain.EntidadBeneficiaria;
import com.donatrack.donaciones.domain.NecesidadMaterial;
import com.donatrack.donaciones.repository.EntidadBeneficiariaRepository;
import com.donatrack.donaciones.repository.NecesidadMaterialRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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

    @GetMapping("/{id}")
    public ResponseEntity<EntidadBeneficiaria> obtenerEntidad(@PathVariable("id") Long id) {
        return entidadRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<EntidadBeneficiaria> crearEntidad(@RequestBody EntidadBeneficiaria entidad) {
        return ResponseEntity.ok(entidadRepository.save(entidad));
    }

    @PutMapping("/{id}")
    public ResponseEntity<EntidadBeneficiaria> modificarEntidad(@PathVariable("id") Long id,
                                                                @RequestBody Map<String, Object> body) {
        return entidadRepository.findById(id).map(entidad -> {
            if (body.containsKey("razonSocial")) entidad.setRazonSocial((String) body.get("razonSocial"));
            if (body.containsKey("direccion")) entidad.setDireccion((String) body.get("direccion"));
            if (body.containsKey("telefono")) entidad.setTelefono((String) body.get("telefono"));
            if (body.containsKey("emailPrincipal")) {
                entidad.getEmailsRepresentantes().clear();
                entidad.getEmailsRepresentantes().add((String) body.get("emailPrincipal"));
            }
            return ResponseEntity.ok(entidadRepository.save(entidad));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarEntidad(@PathVariable("id") Long id) {
        if (entidadRepository.existsById(id)) {
            entidadRepository.deleteById(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/{entidadId}/necesidades")
    public ResponseEntity<List<NecesidadMaterial>> listarNecesidadesPorEntidad(@PathVariable("entidadId") Long entidadId) {
        return entidadRepository.findById(entidadId)
                .map(entidad -> ResponseEntity.ok(entidad.getNecesidades()))
                .orElse(ResponseEntity.notFound().build());
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

    @PutMapping("/necesidades/{necesidadId}")
    public ResponseEntity<NecesidadMaterial> modificarNecesidad(@PathVariable("necesidadId") Long necesidadId,
                                                                @RequestBody Map<String, Object> body) {
        return necesidadRepository.findById(necesidadId).map(nec -> {
            if (body.containsKey("subcategoria")) nec.setSubcategoria((String) body.get("subcategoria"));
            if (body.containsKey("descripcion")) nec.setDescripcion((String) body.get("descripcion"));
            if (body.containsKey("cantidadSolicitada")) nec.setCantidadSolicitada(((Number) body.get("cantidadSolicitada")).doubleValue());
            if (body.containsKey("unidadMedida")) nec.setUnidadMedida((String) body.get("unidadMedida"));
            if (body.containsKey("tipo")) nec.setTipo(com.donatrack.common.enums.TipoNecesidad.valueOf((String) body.get("tipo")));
            if (body.containsKey("periodo")) nec.setPeriodo((String) body.get("periodo"));
            return ResponseEntity.ok(necesidadRepository.save(nec));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/necesidades/{necesidadId}")
    public ResponseEntity<Void> cancelarNecesidad(@PathVariable("necesidadId") Long necesidadId) {
        if (necesidadRepository.existsById(necesidadId)) {
            necesidadRepository.deleteById(necesidadId);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/necesidades/pendientes")
    public ResponseEntity<List<NecesidadMaterial>> listarPendientes(@RequestParam("subcategoria") String subcategoria) {
        return ResponseEntity.ok(necesidadRepository.findPendientesPorSubcategoria(subcategoria));
    }
}
