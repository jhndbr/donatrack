package com.donatrack.frontend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class ApiClientService {

    private static final Logger log = LoggerFactory.getLogger(ApiClientService.class);

    private final RestTemplate restTemplate;

    @Value("${donatrack.api-gateway.url:http://localhost:8080}")
    private String gatewayUrl;

    public ApiClientService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public String getGatewayUrl() {
        return gatewayUrl;
    }

    // --- Donaciones ---

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listarDonaciones(String estado) {
        try {
            String url = gatewayUrl + "/api/v1/donaciones" + (estado != null && !estado.isBlank() ? "?estado=" + estado : "");
            return restTemplate.getForObject(url, List.class);
        } catch (Exception e) {
            log.warn("Fallo llamando a /api/v1/donaciones: {}", e.getMessage());
            return Collections.emptyList();
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
            HttpHeaders headers = new HttpHeaders();
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

    public Map<String, Object> crearEntidad(Map<String, Object> body) {
        try {
            HttpHeaders headers = new HttpHeaders();
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

    public boolean crearNecesidad(Long entidadId, Map<String, Object> body) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            restTemplate.postForEntity(gatewayUrl + "/api/v1/entidades/" + entidadId + "/necesidades", entity, Map.class);
            return true;
        } catch (Exception e) {
            log.error("Error creando necesidad: {}", e.getMessage());
            return false;
        }
    }

    public boolean crearDonacionSegmentada(Long donanteId, List<Map<String, Object>> bienes) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<List<Map<String, Object>>> entity = new HttpEntity<>(bienes, headers);
            restTemplate.postForEntity(gatewayUrl + "/api/v1/donaciones/carga-segmentada?donanteId=" + donanteId, entity, List.class);
            return true;
        } catch (Exception e) {
            log.error("Error creando donacion segmentada: {}", e.getMessage());
            return false;
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

    public boolean asignarDonacion(Long donacionId, Long entidadId) {
        try {
            restTemplate.postForEntity(gatewayUrl + "/api/v1/donaciones/" + donacionId + "/asignar?entidadId=" + entidadId, null, Map.class);
            return true;
        } catch (Exception e) {
            log.error("Error asignando donacion: {}", e.getMessage());
            return false;
        }
    }

    public boolean actualizarEstadoDonacion(Long donacionId, String nuevoEstado, String justificacion) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, String> body = Map.of(
                    "nuevoEstado", nuevoEstado,
                    "justificacion", justificacion != null ? justificacion : "Actualización desde frontend"
            );
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
                actualizarEstadoDonacion(donacionId, "LISTA_PARA_ENTREGAR", "Preparada para logística");
                estado = "LISTA_PARA_ENTREGAR";
            }
            if ("LISTA_PARA_ENTREGAR".equals(estado)) {
                actualizarEstadoDonacion(donacionId, "EN_TRASLADO", "Despachada hacia la entidad");
                estado = "EN_TRASLADO";
            }
            if ("EN_TRASLADO".equals(estado)) {
                actualizarEstadoDonacion(donacionId, "ENTREGADA", "Recepción confirmada con foto: " + (fotoUrl != null ? fotoUrl : "Comprobante emitido"));
            }
            return true;
        } catch (Exception e) {
            log.error("Error confirmando entrega de donacion {}: {}", donacionId, e.getMessage());
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
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            restTemplate.postForEntity(gatewayUrl + "/api/v1/logistica/camiones", entity, Map.class);
            return true;
        } catch (Exception e) {
            log.error("Error registrando camion: {}", e.getMessage());
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
            HttpHeaders headers = new HttpHeaders();
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
}
