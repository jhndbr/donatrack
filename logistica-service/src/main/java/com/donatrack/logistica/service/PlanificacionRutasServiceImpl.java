package com.donatrack.logistica.service;

import com.donatrack.common.enums.EstadoDonacion;
import com.donatrack.logistica.client.DonacionesServiceClient;
import com.donatrack.logistica.client.RutaOptimizerClient;
import com.donatrack.logistica.domain.*;
import com.donatrack.logistica.dto.PlanificadorCallbackDto;
import com.donatrack.logistica.repository.DonacionPendienteLoteRepository;
import com.donatrack.logistica.repository.EntregaRepository;
import com.donatrack.logistica.repository.RutaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class PlanificacionRutasServiceImpl implements PlanificacionRutasService {

    private static final Logger log = LoggerFactory.getLogger(PlanificacionRutasServiceImpl.class);

    private final FlotaService flotaService;
    private final RutaOptimizerClient rutaOptimizerClient;
    private final DonacionesServiceClient donacionesServiceClient;
    private final RutaRepository rutaRepository;
    private final EntregaRepository entregaRepository;
    private final DonacionPendienteLoteRepository pendienteLoteRepository;

    public PlanificacionRutasServiceImpl(FlotaService flotaService,
                                         RutaOptimizerClient rutaOptimizerClient,
                                         DonacionesServiceClient donacionesServiceClient,
                                         RutaRepository rutaRepository,
                                         EntregaRepository entregaRepository,
                                         DonacionPendienteLoteRepository pendienteLoteRepository) {
        this.flotaService = flotaService;
        this.rutaOptimizerClient = rutaOptimizerClient;
        this.donacionesServiceClient = donacionesServiceClient;
        this.rutaRepository = rutaRepository;
        this.entregaRepository = entregaRepository;
        this.pendienteLoteRepository = pendienteLoteRepository;
    }

    @Override
    public List<PlanificadorCallbackDto> planificarLotes(List<Long> donacionIds, String idCamion) {
        if (donacionIds == null || donacionIds.isEmpty()) {
            throw new IllegalArgumentException("La lista de IDs de donaciones no puede ser vacía");
        }

        Camion camion;
        if (idCamion != null) {
            try {
                camion = flotaService.obtenerPorId(idCamion);
            } catch (Exception e) {
                camion = flotaService.obtenerCamionDisponible();
            }
        } else {
            camion = flotaService.obtenerCamionDisponible();
        }

        List<PlanificadorCallbackDto> lotesGenerados = new ArrayList<>();
        int total = donacionIds.size();

        for (int i = 0; i < total; i += TAMANIO_MAXIMO_LOTE) {
            List<Long> chunk = donacionIds.subList(i, Math.min(total, i + TAMANIO_MAXIMO_LOTE));
            String loteId = "LOTE-" + System.currentTimeMillis() + "-" + (i / TAMANIO_MAXIMO_LOTE + 1);

            PlanificadorCallbackDto loteOptimizado = rutaOptimizerClient.optimizarLote(loteId, chunk, camion);
            lotesGenerados.add(loteOptimizado);

            // Si el optimizador resolvió sincrónicamente con rutas propuestas, persistir y coordinar entrega
            if (loteOptimizado != null && loteOptimizado.getRutasGeneradas() != null && !loteOptimizado.getRutasGeneradas().isEmpty()) {
                procesarCallback(loteOptimizado);
            }

            log.info("Lote {} planificado con {} donaciones (límite estricto 100) para camión {}",
                    loteId, chunk.size(), camion.getPatente());
        }

        return lotesGenerados;
    }

    @Override
    public Map<String, Object> procesarCallback(PlanificadorCallbackDto callbackDto) {
        if (callbackDto == null) {
            throw new IllegalArgumentException("El cuerpo del callback no puede ser nulo");
        }

        String loteId = callbackDto.getLoteId() != null ? callbackDto.getLoteId() : "LOTE-ANONIMO";

        // Idempotencia CU-38 A2: Si el lote ya fue procesado, evitar duplicación
        List<Ruta> rutasExistentes = rutaRepository.findByLoteId(loteId);
        if (!rutasExistentes.isEmpty()) {
            log.warn("El lote {} ya fue procesado previamente. Retornando respuesta idempotente.", loteId);
            return Map.of(
                    "status", "DUPLICADO_IGNORADO",
                    "loteId", loteId,
                    "mensaje", "El lote ya fue registrado previamente sin modificaciones."
            );
        }

        List<Ruta> rutasGuardadas = new ArrayList<>();
        List<Long> donacionesPlanificadasIds = new ArrayList<>();

        if (callbackDto.getRutasGeneradas() != null) {
            for (PlanificadorCallbackDto.RutaPropuestaDto propuesta : callbackDto.getRutasGeneradas()) {
                Ruta ruta = new Ruta(loteId, propuesta.getIdCamion(), propuesta.getPatenteCamion(), LocalDate.now().plusDays(1));

                if (propuesta.getParadas() != null) {
                    for (PlanificadorCallbackDto.ParadaPropuestaDto paradaDto : propuesta.getParadas()) {
                        Parada p = new Parada(
                                paradaDto.getOrden(),
                                paradaDto.getIdEntidad(),
                                paradaDto.getNombreEntidad(),
                                paradaDto.getDireccion(),
                                paradaDto.getLatitud(),
                                paradaDto.getLongitud(),
                                paradaDto.getIdDonacion()
                        );
                        ruta.agregarParada(p);
                        if (paradaDto.getIdDonacion() != null) {
                            donacionesPlanificadasIds.add(paradaDto.getIdDonacion());
                        }
                    }
                }

                Ruta salvada = rutaRepository.save(ruta);
                if (salvada == null) {
                    salvada = ruta;
                }
                rutasGuardadas.add(salvada);

                // Crear entregas asociadas a la ruta enriqueciendo emails desde donaciones-service
                for (Parada p : salvada.getParadas()) {
                    Long idEntidad = p.getIdEntidadBeneficiaria();
                    String nombreEntidad = p.getNombreEntidad();
                    String emailEntidad = "";
                    Long idDonante = null;
                    String emailDonante = "";
                    com.donatrack.common.enums.CanalNotificacion canalDonante = com.donatrack.common.enums.CanalNotificacion.EMAIL;
                    String contactoDonante = null;

                    if (p.getIdDonacion() != null && donacionesServiceClient != null) {
                        Map<String, Object> detalle = donacionesServiceClient.obtenerDetalleDonacion(p.getIdDonacion());
                        if (detalle != null && !detalle.isEmpty()) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> donanteMap = (Map<String, Object>) detalle.get("donante");
                            if (donanteMap != null) {
                                if (donanteMap.get("id") instanceof Number num) {
                                    idDonante = num.longValue();
                                }
                                if (donanteMap.get("emailPrincipal") instanceof String em && !em.isBlank()) {
                                    emailDonante = em;
                                }
                                @SuppressWarnings("unchecked")
                                Map<String, Object> medioPred = (Map<String, Object>) donanteMap.get("medioContactoPredeterminado");
                                if (medioPred != null) {
                                    if (medioPred.get("tipo") instanceof String tipoStr && !tipoStr.isBlank()) {
                                        try {
                                            canalDonante = com.donatrack.common.enums.CanalNotificacion.valueOf(tipoStr.toUpperCase());
                                        } catch (Exception ignored) {}
                                    }
                                    if (medioPred.get("valor") instanceof String valStr && !valStr.isBlank()) {
                                        contactoDonante = valStr;
                                    }
                                }
                            }
                            @SuppressWarnings("unchecked")
                            Map<String, Object> entidadMap = (Map<String, Object>) detalle.get("entidadAsignada");
                            if (entidadMap != null) {
                                if (entidadMap.get("id") instanceof Number num) {
                                    idEntidad = num.longValue();
                                }
                                if (entidadMap.get("razonSocial") instanceof String rs && !rs.isBlank()) {
                                    nombreEntidad = rs;
                                }
                                if (entidadMap.get("emailPrincipal") instanceof String em && !em.isBlank()) {
                                    emailEntidad = em;
                                }
                            }
                        }
                    }

                    Entrega entrega = new Entrega(
                            salvada.getId(),
                            p.getIdDonacion(),
                            idEntidad,
                            nombreEntidad,
                            emailEntidad,
                            idDonante,
                            emailDonante,
                            salvada.getPatenteCamion()
                    );
                    entrega.setCanalPreferidoDonante(canalDonante);
                    entrega.setContactoPreferidoDonante(contactoDonante != null ? contactoDonante : emailDonante);
                    entregaRepository.save(entrega);
                }
            }
        }

        // CU-38 Flujo 7: Sincronizar donaciones a LISTA_PARA_ENTREGAR en donaciones-service
        if (!donacionesPlanificadasIds.isEmpty()) {
            donacionesServiceClient.actualizarEstadoLote(
                    donacionesPlanificadasIds,
                    EstadoDonacion.LISTA_PARA_ENTREGAR,
                    "Asignadas formalmente en lote " + loteId
            );
        }

        // CU-38 A3: Custodiar donaciones devueltas como no asignadas por el planificador
        if (callbackDto.getDonacionesSinAsignar() != null && !callbackDto.getDonacionesSinAsignar().isEmpty()) {
            for (Long idSinAsignar : callbackDto.getDonacionesSinAsignar()) {
                DonacionPendienteLote pendiente = new DonacionPendienteLote(
                        loteId,
                        idSinAsignar,
                        "Capacidad de lote o ventana horaria excedida por el optimizador VRP"
                );
                pendienteLoteRepository.save(pendiente);
            }
            log.info("Custodiadas {} donaciones no asignadas del lote {} para próxima jornada",
                    callbackDto.getDonacionesSinAsignar().size(), loteId);
        }

        return Map.of(
                "status", "PROCESADO",
                "loteId", loteId,
                "rutasCreadas", rutasGuardadas.size(),
                "donacionesPlanificadas", donacionesPlanificadasIds.size(),
                "donacionesSinAsignarCustodiadas", callbackDto.getDonacionesSinAsignar() != null ? callbackDto.getDonacionesSinAsignar().size() : 0
        );
    }

    @Override
    public void ejecutarPlanificacionNocturna() {
        log.info("Iniciando tarea nocturna de planificación automática de rutas (CU-37)...");
        List<Long> idsAsignadas = donacionesServiceClient.obtenerDonacionesAsignadasIds();
        List<Long> idsFinales = new ArrayList<>();

        // Reincorporar donaciones no asignadas en jornadas anteriores (CU-38 A3)
        List<DonacionPendienteLote> pendientesPrevias = pendienteLoteRepository.findByProcesadoFalse();
        for (DonacionPendienteLote p : pendientesPrevias) {
            idsFinales.add(p.getIdDonacion());
            p.setProcesado(true);
            pendienteLoteRepository.save(p);
        }

        if (idsAsignadas != null) {
            for (Long id : idsAsignadas) {
                if (!idsFinales.contains(id)) {
                    idsFinales.add(id);
                }
            }
        }

        if (idsFinales.isEmpty()) {
            log.info("No se encontraron donaciones pendientes en ASIGNACION_REALIZADA para planificar.");
            return;
        }

        log.info("Recuperadas {} donaciones asignadas para planificación en lotes.", idsFinales.size());
        List<PlanificadorCallbackDto> lotes = planificarLotes(idsFinales, null);

        // Procesar automáticamente cada lote resultante si no se procesó en planificarLotes
        for (PlanificadorCallbackDto dto : lotes) {
            List<Ruta> yaGuardadas = (dto.getLoteId() != null) ? rutaRepository.findByLoteId(dto.getLoteId()) : List.of();
            if (yaGuardadas.isEmpty()) {
                procesarCallback(dto);
            }
        }
        log.info("Planificación nocturna completada. {} lotes generados y procesados.", lotes.size());
    }
}
