package com.donatrack.incentivos.service;

import com.donatrack.common.enums.CategoriaDonante;
import com.donatrack.common.events.CategoriaAscendidaEvent;
import com.donatrack.common.events.MisionCumplidaEvent;
import com.donatrack.incentivos.domain.*;
import com.donatrack.incentivos.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.*;

@Service
public class IncentivosService {

    private static final Logger log = LoggerFactory.getLogger(IncentivosService.class);

    private final PerfilDonanteIncentivoRepository perfilRepository;
    private final MisionRepository misionRepository;
    private final ProgresoMisionRepository progresoRepository;
    private final RankingMensualRepository rankingRepository;
    private final RabbitTemplate rabbitTemplate;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${donatrack.rabbitmq.exchange:donatrack.events.exchange}")
    private String exchange;

    @Value("${donatrack.rabbitmq.routing-key.mision-cumplida:mision.cumplida}")
    private String routingKeyMisionCumplida;

    @Value("${donatrack.rabbitmq.routing-key.categoria-ascendida:categoria.ascendida}")
    private String routingKeyCategoriaAscendida;

    @Value("${donatrack.integraciones.n8n-webhook-url:http://localhost:5678/webhook/insignias}")
    private String n8nWebhookUrl;

    public IncentivosService(PerfilDonanteIncentivoRepository perfilRepository,
                             MisionRepository misionRepository,
                             ProgresoMisionRepository progresoRepository,
                             RankingMensualRepository rankingRepository,
                             RabbitTemplate rabbitTemplate) {
        this.perfilRepository = perfilRepository;
        this.misionRepository = misionRepository;
        this.progresoRepository = progresoRepository;
        this.rankingRepository = rankingRepository;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Transactional
    public PerfilDonanteIncentivo obtenerOCrearPerfil(Long idDonante, String nombre, String email) {
        return perfilRepository.findByIdDonante(idDonante)
                .orElseGet(() -> {
                    String nombreFinal = (nombre != null && !nombre.isBlank()) ? nombre : "Donante " + idDonante;
                    String emailFinal = (email != null && !email.isBlank()) ? email : "donante" + idDonante + "@donatrack.org";
                    PerfilDonanteIncentivo nuevo = new PerfilDonanteIncentivo(idDonante, nombreFinal, emailFinal);
                    perfilRepository.save(nuevo);
                    inicializarMisionesParaDonante(idDonante, CategoriaDonante.COLABORADOR);
                    return nuevo;
                });
    }

    private void inicializarMisionesParaDonante(Long idDonante, CategoriaDonante categoria) {
        List<Mision> misiones = misionRepository.findByCategoriaRequeridaOrderByOrdenSecuenciaAsc(categoria);
        for (Mision m : misiones) {
            if (progresoRepository.findByIdDonanteAndMisionId(idDonante, m.getId()).isEmpty()) {
                progresoRepository.save(new ProgresoMision(idDonante, m));
            }
        }
    }

    @Transactional
    public void registrarImpactoDonacion(Long idDonante, String nombre, String email, double cantidadBienes) {
        PerfilDonanteIncentivo perfil = obtenerOCrearPerfil(idDonante, nombre, email);
        perfil.setTotalDonaciones(perfil.getTotalDonaciones() + 1);
        perfil.setTotalBienesDonados(perfil.getTotalBienesDonados() + cantidadBienes);
        perfil.setUltimaDonacionFecha(LocalDate.now());

        // Evaluar progreso en misiones de su categoria
        List<ProgresoMision> progresos = progresoRepository.findByIdDonante(idDonante);
        for (ProgresoMision prog : progresos) {
            if (!prog.isCompletada()) {
                Mision mision = prog.getMision();
                if (mision.getCategoriaRequerida() == perfil.getCategoria()) {
                    if (mision.getTipo() != Mision.TipoMision.DONACIONES_EXITOSAS) {
                        double delta = (mision.getTipo() == Mision.TipoMision.HABIL_DONADOR) ? cantidadBienes : 1.0;
                        prog.agregarProgreso(delta);
                        progresoRepository.save(prog);

                        if (prog.isCompletada()) {
                            desbloquearInsigniaYNotificar(perfil, mision);
                        }
                        // Las misiones se completan en orden secuencial dentro de cada categoria
                        break;
                    }
                }
            }
        }

        perfilRepository.save(perfil);
    }

    @Transactional
    public void registrarEntregaExitosa(Long idDonante, String emailDonante) {
        if (idDonante == null) return;
        PerfilDonanteIncentivo perfil = obtenerOCrearPerfil(idDonante, "Donante " + idDonante, emailDonante);
        List<ProgresoMision> progresos = progresoRepository.findByIdDonante(idDonante);
        for (ProgresoMision prog : progresos) {
            if (!prog.isCompletada()) {
                Mision mision = prog.getMision();
                if (mision.getCategoriaRequerida() == perfil.getCategoria() && mision.getTipo() == Mision.TipoMision.DONACIONES_EXITOSAS) {
                    prog.agregarProgreso(1.0);
                    progresoRepository.save(prog);

                    if (prog.isCompletada()) {
                        desbloquearInsigniaYNotificar(perfil, mision);
                    }
                    break;
                }
            }
        }
        perfilRepository.save(perfil);
    }

    @Transactional
    public void registrarEntregaExitosaPorEmail(String emailDonante) {
        if (emailDonante == null || emailDonante.isBlank()) return;
        perfilRepository.findByEmail(emailDonante).ifPresent(perfil -> {
            registrarEntregaExitosa(perfil.getIdDonante(), emailDonante);
        });
    }

    private void desbloquearInsigniaYNotificar(PerfilDonanteIncentivo perfil, Mision mision) {
        Insignia insignia = new Insignia(
                mision.getNombreInsignia(),
                "Otorgada por cumplir la mision: " + mision.getNombre(),
                "https://donatrack.org/assets/badges/" + mision.getNombreInsignia().toLowerCase().replace(" ", "_") + ".png"
        );
        perfil.agregarInsignia(insignia);

        // 1. Emitir evento a RabbitMQ
        MisionCumplidaEvent evento = new MisionCumplidaEvent(
                perfil.getIdDonante(),
                perfil.getNombre(),
                perfil.getEmail(),
                mision.getTipo().name(),
                insignia.getNombre(),
                insignia.getDescripcion(),
                perfil.getCategoria().name()
        );
        try {
            rabbitTemplate.convertAndSend(exchange, routingKeyMisionCumplida, evento);
        } catch (Exception e) {
            log.error("Error publicando MisionCumplidaEvent: {}", e.getMessage());
        }

        // 2. Disparar automatizacion de difusion en redes sociales via n8n
        dispararDifusionSocialN8n(perfil.getNombre(), insignia.getNombre(), insignia.getImagenUrl());

        // 3. Evaluar ascenso de categoria si completo todas las misiones de la categoria actual
        evaluarAscensoCategoria(perfil);
    }

    private void dispararDifusionSocialN8n(String nombreDonante, String nombreInsignia, String imagenUrl) {
        try {
            Map<String, String> payload = Map.of(
                    "donante", nombreDonante,
                    "insignia", nombreInsignia,
                    "imagenUrl", imagenUrl,
                    "mensaje", "¡Felicitaciones " + nombreDonante + "! Ha desbloqueado la insignia " + nombreInsignia + " en DonaTrack."
            );
            restTemplate.postForLocation(n8nWebhookUrl, payload);
            log.info("Disparado webhook n8n para difusion de insignia '{}' del donante '{}'", nombreInsignia, nombreDonante);
        } catch (Exception e) {
            log.warn("No se pudo conectar con webhook n8n (simulado si no esta activo): {}", e.getMessage());
        }
    }

    private void evaluarAscensoCategoria(PerfilDonanteIncentivo perfil) {
        List<Mision> misionesCategoria = misionRepository.findByCategoriaRequeridaOrderByOrdenSecuenciaAsc(perfil.getCategoria());
        List<ProgresoMision> completadas = progresoRepository.findByIdDonanteAndCompletadaTrue(perfil.getIdDonante());

        long completadasEnCategoria = completadas.stream()
                .filter(p -> p.getMision().getCategoriaRequerida() == perfil.getCategoria())
                .count();

        if (completadasEnCategoria >= misionesCategoria.size() && !misionesCategoria.isEmpty()) {
            CategoriaDonante anterior = perfil.getCategoria();
            perfil.ascenderCategoria();

            if (anterior != perfil.getCategoria()) {
                log.info("Donante {} ascendio de categoria {} -> {}", perfil.getNombre(), anterior, perfil.getCategoria());
                inicializarMisionesParaDonante(perfil.getIdDonante(), perfil.getCategoria());

                CategoriaAscendidaEvent evento = new CategoriaAscendidaEvent(
                        perfil.getIdDonante(),
                        perfil.getNombre(),
                        perfil.getEmail(),
                        anterior.name(),
                        perfil.getCategoria().name()
                );
                try {
                    rabbitTemplate.convertAndSend(exchange, routingKeyCategoriaAscendida, evento);
                } catch (Exception e) {
                    log.error("Error publicando CategoriaAscendidaEvent: {}", e.getMessage());
                }
            }
        }
    }
}
