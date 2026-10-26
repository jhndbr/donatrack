package com.donatrack.donaciones.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "configuracion_matchmaking")
public class ConfiguracionMatchmaking {

    @Id
    private Long id = 1L;

    @Column(nullable = false)
    private double a1 = 0.40;

    @Column(nullable = false)
    private double a2 = 0.40;

    @Column(nullable = false)
    private double a3 = 0.20;

    @Column(nullable = false)
    private double alpha = 0.10;

    @Column(name = "fecha_actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion = LocalDateTime.now();

    public ConfiguracionMatchmaking() {
        this.id = 1L;
        this.fechaActualizacion = LocalDateTime.now();
    }

    public ConfiguracionMatchmaking(double a1, double a2, double a3, double alpha) {
        this.id = 1L;
        this.a1 = a1;
        this.a2 = a2;
        this.a3 = a3;
        this.alpha = alpha;
        this.fechaActualizacion = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public double getA1() { return a1; }
    public void setA1(double a1) { this.a1 = a1; }

    public double getA2() { return a2; }
    public void setA2(double a2) { this.a2 = a2; }

    public double getA3() { return a3; }
    public void setA3(double a3) { this.a3 = a3; }

    public double getAlpha() { return alpha; }
    public void setAlpha(double alpha) { this.alpha = alpha; }

    public LocalDateTime getFechaActualizacion() { return fechaActualizacion; }
    public void setFechaActualizacion(LocalDateTime fechaActualizacion) { this.fechaActualizacion = fechaActualizacion; }
}
