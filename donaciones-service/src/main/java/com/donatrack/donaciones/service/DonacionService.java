package com.donatrack.donaciones.service;

import com.donatrack.common.enums.EstadoDonacion;
import com.donatrack.common.events.DonacionAsignadaEvent;
import com.donatrack.donaciones.domain.*;
import com.donatrack.donaciones.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class DonacionService {

    private final DonacionRepository donacionRepository;
    private final PersonaDonanteRepository donanteRepository;
    private final EntidadBeneficiariaRepository entidadRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Value("${donatrack.rabbitmq.routing-key.donacion-asignada:donacion.asignada}")
    private String routingKeyDonacionAsignada;

    public DonacionService(DonacionRepository donacionRepository,
                           PersonaDonanteRepository donanteRepository,
                           EntidadBeneficiariaRepository entidadRepository,
                           OutboxEventRepository outboxEventRepository,
                           ObjectMapper objectMapper) {
        this.donacionRepository = donacionRepository;
        this.donanteRepository = donanteRepository;
        this.entidadRepository = entidadRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Segmenta automaticamente un conjunto de bienes por subcategoria.
     * Cada subcategoria constituye la unidad minima de asignacion independiente.
     */
    @Transactional
    public List<Donacion> registrarCargaSegmentada(Long donanteId, List<BienDonado> bienes) {
        PersonaDonante donante = donanteRepository.findById(donanteId)
                .orElseThrow(() -> new IllegalArgumentException("Donante no encontrado: " + donanteId));
        donante.registrarActividad();

        // Agrupar bienes por subcategoria y por fecha de vencimiento (si aplica)
        Map<String, List<BienDonado>> porSubcategoria = bienes.stream()
                .collect(Collectors.groupingBy(b -> b.getSubcategoria() + "_" + (b.getFechaVencimiento() != null ? b.getFechaVencimiento() : "NO_VENCE")));

        List<Donacion> donacionesResultantes = new ArrayList<>();

        for (List<BienDonado> grupoBienes : porSubcategoria.values()) {
            BienDonado muestra = grupoBienes.get(0);
            double cantidadTotal = grupoBienes.stream().mapToDouble(BienDonado::getCantidad).sum();
            boolean algunoUsado = grupoBienes.stream().anyMatch(BienDonado::isEsUsado);

            Donacion donacion = new Donacion(
                    donante,
                    muestra.getSubcategoria(),
                    cantidadTotal,
                    muestra.getUnidadMedida(),
                    algunoUsado,
                    muestra.getFechaVencimiento()
            );

            for (BienDonado b : grupoBienes) {
                donacion.agregarBien(b);
            }

            donacionesResultantes.add(donacionRepository.save(donacion));
        }

        return donacionesResultantes;
    }

    /**
     * Asigna una donacion a una entidad beneficiaria.
     * Utiliza Transactional Outbox Pattern para garantizar consistencia con RabbitMQ.
     */
    @Transactional
    public Donacion asignarDonacion(Long donacionId, Long entidadId) {
        Donacion donacion = donacionRepository.findById(donacionId)
                .orElseThrow(() -> new IllegalArgumentException("Donacion no encontrada: " + donacionId));
        EntidadBeneficiaria entidad = entidadRepository.findById(entidadId)
                .orElseThrow(() -> new IllegalArgumentException("Entidad beneficiaria no encontrada: " + entidadId));

        donacion.asignarA(entidad);
        Donacion guardada = donacionRepository.save(donacion);

        // Crear evento para el Outbox en la misma transaccion de PostgreSQL
        DonacionAsignadaEvent evento = new DonacionAsignadaEvent(
                guardada.getId(),
                guardada.getSubcategoria(),
                guardada.getCantidad(),
                guardada.getUnidadMedida(),
                entidad.getId(),
                entidad.getRazonSocial(),
                entidad.getEmailPrincipal(),
                guardada.getDonante().getId(),
                guardada.getDonante().getEmailPrincipal(),
                guardada.getDonante().getNombreCompleto()
        );

        try {
            String payloadJson = objectMapper.writeValueAsString(evento);
            OutboxEvent outbox = new OutboxEvent(
                    "Donacion",
                    guardada.getId().toString(),
                    "DonacionAsignadaEvent",
                    routingKeyDonacionAsignada,
                    payloadJson
            );
            outboxEventRepository.save(outbox);
        } catch (Exception e) {
            throw new RuntimeException("Error al serializar evento outbox para donacion " + donacionId, e);
        }

        return guardada;
    }

    @Transactional
    public Donacion actualizarEstado(Long donacionId, EstadoDonacion nuevoEstado, String justificacion) {
        Donacion donacion = donacionRepository.findById(donacionId)
                .orElseThrow(() -> new IllegalArgumentException("Donacion no encontrada: " + donacionId));

        switch (nuevoEstado) {
            case LISTA_PARA_ENTREGAR -> donacion.marcarListaParaEntregar();
            case EN_TRASLADO -> donacion.iniciarTraslado();
            case ENTREGADA -> donacion.confirmarEntrega();
            case ENTREGA_FALLIDA -> donacion.registrarEntregaFallida(justificacion);
            case VENCIDA -> donacion.marcarVencida();
            case EN_DEPOSITO -> donacion.reingresarADeposito();
            default -> throw new IllegalArgumentException("Transicion de estado no permitida directamente: " + nuevoEstado);
        }

        return donacionRepository.save(donacion);
    }

    @Transactional
    public List<Donacion> actualizarEstadoLote(List<Long> donacionIds, EstadoDonacion nuevoEstado, String justificacion) {
        List<Donacion> actualizadas = new ArrayList<>();
        for (Long id : donacionIds) {
            actualizadas.add(actualizarEstado(id, nuevoEstado, justificacion));
        }
        return actualizadas;
    }
}
