package com.donatrack.donaciones.service;

import com.donatrack.donaciones.domain.Donacion;
import com.donatrack.donaciones.domain.NecesidadMaterial;
import com.donatrack.donaciones.matchmaking.*;
import com.donatrack.donaciones.repository.DonacionRepository;
import com.donatrack.donaciones.repository.NecesidadMaterialRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Servicio de Asignación Automática y Matchmaking (donaciones-service) - DonaTrack
 * Orquesta la evaluación de afinidad algorítmica entre donaciones en depósito y necesidades activas.
 * Implementa el Patrón Registry (OCP) para desacoplar el core de nuevas estrategias futuras.
 */
@Service
public class MatchmakingService {

    private final DonacionRepository donacionRepository;
    private final NecesidadMaterialRepository necesidadRepository;
    private final MatchmakingStrategyRegistry strategyRegistry;
    private final CompatibilidadSemanticaStrategy estrategiaSemantica;
    private final PrioridadSubatendidosStrategy estrategiaSubatendidos;

    @Autowired(required = false)
    private com.donatrack.donaciones.repository.ConfiguracionMatchmakingRepository configuracionRepository;

    @Autowired
    public MatchmakingService(DonacionRepository donacionRepository,
                              NecesidadMaterialRepository necesidadRepository,
                              MatchmakingStrategyRegistry strategyRegistry) {
        this.donacionRepository = donacionRepository;
        this.necesidadRepository = necesidadRepository;
        this.strategyRegistry = strategyRegistry;
        this.estrategiaSemantica = strategyRegistry.obtenerPorTipo(CompatibilidadSemanticaStrategy.class)
                .orElseGet(CompatibilidadSemanticaStrategy::new);
        this.estrategiaSubatendidos = strategyRegistry.obtenerPorTipo(PrioridadSubatendidosStrategy.class)
                .orElseGet(PrioridadSubatendidosStrategy::new);
    }

    @jakarta.annotation.PostConstruct
    public void cargarConfiguracionPersistida() {
        if (configuracionRepository != null) {
            try {
                configuracionRepository.findById(1L).ifPresent(cfg -> {
                    if (estrategiaSemantica != null) {
                        estrategiaSemantica.configurarPesos(cfg.getA1(), cfg.getA2(), cfg.getA3());
                    }
                    if (estrategiaSubatendidos != null && cfg.getAlpha() > 0) {
                        estrategiaSubatendidos.setAlpha(cfg.getAlpha());
                    }
                });
            } catch (Exception ignored) {
            }
        }
    }

    @Transactional
    public com.donatrack.donaciones.domain.ConfiguracionMatchmaking guardarConfiguracion(double a1, double a2, double a3, double alpha) {
        if (estrategiaSemantica != null) {
            estrategiaSemantica.configurarPesos(a1, a2, a3);
        }
        if (estrategiaSubatendidos != null) {
            estrategiaSubatendidos.setAlpha(alpha);
        }
        com.donatrack.donaciones.domain.ConfiguracionMatchmaking cfg =
                new com.donatrack.donaciones.domain.ConfiguracionMatchmaking(a1, a2, a3, alpha);
        if (configuracionRepository != null) {
            return configuracionRepository.save(cfg);
        }
        return cfg;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> obtenerConfiguracionActual() {
        if (configuracionRepository != null) {
            Optional<com.donatrack.donaciones.domain.ConfiguracionMatchmaking> opt = configuracionRepository.findById(1L);
            if (opt.isPresent()) {
                com.donatrack.donaciones.domain.ConfiguracionMatchmaking cfg = opt.get();
                return Map.of("a1", cfg.getA1(), "a2", cfg.getA2(), "a3", cfg.getA3(), "alpha", cfg.getAlpha());
            }
        }
        double a1 = estrategiaSemantica != null ? estrategiaSemantica.getA1() : 0.4;
        double a2 = estrategiaSemantica != null ? estrategiaSemantica.getA2() : 0.4;
        double a3 = estrategiaSemantica != null ? estrategiaSemantica.getA3() : 0.2;
        double alpha = estrategiaSubatendidos != null ? estrategiaSubatendidos.getAlpha() : 0.1;
        return Map.of("a1", a1, "a2", a2, "a3", a3, "alpha", alpha);
    }

    // Sobrecarga para pruebas unitarias directas
    public MatchmakingService(DonacionRepository donacionRepository,
                              NecesidadMaterialRepository necesidadRepository,
                              CompatibilidadSemanticaStrategy estrategiaSemantica,
                              PrioridadSubatendidosStrategy estrategiaSubatendidos) {
        this.donacionRepository = donacionRepository;
        this.necesidadRepository = necesidadRepository;
        this.estrategiaSemantica = estrategiaSemantica;
        this.estrategiaSubatendidos = estrategiaSubatendidos;
        List<MatchmakingStrategy> list = new ArrayList<>();
        if (estrategiaSemantica != null) list.add(estrategiaSemantica);
        if (estrategiaSubatendidos != null) list.add(estrategiaSubatendidos);
        this.strategyRegistry = new MatchmakingStrategyRegistry(list);
    }

    /**
     * Evalúa una donación contra las necesidades activas de la misma subcategoría (CU-30 a CU-33).
     * Retorna Top 10 de Compatibilidad Semántica, Top 10 de Subatendidos y Coincidencias Cruzadas.
     */
    @Transactional(readOnly = true)
    public MatchmakingRankingResult evaluarDonacion(Long donacionId) {
        Donacion donacion = donacionRepository.findById(donacionId)
                .orElseThrow(() -> new IllegalArgumentException("Donacion no encontrada con ID: " + donacionId));

        // Solo se evaluan necesidades con la misma subcategoria (unidad minima) y activas
        List<NecesidadMaterial> necesidades = necesidadRepository.findPendientesPorSubcategoria(donacion.getSubcategoria());

        List<MatchmakingRankingResult.EvaluacionPar> rankingSemantico = necesidades.stream()
                .map(n -> new MatchmakingRankingResult.EvaluacionPar(n, estrategiaSemantica.calcularScore(donacion, n)))
                .sorted(Comparator.comparingDouble(MatchmakingRankingResult.EvaluacionPar::getScore).reversed())
                .limit(10)
                .collect(Collectors.toList());

        List<MatchmakingRankingResult.EvaluacionPar> rankingSubatendidos = necesidades.stream()
                .map(n -> new MatchmakingRankingResult.EvaluacionPar(n, estrategiaSubatendidos.calcularScore(donacion, n)))
                .sorted(Comparator.comparingDouble(MatchmakingRankingResult.EvaluacionPar::getScore).reversed())
                .limit(10)
                .collect(Collectors.toList());

        // Filtrar necesidades coincidentes en ambos Top 10 (CU-33)
        Set<Long> idsTopSemantico = rankingSemantico.stream()
                .map(MatchmakingRankingResult.EvaluacionPar::getIdNecesidad)
                .collect(Collectors.toSet());

        List<MatchmakingRankingResult.EvaluacionPar> coincidencias = rankingSubatendidos.stream()
                .filter(p -> idsTopSemantico.contains(p.getIdNecesidad()))
                .collect(Collectors.toList());

        return new MatchmakingRankingResult(donacion.getId(), donacion.getSubcategoria(),
                rankingSemantico, rankingSubatendidos, coincidencias);
    }

    /**
     * Evalúa dinámicamente una donación contra todas las estrategias registradas en el Registry (OCP).
     */
    @Transactional(readOnly = true)
    public Map<String, List<MatchmakingRankingResult.EvaluacionPar>> evaluarConTodasLasEstrategias(Long donacionId) {
        Donacion donacion = donacionRepository.findById(donacionId)
                .orElseThrow(() -> new IllegalArgumentException("Donacion no encontrada con ID: " + donacionId));

        List<NecesidadMaterial> necesidades = necesidadRepository.findPendientesPorSubcategoria(donacion.getSubcategoria());
        Map<String, List<MatchmakingRankingResult.EvaluacionPar>> resultadosPorEstrategia = new LinkedHashMap<>();

        for (MatchmakingStrategy strategy : strategyRegistry.listarEstrategias()) {
            List<MatchmakingRankingResult.EvaluacionPar> top10 = necesidades.stream()
                    .map(n -> new MatchmakingRankingResult.EvaluacionPar(n, strategy.calcularScore(donacion, n)))
                    .sorted(Comparator.comparingDouble(MatchmakingRankingResult.EvaluacionPar::getScore).reversed())
                    .limit(10)
                    .collect(Collectors.toList());
            resultadosPorEstrategia.put(strategy.getNombre(), top10);
        }

        return resultadosPorEstrategia;
    }

    public MatchmakingStrategyRegistry getStrategyRegistry() {
        return strategyRegistry;
    }
}
