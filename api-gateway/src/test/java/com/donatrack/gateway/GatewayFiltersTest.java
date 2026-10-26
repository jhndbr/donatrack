package com.donatrack.gateway;

import com.donatrack.gateway.filter.AuthenticationGatewayFilter;
import com.donatrack.gateway.filter.RateLimitingFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class GatewayFiltersTest {

    private RateLimitingFilter rateLimitingFilter;
    private AuthenticationGatewayFilter authFilter;

    @BeforeEach
    void setUp() {
        rateLimitingFilter = new RateLimitingFilter(true, 3);
        authFilter = new AuthenticationGatewayFilter(true, true);
    }

    @Test
    @DisplayName("RateLimitingFilter añade X-RateLimit-Remaining y devuelve 429 al superar la capacidad")
    void testRateLimitingFilterEnforcesCapacity() {
        GatewayFilterChain chain = exchange -> Mono.empty();

        // Petición 1 (quedan 2 tokens)
        MockServerWebExchange ex1 = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/donaciones")
                        .header("X-Forwarded-For", "192.168.1.50")
                        .build()
        );
        rateLimitingFilter.filter(ex1, chain).block();
        assertNull(ex1.getResponse().getStatusCode());
        assertEquals("3", ex1.getResponse().getHeaders().getFirst("X-RateLimit-Limit"));
        assertEquals("2", ex1.getResponse().getHeaders().getFirst("X-RateLimit-Remaining"));

        // Petición 2 (queda 1 token)
        MockServerWebExchange ex2 = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/donaciones")
                        .header("X-Forwarded-For", "192.168.1.50")
                        .build()
        );
        rateLimitingFilter.filter(ex2, chain).block();
        assertEquals("1", ex2.getResponse().getHeaders().getFirst("X-RateLimit-Remaining"));

        // Petición 3 (quedan 0 tokens)
        MockServerWebExchange ex3 = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/donaciones")
                        .header("X-Forwarded-For", "192.168.1.50")
                        .build()
        );
        rateLimitingFilter.filter(ex3, chain).block();
        assertEquals("0", ex3.getResponse().getHeaders().getFirst("X-RateLimit-Remaining"));

        // Petición 4: supera el límite -> 429 Too Many Requests
        MockServerWebExchange ex4 = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/donaciones")
                        .header("X-Forwarded-For", "192.168.1.50")
                        .build()
        );
        rateLimitingFilter.filter(ex4, chain).block();
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex4.getResponse().getStatusCode());
        assertEquals("0", ex4.getResponse().getHeaders().getFirst("X-RateLimit-Remaining"));

        // Otra IP distinta no debe verse afectada
        MockServerWebExchange exOtherIp = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/donaciones")
                        .header("X-Forwarded-For", "10.0.0.99")
                        .build()
        );
        rateLimitingFilter.filter(exOtherIp, chain).block();
        assertNull(exOtherIp.getResponse().getStatusCode());
        assertEquals("2", exOtherIp.getResponse().getHeaders().getFirst("X-RateLimit-Remaining"));
    }

    @Test
    @DisplayName("AuthenticationGatewayFilter rechaza con 401 rutas protegidas sin token cuando enforce-strict-jwt=true")
    void testAuthFilterRejectsProtectedRouteWithoutTokenInStrictMode() {
        GatewayFilterChain chain = exchange -> Mono.empty();

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/donaciones/1")
                        .header("X-Gateway-Verified", "true")
                        .header("X-Authenticated-User", "hacker")
                        .build()
        );

        authFilter.filter(exchange, chain).block();
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("AuthenticationGatewayFilter permite rutas públicas sin token aún con enforce-strict-jwt=true y limpia headers falsos")
    void testAuthFilterAllowsPublicRoutesAndSanitizesSpoofedHeaders() {
        AtomicReference<ServerWebExchange> capturedExchange = new AtomicReference<>();
        GatewayFilterChain chain = exchange -> {
            capturedExchange.set(exchange);
            return Mono.empty();
        };

        // 1. Ruta pública de donaciones
        MockServerWebExchange exPublic = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/donaciones/publico/resumen")
                        .header("X-Gateway-Verified", "true")
                        .header("X-Authenticated-User", "spoofed")
                        .build()
        );
        authFilter.filter(exPublic, chain).block();
        assertNull(exPublic.getResponse().getStatusCode());
        assertNotNull(capturedExchange.get());
        assertNull(capturedExchange.get().getRequest().getHeaders().getFirst("X-Gateway-Verified"));
        assertNull(capturedExchange.get().getRequest().getHeaders().getFirst("X-Authenticated-User"));

        // 2. Ruta pública de registro de persona jurídica (POST /api/v1/personas-donantes/juridica)
        MockServerWebExchange exRegistroJuridica = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/personas-donantes/juridica").build()
        );
        authFilter.filter(exRegistroJuridica, chain).block();
        assertNull(exRegistroJuridica.getResponse().getStatusCode());

        // 3. Ruta pública de Actuator Prometheus
        MockServerWebExchange exActuator = MockServerWebExchange.from(
                MockServerHttpRequest.get("/actuator/prometheus").build()
        );
        authFilter.filter(exActuator, chain).block();
        assertNull(exActuator.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("AuthenticationGatewayFilter extrae claims sub y realm_access.roles de un JWT válido e inyecta X-Gateway-Verified")
    void testAuthFilterExtractsClaimsFromValidJwt() {
        AtomicReference<ServerWebExchange> capturedExchange = new AtomicReference<>();
        GatewayFilterChain chain = exchange -> {
            capturedExchange.set(exchange);
            return Mono.empty();
        };

        long futureExp = Instant.now().getEpochSecond() + 3600;
        String token = buildSampleJwt("{\"sub\":\"user-uuid-123\",\"realm_access\":{\"roles\":[\"ROLE_ADMIN\",\"ROLE_DONANTE\"]},\"exp\":" + futureExp + "}");

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/donaciones")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .build()
        );

        authFilter.filter(exchange, chain).block();
        assertNull(exchange.getResponse().getStatusCode());
        assertNotNull(capturedExchange.get());

        HttpHeaders forwardedHeaders = capturedExchange.get().getRequest().getHeaders();
        assertEquals("true", forwardedHeaders.getFirst("X-Gateway-Verified"));
        assertEquals("user-uuid-123", forwardedHeaders.getFirst("X-User-Id"));
        assertEquals("user-uuid-123", forwardedHeaders.getFirst("X-Authenticated-User"));
        assertEquals("ROLE_ADMIN,ROLE_DONANTE", forwardedHeaders.getFirst("X-User-Roles"));
    }

    @Test
    @DisplayName("AuthenticationGatewayFilter rechaza con 401 un JWT expirado cuando enforce-strict-jwt=true")
    void testAuthFilterRejectsExpiredJwt() {
        GatewayFilterChain chain = exchange -> Mono.empty();

        long pastExp = Instant.now().getEpochSecond() - 3600;
        String expiredToken = buildSampleJwt("{\"sub\":\"user-uuid-123\",\"exp\":" + pastExp + "}");

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/incentivos/misiones")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken)
                        .build()
        );

        authFilter.filter(exchange, chain).block();
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    private String buildSampleJwt(String payloadJson) {
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
        String signature = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("valid-signature-bytes".getBytes(StandardCharsets.UTF_8));
        return header + "." + payload + "." + signature;
    }
}
