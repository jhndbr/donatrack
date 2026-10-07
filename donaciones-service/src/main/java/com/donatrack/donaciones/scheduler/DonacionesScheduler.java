package com.donatrack.donaciones.scheduler;

import com.donatrack.common.enums.EstadoDonacion;
import com.donatrack.common.events.DonanteInactivoEvent;
import com.donatrack.donaciones.domain.Donacion;
import com.donatrack.donaciones.domain.OutboxEvent;
import com.donatrack.donaciones.domain.PersonaDonante;
import com.donatrack.donaciones.repository.DonacionRepository;
import com.donatrack.donaciones.repository.OutboxEventRepository;
import com.donatrack.donaciones.repository.PersonaDonanteRepository;
import com.donatrack.donaciones.service.MatchmakingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
public class DonacionesScheduler {

    private static final Logger log = LoggerFactory.getLogger(DonacionesScheduler.class);

    private final DonacionRepository donacionRepository;
    private final PersonaDonanteRepository donanteRepository;
    private final MatchmakingService matchmakingService;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Value("${donatrack.rabbitmq.routing-key.donante-inactivo:donante.inactivo}")
    private String routingKeyDonanteInactivo;

    public DonacionesScheduler(DonacionRepository donacionRepository,
                               PersonaDonanteRepository donanteRepository,
                               MatchmakingService matchmakingService,
                               OutboxEventRepository outboxEventRepository,
                               ObjectMapper objectMapper) {
        this.donacionRepository = donacionRepository;
        this.donanteRepository = donanteRepository;
        this.matchmakingService = matchmakingService;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Cronjob: Ejecucion batch de matchmaking en horario de baja carga (ej. todos los dias a las 02:00 AM).
     */
    @Scheduled(cron = "${donatrack.cron.matchmaking:0 0 2 * * ?}")
    public void ejecutarMatchmakingNocturno() {
        log.info("Iniciando ejecucion asincronica batch de matchmaking nocturno...");
        List<Donacion> enDeposito = donacionRepository.findByEstado(EstadoDonacion.EN_DEPOSITO);

        for (Donacion d : enDeposito) {
            try {
                matchmakingService.evaluarDonacion(d.getId());
            } catch (Exception e) {
                log.error("Error evaluando matchmaking para donacion id={}: {}", d.getId(), e.getMessage());
            }
        }
        log.info("Finalizada ejecucion batch de matchmaking. Total donaciones evaluadas: {}", enDeposito.size());
    }

    /**
     * Cronjob: Deteccion de donantes inactivos por mas de 20 dias para incentivar nueva donacion.
     */
    @Scheduled(cron = "${donatrack.cron.inactividad:0 0 4 * * ?}")
    @Transactional
    public void detectarDonantesInactivos() {
        log.info("Verificando donantes con inactividad mayor a 20 dias...");
        LocalDateTime limite = LocalDateTime.now().minusDays(20);
        List<PersonaDonante> inactivos = donanteRepository.findInactivosAntesDe(limite);

        for (PersonaDonante d : inactivos) {
            long dias = ChronoUnit.DAYS.between(d.getUltimaActividad(), LocalDateTime.now());
            DonanteInactivoEvent evento = new DonanteInactivoEvent(
                    d.getId(),
                    d.getNombreCompleto(),
                    d.getEmailPrincipal(),
                    dias
            );

            try {
                String payload = objectMapper.writeValueAsString(evento);
                outboxEventRepository.save(new OutboxEvent(
                        "PersonaDonante",
                        d.getId().toString(),
                        "DonanteInactivoEvent",
                        routingKeyDonanteInactivo,
                        payload
                ));
            } catch (Exception e) {
                log.error("Error creando evento de donante inactivo para donante id={}: {}", d.getId(), e.getMessage());
            }
        }
        log.info("Procesados {} donantes inactivos.", inactivos.size());
    }

    /**
     * Cronjob: Auditoria de donaciones vencidas en deposito.
     */
    @Scheduled(cron = "${donatrack.cron.vencimiento:0 0 3 * * ?}")
    @Transactional
    public void auditarDonacionesVencidas() {
        log.info("Auditando bienes perecederos vencidos en deposito...");
        List<Donacion> vencidas = donacionRepository.findVencidasEnDeposito(LocalDate.now());

        for (Donacion d : vencidas) {
            d.marcarVencida();
            donacionRepository.save(d);
            log.warn("Donacion id={} con subcategoria {} marcada como VENCIDA automaticamente", d.getId(), d.getSubcategoria());
        }
    }
}
