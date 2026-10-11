package com.donatrack.donaciones.controller;

import com.donatrack.donaciones.domain.PersonaDonante;
import com.donatrack.donaciones.domain.PersonaHumana;
import com.donatrack.donaciones.domain.PersonaJuridica;
import com.donatrack.donaciones.repository.PersonaDonanteRepository;
import com.donatrack.donaciones.service.CsvDonanteImporterService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;

@RestController
@RequestMapping("/api/v1/donantes")
public class DonanteController {

    private final PersonaDonanteRepository donanteRepository;
    private final CsvDonanteImporterService csvImporterService;

    public DonanteController(PersonaDonanteRepository donanteRepository,
                             CsvDonanteImporterService csvImporterService) {
        this.donanteRepository = donanteRepository;
        this.csvImporterService = csvImporterService;
    }

    @GetMapping
    public ResponseEntity<List<PersonaDonante>> listarDonantes() {
        return ResponseEntity.ok(donanteRepository.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PersonaDonante> obtenerPorId(@PathVariable("id") Long id) {
        return donanteRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/humana")
    public ResponseEntity<PersonaHumana> crearDonanteHumana(@RequestBody PersonaHumana persona) {
        if (persona.getMediosContacto() != null) {
            persona.getMediosContacto().forEach(m -> m.setDonante(persona));
        }
        return ResponseEntity.ok(donanteRepository.save(persona));
    }

    @PostMapping("/juridica")
    public ResponseEntity<PersonaJuridica> crearDonanteJuridica(@RequestBody PersonaJuridica persona) {
        if (persona.getMediosContacto() != null) {
            persona.getMediosContacto().forEach(m -> m.setDonante(persona));
        }
        return ResponseEntity.ok(donanteRepository.save(persona));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PersonaDonante> modificarDonante(@PathVariable("id") Long id,
                                                           @RequestBody java.util.Map<String, Object> body) {
        return donanteRepository.findById(id).map(donante -> {
            if (donante instanceof PersonaHumana humana) {
                if (body.containsKey("nombre")) humana.setNombre((String) body.get("nombre"));
                if (body.containsKey("apellido")) humana.setApellido((String) body.get("apellido"));
                if (body.containsKey("edad")) humana.setEdad(((Number) body.get("edad")).intValue());
                if (body.containsKey("direccion")) humana.setDireccion((String) body.get("direccion"));
            } else if (donante instanceof PersonaJuridica juridica) {
                if (body.containsKey("razonSocial")) juridica.setRazonSocial((String) body.get("razonSocial"));
                if (body.containsKey("rubro")) juridica.setRubro((String) body.get("rubro"));
                if (body.containsKey("tipo")) juridica.setTipo((String) body.get("tipo"));
            }
            return ResponseEntity.ok(donanteRepository.save(donante));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarDonante(@PathVariable("id") Long id) {
        if (donanteRepository.existsById(id)) {
            donanteRepository.deleteById(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    /**
     * Importacion masiva de mas de 10.000 / 20.000 filas de donantes mediante Streaming CSV.
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
