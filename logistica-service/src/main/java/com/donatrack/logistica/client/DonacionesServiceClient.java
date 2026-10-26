package com.donatrack.logistica.client;

import com.donatrack.common.enums.EstadoDonacion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Cliente REST sincrónico para la integración con donaciones-service.
 */
@Component
public class DonacionesServiceClient {

    private static final Logger log = LoggerFactory.getLogger(DonacionesServiceClient.class);

    private final RestClient restClient;

    public DonacionesServiceClient(@Value("${donatrack.servicios.donaciones-url:http://localhost:8082}") String donacionesUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));

        this.restClient = RestClient.builder()
                .baseUrl(donacionesUrl)
                .requestFactory(factory)
                .build();
    }

    /**
     * Consulta donaciones en estado ASIGNACION_REALIZADA para planificar lotes de ruta.
     */
    public List<Long> obtenerDonacionesAsignadasIds() {
        try {
            List<Map<String, Object>> response = restClient.get()
                    .uri("/api/v1/donaciones/asignadas-logistica")
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            if (response == null) return List.of();

            List<Long> ids = new ArrayList<>();
            for (Map<String, Object> map : response) {
                if (map.containsKey("id")) {
                    ids.add(((Number) map.get("id")).longValue());
                }
            }
            return ids;
        } catch (Exception e) {
            log.warn("No se pudo consultar donaciones-service (/asignadas-logistica): {}. Continuando con fallback vacío.", e.getMessage());
            return List.of();
        }
    }

    /**
     * Actualiza masivamente el estado de donaciones (ej: LISTA_PARA_ENTREGAR, EN_TRASLADO).
     */
    public void actualizarEstadoLote(List<Long> ids, EstadoDonacion nuevoEstado, String justificacion) {
        if (ids == null || ids.isEmpty()) return;

        Map<String, Object> body = Map.of(
                "donacionIds", ids,
                "nuevoEstado", nuevoEstado.name(),
                "justificacion", justificacion != null ? justificacion : "Actualización masiva logística"
        );

        try {
            restClient.put()
                    .uri("/api/v1/donaciones/bulk-estado")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Sincronizadas {} donaciones a estado {} en donaciones-service", ids.size(), nuevoEstado);
        } catch (Exception e) {
            log.error("Error al actualizar estado en lote en donaciones-service: {}", e.getMessage());
        }
    }

    /**
     * Actualiza el estado de una donación individual (ej: ENTREGADA, ENTREGA_FALLIDA, EN_DEPOSITO).
     */
    public void actualizarEstadoDonacion(Long donacionId, EstadoDonacion nuevoEstado, String justificacion, String usuario) {
        actualizarEstadoDonacion(donacionId, nuevoEstado, justificacion, usuario, null);
    }

    public void actualizarEstadoDonacion(Long donacionId, EstadoDonacion nuevoEstado, String justificacion, String usuario, String fotoUrl) {
        if (donacionId == null) return;

        java.util.Map<String, String> body = new java.util.HashMap<>();
        body.put("nuevoEstado", nuevoEstado.name());
        body.put("justificacion", justificacion != null ? justificacion : "Actualización logística");
        body.put("usuario", usuario != null ? usuario : "LOGISTICA_SERVICE");
        if (fotoUrl != null && !fotoUrl.isBlank()) {
            body.put("fotoUrl", fotoUrl);
        }

        try {
            restClient.patch()
                    .uri("/api/v1/donaciones/{id}/estado", donacionId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Sincronizada donación {} a estado {} en donaciones-service", donacionId, nuevoEstado);
        } catch (Exception e) {
            log.error("Error al actualizar donación {} en donaciones-service: {}", donacionId, e.getMessage());
        }
    }

    /**
     * Obtiene los datos de contacto del donante y entidad beneficiaria para enriquecer las entregas y notificaciones.
     */
    public Map<String, Object> obtenerDetalleDonacion(Long donacionId) {
        if (donacionId == null) return Map.of();
        try {
            Map<String, Object> response = restClient.get()
                    .uri("/api/v1/donaciones/{id}", donacionId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            return response != null ? response : Map.of();
        } catch (Exception e) {
            log.debug("No se pudo obtener detalle de donación {} desde donaciones-service: {}", donacionId, e.getMessage());
            return Map.of();
        }
    }
}
