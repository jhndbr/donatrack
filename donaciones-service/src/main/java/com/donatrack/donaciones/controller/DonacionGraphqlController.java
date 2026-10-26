package com.donatrack.donaciones.controller;

import com.donatrack.common.enums.EstadoDonacion;
import com.donatrack.donaciones.domain.Donacion;
import com.donatrack.donaciones.repository.DonacionRepository;
import com.donatrack.donaciones.repository.specification.DonacionSpecification;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.List;

/**
 * Controlador GraphQL para Donaciones (Entrega 6 - Requerimiento 5b).
 * Permite filtros dinámicos y selección de campos específicos.
 */
@Controller
public class DonacionGraphqlController {

    private final DonacionRepository donacionRepository;

    public DonacionGraphqlController(DonacionRepository donacionRepository) {
        this.donacionRepository = donacionRepository;
    }

    @QueryMapping
    public String saludo() {
        return "Hola desde GraphQL en Servicio de Donaciones (Entrega 6)";
    }

    @QueryMapping
    public List<DonacionGraphqlDto> donaciones(@Argument String estado, @Argument String subcategoria) {
        EstadoDonacion estadoEnum = (estado != null && !estado.isBlank())
                ? EstadoDonacion.valueOf(estado.trim().toUpperCase())
                : null;
        List<Donacion> filtradas = donacionRepository.findAll(
                DonacionSpecification.conFiltros(estadoEnum, null, subcategoria, null, null)
        );
        return filtradas.stream()
                .map(this::mapToDto)
                .toList();
    }

    @QueryMapping
    public DonacionGraphqlDto donacionPorId(@Argument Long id) {
        return donacionRepository.findById(id).map(this::mapToDto).orElse(null);
    }

    private DonacionGraphqlDto mapToDto(Donacion d) {
        return new DonacionGraphqlDto(
                d.getId().toString(),
                d.getSubcategoria(),
                d.getCantidad(),
                d.getUnidadMedida(),
                d.isEsUsado(),
                d.getFechaVencimiento() != null ? d.getFechaVencimiento().toString() : null,
                d.getEstado().name(),
                d.getDonante() != null ? d.getDonante().getNombreCompleto() : "Anónimo",
                d.getEntidadAsignada() != null ? d.getEntidadAsignada().getRazonSocial() : null
        );
    }

    public record DonacionGraphqlDto(
            String id,
            String subcategoria,
            Double cantidad,
            String unidadMedida,
            Boolean esUsado,
            String fechaVencimiento,
            String estado,
            String nombreDonante,
            String entidadAsignada
    ) {}
}
