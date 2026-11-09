package com.donatrack.frontend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
public class FrontendServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FrontendServiceApplication.class, args);
    }

    @Bean
    public RestTemplate restTemplate() {
        org.springframework.http.client.JdkClientHttpRequestFactory factory =
                new org.springframework.http.client.JdkClientHttpRequestFactory();
        factory.setReadTimeout(java.time.Duration.ofSeconds(8));
        RestTemplate rt = new RestTemplate(factory);
        rt.getInterceptors().add((request, body, execution) -> {
            String correlationId = org.slf4j.MDC.get("correlationId");
            String traceId = org.slf4j.MDC.get("traceId");
            if (correlationId != null && !correlationId.isBlank() && !request.getHeaders().containsKey("X-Correlation-Id")) {
                request.getHeaders().set("X-Correlation-Id", correlationId);
            }
            if (traceId != null && !traceId.isBlank() && !request.getHeaders().containsKey("X-Trace-Id")) {
                request.getHeaders().set("X-Trace-Id", traceId);
            }
            return execution.execute(request, body);
        });
        return rt;
    }
}
