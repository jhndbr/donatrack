package com.donatrack.incentivos.scheduler;

import com.donatrack.incentivos.domain.PerfilDonanteIncentivo;
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
        int anio = mesAnterior.getYear();
        int mes = mesAnterior.getMonthValue();

        log.info("Iniciando calculo del ranking mensual de donantes activos para {}/{}", mes, anio);

        List<PerfilDonanteIncentivo> perfiles = perfilRepository.findAll();

        record DonanteMisiones(PerfilDonanteIncentivo perfil, int totalMisiones) {}

        List<DonanteMisiones> ranking = perfiles.stream()
                .map(p -> {
                    int misionesCompletadas = progresoRepository.findByIdDonanteAndCompletadaTrue(p.getIdDonante()).size();
                    return new DonanteMisiones(p, misionesCompletadas);
                })
                .sorted(Comparator.comparingInt(DonanteMisiones::totalMisiones).reversed())
                .toList();

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
            rankingRepository.save(rm);
        }

        log.info("Finalizado calculo de ranking mensual para {}/{} con {} participantes", mes, anio, ranking.size());
    }
}
