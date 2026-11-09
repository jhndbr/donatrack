package com.donatrack.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

/**
 * Filtro global de observabilidad, correlación distribuida y resiliencia para el API Gateway.
 *
 * Responsabilidades:
 * 1. Genera o propaga los encabezados de trazabilidad X-Correlation-Id y X-Trace-Id hacia los microservicios
 *    y los devuelve en la respuesta HTTP al cliente.
 * 2. Registra logs estructurados de entrada y salida con método, ruta, servicio destino, código HTTP y duración (ms),
 *    sin exponer tokens ni credenciales sensibles.
 * 3. Captura errores de conectividad o timeouts de los microservicios aguas abajo devolviendo una respuesta JSON
 *    controlada (503 Service Unavailable / 504 Gateway Timeout).
 */
@Component
public class ObservabilityAndCorrelationGatewayFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(ObservabilityAndCorrelationGatewayFilter.class);

    public static final String HEADER_CORRELATION_ID = "X-Correlation-Id";
    public static final String HEADER_TRACE_ID = "X-Trace-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startTimeNanos = System.nanoTime();
        ServerHttpRequest request = exchange.getRequest();

        String incomingCorrelationId = request.getHeaders().getFirst(HEADER_CORRELATION_ID);
        final String correlationId = (incomingCorrelationId != null && !incomingCorrelationId.isBlank())
                ? incomingCorrelationId.trim()
                : UUID.randomUUID().toString();

        String incomingTraceId = request.getHeaders().getFirst(HEADER_TRACE_ID);
        final String traceId = (incomingTraceId != null && !incomingTraceId.isBlank())
                ? incomingTraceId.trim()
                : UUID.randomUUID().toString().replace("-", "");

        String method = request.getMethod() != null ? request.getMethod().name() : "UNKNOWN";
        String path = request.getURI().getRawPath();
        String clientIp = resolveClientIp(request);

        ServerHttpRequest mutatedRequest = request.mutate()
                .headers(headers -> {
                    headers.set(HEADER_CORRELATION_ID, correlationId);
                    headers.set(HEADER_TRACE_ID, traceId);
                })
                .build();

        ServerHttpResponse response = exchange.getResponse();
        response.getHeaders().set(HEADER_CORRELATION_ID, correlationId);
        response.getHeaders().set(HEADER_TRACE_ID, traceId);

        log.info("[GATEWAY-IN] correlationId={} traceId={} method={} path={} clientIp={}",
                correlationId, traceId, method, path, clientIp);

        ServerWebExchange mutatedExchange = exchange.mutate().request(mutatedRequest).build();

        return chain.filter(mutatedExchange)
                .doOnSuccess(aVoid -> logCompletion(mutatedExchange, correlationId, traceId, method, path, startTimeNanos))
                .onErrorResume(ex -> handleDownstreamError(mutatedExchange, correlationId, traceId, method, path, startTimeNanos, ex));
    }

    private void logCompletion(ServerWebExchange exchange,
                               String correlationId,
                               String traceId,
                               String method,
                               String path,
                               long startTimeNanos) {
        long durationMs = (System.nanoTime() - startTimeNanos) / 1_000_000;
        HttpStatusCode statusCode = exchange.getResponse().getStatusCode();
        int status = statusCode != null ? statusCode.value() : 200;

        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        URI routeUri = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR);
        String targetService = route != null ? route.getId() : (routeUri != null ? routeUri.getAuthority() : "gateway-local");

        if (status >= 500) {
            log.error("[GATEWAY-OUT] correlationId={} traceId={} method={} path={} target={} status={} durationMs={}",
                    correlationId, traceId, method, path, targetService, status, durationMs);
        } else if (status >= 400) {
            log.warn("[GATEWAY-OUT] correlationId={} traceId={} method={} path={} target={} status={} durationMs={}",
                    correlationId, traceId, method, path, targetService, status, durationMs);
        } else {
            log.info("[GATEWAY-OUT] correlationId={} traceId={} method={} path={} target={} status={} durationMs={}",
                    correlationId, traceId, method, path, targetService, status, durationMs);
        }
    }

    private Mono<Void> handleDownstreamError(ServerWebExchange exchange,
                                             String correlationId,
                                             String traceId,
                                             String method,
                                             String path,
                                             long startTimeNanos,
                                             Throwable ex) {
        long durationMs = (System.nanoTime() - startTimeNanos) / 1_000_000;
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        String targetService = route != null ? route.getId() : "downstream-service";

        boolean isTimeout = ex instanceof TimeoutException
                || (ex.getCause() instanceof TimeoutException)
                || (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("timeout"));

        HttpStatus status = isTimeout ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.SERVICE_UNAVAILABLE;

        log.error("[GATEWAY-ERROR] correlationId={} traceId={} method={} path={} target={} status={} durationMs={} cause={}",
                correlationId, traceId, method, path, targetService, status.value(), durationMs, ex.getMessage());

        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.error(ex);
        }

        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().set(HEADER_CORRELATION_ID, correlationId);
        response.getHeaders().set(HEADER_TRACE_ID, traceId);

        String safeMessage = isTimeout
                ? "Tiempo de espera agotado al comunicarse con el microservicio destino (" + targetService + ")"
                : "El microservicio destino (" + targetService + ") no se encuentra disponible temporalmente";

        String body = String.format(
                "{\"timestamp\":\"%s\",\"status\":%d,\"error\":\"%s\",\"message\":\"%s\",\"path\":\"%s\",\"correlationId\":\"%s\",\"traceId\":\"%s\"}",
                Instant.now().toString(),
                status.value(),
                status.getReasonPhrase(),
                safeMessage,
                path,
                correlationId,
                traceId
        );

        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    private String resolveClientIp(ServerHttpRequest request) {
        HttpHeaders headers = request.getHeaders();
        String forwardedFor = headers.getFirst("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        if (request.getRemoteAddress() != null && request.getRemoteAddress().getAddress() != null) {
            return request.getRemoteAddress().getAddress().getHostAddress();
        }
        return "unknown";
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
