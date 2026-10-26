package com.donatrack.donaciones.matchmaking;

import com.donatrack.donaciones.domain.Donacion;
import com.donatrack.donaciones.domain.EntidadBeneficiaria;
import com.donatrack.donaciones.domain.NecesidadMaterial;
import com.donatrack.donaciones.repository.DonacionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Estrategia de asignación prioritaria a Entidades Subatendidas (CU-32).
 * Pondera inversamente a las organizaciones según el historial de donaciones efectivamente
 * recibidas en el último trimestre (ventana móvil de 90 días):
 * S_sub = 1 / (1 + alpha * N) con factor de suavizado alpha = 0.1.
 */
@Component
public class PrioridadSubatendidosStrategy implements MatchmakingStrategy {

    private final DonacionRepository donacionRepository;

    @Value("${donatrack.matchmaking.subatendidos.alpha:0.1}")
    private double alpha = 0.1;

    @Autowired
    public PrioridadSubatendidosStrategy(DonacionRepository donacionRepository) {
        this.donacionRepository = donacionRepository;
    }

    public PrioridadSubatendidosStrategy() {
        this.donacionRepository = null;
    }

    public PrioridadSubatendidosStrategy(DonacionRepository donacionRepository, double alpha) {
        this.donacionRepository = donacionRepository;
        setAlpha(alpha);
    }

    @Override
    public String getNombre() {
        return "Prioridad a Subatendidos";
    }

    @Override
    public double calcularScore(Donacion donacion, NecesidadMaterial necesidad) {
        int donacionesTrimestre = 0;
        if (necesidad.getEntidadBeneficiaria() != null) {
            EntidadBeneficiaria entidad = necesidad.getEntidadBeneficiaria();
            if (donacionRepository != null && entidad.getId() != null) {
                // Consulta dinámica de entregas de los últimos 90 días (CU-32)
                LocalDateTime hace90Dias = LocalDateTime.now().minusDays(90);
                long conteoDinamico = donacionRepository.countEntregadasRecientesByEntidadId(entidad.getId(), hace90Dias);
                donacionesTrimestre = (int) conteoDinamico;
            } else {
                donacionesTrimestre = entidad.getDonacionesRecibidasTrimestre();
            }
        }

        // S_sub = 1 / (1 + alpha * CantDonacionesTrimestre)
        double score = 1.0 / (1.0 + (alpha * donacionesTrimestre));
        return Math.round(score * 10000.0) / 10000.0;
    }

    public double getAlpha() {
        return alpha;
    }

    public void setAlpha(double alpha) {
        if (alpha <= 0) {
            throw new IllegalArgumentException("El factor alpha debe ser mayor a 0");
        }
        this.alpha = alpha;
    }
}
