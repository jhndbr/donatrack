package com.donatrack.incentivos.config;

import com.donatrack.common.enums.CategoriaDonante;
import com.donatrack.incentivos.domain.Mision;
import com.donatrack.incentivos.repository.MisionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class IncentivosDataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(IncentivosDataInitializer.class);

    private final MisionRepository misionRepository;

    public IncentivosDataInitializer(MisionRepository misionRepository) {
        this.misionRepository = misionRepository;
    }

    @Override
    public void run(String... args) {
        if (misionRepository.count() == 0) {
            log.info("Inicializando catálogo canónico de misiones e insignias de DonaTrack...");

            List<Mision> misionesIniciales = List.of(
                    // Categoría COLABORADOR
                    new Mision(
                            "Primer Paso",
                            "Realizar al menos 1 donación en la plataforma",
                            Mision.TipoMision.RACHA,
                            CategoriaDonante.COLABORADOR,
                            1.0,
                            1,
                            "Insignia Solidaridad Inicial"
                    ),
                    new Mision(
                            "Completitud Solidaria",
                            "Realizar 2 donaciones de distintas subcategorías",
                            Mision.TipoMision.COMPLETITUD,
                            CategoriaDonante.COLABORADOR,
                            2.0,
                            2,
                            "Insignia Mano Abierta"
                    ),
                    new Mision(
                            "Hábil Donador",
                            "Realizar una donación con volumen de 20 o más unidades/kg",
                            Mision.TipoMision.HABIL_DONADOR,
                            CategoriaDonante.COLABORADOR,
                            20.0,
                            3,
                            "Insignia Corazón Generoso"
                    ),

                    // Categoría SOSTENEDOR
                    new Mision(
                            "Racha Activa",
                            "Mantener donaciones regulares acumulando 3 aportes",
                            Mision.TipoMision.RACHA,
                            CategoriaDonante.SOSTENEDOR,
                            3.0,
                            1,
                            "Insignia Pilar Comunitario"
                    ),
                    new Mision(
                            "Entregas Concretadas",
                            "Lograr que 5 donaciones sean entregadas satisfactoriamente",
                            Mision.TipoMision.DONACIONES_EXITOSAS,
                            CategoriaDonante.SOSTENEDOR,
                            5.0,
                            2,
                            "Insignia Impacto Directo"
                    ),

                    // Categoría TRANSFORMADOR
                    new Mision(
                            "Héroe de la Solidaridad",
                            "Aportar más de 100 unidades/kg en donaciones",
                            Mision.TipoMision.HABIL_DONADOR,
                            CategoriaDonante.TRANSFORMADOR,
                            100.0,
                            1,
                            "Insignia Héroe Comunitario"
                    ),
                    new Mision(
                            "Leyenda Viva",
                            "Concretar 10 entregas exitosas a entidades vulnerables",
                            Mision.TipoMision.DONACIONES_EXITOSAS,
                            CategoriaDonante.TRANSFORMADOR,
                            10.0,
                            2,
                            "Insignia Leyenda Solidaria"
                    )
            );

            misionRepository.saveAll(misionesIniciales);
            log.info("Catálogo de {} misiones cargado con éxito.", misionesIniciales.size());
        }
    }
}
