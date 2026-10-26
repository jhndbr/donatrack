package com.donatrack.donaciones.config;

import com.donatrack.common.enums.CanalNotificacion;
import com.donatrack.common.enums.TipoNecesidad;
import com.donatrack.donaciones.domain.*;
import com.donatrack.donaciones.repository.DonacionRepository;
import com.donatrack.donaciones.repository.EntidadBeneficiariaRepository;
import com.donatrack.donaciones.repository.NecesidadMaterialRepository;
import com.donatrack.donaciones.repository.PersonaDonanteRepository;
import com.donatrack.donaciones.service.DonacionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final PersonaDonanteRepository donanteRepository;
    private final EntidadBeneficiariaRepository entidadRepository;
    private final NecesidadMaterialRepository necesidadRepository;
    private final DonacionRepository donacionRepository;
    private final DonacionService donacionService;

    public DataInitializer(PersonaDonanteRepository donanteRepository,
                           EntidadBeneficiariaRepository entidadRepository,
                           NecesidadMaterialRepository necesidadRepository,
                           DonacionRepository donacionRepository,
                           DonacionService donacionService) {
        this.donanteRepository = donanteRepository;
        this.entidadRepository = entidadRepository;
        this.necesidadRepository = necesidadRepository;
        this.donacionRepository = donacionRepository;
        this.donacionService = donacionService;
    }

    @Override
    @Transactional
    public void run(String... args) {
        inicializarDonantesYEntidades();
    }

    private void inicializarDonantesYEntidades() {
        // 1. Donante Demo Juan Pérez
        PersonaHumana juan = null;
        for (PersonaDonante d : donanteRepository.findAll()) {
            if (d instanceof PersonaHumana ph && "35123456".equals(ph.getNroDocumento())) {
                juan = ph;
                break;
            }
        }
        if (juan == null) {
            juan = new PersonaHumana("Juan", "Pérez", 28, "35123456", "Masculino", "Av. Medrano 951, CABA");
            juan.setLatitud(-34.5985);
            juan.setLongitud(-58.4201);
            juan.agregarMedioContacto(new MedioContacto(CanalNotificacion.EMAIL, "juan.donante@example.com", true));
            juan.agregarMedioContacto(new MedioContacto(CanalNotificacion.WHATSAPP, "+5491144440001", false));
            juan = donanteRepository.save(juan);
            log.info("Donante demo Juan Pérez inicializado con ID {}", juan.getId());
        }

        // 2. Donante Empresa Demo Molinos
        PersonaJuridica molinos = null;
        for (PersonaDonante d : donanteRepository.findAll()) {
            if (d instanceof PersonaJuridica pj && "30-11223344-9".equals(pj.getCuit())) {
                molinos = pj;
                break;
            }
        }
        if (molinos == null) {
            molinos = new PersonaJuridica("Molinos Río de la Plata S.A.", "30-11223344-9", "Empresa", "Alimentos");
            molinos.setDireccion("Bouchard 680, CABA");
            molinos.setLatitud(-34.6010);
            molinos.setLongitud(-58.3690);
            molinos.agregarMedioContacto(new MedioContacto(CanalNotificacion.EMAIL, "rse@molinos.com.ar", true));
            molinos = donanteRepository.save(molinos);
            log.info("Donante corporativo demo Molinos inicializado con ID {}", molinos.getId());
        }

        // 3. Entidad Demo Comedor Esperanza
        EntidadBeneficiaria esperanza = null;
        for (EntidadBeneficiaria ent : entidadRepository.findAll()) {
            if (ent.getRazonSocial() != null && ent.getRazonSocial().toLowerCase().contains("esperanza")) {
                esperanza = ent;
                break;
            }
        }
        if (esperanza == null) {
            esperanza = new EntidadBeneficiaria();
            esperanza.setRazonSocial("Comedor Comunitario Esperanza");
            esperanza.setDireccion("Av. Corrientes 4500, CABA");
            esperanza.setLatitud(-34.6037);
            esperanza.setLongitud(-58.4260);
            esperanza.setTelefono("+54 11 4444-5555");
            esperanza.setEmailsRepresentantes(List.of("esperanza@comedor.org", "contacto@esperanza.org"));
            esperanza = entidadRepository.save(esperanza);
            log.info("Entidad demo Comedor Esperanza inicializada con ID {}", esperanza.getId());

            // Crear necesidades para la entidad
            NecesidadMaterial n1 = new NecesidadMaterial("arroz", "Arroz largo fino para meriendas comunitarias", TipoNecesidad.RECURRENTE, 100.0, "kg", "Mensual");
            n1.setEntidadBeneficiaria(esperanza);

            NecesidadMaterial n2 = new NecesidadMaterial("leche", "Leche entera larga vida", TipoNecesidad.EXTRAORDINARIA, 50.0, "litros", "Único");
            n2.setEntidadBeneficiaria(esperanza);

            necesidadRepository.saveAll(List.of(n1, n2));
        }

        // 4. Si no hay donaciones, crear donación inicial en depósito para Juan
        if (donacionRepository.count() == 0 && juan != null) {
            try {
                BienDonado bien = new BienDonado("Arroz fino 1kg", "Alimentos", "arroz", 30.0, "kg", false, null, null);
                donacionService.registrarCargaSegmentada(juan.getId(), List.of(bien));
                log.info("Donación inicial creada en depósito para Juan Pérez.");
            } catch (Exception e) {
                log.warn("No se pudo auto-crear la donación inicial: {}", e.getMessage());
            }
        }
    }
}
