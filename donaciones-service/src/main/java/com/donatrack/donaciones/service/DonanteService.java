package com.donatrack.donaciones.service;

import com.donatrack.donaciones.domain.PersonaDonante;
import com.donatrack.donaciones.domain.PersonaHumana;
import com.donatrack.donaciones.domain.PersonaJuridica;
import com.donatrack.donaciones.repository.DonacionRepository;
import com.donatrack.donaciones.repository.PersonaDonanteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Servicio de Gestión de Personas Donantes (donaciones-service) - DonaTrack
 * Implementa altas, modificaciones, consulta y baja lógica validada (CU-07 a CU-11).
 */
@Service
public class DonanteService {

    private final PersonaDonanteRepository donanteRepository;
    private final DonacionRepository donacionRepository;
    private final NormalizacionDireccionService normalizacionService;
    private final com.donatrack.donaciones.repository.OutboxEventRepository outboxEventRepository;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    public DonanteService(PersonaDonanteRepository donanteRepository,
                          DonacionRepository donacionRepository,
                          NormalizacionDireccionService normalizacionService) {
        this(donanteRepository, donacionRepository, normalizacionService, null, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public DonanteService(PersonaDonanteRepository donanteRepository,
                          DonacionRepository donacionRepository,
                          NormalizacionDireccionService normalizacionService,
                          com.donatrack.donaciones.repository.OutboxEventRepository outboxEventRepository,
                          com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        this.donanteRepository = donanteRepository;
        this.donacionRepository = donacionRepository;
        this.normalizacionService = normalizacionService;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<PersonaDonante> listarDonantes(boolean soloActivos) {
        return soloActivos ? donanteRepository.findByActivoTrue() : donanteRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<PersonaDonante> listarDonantes() {
        return listarDonantes(true);
    }

    @Transactional(readOnly = true)
    public Optional<PersonaDonante> obtenerPorId(Long id) {
        return donanteRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<PersonaDonante> obtenerPorEmail(String email) {
        return donanteRepository.findByEmail(email);
    }

    @Transactional(readOnly = true)
    public Optional<PersonaDonante> obtenerPorDocumento(String documento) {
        if (documento == null || documento.isBlank()) return Optional.empty();
        return donanteRepository.findByNroDocumento(documento.trim()).map(h -> (PersonaDonante) h);
    }

    @Transactional(readOnly = true)
    public Optional<PersonaDonante> obtenerPorCuit(String cuit) {
        if (cuit == null || cuit.isBlank()) return Optional.empty();
        return donanteRepository.findByCuit(cuit.trim()).map(j -> (PersonaDonante) j);
    }

    @Transactional
    public PersonaHumana registrarPersonaHumana(PersonaHumana persona) {
        return registrarDonanteHumano(persona);
    }

    @Transactional
    public PersonaHumana registrarDonanteHumano(PersonaHumana persona) {
        if (persona.getEmailPrincipal() == null || persona.getEmailPrincipal().isBlank()) {
            throw new IllegalArgumentException("El correo electrónico es obligatorio para donantes humanos (CU-07)");
        }
        if (persona.getNroDocumento() != null && !persona.getNroDocumento().isBlank()) {
            donanteRepository.findByNroDocumento(persona.getNroDocumento().trim()).ifPresent(d -> {
                throw new IllegalArgumentException("Ya existe un donante registrado con el documento: " + persona.getNroDocumento());
            });
        }
        donanteRepository.findByEmail(persona.getEmailPrincipal()).ifPresent(d -> {
            throw new IllegalStateException("Ya existe un donante registrado con el email: " + persona.getEmailPrincipal());
        });

        // Validación y normalización geográfica de domicilio si fue provisto
        if (persona.getDireccion() != null && !persona.getDireccion().trim().isBlank()
                && !"Sin direccion".equalsIgnoreCase(persona.getDireccion().trim())) {
            com.donatrack.donaciones.dto.DireccionNormalizadaDto norm = normalizacionService.normalizar(persona.getDireccion());
            persona.setDireccion(norm.nomenclatura());
            persona.setLatitud(norm.latitud());
            persona.setLongitud(norm.longitud());
        }

        if (persona.getMediosContacto() != null) {
            persona.getMediosContacto().forEach(m -> m.setDonante(persona));
        }
        persona.setActivo(true);
        PersonaHumana guardada = donanteRepository.save(persona);

        publicarEventoDonanteRegistrado(guardada, "DEPOSITO_O_ALTA");
        return guardada;
    }

    @Transactional
    public PersonaJuridica registrarPersonaJuridica(PersonaJuridica persona) {
        return registrarDonanteJuridico(persona);
    }

    @Transactional
    public PersonaJuridica registrarDonanteJuridico(PersonaJuridica persona) {
        if (persona.getRazonSocial() == null || persona.getRazonSocial().isBlank()) {
            throw new IllegalArgumentException("La razón social es obligatoria para personas jurídicas (CU-08)");
        }
        if (persona.getCuit() == null || persona.getCuit().isBlank()) {
            throw new IllegalArgumentException("El CUIT es obligatorio para personas jurídicas (CU-08)");
        }
        if (persona.getMediosContacto() == null || persona.getMediosContacto().isEmpty()) {
            throw new IllegalArgumentException("Debe especificar al menos un medio de contacto para personas jurídicas (CU-08)");
        }
        donanteRepository.findByCuit(persona.getCuit().trim()).ifPresent(d -> {
            throw new IllegalArgumentException("Ya existe una persona jurídica registrada con el CUIT: " + persona.getCuit());
        });
        if (persona.getEmailPrincipal() != null && !persona.getEmailPrincipal().isBlank()) {
            donanteRepository.findByEmail(persona.getEmailPrincipal()).ifPresent(d -> {
                throw new IllegalArgumentException("Ya existe un donante registrado con el email: " + persona.getEmailPrincipal());
            });
        }

        // Validación y normalización geográfica de domicilio si fue provisto
        if (persona.getDireccion() != null && !persona.getDireccion().trim().isBlank()
                && !"Sin direccion".equalsIgnoreCase(persona.getDireccion().trim())) {
            com.donatrack.donaciones.dto.DireccionNormalizadaDto norm = normalizacionService.normalizar(persona.getDireccion());
            persona.setDireccion(norm.nomenclatura());
            persona.setLatitud(norm.latitud());
            persona.setLongitud(norm.longitud());
        }

        if (persona.getMediosContacto() != null) {
            persona.getMediosContacto().forEach(m -> m.setDonante(persona));
        }
        persona.setActivo(true);
        PersonaJuridica guardada = donanteRepository.save(persona);

        publicarEventoDonanteRegistrado(guardada, "DEPOSITO_O_ALTA");
        return guardada;
    }

    @Transactional
    public PersonaHumana actualizarPersonaHumana(Long id, PersonaHumana datos) {
        PersonaDonante donante = donanteRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Donante no encontrado: " + id));
        if (!(donante instanceof PersonaHumana existente)) {
            throw new IllegalArgumentException("El donante con ID " + id + " no es una Persona Humana");
        }

        if (datos.getNombre() != null && !datos.getNombre().isBlank()) existente.setNombre(datos.getNombre());
        if (datos.getApellido() != null && !datos.getApellido().isBlank()) existente.setApellido(datos.getApellido());
        if (datos.getEdad() != null) existente.setEdad(datos.getEdad());
        if (datos.getTipoDocumento() != null && !datos.getTipoDocumento().isBlank()) existente.setTipoDocumento(datos.getTipoDocumento());
        if (datos.getNroDocumento() != null && !datos.getNroDocumento().isBlank()) {
            donanteRepository.findByNroDocumento(datos.getNroDocumento().trim()).ifPresent(otro -> {
                if (!otro.getId().equals(id)) {
                    throw new IllegalArgumentException("Ya existe otro donante con el documento: " + datos.getNroDocumento());
                }
            });
            existente.setNroDocumento(datos.getNroDocumento());
        }
        if (datos.getGenero() != null) existente.setGenero(datos.getGenero());

        if (datos.getDireccion() != null && !datos.getDireccion().trim().isBlank()) {
            if (datos.getLatitud() != null && datos.getLongitud() != null) {
                existente.setDireccion(datos.getDireccion());
                existente.setLatitud(datos.getLatitud());
                existente.setLongitud(datos.getLongitud());
            } else if (normalizacionService != null && !"Sin direccion".equalsIgnoreCase(datos.getDireccion().trim())) {
                try {
                    com.donatrack.donaciones.dto.DireccionNormalizadaDto norm = normalizacionService.normalizar(datos.getDireccion());
                    existente.setDireccion(norm.nomenclatura());
                    existente.setLatitud(norm.latitud());
                    existente.setLongitud(norm.longitud());
                } catch (Exception e) {
                    existente.setDireccion(datos.getDireccion());
                }
            } else {
                existente.setDireccion(datos.getDireccion());
            }
        }
        if (datos.getLatitud() != null) existente.setLatitud(datos.getLatitud());
        if (datos.getLongitud() != null) existente.setLongitud(datos.getLongitud());

        if (datos.getMediosContacto() != null && !datos.getMediosContacto().isEmpty()) {
            existente.getMediosContacto().clear();
            for (com.donatrack.donaciones.domain.MedioContacto mc : datos.getMediosContacto()) {
                existente.agregarMedioContacto(mc);
            }
        }

        existente.registrarActividad();
        return donanteRepository.save(existente);
    }

    @Transactional
    public PersonaJuridica actualizarPersonaJuridica(Long id, PersonaJuridica datos) {
        PersonaDonante donante = donanteRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Donante no encontrado: " + id));
        if (!(donante instanceof PersonaJuridica existente)) {
            throw new IllegalArgumentException("El donante con ID " + id + " no es una Persona Jurídica");
        }

        if (datos.getRazonSocial() != null && !datos.getRazonSocial().isBlank()) existente.setRazonSocial(datos.getRazonSocial());
        if (datos.getCuit() != null && !datos.getCuit().isBlank()) {
            donanteRepository.findByCuit(datos.getCuit().trim()).ifPresent(otro -> {
                if (!otro.getId().equals(id)) {
                    throw new IllegalArgumentException("Ya existe otra persona jurídica con el CUIT: " + datos.getCuit());
                }
            });
            existente.setCuit(datos.getCuit());
        }
        if (datos.getTipo() != null && !datos.getTipo().isBlank()) existente.setTipo(datos.getTipo());
        if (datos.getRubro() != null && !datos.getRubro().isBlank()) existente.setRubro(datos.getRubro());

        if (datos.getDireccion() != null && !datos.getDireccion().trim().isBlank()) {
            if (datos.getLatitud() != null && datos.getLongitud() != null) {
                existente.setDireccion(datos.getDireccion());
                existente.setLatitud(datos.getLatitud());
                existente.setLongitud(datos.getLongitud());
            } else if (normalizacionService != null && !"Sin direccion".equalsIgnoreCase(datos.getDireccion().trim())) {
                try {
                    com.donatrack.donaciones.dto.DireccionNormalizadaDto norm = normalizacionService.normalizar(datos.getDireccion());
                    existente.setDireccion(norm.nomenclatura());
                    existente.setLatitud(norm.latitud());
                    existente.setLongitud(norm.longitud());
                } catch (Exception e) {
                    existente.setDireccion(datos.getDireccion());
                }
            } else {
                existente.setDireccion(datos.getDireccion());
            }
        }
        if (datos.getLatitud() != null) existente.setLatitud(datos.getLatitud());
        if (datos.getLongitud() != null) existente.setLongitud(datos.getLongitud());

        if (datos.getRepresentantes() != null) {
            existente.getRepresentantes().clear();
            existente.getRepresentantes().addAll(datos.getRepresentantes());
        }

        if (datos.getMediosContacto() != null && !datos.getMediosContacto().isEmpty()) {
            existente.getMediosContacto().clear();
            for (com.donatrack.donaciones.domain.MedioContacto mc : datos.getMediosContacto()) {
                existente.agregarMedioContacto(mc);
            }
        }

        existente.registrarActividad();
        return donanteRepository.save(existente);
    }

    @Transactional
    public PersonaDonante actualizarContactoPreferido(Long id, com.donatrack.common.enums.CanalNotificacion canal, String valor) {
        PersonaDonante donante = donanteRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Donante no encontrado: " + id));
        if (canal == null) {
            throw new IllegalArgumentException("El canal de notificación preferido es obligatorio");
        }
        boolean encontrado = false;
        for (com.donatrack.donaciones.domain.MedioContacto mc : donante.getMediosContacto()) {
            if (mc.getTipo() == canal) {
                mc.setEsPredeterminado(true);
                if (valor != null && !valor.isBlank()) {
                    mc.setValor(valor);
                }
                encontrado = true;
            } else {
                mc.setEsPredeterminado(false);
            }
        }
        if (!encontrado && valor != null && !valor.isBlank()) {
            donante.agregarMedioContacto(new com.donatrack.donaciones.domain.MedioContacto(canal, valor, true));
        }
        donante.registrarActividad();
        return donanteRepository.save(donante);
    }

    private void publicarEventoDonanteRegistrado(PersonaDonante donante, String origen) {
        try {
            com.donatrack.donaciones.domain.MedioContacto m = donante.getMedioContactoPredeterminado();
            com.donatrack.common.enums.CanalNotificacion canal = (m != null && m.getTipo() != null)
                    ? m.getTipo() : com.donatrack.common.enums.CanalNotificacion.EMAIL;
            String contacto = (m != null && m.getValor() != null)
                    ? m.getValor() : donante.getEmailPrincipal();

            com.donatrack.common.events.DonanteRegistradoEvent ev = new com.donatrack.common.events.DonanteRegistradoEvent(
                    donante.getId(),
                    donante.getNombreCompleto(),
                    donante.getEmailPrincipal(),
                    canal,
                    contacto,
                    donante.getEmailPrincipal(),
                    "donante123",
                    origen
            );
            String payloadJson = objectMapper.writeValueAsString(ev);
            outboxEventRepository.save(new com.donatrack.donaciones.domain.OutboxEvent(
                    "PersonaDonante",
                    donante.getId().toString(),
                    "DonanteRegistradoEvent",
                    "donante.registrado",
                    payloadJson
            ));
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(DonanteService.class).error("Error emitiendo DonanteRegistradoEvent: {}", e.getMessage());
        }
    }

    /**
     * Aplica baja lógica a un donante (CU-11).
     * Invariante de negocio: Bloquea la baja si el donante posee donaciones activas
     * en estados EN_DEPOSITO, ASIGNACION_REALIZADA, LISTA_PARA_ENTREGAR o EN_TRASLADO.
     */
    @Transactional
    public PersonaDonante darDeBajaLogica(Long id, String motivo) {
        PersonaDonante donante = donanteRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Donante no encontrado: " + id));

        long donacionesActivas = donacionRepository.countActivasByDonanteId(id);
        if (donacionesActivas > 0) {
            throw new IllegalStateException("No se puede dar de baja al donante (ID: " + id + ") porque posee "
                    + donacionesActivas + " donación(es) activa(s) en depósito, asignadas o en traslado (CU-11).");
        }

        donante.darDeBaja(motivo);
        return donanteRepository.save(donante);
    }

    /**
     * Reactivación administrativa de un donante inactivo (CU-11).
     */
    @Transactional
    public PersonaDonante reactivarDonante(Long id) {
        PersonaDonante donante = donanteRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Donante no encontrado: " + id));
        donante.reactivar();
        return donanteRepository.save(donante);
    }
}
