package com.donatrack.donaciones.service;

import com.donatrack.common.enums.TipoNecesidad;
import com.donatrack.donaciones.domain.EntidadBeneficiaria;
import com.donatrack.donaciones.domain.NecesidadMaterial;
import com.donatrack.donaciones.repository.DonacionRepository;
import com.donatrack.donaciones.repository.EntidadBeneficiariaRepository;
import com.donatrack.donaciones.repository.NecesidadMaterialRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Servicio de Entidades Beneficiarias y Necesidades (donaciones-service) - DonaTrack
 * Administra el catálogo de entidades, sus necesidades materiales (CU-22 a CU-29),
 * y la baja lógica validada contra entregas pendientes en tránsito (CU-25).
 */
@Service
public class EntidadBeneficiariaService {

    private final EntidadBeneficiariaRepository entidadRepository;
    private final NecesidadMaterialRepository necesidadRepository;
    private final DonacionRepository donacionRepository;
    private final NormalizacionDireccionService normalizacionService;

    public EntidadBeneficiariaService(EntidadBeneficiariaRepository entidadRepository,
                                      NecesidadMaterialRepository necesidadRepository,
                                      DonacionRepository donacionRepository,
                                      NormalizacionDireccionService normalizacionService) {
        this.entidadRepository = entidadRepository;
        this.necesidadRepository = necesidadRepository;
        this.donacionRepository = donacionRepository;
        this.normalizacionService = normalizacionService;
    }

    @Transactional(readOnly = true)
    public List<EntidadBeneficiaria> listarEntidades(boolean soloActivas) {
        return soloActivas ? entidadRepository.findByActivoTrue() : entidadRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<EntidadBeneficiaria> listarEntidades() {
        return listarEntidades(true);
    }

    @Transactional(readOnly = true)
    public Optional<EntidadBeneficiaria> obtenerPorId(Long id) {
        return entidadRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<EntidadBeneficiaria> obtenerPorEmail(String email) {
        if (email == null || email.isBlank()) return Optional.empty();
        List<EntidadBeneficiaria> list = entidadRepository.findByEmailRepresentante(email.trim());
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    @Transactional(readOnly = true)
    public Optional<EntidadBeneficiaria> obtenerPorRazonSocial(String razonSocial) {
        if (razonSocial == null || razonSocial.isBlank()) return Optional.empty();
        return entidadRepository.findByRazonSocial(razonSocial.trim());
    }

    @Transactional
    public EntidadBeneficiaria registrarEntidad(EntidadBeneficiaria entidad) {
        if (entidad.getRazonSocial() == null || entidad.getRazonSocial().isBlank()) {
            throw new IllegalArgumentException("La razón social es obligatoria (CU-22)");
        }
        if (entidad.getDireccion() == null || entidad.getDireccion().isBlank()) {
            throw new IllegalArgumentException("La dirección es obligatoria (CU-22)");
        }
        com.donatrack.donaciones.dto.DireccionNormalizadaDto norm = normalizacionService.normalizar(entidad.getDireccion());
        entidad.setDireccion(norm.nomenclatura());
        entidad.setLatitud(norm.latitud());
        entidad.setLongitud(norm.longitud());

        if (entidad.getEmailsRepresentantes() == null || entidad.getEmailsRepresentantes().isEmpty()) {
            entidad.getEmailsRepresentantes().add(entidad.getEmailPrincipal());
        }

        entidad.setActivo(true);
        return entidadRepository.save(entidad);
    }

    /**
     * Registro de una necesidad material vinculada a una entidad (CU-26).
     * Valida unidad mínima (subcategoría), cantidad positiva y período obligatorio en recurrentes.
     */
    @Transactional
    public NecesidadMaterial registrarNecesidad(Long entidadId, NecesidadMaterial necesidad) {
        EntidadBeneficiaria entidad = entidadRepository.findById(entidadId)
                .orElseThrow(() -> new IllegalArgumentException("Entidad beneficiaria no encontrada: " + entidadId));

        if (!entidad.isActivo()) {
            throw new IllegalStateException("La entidad beneficiaria se encuentra inactiva y no puede registrar necesidades.");
        }
        if (necesidad.getSubcategoria() == null || necesidad.getSubcategoria().isBlank()) {
            throw new IllegalArgumentException("La subcategoría es la unidad mínima y es obligatoria (CU-26)");
        }
        if (necesidad.getCantidadSolicitada() <= 0) {
            throw new IllegalArgumentException("La cantidad solicitada debe ser mayor a cero (CU-26)");
        }
        if (necesidad.getTipo() == TipoNecesidad.RECURRENTE && (necesidad.getPeriodo() == null || necesidad.getPeriodo().isBlank())) {
            throw new IllegalArgumentException("Para necesidades de tipo RECURRENTE es obligatorio especificar el período (ej. SEMANAL, MENSUAL) (CU-26)");
        }

        necesidad.setEntidadBeneficiaria(entidad);
        return necesidadRepository.save(necesidad);
    }

    /**
     * Modificación de necesidad material (CU-27).
     * Invariante: La nueva cantidad solicitada no puede ser inferior a la cantidad cubierta acumulada.
     */
    @Transactional
    public NecesidadMaterial modificarNecesidad(Long necesidadId, Double nuevaCantidad, String nuevaDescripcion) {
        NecesidadMaterial necesidad = necesidadRepository.findById(necesidadId)
                .orElseThrow(() -> new IllegalArgumentException("Necesidad material no encontrada: " + necesidadId));

        if (nuevaCantidad != null) {
            if (nuevaCantidad <= 0) {
                throw new IllegalArgumentException("La cantidad solicitada debe ser mayor a cero (CU-27)");
            }
            if (nuevaCantidad < necesidad.getCantidadCubierta()) {
                throw new IllegalArgumentException("La nueva cantidad solicitada (" + nuevaCantidad
                        + ") no puede ser inferior a la cantidad ya cubierta por donaciones (" + necesidad.getCantidadCubierta() + ") (CU-27)");
            }
            necesidad.setCantidadSolicitada(nuevaCantidad);
        }

        if (nuevaDescripcion != null && !nuevaDescripcion.isBlank()) {
            necesidad.setDescripcion(nuevaDescripcion);
        }

        return necesidadRepository.save(necesidad);
    }

    /**
     * Cancelación o cierre formal de una necesidad material con justificación (CU-29).
     */
    @Transactional
    public NecesidadMaterial cancelarNecesidad(Long necesidadId, String motivo) {
        NecesidadMaterial necesidad = necesidadRepository.findById(necesidadId)
                .orElseThrow(() -> new IllegalArgumentException("Necesidad material no encontrada: " + necesidadId));

        necesidad.cancelar(motivo);
        return necesidadRepository.save(necesidad);
    }

    /**
     * Aplica baja lógica a una entidad beneficiaria (CU-25).
     * Invariante de negocio: Bloquea la baja si la entidad posee donaciones asignadas
     * o en tránsito hacia su sede en el circuito logístico.
     */
    @Transactional
    public EntidadBeneficiaria darDeBajaLogica(Long id, String motivo) {
        EntidadBeneficiaria entidad = entidadRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Entidad beneficiaria no encontrada: " + id));

        long donacionesActivas = donacionRepository.countActivasByEntidadId(id);
        if (donacionesActivas > 0) {
            throw new IllegalStateException("No se puede dar de baja a la entidad (ID: " + id + ") porque posee "
                    + donacionesActivas + " donación(es) asignadas o en traslado hacia su sede (CU-25).");
        }

        entidad.darDeBaja(motivo);
        return entidadRepository.save(entidad);
    }

    /**
     * Reactivación administrativa de una entidad inactiva (CU-25).
     */
    @Transactional
    public EntidadBeneficiaria reactivarEntidad(Long id) {
        EntidadBeneficiaria entidad = entidadRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Entidad beneficiaria no encontrada: " + id));
        entidad.reactivar();
        return entidadRepository.save(entidad);
    }
}
