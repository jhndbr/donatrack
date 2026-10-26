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
    private final com.donatrack.incentivos.repository.InsigniaRepository insigniaRepository;
    private final com.donatrack.incentivos.scheduler.IncentivosScheduler incentivosScheduler;

    public IncentivosController(IncentivosService incentivosService,
                                PerfilDonanteIncentivoRepository perfilRepository,
                                ProgresoMisionRepository progresoRepository,
                                RankingMensualRepository rankingRepository,
                                com.donatrack.incentivos.repository.MisionRepository misionRepository,
                                com.donatrack.incentivos.repository.InsigniaRepository insigniaRepository,
                                @org.springframework.beans.factory.annotation.Autowired(required = false) com.donatrack.incentivos.scheduler.IncentivosScheduler incentivosScheduler) {
        this.incentivosService = incentivosService;
        this.perfilRepository = perfilRepository;
        this.progresoRepository = progresoRepository;
        this.rankingRepository = rankingRepository;
        this.misionRepository = misionRepository;
        this.insigniaRepository = insigniaRepository;
        this.incentivosScheduler = incentivosScheduler;
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

    /**
     * CU-47: Consulta de métricas y analítica avanzada del donante.
     */
    @GetMapping("/donantes/{idDonante}/analitica")
    public ResponseEntity<Map<String, Object>> obtenerAnalitica(@PathVariable("idDonante") Long idDonante) {
        Map<String, Object> analitica = incentivosService.obtenerAnaliticaDonante(idDonante);
        if (analitica.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(analitica);
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

    /**
     * Ejecución forzada manual del scheduler de ranking mensual (CU-54).
     */
    @PostMapping("/rankings/forzar-calculo")
    public ResponseEntity<List<RankingMensual>> forzarCalculoRanking(@RequestParam(value = "anio", required = false) Integer anio,
                                                                    @RequestParam(value = "mes", required = false) Integer mes) {
        if (incentivosScheduler == null) {
            return ResponseEntity.internalServerError().build();
        }
        int a = anio != null ? anio : LocalDate.now().getYear();
        int m = mes != null ? mes : LocalDate.now().getMonthValue();
        List<RankingMensual> ranking = incentivosScheduler.calcularRankingParaMes(a, m);
        return ResponseEntity.ok(ranking);
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

    /**
     * CU-52: Configurar visibilidad de insignias (publica / privada)
     * Soporta tanto query param (?visible=true) como body JSON ({"visible": true}) defensivamente.
     */
    @PatchMapping("/insignias/{id}/visibilidad")
    public ResponseEntity<Insignia> configurarVisibilidadInsignia(@PathVariable("id") Long id,
                                                                  @RequestParam(value = "visible", required = false) Boolean visibleParam,
                                                                  @RequestBody(required = false) Map<String, Object> body) {
        boolean visible = true;
        if (visibleParam != null) {
            visible = visibleParam;
        } else if (body != null && body.containsKey("visible")) {
            Object val = body.get("visible");
            visible = Boolean.parseBoolean(String.valueOf(val));
        }

        final boolean esVisible = visible;
        return insigniaRepository.findById(id).map(insignia -> {
            insignia.setEsPublica(esVisible);
            return ResponseEntity.ok(insigniaRepository.save(insignia));
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * CU-55: Publicar insignia en red social
     */
    @PostMapping("/insignias/{id}/publicar-red-social")
    public ResponseEntity<Map<String, Object>> publicarEnRedSocial(@PathVariable("id") Long id,
                                                                   @RequestParam(value = "redSocial", defaultValue = "TWITTER") String redSocial) {
        return insigniaRepository.findById(id).map(insignia -> {
            String mensaje = "¡He desbloqueado la insignia '" + insignia.getNombre() + "' colaborando con UTN Solidaria en DonaTrack!";
            String shareUrl = "https://donatrack.org/insignias/" + insignia.getId();
            Map<String, Object> resp = new java.util.HashMap<>();
            resp.put("status", "PUBLICADA");
            resp.put("insigniaId", insignia.getId());
            resp.put("insigniaNombre", insignia.getNombre());
            resp.put("redSocial", redSocial);
            resp.put("mensajeDifusion", mensaje);
            resp.put("shareUrl", shareUrl);
            return ResponseEntity.ok(resp);
        }).orElse(ResponseEntity.notFound().build());
    }
}
