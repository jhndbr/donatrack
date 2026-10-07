package com.donatrack.donaciones.service;

import com.donatrack.donaciones.domain.Donacion;
import com.donatrack.donaciones.domain.NecesidadMaterial;
import com.donatrack.donaciones.matchmaking.*;
import com.donatrack.donaciones.repository.DonacionRepository;
import com.donatrack.donaciones.repository.NecesidadMaterialRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class MatchmakingService {

    private final DonacionRepository donacionRepository;
    private final NecesidadMaterialRepository necesidadRepository;
    private final CompatibilidadSemanticaStrategy estrategiaSemantica;
    private final PrioridadSubatendidosStrategy estrategiaSubatendidos;

    public MatchmakingService(DonacionRepository donacionRepository,
                              NecesidadMaterialRepository necesidadRepository,
                              CompatibilidadSemanticaStrategy estrategiaSemantica,
                              PrioridadSubatendidosStrategy estrategiaSubatendidos) {
        this.donacionRepository = donacionRepository;
        this.necesidadRepository = necesidadRepository;
        this.estrategiaSemantica = estrategiaSemantica;
        this.estrategiaSubatendidos = estrategiaSubatendidos;
    }

    @Transactional(readOnly = true)
    public MatchmakingRankingResult evaluarDonacion(Long donacionId) {
        Donacion donacion = donacionRepository.findById(donacionId)
                .orElseThrow(() -> new IllegalArgumentException("Donacion no encontrada con ID: " + donacionId));

        // Solo se evaluan necesidades con la misma subcategoria (unidad minima)
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

        // Filtrar necesidades coincidentes en ambos Top 10
        Set<Long> idsTopSemantico = rankingSemantico.stream()
                .map(MatchmakingRankingResult.EvaluacionPar::getIdNecesidad)
                .collect(Collectors.toSet());

        List<MatchmakingRankingResult.EvaluacionPar> coincidencias = rankingSubatendidos.stream()
                .filter(p -> idsTopSemantico.contains(p.getIdNecesidad()))
                .collect(Collectors.toList());

        return new MatchmakingRankingResult(donacion.getId(), donacion.getSubcategoria(),
                rankingSemantico, rankingSubatendidos, coincidencias);
    }
}
