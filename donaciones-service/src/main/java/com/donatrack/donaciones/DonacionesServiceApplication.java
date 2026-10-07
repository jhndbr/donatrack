package com.donatrack.donaciones;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DonacionesServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(DonacionesServiceApplication.class, args);
    }
}
