package com.donatrack.donaciones.controller;

import com.donatrack.donaciones.domain.PersonaDonante;
import com.donatrack.donaciones.domain.PersonaHumana;
import com.donatrack.donaciones.domain.PersonaJuridica;
import com.donatrack.donaciones.service.CsvDonanteImporterService;
import com.donatrack.donaciones.service.DonanteService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/donantes")
public class DonanteController {

    private final DonanteService donanteService;
    private final CsvDonanteImporterService csvImporterService;

    public DonanteController(DonanteService donanteService,
                             CsvDonanteImporterService csvImporterService) {
        this.donanteService = donanteService;
        this.csvImporterService = csvImporterService;
    }

    @GetMapping
    public ResponseEntity<List<PersonaDonante>> listarDonantes(@RequestParam(value = "soloActivos", required = false, defaultValue = "true") boolean soloActivos) {
        return ResponseEntity.ok(donanteService.listarDonantes(soloActivos));
    }

    @GetMapping("/buscar")
    public ResponseEntity<PersonaDonante> buscarDonante(
            @RequestParam(value = "email", required = false) String email,
            @RequestParam(value = "documento", required = false) String documento,
            @RequestParam(value = "cuit", required = false) String cuit) {
        if (email != null && !email.isBlank()) {
            return donanteService.obtenerPorEmail(email.trim())
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        }
        if (documento != null && !documento.isBlank()) {
            return donanteService.obtenerPorDocumento(documento.trim())
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        }
        if (cuit != null && !cuit.isBlank()) {
            return donanteService.obtenerPorCuit(cuit.trim())
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        }
        return ResponseEntity.badRequest().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<PersonaDonante> obtenerPorId(@PathVariable("id") Long id) {
        return donanteService.obtenerPorId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/humana")
    public ResponseEntity<PersonaHumana> crearDonanteHumana(@RequestBody PersonaHumana persona) {
        return ResponseEntity.ok(donanteService.registrarDonanteHumano(persona));
    }

    @PostMapping("/juridica")
    public ResponseEntity<PersonaJuridica> crearDonanteJuridica(@RequestBody PersonaJuridica persona) {
        return ResponseEntity.ok(donanteService.registrarDonanteJuridico(persona));
    }

    @PutMapping("/humana/{id}")
    public ResponseEntity<PersonaHumana> actualizarDonanteHumana(@PathVariable("id") Long id,
                                                                 @RequestBody PersonaHumana persona) {
        return ResponseEntity.ok(donanteService.actualizarPersonaHumana(id, persona));
    }

    @PutMapping("/juridica/{id}")
    public ResponseEntity<PersonaJuridica> actualizarDonanteJuridica(@PathVariable("id") Long id,
                                                                     @RequestBody PersonaJuridica persona) {
        return ResponseEntity.ok(donanteService.actualizarPersonaJuridica(id, persona));
    }

    @PutMapping("/{id}/contacto-preferido")
    public ResponseEntity<PersonaDonante> actualizarContactoPreferido(
            @PathVariable("id") Long id,
            @RequestParam(value = "canal", required = false) String canalParam,
            @RequestParam(value = "valor", required = false) String valorParam,
            @RequestBody(required = false) Map<String, String> body) {
        String canalStr = canalParam;
        String valorStr = valorParam;
        if (body != null) {
            if (canalStr == null) canalStr = body.getOrDefault("canal", body.get("tipo"));
            if (valorStr == null) valorStr = body.getOrDefault("valor", body.get("contacto"));
        }
        com.donatrack.common.enums.CanalNotificacion canal = canalStr != null
                ? com.donatrack.common.enums.CanalNotificacion.valueOf(canalStr.trim().toUpperCase())
                : com.donatrack.common.enums.CanalNotificacion.EMAIL;
        return ResponseEntity.ok(donanteService.actualizarContactoPreferido(id, canal, valorStr));
    }

    /**
     * Baja lógica de persona donante (CU-11).
     * Invariante de negocio: Bloquea si posee donaciones activas asociadas.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<PersonaDonante> darDeBajaDonante(@PathVariable("id") Long id,
                                                           @RequestParam(value = "motivo", required = false, defaultValue = "Baja solicitada") String motivo) {
        PersonaDonante donanteInactivado = donanteService.darDeBajaLogica(id, motivo);
        return ResponseEntity.ok(donanteInactivado);
    }

    /**
     * Reactivación administrativa de persona donante (CU-11).
     */
    @PostMapping("/{id}/reactivar")
    public ResponseEntity<PersonaDonante> reactivarDonante(@PathVariable("id") Long id) {
        return ResponseEntity.ok(donanteService.reactivarDonante(id));
    }

    /**
     * Importacion masiva de mas de 10.000 / 20.000 filas de donantes mediante Streaming CSV (CU-12).
     */
    @PostMapping(value = "/importar-csv", consumes = "multipart/form-data")
    public ResponseEntity<CsvDonanteImporterService.ImportSummary> importarCsv(@RequestParam("file") MultipartFile file) {
        try (InputStream is = file.getInputStream()) {
            CsvDonanteImporterService.ImportSummary summary = csvImporterService.importarDesdeStream(is);
            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            throw new RuntimeException("Error procesando carga de archivo CSV", e);
        }
    }
}
