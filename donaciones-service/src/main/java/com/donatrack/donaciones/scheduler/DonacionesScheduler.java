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

import com.donatrack.donaciones.domain.EntidadBeneficiaria;
import com.donatrack.donaciones.matchmaking.MatchmakingRankingResult;
import com.donatrack.donaciones.repository.EntidadBeneficiariaRepository;
import com.donatrack.donaciones.service.DonacionService;
import java.util.Comparator;

@Component
public class DonacionesScheduler {

    private static final Logger log = LoggerFactory.getLogger(DonacionesScheduler.class);

    private final DonacionRepository donacionRepository;
    private final PersonaDonanteRepository donanteRepository;
    private final MatchmakingService matchmakingService;
    private final DonacionService donacionService;
    private final EntidadBeneficiariaRepository entidadRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final com.donatrack.donaciones.repository.NecesidadMaterialRepository necesidadRepository;

    @Value("${donatrack.rabbitmq.routing-key.donante-inactivo:donante.inactivo}")
    private String routingKeyDonanteInactivo;

    @Value("${donatrack.scheduler.matchmaking.auto-assign:false}")
    private boolean autoAssignEnabled;

    public DonacionesScheduler(DonacionRepository donacionRepository,
                               PersonaDonanteRepository donanteRepository,
                               MatchmakingService matchmakingService,
                               DonacionService donacionService,
                               EntidadBeneficiariaRepository entidadRepository,
                               OutboxEventRepository outboxEventRepository,
                               ObjectMapper objectMapper,
                               com.donatrack.donaciones.repository.NecesidadMaterialRepository necesidadRepository) {
        this.donacionRepository = donacionRepository;
        this.donanteRepository = donanteRepository;
        this.matchmakingService = matchmakingService;
        this.donacionService = donacionService;
        this.entidadRepository = entidadRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
        this.necesidadRepository = necesidadRepository;
    }

    /**
     * Ejecuta la evaluación y asignación algorítmica de donaciones en depósito (CU-30 a CU-35).
     * Retorna la cantidad de donaciones asignadas exitosamente.
     */
    public int procesarMatchmakingDonaciones() {
        log.info("Iniciando procesamiento batch de matchmaking para donaciones en depósito...");
        List<Donacion> enDeposito = donacionRepository.findByEstado(EstadoDonacion.EN_DEPOSITO);
        int asignadas = 0;

        for (Donacion d : enDeposito) {
            try {
                MatchmakingRankingResult ranking = matchmakingService.evaluarDonacion(d.getId());
                Long entidadIdDestino = null;
                Long necesidadIdDestino = null;

                if (ranking != null) {
                    if (ranking.getCoincidenciasDestacadas() != null && !ranking.getCoincidenciasDestacadas().isEmpty()) {
                        MatchmakingRankingResult.EvaluacionPar mejor = ranking.getCoincidenciasDestacadas().get(0);
                        entidadIdDestino = mejor.getIdEntidad();
                        necesidadIdDestino = mejor.getIdNecesidad();
                    } else if (ranking.getRankingSubatendidosTop10() != null && !ranking.getRankingSubatendidosTop10().isEmpty()) {
                        MatchmakingRankingResult.EvaluacionPar mejor = ranking.getRankingSubatendidosTop10().get(0);
                        entidadIdDestino = mejor.getIdEntidad();
                        necesidadIdDestino = mejor.getIdNecesidad();
                    } else if (ranking.getRankingSemanticoTop10() != null && !ranking.getRankingSemanticoTop10().isEmpty()) {
                        MatchmakingRankingResult.EvaluacionPar mejor = ranking.getRankingSemanticoTop10().get(0);
                        entidadIdDestino = mejor.getIdEntidad();
                        necesidadIdDestino = mejor.getIdNecesidad();
                    }
                }

                if (entidadIdDestino != null && necesidadIdDestino != null && donacionService != null) {
                    donacionService.asignarDonacion(d.getId(), entidadIdDestino, necesidadIdDestino, "SISTEMA_MATCHMAKING");
                    log.info("Donación id={} asignada por Matchmaking a Entidad id={} (Necesidad={})",
                            d.getId(), entidadIdDestino, necesidadIdDestino);
                    asignadas++;
                }
            } catch (Exception e) {
                log.error("Error evaluando/asignando matchmaking para donacion id={}: {}", d.getId(), e.getMessage());
            }
        }

        log.info("Finalizado matchmaking batch. Total donaciones evaluadas: {}, asignadas: {}",
                enDeposito.size(), asignadas);
        return asignadas;
    }

    /**
     * Cronjob nocturno: Ejecución batch en horario de baja carga (ej. 02:00 AM).
     */
    @Scheduled(cron = "${donatrack.cron.matchmaking:0 0 2 * * ?}")
    public void ejecutarMatchmakingNocturno() {
        procesarMatchmakingDonaciones();
    }

    /**
     * Programación periódica asistida: Se ejecuta a los 5 segundos del arranque del microservicio
     * y luego periódicamente si está habilitado en configuración.
     */
    @Scheduled(initialDelayString = "${donatrack.scheduler.matchmaking.initial-delay:5000}",
               fixedDelayString = "${donatrack.scheduler.matchmaking.fixed-delay:30000}")
    public void ejecutarMatchmakingPeriodico() {
        if (autoAssignEnabled) {
            procesarMatchmakingDonaciones();
        }
    }

    /**
     * Cronjob: Reinicio periódico de necesidades recurrentes semanales (los lunes a medianoche).
     */
    @Scheduled(cron = "${donatrack.cron.recurrentes-semanal:0 0 0 * * MON}")
    @Transactional
    public void reiniciarNecesidadesRecurrentesSemanal() {
        if (necesidadRepository == null) return;
        log.info("Iniciando reinicio de necesidades materiales recurrentes SEMANALES...");
        List<com.donatrack.donaciones.domain.NecesidadMaterial> recurrentes =
                necesidadRepository.findByTipoAndPeriodoIgnoreCase(com.donatrack.common.enums.TipoNecesidad.RECURRENTE, "SEMANAL");
        int count = 0;
        for (com.donatrack.donaciones.domain.NecesidadMaterial n : recurrentes) {
            n.reiniciarPeriodoRecurrente();
            necesidadRepository.save(n);
            count++;
        }
        log.info("Reiniciadas {} necesidades recurrentes semanales.", count);
    }

    /**
     * Cronjob: Reinicio periódico de necesidades recurrentes mensuales (el primer día del mes).
     */
    @Scheduled(cron = "${donatrack.cron.recurrentes-mensual:0 0 0 1 * ?}")
    @Transactional
    public void reiniciarNecesidadesRecurrentesMensual() {
        if (necesidadRepository == null) return;
        log.info("Iniciando reinicio de necesidades materiales recurrentes MENSUALES...");
        List<com.donatrack.donaciones.domain.NecesidadMaterial> recurrentes =
                necesidadRepository.findByTipoAndPeriodoIgnoreCase(com.donatrack.common.enums.TipoNecesidad.RECURRENTE, "MENSUAL");
        int count = 0;
        for (com.donatrack.donaciones.domain.NecesidadMaterial n : recurrentes) {
            n.reiniciarPeriodoRecurrente();
            necesidadRepository.save(n);
            count++;
        }
        log.info("Reiniciadas {} necesidades recurrentes mensuales.", count);
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
            com.donatrack.donaciones.domain.MedioContacto medio = d.getMedioContactoPredeterminado();
            com.donatrack.common.enums.CanalNotificacion canal = (medio != null && medio.getTipo() != null)
                    ? medio.getTipo() : com.donatrack.common.enums.CanalNotificacion.EMAIL;
            String contacto = (medio != null && medio.getValor() != null)
                    ? medio.getValor() : d.getEmailPrincipal();

            DonanteInactivoEvent evento = new DonanteInactivoEvent(
                    d.getId(),
                    d.getNombreCompleto(),
                    d.getEmailPrincipal(),
                    dias,
                    canal,
                    contacto
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
                d.setUltimaAlertaInactividad(LocalDateTime.now());
                donanteRepository.save(d);
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
