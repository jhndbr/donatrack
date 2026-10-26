package com.donatrack.donaciones.repository.specification;

import com.donatrack.common.enums.EstadoDonacion;
import com.donatrack.donaciones.domain.Donacion;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Especificación JPA para filtrado dinámico multicriterio de Donaciones (CU-16, CU-17).
 * Permite combinar libremente filtros por listado de estados, subcategoría, donante,
 * entidad asignada, rango de fechas de recepción y condición de uso.
 */
public class DonacionSpecification {

    public static Specification<Donacion> conFiltros(
            EstadoDonacion estado,
            String categoria,
            String subcategoria,
            Long donanteId,
            Long entidadId
    ) {
        List<EstadoDonacion> estados = estado != null ? List.of(estado) : null;
        return conFiltros(estados, subcategoria, donanteId, entidadId, null, null, null);
    }

    public static Specification<Donacion> conFiltros(
            List<EstadoDonacion> estados,
            String subcategoria,
            Long donanteId,
            Long entidadId,
            LocalDateTime fechaDesde,
            LocalDateTime fechaHasta,
            Boolean esUsado
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (estados != null && !estados.isEmpty()) {
                predicates.add(root.get("estado").in(estados));
            }

            if (subcategoria != null && !subcategoria.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("subcategoria")), subcategoria.toLowerCase().trim()));
            }

            if (donanteId != null) {
                predicates.add(cb.equal(root.get("donante").get("id"), donanteId));
            }

            if (entidadId != null) {
                predicates.add(cb.equal(root.get("entidadAsignada").get("id"), entidadId));
            }

            if (fechaDesde != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("fechaRecepcion"), fechaDesde));
            }

            if (fechaHasta != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("fechaRecepcion"), fechaHasta));
            }

            if (esUsado != null) {
                predicates.add(cb.equal(root.get("esUsado"), esUsado));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
