package com.donatrack.frontend.security;

import com.donatrack.frontend.service.ApiClientService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private final ApiClientService apiClient;
    private final RestTemplate restTemplate;

    private final Map<String, String> credencialesRegistradas = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.nio.file.Path credencialesFilePath = java.nio.file.Paths.get("data", "credenciales-locales.properties");

    @Value("${donatrack.keycloak.url:http://localhost:8081}")
    private String keycloakUrl;

    @Value("${donatrack.keycloak.realm:donatrack}")
    private String realm;

    @Value("${donatrack.keycloak.client-id:donatrack-client}")
    private String clientId;

    public AuthService(ApiClientService apiClient, RestTemplate restTemplate) {
        this.apiClient = apiClient;
        this.restTemplate = restTemplate;
        this.credencialesRegistradas.put("admin@donatrack.org", "admin123");
        this.credencialesRegistradas.put("admin", "admin123");
        this.credencialesRegistradas.put("juan.donante@example.com", "donante123");
        this.credencialesRegistradas.put("donante_juan", "donante123");
        this.credencialesRegistradas.put("35123456", "donante123");
        this.credencialesRegistradas.put("rse@molinos.com.ar", "donante123");
        this.credencialesRegistradas.put("30-11223344-9", "donante123");
        this.credencialesRegistradas.put("esperanza@comedor.org", "comedor123");
        this.credencialesRegistradas.put("comedor_esperanza", "comedor123");
        this.credencialesRegistradas.put("contacto@comedor.los.chicos.felices.org", "comedor123");
        this.credencialesRegistradas.put("chofer@donatrack.org", "chofer123");
        this.credencialesRegistradas.put("chofer", "chofer123");
        cargarCredencialesLocales();
    }

    public void registrarCredencial(String email, String password) {
        if (email != null && password != null && !password.isBlank()) {
            String key = email.trim().toLowerCase();
            credencialesRegistradas.put(key, password);
            guardarCredencialesLocales();
            log.info("Credencial registrada y persistida en AuthService para usuario: {}", key);
        }
    }

    private synchronized void cargarCredencialesLocales() {
        try {
            if (java.nio.file.Files.exists(credencialesFilePath)) {
                java.util.Properties props = new java.util.Properties();
                try (java.io.InputStream in = java.nio.file.Files.newInputStream(credencialesFilePath)) {
                    props.load(in);
                }
                for (String name : props.stringPropertyNames()) {
                    credencialesRegistradas.put(name.trim().toLowerCase(), props.getProperty(name));
                }
            }
        } catch (Exception e) {
            log.debug("No se pudieron cargar credenciales locales desde {}: {}", credencialesFilePath, e.getMessage());
        }
    }

    private synchronized void guardarCredencialesLocales() {
        try {
            if (credencialesFilePath.getParent() != null) {
                java.nio.file.Files.createDirectories(credencialesFilePath.getParent());
            }
            java.util.Properties props = new java.util.Properties();
            props.putAll(credencialesRegistradas);
            try (java.io.OutputStream out = java.nio.file.Files.newOutputStream(credencialesFilePath)) {
                props.store(out, "Credenciales locales registradas en DonaTrack frontend-service");
            }
        } catch (Exception e) {
            log.debug("No se pudieron guardar credenciales locales en {}: {}", credencialesFilePath, e.getMessage());
        }
    }

    /**
     * Valida credenciales contra Keycloak (OIDC Direct Access Grant) y/o fallback local.
     * Retorna Optional<UserSession> con la sesion armada y el token JWT si es exitoso.
     */
    public Optional<UserSession> autenticar(String username, String password) {
        if (username == null || password == null || username.isBlank() || password.isBlank()) {
            return Optional.empty();
        }

        String userClean = username.trim().toLowerCase();

        // Intentar autenticación directa contra Keycloak OIDC
        String tokenKeycloak = obtenerTokenKeycloak(userClean, password);
        if (tokenKeycloak != null) {
            log.info("Token OIDC obtenido exitosamente de Keycloak para usuario: {}", userClean);
            UserSession session = armarSesionDesdeUsuario(userClean);
            session.setAccessToken(tokenKeycloak);
            return Optional.of(session);
        }

        // Fallback o autenticación local asistida
        return autenticarLocalmente(userClean, password);
    }

    private String obtenerTokenKeycloak(String username, String password) {
        try {
            String tokenUrl = keycloakUrl + "/realms/" + realm + "/protocol/openid-connect/token";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            formData.add("client_id", clientId);
            formData.add("grant_type", "password");
            formData.add("username", username);
            formData.add("password", password);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(formData, headers);
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(tokenUrl, request, Map.class);

            if (response != null && response.containsKey("access_token")) {
                return (String) response.get("access_token");
            }
        } catch (Exception e) {
            log.debug("Keycloak no disponible o credenciales no reconocidas en OIDC ({}): {}", username, e.getMessage());
        }
        return null;
    }

    private UserSession armarSesionDesdeUsuario(String userClean) {
        if (userClean.contains("admin")) {
            return new UserSession("admin", "Administrador Central", "admin@donatrack.org", "ROLE_ADMIN");
        }
        if (userClean.contains("chofer")) {
            return new UserSession(userClean, "Chofer Flota", userClean, "ROLE_CHOFER");
        }
        if (userClean.contains("comedor") || userClean.contains("esperanza")) {
            List<Map<String, Object>> entidades = apiClient.listarEntidades();
            Long entId = obtenerOCrearEntidadDemo(entidades);
            UserSession s = new UserSession(userClean, "Comedor Esperanza", "esperanza@comedor.org", "ROLE_BENEFICIARIA");
            s.setEntidadId(entId);
            return s;
        }

        // Buscar si es entidad
        List<Map<String, Object>> entidades = apiClient.listarEntidades();
        Optional<Map<String, Object>> entOpt = entidades.stream()
                .filter(e -> coincideEmailOId(e, userClean))
                .findFirst();
        if (entOpt.isPresent()) {
            Map<String, Object> ent = entOpt.get();
            Long id = ent.get("id") instanceof Number ? ((Number) ent.get("id")).longValue() : 1L;
            String nombre = ent.get("razonSocial") != null ? ent.get("razonSocial").toString() : "Entidad Beneficiaria";
            UserSession s = new UserSession(userClean, nombre, userClean, "ROLE_BENEFICIARIA");
            s.setEntidadId(id);
            return s;
        }

        // Buscar si es donante
        List<Map<String, Object>> donantes = apiClient.listarDonantes();
        Optional<Map<String, Object>> donOpt = donantes.stream()
                .filter(d -> coincideEmailDonante(d, userClean))
                .findFirst();
        if (donOpt.isPresent()) {
            Map<String, Object> d = donOpt.get();
            Long id = d.get("id") instanceof Number ? ((Number) d.get("id")).longValue() : 1L;
            String nombre = (d.get("nombreCompleto") != null) ? d.get("nombreCompleto").toString()
                    : ((d.get("nombre") != null ? d.get("nombre") : "Donante") + " " + (d.get("apellido") != null ? d.get("apellido") : ""));
            String email = d.get("emailPrincipal") != null ? d.get("emailPrincipal").toString() : userClean;
            UserSession s = new UserSession(userClean, nombre.trim(), email, "ROLE_DONANTE");
            s.setDonanteId(id);
            return s;
        }

        if (userClean.contains("juan") || userClean.contains("perez")) {
            Long juanId = obtenerOCrearDonanteDemo(donantes);
            UserSession s = new UserSession(userClean, "Juan Pérez", "juan.donante@example.com", "ROLE_DONANTE");
            s.setDonanteId(juanId);
            return s;
        }

        // Donante por defecto
        Long defId = !donantes.isEmpty() && donantes.get(0).get("id") instanceof Number num ? num.longValue() : 1L;
        UserSession s = new UserSession(userClean, "Donante " + userClean, userClean, "ROLE_DONANTE");
        s.setDonanteId(defId);
        return s;
    }

    private Optional<UserSession> autenticarLocalmente(String userClean, String password) {
        // 1. Verificación de Administrador
        if (("admin".equals(userClean) || "admin@donatrack.org".equals(userClean))) {
            if ("admin123".equals(password)) {
                UserSession adminSession = new UserSession("admin", "Administrador Central", "admin@donatrack.org", "ROLE_ADMIN");
                log.info("Autenticación exitosa como ROLE_ADMIN para usuario: {}", userClean);
                return Optional.of(adminSession);
            } else {
                log.warn("Intento de login fallido para admin: contraseña incorrecta");
                return Optional.empty();
            }
        }

        // 1.1 Verificación de Chofer
        if ("chofer".equals(userClean) || "chofer@donatrack.org".equals(userClean)) {
            if ("chofer123".equals(password) || "admin123".equals(password)) {
                UserSession choferSession = new UserSession("chofer", "Chofer Flota", "chofer@donatrack.org", "ROLE_CHOFER");
                log.info("Autenticación exitosa como ROLE_CHOFER para usuario: {}", userClean);
                return Optional.of(choferSession);
            } else {
                log.warn("Intento de login fallido para chofer: contraseña incorrecta");
                return Optional.empty();
            }
        }

        // 2. Verificación de Entidad Beneficiaria (clave configurada o defaults)
        List<Map<String, Object>> entidades = apiClient.listarEntidades();
        Optional<Map<String, Object>> entidadEncontrada = entidades.stream()
                .filter(e -> coincideEmailOId(e, userClean))
                .findFirst();

        if (entidadEncontrada.isPresent()) {
            if (validarPasswordEntidad(entidadEncontrada.get(), userClean, password)) {
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

        // 3. Verificación de Donante (clave configurada o defaults)
        List<Map<String, Object>> donantes = apiClient.listarDonantes();
        Optional<Map<String, Object>> donanteEncontrado = donantes.stream()
                .filter(d -> coincideEmailDonante(d, userClean))
                .findFirst();

        if (donanteEncontrado.isPresent()) {
            if (validarPasswordDonante(donanteEncontrado.get(), userClean, password)) {
                Map<String, Object> d = donanteEncontrado.get();
                Long id = d.get("id") instanceof Number ? ((Number) d.get("id")).longValue() : 1L;
                String nombre = (d.get("nombreCompleto") != null) ? d.get("nombreCompleto").toString()
                        : (d.get("razonSocial") != null ? d.get("razonSocial").toString()
                        : ((d.get("nombre") != null ? d.get("nombre") : "Donante") + " " + (d.get("apellido") != null ? d.get("apellido") : "")));
                String email = d.get("emailPrincipal") != null ? d.get("emailPrincipal").toString() : userClean;

                UserSession session = new UserSession(userClean, nombre.trim(), email, "ROLE_DONANTE");
                session.setDonanteId(id);
                log.info("Autenticación exitosa como ROLE_DONANTE: {}", nombre);
                return Optional.of(session);
            } else {
                log.warn("Contraseña incorrecta para donante: {}", userClean);
                return Optional.empty();
            }
        }

        // 4. Soporte para usuario demo de donante especificado en la documentación del TP
        if ("juan.donante@example.com".equals(userClean) || "donante_juan".equals(userClean) || "35123456".equals(userClean)) {
            if ("donante123".equals(password) || "admin123".equals(password)) {
                Long juanId = obtenerOCrearDonanteDemo(donantes);
                UserSession session = new UserSession(userClean, "Juan Pérez", "juan.donante@example.com", "ROLE_DONANTE");
                session.setDonanteId(juanId);
                return Optional.of(session);
            }
        }

        // Soporte para usuario demo corporativo
        if ("rse@molinos.com.ar".equals(userClean) || "30-11223344-9".equals(userClean) || "empresa_demo".equals(userClean)) {
            if ("donante123".equals(password) || "admin123".equals(password) || "empresa123".equals(password)) {
                Long molinosId = obtenerOCrearEmpresaDemo(donantes);
                UserSession session = new UserSession(userClean, "Molinos Río de la Plata S.A.", "rse@molinos.com.ar", "ROLE_DONANTE");
                session.setDonanteId(molinosId);
                return Optional.of(session);
            }
        }

        if ("esperanza@comedor.org".equals(userClean) || "comedor_esperanza".equals(userClean) || "contacto@comedor.los.chicos.felices.org".equals(userClean)) {
            if ("comedor123".equals(password)) {
                Long entId = obtenerOCrearEntidadDemo(entidades);
                String emailSession = "contacto@comedor.los.chicos.felices.org".equalsIgnoreCase(userClean)
                        ? "contacto@comedor.los.chicos.felices.org"
                        : "esperanza@comedor.org";
                String nombreSession = "contacto@comedor.los.chicos.felices.org".equalsIgnoreCase(userClean)
                        ? "Comedor Los Chicos Felices"
                        : "Comedor Esperanza";
                UserSession session = new UserSession(userClean, nombreSession, emailSession, "ROLE_BENEFICIARIA");
                session.setEntidadId(entId);
                return Optional.of(session);
            }
        }

        log.warn("Usuario no encontrado en el sistema: {}", userClean);
        return Optional.empty();
    }

    private Long obtenerOCrearDonanteDemo(List<Map<String, Object>> donantes) {
        if (donantes != null) {
            Optional<Map<String, Object>> juanOpt = donantes.stream()
                    .filter(d -> coincideEmailDonante(d, "juan.donante@example.com") ||
                            coincideEmailDonante(d, "35123456") ||
                            (d.get("nombreCompleto") != null && d.get("nombreCompleto").toString().toLowerCase().contains("perez")))
                    .findFirst();
            if (juanOpt.isPresent() && juanOpt.get().get("id") instanceof Number num) {
                return num.longValue();
            }
        }

        Map<String, Object> existente = apiClient.buscarDonantePorEmail("juan.donante@example.com");
        if (existente == null) {
            existente = apiClient.buscarDonantePorDocumento("35123456");
        }
        if (existente != null && existente.get("id") instanceof Number num) {
            return num.longValue();
        }

        try {
            Map<String, Object> nuevo = apiClient.crearDonanteHumana(Map.of(
                    "nombre", "Juan",
                    "apellido", "Pérez",
                    "nroDocumento", "35123456",
                    "edad", 28,
                    "genero", "Masculino",
                    "direccion", "Av. Medrano 951, CABA",
                    "mediosContacto", List.of(
                            Map.of("tipo", "EMAIL", "valor", "juan.donante@example.com", "esPredeterminado", true)
                    )
            ));
            if (nuevo != null && nuevo.get("id") instanceof Number num) {
                return num.longValue();
            }
        } catch (Exception e) {
            log.error("Error auto-creando donante demo: {}", e.getMessage());
            Map<String, Object> reintento = apiClient.buscarDonantePorEmail("juan.donante@example.com");
            if (reintento != null && reintento.get("id") instanceof Number num) {
                return num.longValue();
            }
        }

        if (donantes != null && !donantes.isEmpty() && donantes.get(0).get("id") instanceof Number num) {
            return num.longValue();
        }
        return 1L;
    }

    private Long obtenerOCrearEmpresaDemo(List<Map<String, Object>> donantes) {
        if (donantes != null) {
            Optional<Map<String, Object>> molOpt = donantes.stream()
                    .filter(d -> coincideEmailDonante(d, "rse@molinos.com.ar") ||
                            coincideEmailDonante(d, "30-11223344-9") ||
                            (d.get("razonSocial") != null && d.get("razonSocial").toString().toLowerCase().contains("molinos")))
                    .findFirst();
            if (molOpt.isPresent() && molOpt.get().get("id") instanceof Number num) {
                return num.longValue();
            }
        }

        Map<String, Object> existente = apiClient.buscarDonantePorEmail("rse@molinos.com.ar");
        if (existente != null && existente.get("id") instanceof Number num) {
            return num.longValue();
        }

        try {
            Map<String, Object> nueva = apiClient.crearDonanteJuridica(Map.of(
                    "razonSocial", "Molinos Río de la Plata S.A.",
                    "cuit", "30-11223344-9",
                    "tipo", "Empresa",
                    "rubro", "Alimentos",
                    "direccion", "Av. Paseo Colón 746, CABA",
                    "mediosContacto", List.of(
                            Map.of("tipo", "EMAIL", "valor", "rse@molinos.com.ar", "esPredeterminado", true)
                    )
            ));
            if (nueva != null && nueva.get("id") instanceof Number num) {
                return num.longValue();
            }
        } catch (Exception e) {
            log.error("Error auto-creando empresa demo: {}", e.getMessage());
            Map<String, Object> reintento = apiClient.buscarDonantePorEmail("rse@molinos.com.ar");
            if (reintento != null && reintento.get("id") instanceof Number num) {
                return num.longValue();
            }
        }

        if (donantes != null && !donantes.isEmpty() && donantes.get(0).get("id") instanceof Number num) {
            return num.longValue();
        }
        return 1L;
    }

    private Long obtenerOCrearEntidadDemo(List<Map<String, Object>> entidades) {
        if (entidades != null) {
            Optional<Map<String, Object>> entOpt = entidades.stream()
                    .filter(e -> coincideEmailOId(e, "esperanza@comedor.org") ||
                            (e.get("razonSocial") != null && e.get("razonSocial").toString().toLowerCase().contains("esperanza")))
                    .findFirst();
            if (entOpt.isPresent() && entOpt.get().get("id") instanceof Number num) {
                return num.longValue();
            }
        }

        Map<String, Object> existente = apiClient.buscarEntidadPorEmail("esperanza@comedor.org");
        if (existente != null && existente.get("id") instanceof Number num) {
            return num.longValue();
        }

        try {
            Map<String, Object> nueva = apiClient.crearEntidad(Map.of(
                    "razonSocial", "Comedor Comunitario Esperanza",
                    "direccion", "Av. Corrientes 4500, CABA",
                    "telefono", "11-4444-5555",
                    "emailsRepresentantes", List.of("esperanza@comedor.org")
            ));
            if (nueva != null && nueva.get("id") instanceof Number num) {
                return num.longValue();
            }
        } catch (Exception e) {
            log.error("Error auto-creando entidad demo: {}", e.getMessage());
            Map<String, Object> reintento = apiClient.buscarEntidadPorEmail("esperanza@comedor.org");
            if (reintento != null && reintento.get("id") instanceof Number num) {
                return num.longValue();
            }
        }

        if (entidades != null && !entidades.isEmpty() && entidades.get(0).get("id") instanceof Number num) {
            return num.longValue();
        }
        return 1L;
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
        if (donante.get("cuit") != null && donante.get("cuit").toString().equalsIgnoreCase(userClean)) {
            return true;
        }
        if (donante.get("razonSocial") != null && donante.get("razonSocial").toString().toLowerCase().contains(userClean)) {
            return true;
        }
        if (donante.get("nombreCompleto") != null && donante.get("nombreCompleto").toString().toLowerCase().contains(userClean)) {
            return true;
        }
        if (donante.get("apellido") != null && donante.get("apellido").toString().toLowerCase().contains(userClean)) {
            return true;
        }
        if (donante.get("mediosContacto") instanceof List) {
            List<Map<String, Object>> medios = (List<Map<String, Object>>) donante.get("mediosContacto");
            return medios.stream().anyMatch(m -> m.get("valor") != null && m.get("valor").toString().equalsIgnoreCase(userClean));
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private boolean validarPasswordEntidad(Map<String, Object> entidad, String userClean, String password) {
        if (password.equals(credencialesRegistradas.get(userClean))) {
            return true;
        }
        boolean esDemo = "esperanza@comedor.org".equalsIgnoreCase(userClean)
                || "comedor_esperanza".equalsIgnoreCase(userClean)
                || "contacto@comedor.los.chicos.felices.org".equalsIgnoreCase(userClean);
        if (entidad.get("emailsRepresentantes") instanceof List) {
            List<String> emails = (List<String>) entidad.get("emailsRepresentantes");
            for (String em : emails) {
                if (em != null) {
                    String emLow = em.trim().toLowerCase();
                    if (password.equals(credencialesRegistradas.get(emLow))) {
                        return true;
                    }
                    if ("esperanza@comedor.org".equals(emLow) || "contacto@comedor.los.chicos.felices.org".equals(emLow)) {
                        esDemo = true;
                    }
                }
            }
        }
        if (esDemo && ("comedor123".equals(password) || "admin123".equals(password))) {
            return true;
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private boolean validarPasswordDonante(Map<String, Object> donante, String userClean, String password) {
        if (password.equals(credencialesRegistradas.get(userClean))) {
            return true;
        }
        if (donante.get("emailPrincipal") != null && password.equals(credencialesRegistradas.get(donante.get("emailPrincipal").toString().toLowerCase()))) {
            return true;
        }
        if (donante.get("email") != null && password.equals(credencialesRegistradas.get(donante.get("email").toString().toLowerCase()))) {
            return true;
        }
        if (donante.get("nroDocumento") != null && password.equals(credencialesRegistradas.get(donante.get("nroDocumento").toString().toLowerCase()))) {
            return true;
        }
        if (donante.get("cuit") != null && password.equals(credencialesRegistradas.get(donante.get("cuit").toString().toLowerCase()))) {
            return true;
        }
        if (donante.get("razonSocial") != null && password.equals(credencialesRegistradas.get(donante.get("razonSocial").toString().toLowerCase()))) {
            return true;
        }
        if (donante.get("mediosContacto") instanceof List) {
            List<Map<String, Object>> medios = (List<Map<String, Object>>) donante.get("mediosContacto");
            for (Map<String, Object> m : medios) {
                if (m.get("valor") != null && password.equals(credencialesRegistradas.get(m.get("valor").toString().toLowerCase()))) {
                    return true;
                }
            }
        }
        if ("donante123".equals(password) || "admin123".equals(password) || "empresa123".equals(password)) {
            return true;
        }
        return false;
    }
}
