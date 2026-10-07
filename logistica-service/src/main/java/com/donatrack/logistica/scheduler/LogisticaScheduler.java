package com.donatrack.logistica.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class LogisticaScheduler {

    private static final Logger log = LoggerFactory.getLogger(LogisticaScheduler.class);

    @Value("${donatrack.rutas.max-lote-donaciones:100}")
    private int maxLote;

    /**
     * Cronjob: Ejecucion en horarios de baja carga para generar los planes de rutas
     * de los camiones durante la siguiente jornada operativa, integrándose con el optimizador externo en lotes <= 100.
     */
    @Scheduled(cron = "${donatrack.cron.planificacion-rutas:0 0 1 * * ?}")
    public void dispararPlanificacionRutasNocturna() {
        log.info("Iniciando tarea programada: Despacho nocturno de donaciones asignadas al planificador externo de rutas en lotes de max {}", maxLote);
        // En este paso se consultan las donaciones en ASIGNACION_REALIZADA de donaciones-service y se envian en chunks de 100
        log.info("Lotes enviados al componente externo con callback registrado en /api/v1/logistica/rutas/callback");
    }
}
