package com.donatrack.incentivos.controller;

import com.donatrack.incentivos.domain.Insignia;
import com.donatrack.incentivos.domain.PerfilDonanteIncentivo;
import com.donatrack.incentivos.domain.ProgresoMision;
import com.donatrack.incentivos.domain.RankingMensual;
import com.donatrack.incentivos.repository.PerfilDonanteIncentivoRepository;
import com.donatrack.incentivos.repository.ProgresoMisionRepository;
import com.donatrack.incentivos.repository.RankingMensualRepository;
import com.donatrack.incentivos.service.IncentivosService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/incentivos")
public class IncentivosController {

    private final IncentivosService incentivosService;
    private final PerfilDonanteIncentivoRepository perfilRepository;
    private final ProgresoMisionRepository progresoRepository;
    private final RankingMensualRepository rankingRepository;
    private final com.donatrack.incentivos.repository.MisionRepository misionRepository;

    public IncentivosController(IncentivosService incentivosService,
                                PerfilDonanteIncentivoRepository perfilRepository,
                                ProgresoMisionRepository progresoRepository,
                                RankingMensualRepository rankingRepository,
                                com.donatrack.incentivos.repository.MisionRepository misionRepository) {
        this.incentivosService = incentivosService;
        this.perfilRepository = perfilRepository;
        this.progresoRepository = progresoRepository;
        this.rankingRepository = rankingRepository;
        this.misionRepository = misionRepository;
    }

    @GetMapping("/misiones/activas")
    public ResponseEntity<List<com.donatrack.incentivos.domain.Mision>> listarMisionesActivas() {
        return ResponseEntity.ok(misionRepository.findAll());
    }

    @GetMapping("/donantes/{idDonante}/metricas")
    public ResponseEntity<PerfilDonanteIncentivo> obtenerMetricas(@PathVariable("idDonante") Long idDonante) {
        return perfilRepository.findByIdDonante(idDonante)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/donantes/{idDonante}/misiones")
    public ResponseEntity<List<ProgresoMision>> obtenerMisiones(@PathVariable("idDonante") Long idDonante) {
        return ResponseEntity.ok(progresoRepository.findByIdDonante(idDonante));
    }

    @GetMapping("/donantes/{idDonante}/insignias")
    public ResponseEntity<List<Insignia>> obtenerInsignias(@PathVariable("idDonante") Long idDonante) {
        return perfilRepository.findByIdDonante(idDonante)
                .map(p -> ResponseEntity.ok(p.getInsignias()))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/rankings/mensual")
    public ResponseEntity<List<RankingMensual>> obtenerRankingMensual(@RequestParam(value = "anio", required = false) Integer anio,
                                                                      @RequestParam(value = "mes", required = false) Integer mes) {
        int a = anio != null ? anio : LocalDate.now().getYear();
        int m = mes != null ? mes : LocalDate.now().getMonthValue();
        return ResponseEntity.ok(rankingRepository.findByAnioAndMesOrderByPosicionAsc(a, m));
    }

    @PostMapping("/donantes/{idDonante}/actividad")
    public ResponseEntity<Void> registrarActividad(@PathVariable("idDonante") Long idDonante,
                                                   @RequestBody Map<String, Object> body) {
        String nombre = (String) body.get("nombre");
        String email = (String) body.get("email");
        double cantidadBienes = ((Number) body.get("cantidadBienes")).doubleValue();

        incentivosService.registrarImpactoDonacion(idDonante, nombre, email, cantidadBienes);
        return ResponseEntity.ok().build();
    }
}
