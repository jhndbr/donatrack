package com.donatrack.logistica.config;

import com.donatrack.logistica.domain.Camion;
import com.donatrack.logistica.domain.CoordenadaGPS;
import com.donatrack.logistica.repository.CamionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
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

            Camion c1 = new Camion("AF123CD", "Mercedes-Benz Accelo 815", 18.5, 3.2, 3500.0);
            c1.setUbicacionActual(new CoordenadaGPS(-34.603722, -58.381592, 35.0, LocalDateTime.now(), "Av. 9 de Julio y Corrientes, CABA"));

            Camion c2 = new Camion("AE789XY", "Iveco Daily 70C17", 14.0, 2.9, 2500.0);
            c2.setUbicacionActual(new CoordenadaGPS(-34.598500, -58.420100, 28.5, LocalDateTime.now().minusMinutes(5), "Av. Medrano 951, CABA"));

            List<Camion> flotaInicial = List.of(c1, c2);
            camionRepository.saveAll(flotaInicial);
            log.info("Flota de {} camiones registrada en MongoDB exitosamente.", flotaInicial.size());
        }
    }
}
