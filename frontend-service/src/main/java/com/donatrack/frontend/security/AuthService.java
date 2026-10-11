package com.donatrack.frontend.security;

import com.donatrack.frontend.service.ApiClientService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private final ApiClientService apiClient;

    public AuthService(ApiClientService apiClient) {
        this.apiClient = apiClient;
    }

    /**
     * Valida credenciales contra las cuentas maestras o entidades/donantes registrados.
     * Retorna Optional<UserSession> con la sesion armada si es exitoso, u Optional.empty() si es invalido.
     */
    public Optional<UserSession> autenticar(String username, String password) {
        if (username == null || password == null || username.isBlank() || password.isBlank()) {
            return Optional.empty();
        }

        String userClean = username.trim().toLowerCase();

        // 1. Verificación de Administrador
        if (("admin".equals(userClean) || "admin@donatrack.org".equals(userClean))) {
            if ("admin123".equals(password)) {
                UserSession adminSession = new UserSession("admin", "Administrador Central", "admin@donatrack.org", "ROLE_ADMIN");
                log.info("Autenticación exitosa como ROLE_ADMIN para usuario: {}", username);
                return Optional.of(adminSession);
            } else {
                log.warn("Intento de login fallido para admin: contraseña incorrecta");
                return Optional.empty();
            }
        }

        // 2. Verificación de Entidad Beneficiaria (clave esperada 'comedor123' o 'admin123')
        List<Map<String, Object>> entidades = apiClient.listarEntidades();
        Optional<Map<String, Object>> entidadEncontrada = entidades.stream()
                .filter(e -> coincideEmailOId(e, userClean))
                .findFirst();

        if (entidadEncontrada.isPresent()) {
            if ("comedor123".equals(password) || "admin123".equals(password)) {
                Map<String, Object> ent = entidadEncontrada.get();
                Long id = ent.get("id") instanceof Number ? ((Number) ent.get("id")).longValue() : 1L;
                String nombre = ent.get("razonSocial") != null ? ent.get("razonSocial").toString() : "Entidad Beneficiaria";

                UserSession session = new UserSession(userClean, nombre, userClean, "ROLE_BENEFICIARIA");
                session.setEntidadId(id);
                log.info("Autenticación exitosa como ROLE_BENEFICIARIA: {}", nombre);
                return Optional.of(session);
            } else {
                log.warn("Contraseña incorrecta para entidad beneficiaria: {}", userClean);
                return Optional.empty();
            }
        }

        // 3. Verificación de Donante (clave esperada 'donante123' o 'admin123')
        List<Map<String, Object>> donantes = apiClient.listarDonantes();
        Optional<Map<String, Object>> donanteEncontrado = donantes.stream()
                .filter(d -> coincideEmailDonante(d, userClean))
                .findFirst();

        if (donanteEncontrado.isPresent()) {
            if ("donante123".equals(password) || "admin123".equals(password)) {
                Map<String, Object> d = donanteEncontrado.get();
                Long id = d.get("id") instanceof Number ? ((Number) d.get("id")).longValue() : 1L;
                String nombre = (d.get("nombre") != null ? d.get("nombre") : "Donante") + " " + (d.get("apellido") != null ? d.get("apellido") : "");

                UserSession session = new UserSession(userClean, nombre.trim(), userClean, "ROLE_DONANTE");
                session.setDonanteId(id);
                log.info("Autenticación exitosa como ROLE_DONANTE: {}", nombre);
                return Optional.of(session);
            } else {
                log.warn("Contraseña incorrecta para donante: {}", userClean);
                return Optional.empty();
            }
        }

        // 4. Soporte para usuario demo de donante especificado en la documentación del TP
        if ("juan.donante@example.com".equals(userClean) || "donante_juan".equals(userClean)) {
            if ("donante123".equals(password)) {
                UserSession session = new UserSession("donante_juan", "Juan Pérez", "juan.donante@example.com", "ROLE_DONANTE");
                session.setDonanteId(1L);
                return Optional.of(session);
            }
        }

        if ("esperanza@comedor.org".equals(userClean) || "comedor_esperanza".equals(userClean)) {
            if ("comedor123".equals(password)) {
                UserSession session = new UserSession("comedor_esperanza", "Comedor Esperanza", "esperanza@comedor.org", "ROLE_BENEFICIARIA");
                session.setEntidadId(1L);
                return Optional.of(session);
            }
        }

        log.warn("Usuario no encontrado en el sistema: {}", username);
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    private boolean coincideEmailOId(Map<String, Object> entidad, String userClean) {
        if (entidad.get("id") != null && entidad.get("id").toString().equals(userClean)) {
            return true;
        }
        if (entidad.get("emailsRepresentantes") instanceof List) {
            List<String> emails = (List<String>) entidad.get("emailsRepresentantes");
            return emails.stream().anyMatch(em -> em != null && em.toLowerCase().contains(userClean));
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private boolean coincideEmailDonante(Map<String, Object> donante, String userClean) {
        if (donante.get("id") != null && donante.get("id").toString().equals(userClean)) {
            return true;
        }
        if (donante.get("emailPrincipal") != null && donante.get("emailPrincipal").toString().equalsIgnoreCase(userClean)) {
            return true;
        }
        if (donante.get("email") != null && donante.get("email").toString().equalsIgnoreCase(userClean)) {
            return true;
        }
        if (donante.get("nroDocumento") != null && donante.get("nroDocumento").toString().equals(userClean)) {
            return true;
        }
        if (donante.get("mediosContacto") instanceof List) {
            List<Map<String, Object>> medios = (List<Map<String, Object>>) donante.get("mediosContacto");
            return medios.stream().anyMatch(m -> m.get("valor") != null && m.get("valor").toString().equalsIgnoreCase(userClean));
        }
        return false;
    }
}
