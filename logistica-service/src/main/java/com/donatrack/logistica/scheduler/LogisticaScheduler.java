package com.donatrack.logistica.scheduler;

import com.donatrack.logistica.service.PlanificacionRutasService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class LogisticaScheduler {

    private static final Logger log = LoggerFactory.getLogger(LogisticaScheduler.class);

    private final PlanificacionRutasService planificacionRutasService;

    @Value("${donatrack.rutas.max-lote-donaciones:100}")
    private int maxLote;

    public LogisticaScheduler(PlanificacionRutasService planificacionRutasService) {
        this.planificacionRutasService = planificacionRutasService;
    }

    /**
     * Cronjob: Ejecución en horarios de baja carga para generar los planes de rutas
     * de los camiones durante la siguiente jornada operativa, integrándose con el optimizador externo en lotes <= 100.
     */
    @Scheduled(cron = "${donatrack.cron.planificacion-rutas:0 0 1 * * ?}")
    public void dispararPlanificacionRutasNocturna() {
        log.info("Iniciando tarea programada nocturna de planificación de rutas en lotes <= {} (CU-37)...", maxLote);
        try {
            planificacionRutasService.ejecutarPlanificacionNocturna();
        } catch (Exception e) {
            log.error("Error durante la ejecución nocturna de planificación de rutas: {}", e.getMessage(), e);
        }
    }
}
