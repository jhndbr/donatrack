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

/**
 * ====================================================================================================
 * SERVICIO CENTRAL DE INCENTIVOS, GAMIFICACIÓN Y ANALÍTICA (incentivos-service) - DonaTrack
 * ====================================================================================================
 *
 * ----------------------------------------------------------------------------------------------------
 * 1. FLUJOS PRINCIPALES RESUMIDOS (Casos de Uso de Incentivos y Gamificación)
 * ----------------------------------------------------------------------------------------------------
 *
 * FLUJO A: Impacto de Donación y Avance en Misiones Secuenciales (CU-48, CU-49)
 *   1. Donaciones informa actividad de donación (donacion.asignada o entrega.confirmada) vía AMQP.
 *   2. Se identifica al donante humano y se recupera su progreso en misiones activas.
 *   3. Las misiones deben resolverse en orden estrictamente SECUENCIAL dentro de cada categoría.
 *   4. Al alcanzar el objetivo:
 *      - Se marca la misión como completada.
 *      - Se desbloquea la insignia asociada (CU-50) y se agrega al perfil del donante.
 *      - Se emite el evento mision.cumplida a RabbitMQ para notificar al donante (CU-59).
 *      - Se activa la siguiente misión de la secuencia.
 *      - Se dispara el flujo automatizado de difusión hacia n8n para publicar en redes sociales (CU-55).
 *
 * FLUJO B: Sistema de Categorías y Ascenso de Rango (CU-53)
 *   1. Categorías oficiales definidas en el TP: COLABORADOR (inicial) -> SOSTENEDOR -> TRANSFORMADOR.
 *   2. Al completar la totalidad de misiones de la categoría actual, el donante asciende automáticamente.
 *   3. Se inicializa el paquete de misiones de la nueva categoría.
 *   4. La categoría queda visible públicamente junto al nombre de usuario.
 *   5. Se emite el evento categoria.ascendida a RabbitMQ para notificar al donante (CU-60).
 *
 * FLUJO C: Difusión Automatizada de Insignias en Redes Sociales (CU-55)
 *   1. Al obtener una insignia, se emite un webhook HTTP POST hacia la herramienta low-code (n8n).
 *   2. n8n recibe los datos (nombre donante, insignia, descripción, imagen generada).
 *   3. n8n publica en la red social institucional mencionando al usuario donante.
 *
 * FLUJO D: Ranking Mensual de Donantes Activos (CU-54)
 *   1. Tarea programada (scheduler cron) ejecutada al cierre de cada mes (00:00 del día 1).
 *   2. Totaliza las misiones cumplidas por cada donante durante el mes calendario vencido (sin importar la categoría).
 *   3. Ordena a los donantes, persiste el ranking histórico en base de datos y destaca al Top 3 en la aplicación.
 *
 * FLUJO E: Métricas de Impacto y Configuración de Visibilidad (CU-47, CU-51, CU-52)
 *   1. El donante consulta en su perfil sus totales históricos, gráficos de evolución mensual y organizaciones ayudadas.
 *   2. El donante puede configurar la visibilidad pública de cada insignia en su perfil (CU-52).
 *
 * ----------------------------------------------------------------------------------------------------
 * 2. LO NECESARIO DEL SERVICE (Requerimientos Técnicos y de Dominio)
 * ----------------------------------------------------------------------------------------------------
 * - Persistencia relacional en PostgreSQL (incentivos_db) con Spring Data JPA.
 * - Consumo asincrónico desacoplado mediante RabbitMQ con control de idempotencia (FT-06).
 * - Productor de eventos hacia RabbitMQ para notificaciones (mision.cumplida, categoria.ascendida).
 * - Cliente HTTP saliente hacia n8n con manejo de timeout y reintentos ante caídas transitorias.
 * - Scheduler cron para liquidación y persistencia de posiciones del ranking mensual.
 *
 * ----------------------------------------------------------------------------------------------------
 * 3. TO-DO MAESTRO: LO QUE LE FALTA AL SERVICE
 * ----------------------------------------------------------------------------------------------------
 * [ ] TODO-INC-01 (CU-49): Implementar la lógica completa de las 4 misiones oficiales del enunciado del TP:
 *     - 'Racha': verificar donaciones en meses consecutivos con pérdida de progreso (reseteo a 0) ante inactividad mensual.
 *     - 'Completitud': registrar y evaluar variedad de categorías de bienes donadas por el donante.
 *     - 'Hábil Donador': validar donaciones que superen X cantidad de bienes unitarios en una sola entrega.
 *     - 'Donaciones Exitosas': acumular recepciones confirmadas por entidades beneficiarias.
 * [ ] TODO-INC-02 (CU-52): Implementar configuración de visibilidad de insignias (atributo visible en Insignia y
 *     método configurarVisibilidadInsignia(idDonante, idInsignia, esVisible)).
 * [ ] TODO-INC-03 (CU-47): Desarrollar cálculo de analítica avanzada: comparativas mes actual vs anterior,
 *     histórico consolidado y contador exacto de organizaciones distintas beneficiadas.
 * [ ] TODO-INC-04 (CU-54): Ajustar el cálculo del ranking mensual para ordenar estrictamente por
 *     'cantidad de misiones cumplidas en el mes vencido' (requisito mandatorio del TP) y persistir histórico mensual.
 * [ ] TODO-INC-05 (CU-55): Robustecer la integración con n8n: implementar reintentos con backoff exponencial,
 *     circuit breaker y prevención de publicaciones duplicadas si el evento se reintenta.
 * [ ] TODO-INC-06 (FT-06): Implementar filtro de idempotencia en consumidores de RabbitMQ para evitar sumar puntos
 *     o progreso dos veces ante reintentos de red de un mismo evento de donación.
 * ====================================================================================================
 */
@Service
public class IncentivosService {

    private static final Logger log = LoggerFactory.getLogger(IncentivosService.class);

    private final PerfilDonanteIncentivoRepository perfilRepository;
    private final MisionRepository misionRepository;
    private final ProgresoMisionRepository progresoRepository;
    private final RankingMensualRepository rankingRepository;
    private final RabbitTemplate rabbitTemplate;
    private final RestTemplate restTemplate;

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

        org.springframework.http.client.SimpleClientHttpRequestFactory factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(java.time.Duration.ofSeconds(2));
        factory.setReadTimeout(java.time.Duration.ofSeconds(3));
        this.restTemplate = new RestTemplate(factory);
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
        registrarImpactoDonacionCompleto(idDonante, nombre, email, cantidadBienes, null, null);
    }

    @Transactional
    public void actualizarContactoPreferidoDonante(Long idDonante, com.donatrack.common.enums.CanalNotificacion canal, String contacto) {
        if (idDonante == null) return;
        perfilRepository.findByIdDonante(idDonante).ifPresent(perfil -> {
            if (canal != null) perfil.setCanalPreferido(canal);
            if (contacto != null && !contacto.isBlank()) perfil.setContactoPreferido(contacto);
            perfilRepository.save(perfil);
        });
    }

    @Transactional
    public void registrarOrganizacionAyudadaPorAsignacion(Long idDonante, String nombreDonante, String emailDonante, Long idEntidadBeneficiaria) {
        if (idDonante == null) return;
        PerfilDonanteIncentivo perfil = obtenerOCrearPerfil(idDonante, nombreDonante, emailDonante);
        if (perfil.getTotalDonaciones() == 0) {
            registrarImpactoDonacionCompleto(idDonante, nombreDonante, emailDonante, 1.0, null, idEntidadBeneficiaria);
            return;
        }
        if (idEntidadBeneficiaria != null) {
            perfil.registrarOrganizacionAyudada(idEntidadBeneficiaria);
        }
        perfilRepository.save(perfil);
    }

    @Transactional
    public void registrarImpactoDonacionCompleto(Long idDonante, String nombre, String email,
                                                 double cantidadBienes, String subcategoria, Long entidadId) {
        PerfilDonanteIncentivo perfil = obtenerOCrearPerfil(idDonante, nombre, email);
        perfil.setTotalDonaciones(perfil.getTotalDonaciones() + 1);
        perfil.setTotalBienesDonados(perfil.getTotalBienesDonados() + cantidadBienes);
        perfil.actualizarRachaMensual(LocalDate.now());

        if (entidadId != null) {
            perfil.registrarOrganizacionAyudada(entidadId);
        }

        boolean nuevaSubcategoria = false;
        if (subcategoria != null && !subcategoria.isBlank()) {
            nuevaSubcategoria = perfil.registrarSubcategoria(subcategoria);
        }

        // Evaluar progreso en misiones en estricto orden secuencial dentro de su categoria
        List<ProgresoMision> progresos = new ArrayList<>(progresoRepository.findByIdDonante(idDonante));
        progresos.sort(Comparator.comparingInt(p -> p.getMision().getOrdenSecuencia()));

        for (ProgresoMision prog : progresos) {
            if (!prog.isCompletada()) {
                Mision mision = prog.getMision();
                if (mision.getCategoriaRequerida() == perfil.getCategoria()) {
                    boolean avanza = false;
                    double delta = 0.0;

                    switch (mision.getTipo()) {
                        case RACHA -> {
                            // Racha evalúa la cantidad de meses consecutivos del donante
                            prog.setProgresoActual(perfil.getRachaMesesConsecutivos());
                            if (prog.getProgresoActual() >= mision.getObjetivo()) {
                                prog.setCompletada(true);
                                prog.setFechaCompletada(java.time.LocalDateTime.now());
                            }
                            avanza = true;
                        }
                        case COMPLETITUD -> {
                            // Suma cuando dona una categoría o subcategoría diferente a las previas
                            if (nuevaSubcategoria || perfil.getSubcategoriasDonadas().size() > prog.getProgresoActual()) {
                                delta = 1.0;
                                prog.agregarProgreso(delta);
                                avanza = true;
                            }
                        }
                        case HABIL_DONADOR -> {
                            // Hábil Donador requiere alcanzar el objetivo de bienes en una única donación
                            if (cantidadBienes >= mision.getObjetivo()) {
                                prog.agregarProgreso(mision.getObjetivo());
                                avanza = true;
                            }
                        }
                        case DONACIONES_EXITOSAS -> {
                            // Las donaciones exitosas solo avanzan al recibir la confirmación física
                            avanza = false;
                        }
                    }

                    if (avanza) {
                        progresoRepository.save(prog);
                        if (prog.isCompletada()) {
                            desbloquearInsigniaYNotificar(perfil, mision);
                        }
                    }
                    // Principio de Secuencialidad: evaluar únicamente la primera misión incompleta en orden secuencial
                    break;
                }
            }
        }

        perfilRepository.save(perfil);
    }

    @Transactional
    public void registrarEntregaExitosa(Long idDonante, String emailDonante) {
        if (idDonante == null) return;
        PerfilDonanteIncentivo perfil = obtenerOCrearPerfil(idDonante, "Donante " + idDonante, emailDonante);
        List<ProgresoMision> progresos = new ArrayList<>(progresoRepository.findByIdDonante(idDonante));
        progresos.sort(Comparator.comparingInt(p -> p.getMision().getOrdenSecuencia()));

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
                } else if (mision.getCategoriaRequerida() == perfil.getCategoria()) {
                    // Si hay una misión previa incompleta que no es de entrega exitosa, la secuencia se respeta
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

    /**
     * CU-47: Consulta y cálculo de métricas de actividad y analítica avanzada para el donante.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> obtenerAnaliticaDonante(Long idDonante) {
        Optional<PerfilDonanteIncentivo> perfilOpt = perfilRepository.findByIdDonante(idDonante);
        if (perfilOpt.isEmpty()) {
            return Collections.emptyMap();
        }

        PerfilDonanteIncentivo perfil = perfilOpt.get();
        List<ProgresoMision> completadas = progresoRepository.findByIdDonanteAndCompletadaTrue(idDonante);

        LocalDate ahora = LocalDate.now();
        int mesActual = ahora.getMonthValue();
        int anioActual = ahora.getYear();

        long misionesMesActual = completadas.stream()
                .filter(p -> p.getFechaCompletada() != null &&
                        p.getFechaCompletada().getMonthValue() == mesActual &&
                        p.getFechaCompletada().getYear() == anioActual)
                .count();

        LocalDate mesAnt = ahora.minusMonths(1);
        long misionesMesAnterior = completadas.stream()
                .filter(p -> p.getFechaCompletada() != null &&
                        p.getFechaCompletada().getMonthValue() == mesAnt.getMonthValue() &&
                        p.getFechaCompletada().getYear() == mesAnt.getYear())
                .count();

        // Obtener ranking mensual si existe
        Optional<RankingMensual> rankingOpt = rankingRepository.findByAnioAndMesOrderByPosicionAsc(anioActual, mesActual).stream()
                .filter(r -> r.getIdDonante().equals(idDonante))
                .findFirst();

        Map<String, Object> analitica = new LinkedHashMap<>();
        analitica.put("idDonante", perfil.getIdDonante());
        analitica.put("nombre", perfil.getNombre());
        analitica.put("categoria", perfil.getCategoria().name());
        analitica.put("totalDonaciones", perfil.getTotalDonaciones());
        analitica.put("totalBienesDonados", perfil.getTotalBienesDonados());
        analitica.put("organizacionesAyudadas", perfil.getOrganizacionesAyudadas());
        analitica.put("rachaMesesConsecutivos", perfil.getRachaMesesConsecutivos());
        analitica.put("misionesCompletadasTotales", completadas.size());
        analitica.put("misionesMesActual", misionesMesActual);
        analitica.put("misionesMesAnterior", misionesMesAnterior);
        analitica.put("posicionRanking", rankingOpt.map(RankingMensual::getPosicion).orElse(null));
        analitica.put("insigniasDesbloqueadas", perfil.getInsignias().size());
        analitica.put("subcategoriasDiversas", perfil.getSubcategoriasDonadas().size());

        List<Map<String, Object>> evolucionMensual = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            LocalDate fechaMes = ahora.minusMonths(i);
            int y = fechaMes.getYear();
            int m = fechaMes.getMonthValue();
            String periodo = String.format("%04d-%02d", y, m);
            long misionesEnMes = completadas.stream()
                    .filter(p -> p.getFechaCompletada() != null
                            && p.getFechaCompletada().getYear() == y
                            && p.getFechaCompletada().getMonthValue() == m)
                    .count();
            int donacionesEnMes = (i == 0) ? Math.max(perfil.getTotalDonaciones(), (int) misionesEnMes) : (int) misionesEnMes;
            Map<String, Object> itemMes = new LinkedHashMap<>();
            itemMes.put("periodo", periodo);
            itemMes.put("donaciones", donacionesEnMes);
            itemMes.put("misionesCompletadas", (int) misionesEnMes);
            evolucionMensual.add(itemMes);
        }
        analitica.put("evolucionMensual", evolucionMensual);

        return analitica;
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
                perfil.getCategoria().name(),
                perfil.getCanalPreferido(),
                perfil.getContactoPreferido() != null ? perfil.getContactoPreferido() : perfil.getEmail()
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
                        perfil.getCategoria().name(),
                        perfil.getCanalPreferido(),
                        perfil.getContactoPreferido() != null ? perfil.getContactoPreferido() : perfil.getEmail()
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
