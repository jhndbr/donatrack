package com.donatrack.frontend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Filtro HTTP de observabilidad y correlación distribuida (frontend-service).
 * Genera o propaga X-Correlation-Id y X-Trace-Id hacia el API Gateway y registra tiempos de respuesta.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(CorrelationIdFilter.class);
    public static final String HEADER_CORRELATION_ID = "X-Correlation-Id";
    public static final String HEADER_TRACE_ID = "X-Trace-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long startNanos = System.nanoTime();
        String corrHeader = request.getHeader(HEADER_CORRELATION_ID);
        String correlationId = (corrHeader != null && !corrHeader.isBlank()) ? corrHeader.trim() : UUID.randomUUID().toString();

        String traceHeader = request.getHeader(HEADER_TRACE_ID);
        String traceId = (traceHeader != null && !traceHeader.isBlank()) ? traceHeader.trim() : UUID.randomUUID().toString().replace("-", "");

        MDC.put("correlationId", correlationId);
        MDC.put("traceId", traceId);
        response.setHeader(HEADER_CORRELATION_ID, correlationId);
        response.setHeader(HEADER_TRACE_ID, traceId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
            String uri = request.getRequestURI();
            if (!uri.startsWith("/actuator") && !uri.startsWith("/css/") && !uri.startsWith("/js/") && !uri.startsWith("/images/")) {
                log.info("[HTTP-MVC] method={} uri={} status={} durationMs={}", request.getMethod(), uri, response.getStatus(), durationMs);
            }
            MDC.remove("correlationId");
            MDC.remove("traceId");
        }
    }
}
