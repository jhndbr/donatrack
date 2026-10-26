package com.donatrack.gateway.filter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Filtro global de seguridad perimetral en API Gateway (CORR-003).
 * 1. Limpia cualquier cabecera X-Authenticated-User o X-Gateway-Verified falsificada desde el exterior.
 * 2. Si la petición incluye Authorization: Bearer <token>, decodifica y verifica estructura/expiración del JWT
 *    y propaga X-User-Id (sub), X-User-Roles (realm_access.roles) y X-Gateway-Verified: true.
 * 3. Cuando enforce-strict-jwt=true, exige un token Bearer válido en todas las rutas no públicas o retorna 401.
 */
@Component
public class AuthenticationGatewayFilter implements GlobalFilter, Ordered {

    private static final String BEARER_PREFIX = "Bearer ";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${donatrack.gateway.security.enabled:true}")
    private boolean securityEnabled = true;

    @Value("${donatrack.gateway.security.enforce-strict-jwt:false}")
    private boolean enforceStrictJwt = false;

    public AuthenticationGatewayFilter() {
    }

    public AuthenticationGatewayFilter(boolean securityEnabled, boolean enforceStrictJwt) {
        this.securityEnabled = securityEnabled;
        this.enforceStrictJwt = enforceStrictJwt;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!securityEnabled) {
            return chain.filter(exchange);
        }

        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        HttpMethod method = request.getMethod();

        // 1. Limpiar cabeceras de seguridad internas para impedir suplantación desde el exterior
        ServerHttpRequest.Builder mutatedRequest = request.mutate()
                .headers(headers -> {
                    headers.remove("X-Authenticated-User");
                    headers.remove("X-Gateway-Verified");
                });

        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        boolean hasBearer = authHeader != null && authHeader.startsWith(BEARER_PREFIX);

        if (hasBearer) {
            String token = authHeader.substring(BEARER_PREFIX.length()).trim();
            JwtClaims claims = parseAndValidateJwt(token);
            if (claims != null) {
                mutatedRequest.header("X-Gateway-Verified", "true");
                if (claims.sub != null && !claims.sub.isBlank()) {
                    mutatedRequest.header("X-User-Id", claims.sub);
                    mutatedRequest.header("X-Authenticated-User", claims.sub);
                }
                if (claims.rolesCsv != null && !claims.rolesCsv.isBlank()) {
                    mutatedRequest.header("X-User-Roles", claims.rolesCsv);
                }
                return chain.filter(exchange.mutate().request(mutatedRequest.build()).build());
            } else if (enforceStrictJwt && !isPublicRoute(method, path)) {
                return unauthorized(exchange.getResponse(), "Token JWT inválido o expirado.");
            }
        } else if (enforceStrictJwt && !isPublicRoute(method, path)) {
            return unauthorized(exchange.getResponse(), "Se requiere autenticación Bearer JWT para acceder a este recurso.");
        }

        return chain.filter(exchange.mutate().request(mutatedRequest.build()).build());
    }

    public boolean isPublicRoute(HttpMethod method, String path) {
        if (path == null) {
            return false;
        }
        if (HttpMethod.OPTIONS.equals(method)) {
            return true;
        }
        if (path.startsWith("/api/v1/donaciones/publico/") || path.equals("/api/v1/donaciones/publico")
                || path.startsWith("/api/v1/direcciones/") || path.equals("/api/v1/direcciones")
                || path.startsWith("/api/v1/donaciones/direcciones/") || path.equals("/api/v1/donaciones/direcciones")
                || path.startsWith("/actuator/") || path.equals("/actuator")) {
            return true;
        }
        if (HttpMethod.GET.equals(method) && path.equals("/api/v1/donaciones/saludo")) {
            return true;
        }
        if (HttpMethod.POST.equals(method)) {
            if (path.startsWith("/api/v1/donantes/") || path.equals("/api/v1/donantes")
                    || path.startsWith("/api/v1/personas-donantes/") || path.equals("/api/v1/personas-donantes")
                    || path.equals("/api/v1/entidades")) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private JwtClaims parseAndValidateJwt(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3 || parts[0].isBlank() || parts[1].isBlank() || parts[2].isBlank()) {
            return null;
        }
        try {
            byte[] decodedPayload = Base64.getUrlDecoder().decode(parts[1]);
            Map<String, Object> payload = objectMapper.readValue(decodedPayload, new TypeReference<Map<String, Object>>() {});

            // Verificar expiración (exp en segundos epoch)
            Object expObj = payload.get("exp");
            if (expObj instanceof Number expNum) {
                long expEpochSeconds = expNum.longValue();
                if (expEpochSeconds < Instant.now().getEpochSecond()) {
                    return null;
                }
            }

            String sub = null;
            if (payload.get("sub") != null) {
                sub = payload.get("sub").toString();
            } else if (payload.get("preferred_username") != null) {
                sub = payload.get("preferred_username").toString();
            }

            String rolesCsv = null;
            Object realmAccess = payload.get("realm_access");
            if (realmAccess instanceof Map<?, ?> realmMap) {
                Object rolesObj = realmMap.get("roles");
                if (rolesObj instanceof Collection<?> rolesCol) {
                    rolesCsv = rolesCol.stream()
                            .filter(r -> r != null && !r.toString().isBlank())
                            .map(Object::toString)
                            .collect(Collectors.joining(","));
                }
            }
            if ((rolesCsv == null || rolesCsv.isBlank()) && payload.get("roles") instanceof Collection<?> topRoles) {
                rolesCsv = topRoles.stream()
                        .filter(r -> r != null && !r.toString().isBlank())
                        .map(Object::toString)
                        .collect(Collectors.joining(","));
            }

            return new JwtClaims(sub, rolesCsv);
        } catch (Exception e) {
            return null;
        }
    }

    private Mono<Void> unauthorized(ServerHttpResponse response, String message) {
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Bearer realm=\"donatrack\"");
        String body = "{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"" + message + "\"}";
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    public boolean isSecurityEnabled() {
        return securityEnabled;
    }

    public void setSecurityEnabled(boolean securityEnabled) {
        this.securityEnabled = securityEnabled;
    }

    public boolean isEnforceStrictJwt() {
        return enforceStrictJwt;
    }

    public void setEnforceStrictJwt(boolean enforceStrictJwt) {
        this.enforceStrictJwt = enforceStrictJwt;
    }

    private record JwtClaims(String sub, String rolesCsv) {
    }
}
