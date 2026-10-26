package com.donatrack.donaciones.controller;

import com.donatrack.common.enums.PeriodoNecesidad;
import com.donatrack.common.enums.TipoNecesidad;
import com.donatrack.donaciones.domain.EntidadBeneficiaria;
import com.donatrack.donaciones.domain.NecesidadMaterial;
import com.donatrack.donaciones.repository.NecesidadMaterialRepository;
import com.donatrack.donaciones.service.EntidadBeneficiariaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/entidades")
public class EntidadBeneficiariaController {

    private final EntidadBeneficiariaService entidadService;
    private final NecesidadMaterialRepository necesidadRepository;

    public EntidadBeneficiariaController(EntidadBeneficiariaService entidadService,
                                         NecesidadMaterialRepository necesidadRepository) {
        this.entidadService = entidadService;
        this.necesidadRepository = necesidadRepository;
    }

    @GetMapping
    public ResponseEntity<List<EntidadBeneficiaria>> listarEntidades(@RequestParam(value = "soloActivas", required = false, defaultValue = "true") boolean soloActivas) {
        return ResponseEntity.ok(entidadService.listarEntidades(soloActivas));
    }

    @GetMapping("/buscar")
    public ResponseEntity<EntidadBeneficiaria> buscarEntidad(
            @RequestParam(value = "email", required = false) String email,
            @RequestParam(value = "razonSocial", required = false) String razonSocial) {
        if (email != null && !email.isBlank()) {
            return entidadService.obtenerPorEmail(email.trim())
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        }
        if (razonSocial != null && !razonSocial.isBlank()) {
            return entidadService.obtenerPorRazonSocial(razonSocial.trim())
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        }
        return ResponseEntity.badRequest().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<EntidadBeneficiaria> obtenerEntidad(@PathVariable("id") Long id) {
        return entidadService.obtenerPorId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<EntidadBeneficiaria> crearEntidad(@RequestBody EntidadBeneficiaria entidad) {
        return ResponseEntity.ok(entidadService.registrarEntidad(entidad));
    }

    /**
     * Baja lógica de entidad beneficiaria (CU-25).
     * Invariante de negocio: Bloquea si posee donaciones activas en tránsito o asignadas.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<EntidadBeneficiaria> darDeBajaEntidad(@PathVariable("id") Long id,
                                                                @RequestParam(value = "motivo", required = false, defaultValue = "Baja de entidad") String motivo) {
        EntidadBeneficiaria inactivada = entidadService.darDeBajaLogica(id, motivo);
        return ResponseEntity.ok(inactivada);
    }

    /**
     * Reactivación administrativa de una entidad inactiva (CU-25).
     */
    @PostMapping("/{id}/reactivar")
    public ResponseEntity<EntidadBeneficiaria> reactivarEntidad(@PathVariable("id") Long id) {
        return ResponseEntity.ok(entidadService.reactivarEntidad(id));
    }

    @GetMapping("/{entidadId}/necesidades")
    public ResponseEntity<List<NecesidadMaterial>> listarNecesidadesPorEntidad(@PathVariable("entidadId") Long entidadId) {
        return ResponseEntity.ok(necesidadRepository.findByEntidadBeneficiariaIdAndActivaTrue(entidadId));
    }

    @PostMapping("/{entidadId}/necesidades")
    public ResponseEntity<NecesidadMaterial> registrarNecesidad(@PathVariable("entidadId") Long entidadId,
                                                                @RequestBody NecesidadMaterial necesidad) {
        return ResponseEntity.ok(entidadService.registrarNecesidad(entidadId, necesidad));
    }

    @PutMapping({"/necesidades/{necesidadId}", "/{entidadId}/necesidades/{necesidadId}"})
    public ResponseEntity<NecesidadMaterial> modificarNecesidad(@PathVariable("necesidadId") Long necesidadId,
                                                                @RequestBody Map<String, Object> body) {
        Double nuevaCantidad = null;
        if (body.get("cantidadSolicitada") instanceof Number num) {
            nuevaCantidad = num.doubleValue();
        } else if (body.get("cantidadSolicitada") instanceof String str && !str.isBlank()) {
            nuevaCantidad = Double.parseDouble(str.trim());
        }
        String nuevaDescripcion = body.get("descripcion") != null ? String.valueOf(body.get("descripcion")) : null;
        String nuevaSubcategoria = body.get("subcategoria") != null ? String.valueOf(body.get("subcategoria")) : null;
        String nuevaUnidadMedida = body.get("unidadMedida") != null ? String.valueOf(body.get("unidadMedida")) : null;
        String nuevoPeriodo = body.get("periodo") != null ? String.valueOf(body.get("periodo")) : null;
        TipoNecesidad nuevoTipo = null;
        if (body.get("tipo") != null && !String.valueOf(body.get("tipo")).isBlank()) {
            try {
                nuevoTipo = TipoNecesidad.valueOf(String.valueOf(body.get("tipo")).trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
            }
        }

        if (nuevaSubcategoria == null && nuevaUnidadMedida == null && nuevoPeriodo == null && nuevoTipo == null) {
            return ResponseEntity.ok(entidadService.modificarNecesidad(necesidadId, nuevaCantidad, nuevaDescripcion));
        }
        return ResponseEntity.ok(entidadService.modificarNecesidad(
                necesidadId, nuevaSubcategoria, nuevaDescripcion, nuevoTipo, nuevaCantidad, nuevaUnidadMedida, nuevoPeriodo));
    }

    /**
     * Cancelación formal de necesidad con motivo de justificación (CU-29).
     */
    @DeleteMapping({"/necesidades/{necesidadId}", "/{entidadId}/necesidades/{necesidadId}"})
    public ResponseEntity<NecesidadMaterial> cancelarNecesidad(@PathVariable("necesidadId") Long necesidadId,
                                                               @RequestParam(value = "motivo", required = false, defaultValue = "Cancelación administrativa") String motivo) {
        return ResponseEntity.ok(entidadService.cancelarNecesidad(necesidadId, motivo));
    }

    @GetMapping("/necesidades/periodos")
    public ResponseEntity<PeriodoNecesidad[]> listarPeriodosPermitidos() {
        return ResponseEntity.ok(PeriodoNecesidad.values());
    }

    @GetMapping("/necesidades/pendientes")
    public ResponseEntity<List<NecesidadMaterial>> listarPendientes(@RequestParam("subcategoria") String subcategoria) {
        return ResponseEntity.ok(necesidadRepository.findPendientesPorSubcategoria(subcategoria));
    }
}
