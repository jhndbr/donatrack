package com.donatrack.logistica;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class LogisticaServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(LogisticaServiceApplication.class, args);
    }
}
