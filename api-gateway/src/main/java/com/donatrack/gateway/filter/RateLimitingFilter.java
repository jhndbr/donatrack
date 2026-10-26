package com.donatrack.gateway.filter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Filtro global reactivo de limitación de tasa de peticiones (Rate Limiting — Entrega 6 Req 4).
 * Implementa el algoritmo Token Bucket en memoria por dirección IP con cota máxima de entradas
 * (MAX_BUCKETS = 10_000) y limpieza automática para prevenir cualquier riesgo de agotamiento de memoria (OOM).
 */
@Component
public class RateLimitingFilter implements GlobalFilter, Ordered {

    public static final int MAX_BUCKETS = 10_000;
    private static final long REFILL_WINDOW_MS = 60_000L; // 1 minuto

    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    @Value("${donatrack.gateway.rate-limit.enabled:true}")
    private boolean enabled = true;

    @Value("${donatrack.gateway.rate-limit.capacity:120}")
    private int capacity = 120;

    public RateLimitingFilter() {
    }

    public RateLimitingFilter(boolean enabled, int capacity) {
        this.enabled = enabled;
        this.capacity = capacity;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!enabled) {
            return chain.filter(exchange);
        }

        String path = exchange.getRequest().getURI().getPath();
        if (path != null && path.startsWith("/actuator")) {
            return chain.filter(exchange);
        }

        String clientIp = resolveClientIp(exchange.getRequest());
        evictStaleBucketsIfNeeded();

        final int maxTokens = Math.max(1, this.capacity);
        TokenBucket bucket = buckets.computeIfAbsent(clientIp, ip -> new TokenBucket(maxTokens));

        int remaining = bucket.tryConsume(maxTokens);
        ServerHttpResponse response = exchange.getResponse();
        response.getHeaders().set("X-RateLimit-Limit", String.valueOf(maxTokens));
        response.getHeaders().set("X-RateLimit-Remaining", String.valueOf(Math.max(0, remaining)));

        if (remaining < 0) {
            response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            response.getHeaders().set(HttpHeaders.RETRY_AFTER, "60");
            String body = "{\"status\":429,\"error\":\"Too Many Requests\",\"message\":\"Límite de solicitudes excedido. Intente nuevamente en unos segundos.\"}";
            DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
            return response.writeWith(Mono.just(buffer));
        }

        return chain.filter(exchange);
    }

    private String resolveClientIp(ServerHttpRequest request) {
        String forwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            int commaIdx = forwardedFor.indexOf(',');
            return (commaIdx > 0 ? forwardedFor.substring(0, commaIdx) : forwardedFor).trim();
        }
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        if (remoteAddress != null && remoteAddress.getAddress() != null) {
            return remoteAddress.getAddress().getHostAddress();
        }
        return "127.0.0.1";
    }

    private void evictStaleBucketsIfNeeded() {
        if (buckets.size() < MAX_BUCKETS) {
            return;
        }
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, TokenBucket>> iterator = buckets.entrySet().iterator();
        while (iterator.hasNext() && buckets.size() >= MAX_BUCKETS) {
            Map.Entry<String, TokenBucket> entry = iterator.next();
            if (now - entry.getValue().getLastRefillTimestamp() > REFILL_WINDOW_MS) {
                iterator.remove();
            }
        }
        // Si tras limpiar expirados aún está lleno, removemos entradas antiguas hasta liberar espacio
        if (buckets.size() >= MAX_BUCKETS) {
            Iterator<String> keyIter = buckets.keySet().iterator();
            int toRemove = MAX_BUCKETS / 10;
            while (keyIter.hasNext() && toRemove-- > 0) {
                keyIter.next();
                keyIter.remove();
            }
        }
    }

    public void clearBuckets() {
        buckets.clear();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
        buckets.clear();
    }

    private static final class TokenBucket {
        private double tokens;
        private long lastRefillTimestamp;

        TokenBucket(int initialCapacity) {
            this.tokens = initialCapacity;
            this.lastRefillTimestamp = System.currentTimeMillis();
        }

        synchronized int tryConsume(int maxCapacity) {
            long now = System.currentTimeMillis();
            long elapsedMs = now - lastRefillTimestamp;
            if (elapsedMs > 0) {
                double refill = (elapsedMs / (double) REFILL_WINDOW_MS) * maxCapacity;
                if (refill >= 1.0) {
                    tokens = Math.min(maxCapacity, tokens + refill);
                    lastRefillTimestamp = now;
                }
            }
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return (int) Math.floor(tokens);
            }
            return -1;
        }

        synchronized long getLastRefillTimestamp() {
            return lastRefillTimestamp;
        }
    }
}
