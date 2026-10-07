package com.donatrack.logistica.config;

import com.donatrack.logistica.domain.Camion;
import com.donatrack.logistica.repository.CamionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class LogisticaDataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(LogisticaDataInitializer.class);

    private final CamionRepository camionRepository;

    public LogisticaDataInitializer(CamionRepository camionRepository) {
        this.camionRepository = camionRepository;
    }

    @Override
    public void run(String... args) {
        if (camionRepository.count() == 0) {
            log.info("Inicializando flota inicial de camiones en MongoDB...");

            List<Camion> flotaInicial = List.of(
                    new Camion("AF123CD", 18.5, 3.2, 3500.0), // Mercedes-Benz Accelo
                    new Camion("AE789XY", 14.0, 2.9, 2500.0)  // Iveco Daily
            );

            camionRepository.saveAll(flotaInicial);
            log.info("Flota de {} camiones registrada en MongoDB exitosamente.", flotaInicial.size());
        }
    }
}
