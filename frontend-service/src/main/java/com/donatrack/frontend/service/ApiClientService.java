package com.donatrack.frontend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * ====================================================================================================
 * SERVICIO CLIENTE API GATEWAY (frontend-service) - DonaTrack
 * ====================================================================================================
 *
 * ----------------------------------------------------------------------------------------------------
 * 1. FLUJOS PRINCIPALES RESUMIDOS (Casos de Uso de Integración Frontend)
 * ----------------------------------------------------------------------------------------------------
 *
 * FLUJO A: Consulta Pública y Visualización sin Autenticación (CU-01, CU-03, CU-04)
 *   1. El visitante accede a la landing page pública con resumen de impacto y donaciones destacadas.
 *   2. Visualiza el mapa interactivo de donaciones entregadas y galería fotográfica sin necesidad de login.
 *   3. Consulta detalles públicos de donación al hacer clic sobre los marcadores en el mapa.
 *
 * FLUJO B: Operaciones y Portal del Donante (CU-05, CU-16, CU-17, CU-42, CU-47..CU-52)
 *   1. Registro e inicio de sesión de personas donantes.
 *   2. Consulta y filtrado de donaciones propias por estado y subcategoría.
 *   3. Seguimiento satelital en mapa interactivo de las entregas activas con ubicación del camión en tiempo real.
 *   4. Consulta de métricas de impacto, misiones activas/completadas e insignias.
 *   5. Configuración de visibilidad pública de insignias ganadas.
 *
 * FLUJO C: Operaciones y Portal de Entidades Beneficiarias (CU-26, CU-28, CU-42, CU-43, CU-44)
 *   1. Carga de necesidades materiales periódicas (recurrentes) o extraordinarias.
 *   2. Monitoreo en mapa del camión asignado a las donaciones en camino.
 *   3. Confirmación de recepción con carga obligatoria de fotos de los bienes y firma.
 *   4. Registro formal de incidencia en caso de entrega no recibida.
 *
 * FLUJO D: Portal de Administración Operativa (CU-07..CU-12, CU-13..CU-15, CU-30..CU-35, CU-36, CU-45, CU-54)
 *   1. Carga múltiple asistida en depósito con segmentación automática por subcategoría.
 *   2. Importación masiva por streaming de donantes históricos vía CSV (>10.000 / >20.000 registros).
 *   3. Ejecución y confirmación del motor de asignación (matchmaking) hacia entidades beneficiarias.
 *   4. Administración de flota de camiones, despacho de lotes y monitoreo de avance sobre rutas.
 *   5. Revisión y replanificación de entregas fallidas o no recibidas.
 *   6. Consulta del podio y ranking mensual de donantes activos.
 *
 * ----------------------------------------------------------------------------------------------------
 * 2. LO NECESARIO DEL SERVICE (Requerimientos Técnicos y de Dominio)
 * ----------------------------------------------------------------------------------------------------
 * - Desacoplamiento arquitectónico: cliente REST liviano sobre Spring Web MVC (Server-Side Rendering).
 * - Enrutamiento unificado a través del API Gateway (http://localhost:8080/api/v1/...).
 * - Manejo defensivo de fallos con respuestas por defecto (fallback seguro) ante indisponibilidad temporal.
 * - Soporte para transmisión de credenciales y propagación de seguridad Keycloak / JWT.
 *
 * ----------------------------------------------------------------------------------------------------
 * 3. TO-DO MAESTRO: LO QUE LE FALTA AL SERVICE
 * ----------------------------------------------------------------------------------------------------
 * [x] TODO-CLI-01 (CU-03, CU-04): Implementar métodos para la galería pública de fotos y mapa de donaciones
 *     entregadas accesibles para visitantes no autenticados.
 * [x] TODO-CLI-02 (CU-44, CU-45): Implementar consumo de endpoints para registro de incidencias de 'Entrega no recibida'
 *     y el flujo administrativo de replanificación de entregas.
 * [x] TODO-CLI-03 (CU-52): Implementar método para alternar la visibilidad de insignias del donante (PUT /api/v1/incentivos/insignias/{id}/visibilidad).
 * [x] TODO-CLI-04 (CU-46): Implementar método para descarga y visualización del Comprobante formal de entrega digital.
 * [x] TODO-CLI-05 (CU-11, CU-25): Implementar endpoints para la baja lógica de donantes y entidades beneficiarias.
 * [x] TODO-CLI-06 (CU-21): Implementar método para recuperar el historial completo de auditoría de estados de una donación.
 * [x] TODO-CLI-07 (CU-47, CU-54): Implementar métodos para obtener gráficos de evolución mensual y el histórico del ranking mensual.
 * ====================================================================================================
 */
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import com.donatrack.frontend.security.AuthInterceptor;
import com.donatrack.frontend.security.UserSession;

@Service
public class ApiClientService {

    private static final Logger log = LoggerFactory.getLogger(ApiClientService.class);

    private final RestTemplate restTemplate;

    @Value("${donatrack.api-gateway.url:http://localhost:8080}")
    private String gatewayUrl;

    @Value("${donatrack.mailpit.url:http://localhost:8025}")
    private String mailpitUrl;

    public ApiClientService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public String getGatewayUrl() {
        return gatewayUrl;
    }

    public String getMailpitUrl() {
        return mailpitUrl;
    }

    /**
     * Construye HttpHeaders con Bearer token JWT si existe en la sesión activa del usuario
     */
    public HttpHeaders buildAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest req = attrs.getRequest();
                HttpSession session = req.getSession(false);
                if (session != null) {
                    UserSession userSession = (UserSession) session.getAttribute(AuthInterceptor.SESSION_USER_KEY);
                    if (userSession != null && userSession.getAccessToken() != null && !userSession.getAccessToken().isBlank()) {
                        headers.setBearerAuth(userSession.getAccessToken());
                    }
                    if (userSession != null) {
                        headers.set("X-User-Id", userSession.getUsername());
                        headers.set("X-User-Roles", userSession.getRole());
                    }
                }
            }
        } catch (Exception e) {
            log.debug("No se pudo extraer sesión para cabeceras de autorización: {}", e.getMessage());
        }
        return headers;
    }

    // --- Donaciones ---

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listarDonaciones(String estado) {
        return listarDonaciones(estado, null);
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listarDonaciones(String estado, Long donanteId) {
        try {
            StringBuilder url = new StringBuilder(gatewayUrl).append("/api/v1/donaciones?");
            if (estado != null && !estado.isBlank()) {
                url.append("estado=").append(estado).append("&");
            }
            if (donanteId != null) {
                url.append("donanteId=").append(donanteId).append("&");
            }
            String finalUrl = url.toString().replaceAll("[&?]$", "");
            return restTemplate.getForObject(finalUrl, List.class);
        } catch (Exception e) {
            log.warn("Fallo llamando a /api/v1/donaciones: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> listarDonacionesPaginadas(int page, int size, String estado, Long donanteId, String subcategoria) {
        return listarDonacionesPaginadas(page, size, estado, donanteId, subcategoria, null);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> listarDonacionesPaginadas(int page, int size, String estado, Long donanteId, String subcategoria, String categoria) {
        try {
            StringBuilder url = new StringBuilder(gatewayUrl).append("/api/v1/donaciones/paginado?")
                    .append("page=").append(page).append("&size=").append(size).append("&");
            if (estado != null && !estado.isBlank()) {
                url.append("estado=").append(estado).append("&");
            }
            if (donanteId != null) {
                url.append("donanteId=").append(donanteId).append("&");
            }
            if (subcategoria != null && !subcategoria.isBlank()) {
                url.append("subcategoria=").append(subcategoria).append("&");
            }
            if (categoria != null && !categoria.isBlank()) {
                url.append("categoria=").append(categoria).append("&");
            }
            String finalUrl = url.toString().replaceAll("[&?]$", "");
            Map<String, Object> res = restTemplate.getForObject(finalUrl, Map.class);
            return res != null ? res : Collections.emptyMap();
        } catch (Exception e) {
            log.warn("Fallo llamando a /api/v1/donaciones/paginado: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> obtenerDonacion(Long id) {
        try {
            return restTemplate.getForObject(gatewayUrl + "/api/v1/donaciones/" + id, Map.class);
        } catch (Exception e) {
            log.warn("Fallo obteniendo donacion id={}: {}", id, e.getMessage());
            return Collections.emptyMap();
        }
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listarEntidades() {
        try {
            return restTemplate.getForObject(gatewayUrl + "/api/v1/entidades", List.class);
        } catch (Exception e) {
            log.warn("Fallo listando entidades: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listarDonantes() {
        try {
            return restTemplate.getForObject(gatewayUrl + "/api/v1/donantes", List.class);
        } catch (Exception e) {
            log.warn("Fallo listando donantes: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public Map<String, Object> crearDonanteHumana(Map<String, Object> body) {
        try {
            HttpHeaders headers = buildAuthHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            @SuppressWarnings("unchecked")
            Map<String, Object> res = restTemplate.postForObject(gatewayUrl + "/api/v1/donantes/humana", entity, Map.class);
            return res;
        } catch (Exception e) {
            log.error("Error creando donante humana: {}", e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> buscarDonantePorEmail(String email) {
        if (email == null || email.isBlank()) return null;
        try {
            HttpHeaders headers = buildAuthHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            String url = gatewayUrl + "/api/v1/donantes/buscar?email={email}";
            ResponseEntity<Map> res = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class, email.trim());
            return res.getBody();
        } catch (Exception e) {
            log.debug("No se encontró donante por email {}: {}", email, e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> buscarDonantePorDocumento(String documento) {
        if (documento == null || documento.isBlank()) return null;
        try {
            HttpHeaders headers = buildAuthHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            String url = gatewayUrl + "/api/v1/donantes/buscar?documento={documento}";
            ResponseEntity<Map> res = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class, documento.trim());
            return res.getBody();
        } catch (Exception e) {
            log.debug("No se encontró donante por documento {}: {}", documento, e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> buscarEntidadPorEmail(String email) {
        if (email == null || email.isBlank()) return null;
        try {
            HttpHeaders headers = buildAuthHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            String url = gatewayUrl + "/api/v1/entidades/buscar?email={email}";
            ResponseEntity<Map> res = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class, email.trim());
            return res.getBody();
        } catch (Exception e) {
            log.debug("No se encontró entidad por email {}: {}", email, e.getMessage());
            return null;
        }
    }

    public Map<String, Object> crearEntidad(Map<String, Object> body) {
        try {
            HttpHeaders headers = buildAuthHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            @SuppressWarnings("unchecked")
            Map<String, Object> res = restTemplate.postForObject(gatewayUrl + "/api/v1/entidades", entity, Map.class);
            return res;
        } catch (Exception e) {
            log.error("Error creando entidad beneficiaria: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Consulta la normalización y validación oficial geográfica de una dirección.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> normalizarDireccion(String direccion) {
        if (direccion == null || direccion.trim().isBlank()) {
            return Map.of("esValida", false, "mensajeValidacion", "La dirección no puede estar vacía.");
        }
        String dirTrim = direccion.trim();
        try {
            String url = gatewayUrl + "/api/v1/donaciones/direcciones/normalizar?direccion={direccion}";
            Map<String, Object> res = restTemplate.getForObject(url, Map.class, dirTrim);
            if (res != null && Boolean.TRUE.equals(res.get("esValida"))) {
                return res;
            } else if (res != null) {
                return res;
            }
        } catch (Exception e) {
            log.warn("Fallo llamando a servicio de direcciones remoto '{}': {}. Aplicando validación local resiliente...", dirTrim, e.getMessage());
        }

        // Contingencia local si el microservicio de donaciones está temporalmente inaccesible o en arranque
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\b(\\d{1,5})\\b").matcher(dirTrim);
        if (m.find() && dirTrim.length() >= 5) {
            try {
                String altura = m.group(1);
                String nomenclatura = dirTrim.toUpperCase() + ", CIUDAD AUTÓNOMA DE BUENOS AIRES";
                return Map.of(
                        "esValida", true,
                        "nomenclatura", nomenclatura,
                        "nombreCalle", dirTrim.replaceAll("\\b" + altura + "\\b", "").replaceAll("[,]+", "").trim(),
                        "altura", Integer.parseInt(altura),
                        "localidad", "Buenos Aires",
                        "provincia", "Ciudad Autónoma de Buenos Aires",
                        "latitud", -34.5985,
                        "longitud", -58.4201
                );
            } catch (Exception ignored) {}
        }

        return Map.of("esValida", false, "mensajeValidacion", "Indique calle y número de puerta catastral válido (ej. Av. Medrano 951, CABA).");
    }

    public boolean crearNecesidad(Long entidadId, Map<String, Object> body) {
        try {
            HttpHeaders headers = buildAuthHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            restTemplate.postForEntity(gatewayUrl + "/api/v1/entidades/" + entidadId + "/necesidades", entity, Map.class);
            return true;
        } catch (Exception e) {
            log.error("Error creando necesidad: {}", e.getMessage());
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listarNecesidadesEntidad(Long entidadId) {
        try {
            List<Map<String, Object>> res = restTemplate.getForObject(gatewayUrl + "/api/v1/entidades/" + entidadId + "/necesidades", List.class);
            return res != null ? res : Collections.emptyList();
        } catch (Exception e) {
            log.warn("Fallo listando necesidades de entidad {}: {}", entidadId, e.getMessage());
            return Collections.emptyList();
        }
    }

    public boolean actualizarNecesidad(Long necesidadId, Map<String, Object> body) {
        return actualizarNecesidad(null, necesidadId, body);
    }

    public boolean actualizarNecesidad(Long entidadId, Long necesidadId, Map<String, Object> body) {
        try {
            HttpHeaders headers = buildAuthHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            String url = (entidadId != null)
                    ? gatewayUrl + "/api/v1/entidades/" + entidadId + "/necesidades/" + necesidadId
                    : gatewayUrl + "/api/v1/entidades/necesidades/" + necesidadId;
            restTemplate.exchange(url, HttpMethod.PUT, entity, Map.class);
            return true;
        } catch (Exception e) {
            log.error("Error actualizando necesidad {}: {}", necesidadId, e.getMessage());
            return false;
        }
    }

    public boolean eliminarNecesidad(Long necesidadId) {
        return eliminarNecesidad(null, necesidadId);
    }

    public boolean eliminarNecesidad(Long entidadId, Long necesidadId) {
        try {
            HttpHeaders headers = buildAuthHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            String url = (entidadId != null)
                    ? gatewayUrl + "/api/v1/entidades/" + entidadId + "/necesidades/" + necesidadId
                    : gatewayUrl + "/api/v1/entidades/necesidades/" + necesidadId;
            restTemplate.exchange(url, HttpMethod.DELETE, entity, Void.class);
            return true;
        } catch (Exception e) {
            log.error("Error eliminando necesidad {}: {}", necesidadId, e.getMessage());
            return false;
        }
    }

    public record OperacionResult(boolean exitoso, String mensaje) {}

    public boolean crearDonacionSegmentada(Long donanteId, List<Map<String, Object>> bienes) {
        return crearDonacionSegmentada(donanteId, bienes, null);
    }

    public boolean crearDonacionSegmentada(Long donanteId, List<Map<String, Object>> bienes, Long entidadId) {
        return crearDonacionSegmentadaConDetalle(donanteId, bienes, entidadId).exitoso();
    }

    public OperacionResult crearDonacionSegmentadaConDetalle(Long donanteId, List<Map<String, Object>> bienes, Long entidadId) {
        try {
            HttpHeaders headers = buildAuthHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<List<Map<String, Object>>> entity = new HttpEntity<>(bienes, headers);
            String url = gatewayUrl + "/api/v1/donaciones/carga-segmentada?donanteId=" + donanteId +
                    (entidadId != null ? "&entidadId=" + entidadId : "");
            restTemplate.postForEntity(url, entity, List.class);
            return new OperacionResult(true, "Donación registrada exitosamente.");
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            String cuerpo = e.getResponseBodyAsString();
            log.error("Error HTTP ({}) creando donación segmentada: {}", e.getStatusCode(), cuerpo);
            String detalle = "Error en el servidor de donaciones (" + e.getStatusCode().value() + ").";
            if (cuerpo != null && cuerpo.contains("message")) {
                try {
                    Map mapa = new com.fasterxml.jackson.databind.ObjectMapper().readValue(cuerpo, Map.class);
                    if (mapa.get("message") != null) detalle = mapa.get("message").toString();
                } catch (Exception ignored) {}
            }
            return new OperacionResult(false, detalle);
        } catch (Exception e) {
            log.error("Error creando donacion segmentada: {}", e.getMessage());
            return new OperacionResult(false, "No se pudo conectar con el servicio de donaciones: " + e.getMessage());
        }
    }

    /**
     * Sube un archivo de imagen al servidor de donaciones vía multipart/form-data.
     * Retorna la URL relativa para acceder a la imagen o null si hubo fallo.
     */
    public String subirImagenDonacion(org.springframework.web.multipart.MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            return null;
        }
        try {
            HttpHeaders headers = buildAuthHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            org.springframework.util.MultiValueMap<String, Object> body = new org.springframework.util.LinkedMultiValueMap<>();
            body.add("archivo", new org.springframework.core.io.ByteArrayResource(archivo.getBytes()) {
                @Override
                public String getFilename() {
                    return archivo.getOriginalFilename() != null ? archivo.getOriginalFilename() : "imagen.jpg";
                }
            });

            HttpEntity<org.springframework.util.MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
            @SuppressWarnings("unchecked")
            ResponseEntity<Map> response = restTemplate.postForEntity(gatewayUrl + "/api/v1/donaciones/subir-imagen", requestEntity, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return (String) response.getBody().get("url");
            }
        } catch (Exception e) {
            log.error("Error subiendo imagen de donación a backend: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Recupera el contenido binario de una imagen guardada en el backend de donaciones a través del gateway.
     */
    public ResponseEntity<byte[]> obtenerImagenDonacion(String nombreArchivo) {
        try {
            return restTemplate.getForEntity(gatewayUrl + "/api/v1/donaciones/imagenes/" + nombreArchivo, byte[].class);
        } catch (Exception e) {
            log.warn("Fallo obteniendo imagen {}: {}", nombreArchivo, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> obtenerFlotaEnVivo() {
        try {
            return restTemplate.getForObject(gatewayUrl + "/api/v1/logistica/telemetria/flota-en-vivo", List.class);
        } catch (Exception e) {
            log.warn("Fallo llamando a /api/v1/logistica/telemetria/flota-en-vivo: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> evaluarMatchmaking(Long donacionId) {
        try {
            return restTemplate.getForObject(gatewayUrl + "/api/v1/donaciones/" + donacionId + "/matchmaking", Map.class);
        } catch (Exception e) {
            log.warn("Fallo evaluando matchmaking para donacion id={}: {}", donacionId, e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * Dispara la ejecución batch de matchmaking en el backend de donaciones.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> ejecutarMatchmakingBatch() {
        try {
            HttpHeaders headers = buildAuthHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            return restTemplate.postForObject(gatewayUrl + "/api/v1/donaciones/matchmaking/ejecutar-batch", entity, Map.class);
        } catch (Exception e) {
            log.warn("Fallo disparando ejecución batch de matchmaking: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    public boolean asignarDonacion(Long donacionId, Long entidadId) {
        return asignarDonacion(donacionId, entidadId, null);
    }

    public boolean asignarDonacion(Long donacionId, Long entidadId, Long necesidadId) {
        try {
            HttpHeaders headers = buildAuthHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, Object> body = new HashMap<>();
            body.put("entidadId", entidadId);
            if (necesidadId != null) {
                body.put("necesidadId", necesidadId);
            }
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            String url = gatewayUrl + "/api/v1/donaciones/" + donacionId + "/asignar?entidadId=" + entidadId
                    + (necesidadId != null ? "&necesidadId=" + necesidadId : "");
            restTemplate.postForEntity(url, entity, Map.class);
            return true;
        } catch (Exception e) {
            log.error("Error asignando donacion: {}", e.getMessage());
            return false;
        }
    }

    public boolean actualizarEstadoDonacion(Long donacionId, String nuevoEstado, String justificacion) {
        return actualizarEstadoDonacion(donacionId, nuevoEstado, justificacion, null);
    }

    public boolean actualizarEstadoDonacion(Long donacionId, String nuevoEstado, String justificacion, String fotoUrl) {
        try {
            HttpHeaders headers = buildAuthHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, String> body = new HashMap<>();
            body.put("nuevoEstado", nuevoEstado);
            body.put("justificacion", justificacion != null ? justificacion : "Actualización desde frontend");
            body.put("usuario", "OPERADOR");
            if (fotoUrl != null && !fotoUrl.isBlank()) {
                body.put("fotoUrl", fotoUrl);
            }
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);
            restTemplate.exchange(gatewayUrl + "/api/v1/donaciones/" + donacionId + "/estado", HttpMethod.PATCH, entity, Map.class);
            return true;
        } catch (Exception e) {
            log.error("Error actualizando estado de donacion {}: {}", donacionId, e.getMessage());
            return false;
        }
    }

    public boolean entregarDonacion(Long donacionId, String fotoUrl) {
        try {
            Map<String, Object> donacion = obtenerDonacion(donacionId);
            if (donacion == null || donacion.isEmpty()) {
                return false;
            }
            String estado = (String) donacion.get("estado");

            // Avanzar progresivamente respetando la máquina de estados del dominio
            if ("ASIGNACION_REALIZADA".equals(estado)) {
                boolean ok1 = actualizarEstadoDonacion(donacionId, "LISTA_PARA_ENTREGAR", "Preparada para logística");
                if (!ok1) return false;
                estado = "LISTA_PARA_ENTREGAR";
            }
            if ("LISTA_PARA_ENTREGAR".equals(estado)) {
                boolean ok2 = actualizarEstadoDonacion(donacionId, "EN_TRASLADO", "Despachada hacia la entidad");
                if (!ok2) return false;
                estado = "EN_TRASLADO";
            }
            if ("EN_TRASLADO".equals(estado)) {
                return actualizarEstadoDonacion(donacionId, "ENTREGADA", "Recepción confirmada con foto de entrega", fotoUrl);
            }
            return false;
        } catch (Exception e) {
            log.error("Error confirmando entrega de donacion {}: {}", donacionId, e.getMessage());
            return false;
        }
    }

    public boolean desasignarDonacion(Long donacionId, String motivo) {
        try {
            HttpHeaders headers = buildAuthHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, String> body = Map.of(
                    "motivo", motivo != null ? motivo : "Desasignación administrativa",
                    "usuario", "ADMIN"
            );
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);
            restTemplate.postForEntity(gatewayUrl + "/api/v1/donaciones/" + donacionId + "/desasignar", entity, Map.class);
            return true;
        } catch (Exception e) {
            log.error("Error desasignando donacion {}: {}", donacionId, e.getMessage());
            return false;
        }
    }

    // --- Logística ---

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listarCamiones() {
        try {
            return restTemplate.getForObject(gatewayUrl + "/api/v1/logistica/camiones", List.class);
        } catch (Exception e) {
            log.warn("Fallo listando camiones: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public boolean registrarCamion(Map<String, Object> body) {
        try {
            HttpHeaders headers = buildAuthHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            restTemplate.postForEntity(gatewayUrl + "/api/v1/logistica/camiones", entity, Map.class);
            return true;
        } catch (Exception e) {
            log.error("Error registrando camion: {}", e.getMessage());
            return false;
        }
    }

    public boolean actualizarCamion(String idCamion, Map<String, Object> body) {
        try {
            HttpHeaders headers = buildAuthHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            restTemplate.exchange(gatewayUrl + "/api/v1/logistica/camiones/" + idCamion, HttpMethod.PUT, entity, Map.class);
            return true;
        } catch (Exception e) {
            log.error("Error actualizando camion {}: {}", idCamion, e.getMessage());
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listarRutas() {
        try {
            return restTemplate.getForObject(gatewayUrl + "/api/v1/logistica/rutas", List.class);
        } catch (Exception e) {
            log.warn("Fallo listando rutas: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> obtenerRuta(String idRuta) {
        try {
            return restTemplate.getForObject(gatewayUrl + "/api/v1/logistica/rutas/" + idRuta, Map.class);
        } catch (Exception e) {
            log.warn("Fallo obteniendo ruta {}: {}", idRuta, e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listarEntregasPorRuta(String idRuta) {
        try {
            return restTemplate.getForObject(gatewayUrl + "/api/v1/logistica/entregas/ruta/" + idRuta, List.class);
        } catch (Exception e) {
            log.warn("Fallo listando entregas de la ruta {}: {}", idRuta, e.getMessage());
            return Collections.emptyList();
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> obtenerUltimaPosicion(String patente) {
        try {
            return restTemplate.getForObject(gatewayUrl + "/api/v1/logistica/telemetria/camion/" + patente + "/ultima", Map.class);
        } catch (Exception e) {
            log.warn("Fallo obteniendo ultima posicion para patente {}: {}", patente, e.getMessage());
            return Collections.emptyMap();
        }
    }

    public boolean confirmarEntrega(String idEntrega, List<String> fotos) {
        try {
            HttpHeaders headers = buildAuthHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, Object> body = Map.of("fotos", fotos);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            restTemplate.postForEntity(gatewayUrl + "/api/v1/logistica/rutas/entregas/" + idEntrega + "/confirmar", entity, Map.class);
            return true;
        } catch (Exception e) {
            log.error("Error confirmando entrega: {}", e.getMessage());
            return false;
        }
    }

    public boolean confirmarEntregaPorDonacion(Long donacionId, String urlFoto, String comentario) {
        try {
            HttpHeaders headers = buildAuthHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, Object> body = new HashMap<>();
            if (urlFoto != null && !urlFoto.isBlank()) {
                body.put("urlFoto", urlFoto);
                body.put("fotos", List.of(urlFoto));
            }
            if (comentario != null && !comentario.isBlank()) {
                body.put("comentario", comentario);
                body.put("observaciones", comentario);
            }
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            restTemplate.postForEntity(gatewayUrl + "/api/v1/logistica/entregas/donacion/" + donacionId + "/confirmar", entity, Map.class);
            return true;
        } catch (Exception e) {
            log.error("Error confirmando entrega por donacion {}: {}", donacionId, e.getMessage());
            return false;
        }
    }

    public boolean registrarEntregaFallidaPorDonacion(Long donacionId, String motivo, boolean replanificable) {
        try {
            HttpHeaders headers = buildAuthHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, Object> body = Map.of(
                    "motivo", motivo != null ? motivo : "Incidencia en entrega",
                    "replanificable", replanificable
            );
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            restTemplate.postForEntity(gatewayUrl + "/api/v1/logistica/entregas/donacion/" + donacionId + "/fallida", entity, Map.class);
            return true;
        } catch (Exception e) {
            log.error("Error registrando entrega fallida por donacion {}: {}", donacionId, e.getMessage());
            return false;
        }
    }

    public boolean iniciarRuta(String idRuta) {
        try {
            HttpHeaders headers = buildAuthHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            restTemplate.postForEntity(gatewayUrl + "/api/v1/logistica/rutas/" + idRuta + "/iniciar", entity, Map.class);
            return true;
        } catch (Exception e) {
            log.error("Error iniciando ruta {}: {}", idRuta, e.getMessage());
            return false;
        }
    }

    public boolean finalizarRuta(String idRuta) {
        try {
            HttpHeaders headers = buildAuthHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            restTemplate.postForEntity(gatewayUrl + "/api/v1/logistica/rutas/" + idRuta + "/finalizar", entity, Map.class);
            return true;
        } catch (Exception e) {
            log.error("Error finalizando ruta {}: {}", idRuta, e.getMessage());
            return false;
        }
    }

    public boolean reportarTelemetria(String idCamion, String patente, double latitud, double longitud, double velocidad) {
        try {
            HttpHeaders headers = buildAuthHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, Object> body = Map.of(
                    "idCamion", idCamion != null ? idCamion : "CAM-01",
                    "patente", patente != null ? patente : "AE789XY",
                    "latitud", latitud,
                    "longitud", longitud,
                    "velocidad", velocidad
            );
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            restTemplate.postForEntity(gatewayUrl + "/api/v1/logistica/telemetria/posicion", entity, Map.class);
            return true;
        } catch (Exception e) {
            log.error("Error reportando telemetría para camión {}: {}", patente, e.getMessage());
            return false;
        }
    }

    public boolean registrarEntregaFallidaLogistica(String idEntrega, String motivo, boolean replanificable) {
        try {
            HttpHeaders headers = buildAuthHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, Object> body = Map.of(
                    "motivo", motivo != null ? motivo : "Incidencia en entrega",
                    "replanificable", replanificable
            );
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            restTemplate.postForEntity(gatewayUrl + "/api/v1/logistica/rutas/entregas/" + idEntrega + "/fallida", entity, Map.class);
            return true;
        } catch (Exception e) {
            log.error("Error registrando entrega fallida {}: {}", idEntrega, e.getMessage());
            return false;
        }
    }

    public boolean darDeBajaCamion(String idCamion, String motivo) {
        try {
            HttpHeaders headers = buildAuthHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            String url = gatewayUrl + "/api/v1/logistica/camiones/" + idCamion +
                    "?motivo=" + java.net.URLEncoder.encode(motivo != null ? motivo : "Baja operativa", java.nio.charset.StandardCharsets.UTF_8);
            restTemplate.exchange(url, HttpMethod.DELETE, entity, Void.class);
            return true;
        } catch (Exception e) {
            log.error("Error dando de baja camión {}: {}", idCamion, e.getMessage());
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> crearDonanteJuridica(Map<String, Object> body) {
        try {
            HttpHeaders headers = buildAuthHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            return restTemplate.postForObject(gatewayUrl + "/api/v1/donantes/juridica", entity, Map.class);
        } catch (Exception e) {
            log.error("Error creando donante jurídica: {}", e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> importarCsvDonantes(org.springframework.web.multipart.MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        try {
            HttpHeaders headers = buildAuthHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            org.springframework.util.MultiValueMap<String, Object> body = new org.springframework.util.LinkedMultiValueMap<>();
            body.add("file", file.getResource());
            HttpEntity<org.springframework.util.MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
            return restTemplate.postForObject(gatewayUrl + "/api/v1/donantes/importar-csv", requestEntity, Map.class);
        } catch (Exception e) {
            log.error("Error importando CSV de donantes: {}", e.getMessage());
            return null;
        }
    }

    // --- Incentivos ---

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listarMisionesActivas() {
        try {
            return restTemplate.getForObject(gatewayUrl + "/api/v1/incentivos/misiones/activas", List.class);
        } catch (Exception e) {
            log.warn("Fallo listando misiones activas: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> obtenerMetricasDonante(Long idDonante) {
        try {
            return restTemplate.getForObject(gatewayUrl + "/api/v1/incentivos/donantes/" + idDonante + "/metricas", Map.class);
        } catch (Exception e) {
            log.warn("Fallo obteniendo metricas donante {}: {}", idDonante, e.getMessage());
            return Collections.emptyMap();
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> obtenerAnaliticaDonante(Long donanteId) {
        try {
            Map<String, Object> res = restTemplate.getForObject(gatewayUrl + "/api/v1/incentivos/donantes/" + donanteId + "/analitica", Map.class);
            return res != null ? res : Collections.emptyMap();
        } catch (Exception e) {
            log.warn("Fallo obteniendo analitica donante {}: {}", donanteId, e.getMessage());
            return Collections.emptyMap();
        }
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listarInsigniasDonante(Long idDonante) {
        try {
            return restTemplate.getForObject(gatewayUrl + "/api/v1/incentivos/donantes/" + idDonante + "/insignias", List.class);
        } catch (Exception e) {
            log.warn("Fallo listando insignias donante {}: {}", idDonante, e.getMessage());
            return Collections.emptyList();
        }
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listarMisionesDonante(Long idDonante) {
        try {
            return restTemplate.getForObject(gatewayUrl + "/api/v1/incentivos/donantes/" + idDonante + "/misiones", List.class);
        } catch (Exception e) {
            log.warn("Fallo listando misiones de progreso del donante {}: {}", idDonante, e.getMessage());
            return Collections.emptyList();
        }
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> obtenerRankingMensual(Integer anio, Integer mes) {
        try {
            String query = (anio != null && mes != null) ? "?anio=" + anio + "&mes=" + mes : "";
            return restTemplate.getForObject(gatewayUrl + "/api/v1/incentivos/rankings/mensual" + query, List.class);
        } catch (Exception e) {
            log.warn("Fallo obteniendo ranking mensual: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    // --- Extensiones de Cobertura y Trazabilidad (TODO-CLI-01 a TODO-CLI-07) ---

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listarDonacionesPublicasMapa() {
        try {
            return restTemplate.getForObject(gatewayUrl + "/api/v1/donaciones/publico/mapa", List.class);
        } catch (Exception e) {
            log.warn("Fallo obteniendo donaciones publicas para mapa: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> obtenerHistorialDonacion(Long id) {
        try {
            return restTemplate.getForObject(gatewayUrl + "/api/v1/donaciones/" + id + "/historial", List.class);
        } catch (Exception e) {
            log.warn("Fallo obteniendo historial de estados para donacion id={}: {}", id, e.getMessage());
            return Collections.emptyList();
        }
    }

    public boolean darDeBajaDonante(Long donanteId, String motivo) {
        try {
            HttpHeaders headers = buildAuthHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            String url = gatewayUrl + "/api/v1/donantes/" + donanteId + (motivo != null && !motivo.isBlank() ? "?motivo=" + motivo : "");
            restTemplate.exchange(url, HttpMethod.DELETE, entity, Void.class);
            return true;
        } catch (Exception e) {
            log.error("Error aplicando baja lógica a donante id={}: {}", donanteId, e.getMessage());
            return false;
        }
    }

    public boolean darDeBajaEntidad(Long entidadId, String motivo) {
        try {
            HttpHeaders headers = buildAuthHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            String url = gatewayUrl + "/api/v1/entidades/" + entidadId + (motivo != null && !motivo.isBlank() ? "?motivo=" + motivo : "");
            restTemplate.exchange(url, HttpMethod.DELETE, entity, Void.class);
            return true;
        } catch (Exception e) {
            log.error("Error aplicando baja lógica a entidad id={}: {}", entidadId, e.getMessage());
            return false;
        }
    }

    public boolean registrarEntregaFallida(String idEntrega, String motivo, boolean replanificable) {
        try {
            HttpHeaders headers = buildAuthHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, Object> body = Map.of(
                    "motivo", motivo != null ? motivo : "Entrega fallida reportada",
                    "replanificable", replanificable
            );
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            restTemplate.postForEntity(gatewayUrl + "/api/v1/logistica/rutas/entregas/" + idEntrega + "/fallida", entity, Map.class);
            return true;
        } catch (Exception e) {
            log.error("Error registrando entrega fallida {}: {}", idEntrega, e.getMessage());
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> obtenerComprobanteEntrega(String idEntrega) {
        try {
            return restTemplate.getForObject(gatewayUrl + "/api/v1/logistica/entregas/" + idEntrega + "/comprobante", Map.class);
        } catch (Exception e) {
            log.warn("Fallo obteniendo comprobante formal para entrega {}: {}", idEntrega, e.getMessage());
            return Collections.emptyMap();
        }
    }

    public boolean alternarVisibilidadInsignia(Long idInsignia, boolean visible) {
        try {
            HttpHeaders headers = buildAuthHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, Object> body = Map.of("visible", visible);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            restTemplate.exchange(gatewayUrl + "/api/v1/incentivos/insignias/" + idInsignia + "/visibilidad?visible=" + visible, HttpMethod.PATCH, entity, Map.class);
            return true;
        } catch (Exception e) {
            log.error("Error alternando visibilidad para insignia {}: {}", idInsignia, e.getMessage());
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listarNotificaciones(String destinatario) {
        try {
            String url = gatewayUrl + "/api/v1/notificaciones/historial" +
                    (destinatario != null && !destinatario.isBlank() ? "?destinatario=" + destinatario : "");
            List<Map<String, Object>> res = restTemplate.getForObject(url, List.class);
            return res != null ? res : Collections.emptyList();
        } catch (Exception e) {
            log.warn("Fallo consultando historial de notificaciones: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> obtenerEstadisticasNotificaciones() {
        try {
            Map<String, Object> res = restTemplate.getForObject(gatewayUrl + "/api/v1/notificaciones/estadisticas", Map.class);
            return res != null ? res : Collections.emptyMap();
        } catch (Exception e) {
            log.warn("Fallo consultando estadisticas de notificaciones: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    public boolean enviarNotificacionPrueba(String canal, String destinatario, String asunto, String mensaje) {
        try {
            HttpHeaders headers = buildAuthHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String canalStr = canal != null && !canal.isBlank() ? canal : "EMAIL";
            String asuntoStr = asunto != null && !asunto.isBlank() ? asunto : "Notificación de Prueba DonaTrack";
            String mensajeStr = mensaje != null && !mensaje.isBlank() ? mensaje : "Mensaje de prueba enviado desde el panel de administración.";

            String url = gatewayUrl + "/api/v1/notificaciones/test?canal=" + canalStr
                    + "&destinatario=" + java.net.URLEncoder.encode(destinatario != null ? destinatario : "", java.nio.charset.StandardCharsets.UTF_8)
                    + "&asunto=" + java.net.URLEncoder.encode(asuntoStr, java.nio.charset.StandardCharsets.UTF_8)
                    + "&mensaje=" + java.net.URLEncoder.encode(mensajeStr, java.nio.charset.StandardCharsets.UTF_8);

            HttpEntity<Void> entity = new HttpEntity<>(headers);
            restTemplate.postForEntity(url, entity, Map.class);
            return true;
        } catch (Exception e) {
            log.error("Error enviando notificación de prueba: {}", e.getMessage());
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> detectarDonantesInactivos() {
        try {
            HttpHeaders headers = buildAuthHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            Map<String, Object> res = restTemplate.postForObject(gatewayUrl + "/api/v1/donaciones/inactivos/detectar", entity, Map.class);
            return res != null ? res : Collections.emptyMap();
        } catch (Exception e) {
            log.warn("Fallo disparando deteccion de donantes inactivos: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> obtenerDonante(Long id) {
        try {
            Map<String, Object> res = restTemplate.getForObject(gatewayUrl + "/api/v1/donantes/" + id, Map.class);
            return res != null ? res : Collections.emptyMap();
        } catch (Exception e) {
            log.warn("Fallo obteniendo donante id={}: {}", id, e.getMessage());
            return Collections.emptyMap();
        }
    }

    // --- Mailpit REST Integration ---

    @SuppressWarnings("unchecked")
    public Map<String, Object> listarMensajesMailpit(String query, int limit) {
        try {
            int max = (limit > 0) ? limit : 50;
            java.net.URI uri;
            if (query != null && !query.trim().isBlank()) {
                uri = org.springframework.web.util.UriComponentsBuilder
                        .fromHttpUrl(mailpitUrl)
                        .path("/api/v1/search")
                        .queryParam("query", query.trim())
                        .queryParam("limit", max)
                        .build()
                        .toUri();
            } else {
                uri = org.springframework.web.util.UriComponentsBuilder
                        .fromHttpUrl(mailpitUrl)
                        .path("/api/v1/messages")
                        .queryParam("limit", max)
                        .build()
                        .toUri();
            }
            Map<String, Object> res = restTemplate.getForObject(uri, Map.class);
            return res != null ? res : Collections.emptyMap();
        } catch (Exception e) {
            log.warn("Fallo consultando mensajes de Mailpit: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> obtenerMensajeMailpit(String id) {
        if (id == null || id.isBlank()) return Collections.emptyMap();
        try {
            Map<String, Object> res = restTemplate.getForObject(mailpitUrl + "/api/v1/message/" + id, Map.class);
            return res != null ? res : Collections.emptyMap();
        } catch (Exception e) {
            log.warn("Fallo obteniendo mensaje de Mailpit id={}: {}", id, e.getMessage());
            return Collections.emptyMap();
        }
    }

    public boolean eliminarMensajeMailpit(String id) {
        if (id == null || id.isBlank()) return false;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(Map.of("ids", List.of(id)), headers);
            restTemplate.exchange(mailpitUrl + "/api/v1/messages", HttpMethod.DELETE, entity, String.class);
            return true;
        } catch (Exception e) {
            log.warn("Fallo eliminando mensaje de Mailpit id={}: {}", id, e.getMessage());
            return false;
        }
    }

    public boolean vaciarBuzonMailpit() {
        try {
            restTemplate.delete(mailpitUrl + "/api/v1/messages");
            return true;
        } catch (Exception e) {
            log.warn("Fallo vaciando buzon de Mailpit: {}", e.getMessage());
            return false;
        }
    }
}
