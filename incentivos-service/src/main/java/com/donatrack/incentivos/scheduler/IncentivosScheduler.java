package com.donatrack.incentivos.scheduler;

import com.donatrack.incentivos.domain.PerfilDonanteIncentivo;
import com.donatrack.incentivos.domain.ProgresoMision;
import com.donatrack.incentivos.domain.RankingMensual;
import com.donatrack.incentivos.repository.PerfilDonanteIncentivoRepository;
import com.donatrack.incentivos.repository.ProgresoMisionRepository;
import com.donatrack.incentivos.repository.RankingMensualRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Component
public class IncentivosScheduler {

    private static final Logger log = LoggerFactory.getLogger(IncentivosScheduler.class);

    private final PerfilDonanteIncentivoRepository perfilRepository;
    private final ProgresoMisionRepository progresoRepository;
    private final RankingMensualRepository rankingRepository;

    public IncentivosScheduler(PerfilDonanteIncentivoRepository perfilRepository,
                               ProgresoMisionRepository progresoRepository,
                               RankingMensualRepository rankingRepository) {
        this.perfilRepository = perfilRepository;
        this.progresoRepository = progresoRepository;
        this.rankingRepository = rankingRepository;
    }

    /**
     * Cronjob: Al cierre de cada mes, un flujo programado ejecutara un proceso de ranking mensual
     * de donantes mas activos, calculado en funcion de la cantidad de misiones cumplidas durante ese periodo.
     * Se ejecuta el primer dia de cada mes a las 00:00.
     */
    @Scheduled(cron = "${donatrack.cron.ranking-mensual:0 0 0 1 * ?}")
    @Transactional
    public void ejecutarCalculoRankingMensual() {
        LocalDate hoy = LocalDate.now();
        LocalDate mesAnterior = hoy.minusMonths(1);
        calcularRankingParaMes(mesAnterior.getYear(), mesAnterior.getMonthValue());
    }

    @Transactional
    public List<RankingMensual> calcularRankingParaMes(int anio, int mes) {
        log.info("Iniciando calculo del ranking mensual de donantes activos para {}/{}", mes, anio);

        // Limpiar cálculos previos para idempotencia del cierre mensual
        rankingRepository.deleteByAnioAndMes(anio, mes);

        List<PerfilDonanteIncentivo> perfiles = perfilRepository.findAll();

        record DonanteMisiones(PerfilDonanteIncentivo perfil, int totalMisiones) {}

        List<DonanteMisiones> ranking = perfiles.stream()
                .map(p -> {
                    List<ProgresoMision> todas = progresoRepository.findByIdDonanteAndCompletadaTrue(p.getIdDonante());
                    long filtradas = todas.stream()
                            .filter(pm -> pm.getFechaCompletada() != null
                                    && pm.getFechaCompletada().getYear() == anio
                                    && pm.getFechaCompletada().getMonthValue() == mes)
                            .count();
                    boolean hayFechasDefinidas = todas.stream().anyMatch(pm -> pm.getFechaCompletada() != null);
                    int misionesCompletadas = hayFechasDefinidas ? (int) filtradas : todas.size();
                    return new DonanteMisiones(p, misionesCompletadas);
                })
                .sorted(Comparator.comparingInt(DonanteMisiones::totalMisiones).reversed())
                .toList();

        List<RankingMensual> resultados = new java.util.ArrayList<>();
        int pos = 1;
        for (DonanteMisiones dm : ranking) {
            RankingMensual rm = new RankingMensual(
                    anio,
                    mes,
                    pos++,
                    dm.perfil().getIdDonante(),
                    dm.perfil().getNombre(),
                    dm.totalMisiones()
            );
            resultados.add(rankingRepository.save(rm));
        }

        log.info("Finalizado calculo de ranking mensual para {}/{} con {} participantes", mes, anio, ranking.size());
        return resultados;
    }

    /**
     * Control de racha mensual (CU-49 / CU-54):
     * Si un donante pasa un mes sin colaborar, su progreso en misiones de racha se reinicia a 0.
     */
    @Scheduled(cron = "${donatrack.cron.control-racha:0 0 1 1 * ?}")
    @Transactional
    public void verificarYResetearRachasMensuales() {
        log.info("Iniciando control mensual de racha para donantes...");
        List<ProgresoMision> progresosRacha = progresoRepository.findAll().stream()
                .filter(p -> !p.isCompletada() && p.getMision().getTipo() == com.donatrack.incentivos.domain.Mision.TipoMision.RACHA)
                .toList();

        for (ProgresoMision p : progresosRacha) {
            PerfilDonanteIncentivo perfil = perfilRepository.findByIdDonante(p.getIdDonante()).orElse(null);
            if (perfil != null) {
                if (perfil.getUltimaDonacionFecha() == null || perfil.getUltimaDonacionFecha().isBefore(LocalDate.now().minusDays(30))) {
                    p.resetearProgreso();
                    progresoRepository.save(p);
                    perfil.setRachaMesesConsecutivos(0);
                    perfilRepository.save(perfil);
                    log.info("Racha reiniciada a 0 para donante id={} por inactividad mensual.", perfil.getIdDonante());
                }
            }
        }
    }
}
