package com.donatrack.logistica.client;

import com.donatrack.logistica.domain.Camion;
import com.donatrack.logistica.dto.PlanificadorCallbackDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Cliente HTTP real hacia el proveedor externo VRP de rutas con timeouts configurables.
 */
@Component("httpVrpOptimizerClient")
public class HttpVrpOptimizerClient implements RutaOptimizerClient {

    private static final Logger log = LoggerFactory.getLogger(HttpVrpOptimizerClient.class);

    private final RestClient restClient;
    private final String vrpUrl;

    public HttpVrpOptimizerClient(@Value("${donatrack.vrp.url:http://localhost:8085}") String vrpUrl) {
        this.vrpUrl = vrpUrl;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));

        this.restClient = RestClient.builder()
                .baseUrl(vrpUrl)
                .requestFactory(factory)
                .build();
    }

    @Override
    public PlanificadorCallbackDto optimizarLote(String loteId, List<Long> donacionIds, Camion camion) {
        log.info("[HttpVRP] Despachando lote {} al optimizador externo en {}...", loteId, vrpUrl);

        Map<String, Object> requestPayload = Map.of(
                "loteId", loteId,
                "camionId", camion != null ? camion.getId() : "CAMION-01",
                "patente", camion != null ? camion.getPatente() : "AE789XY",
                "donacionIds", donacionIds,
                "callbackUrl", "/api/v1/logistica/rutas/callback"
        );

        return restClient.post()
                .uri("/api/v1/vrp/optimize")
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestPayload)
                .retrieve()
                .body(PlanificadorCallbackDto.class);
    }
}
