package com.donatrack.donaciones.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "bienes_donados")
public class BienDonado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String descripcion;

    @Column(nullable = false)
    private String categoria;

    @Column(nullable = false)
    private String subcategoria;

    @Column(nullable = false)
    private double cantidad;

    @Column(name = "unidad_medida", nullable = false)
    private String unidadMedida;

    @Column(name = "es_usado", nullable = false)
    private boolean esUsado;

    @Column(name = "fecha_vencimiento")
    private LocalDate fechaVencimiento;

    @Column(name = "foto_url")
    private String fotoUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "donacion_id")
    @JsonIgnore
    private Donacion donacion;

    public BienDonado() {}

    public BienDonado(String descripcion, String categoria, String subcategoria,
                      double cantidad, String unidadMedida, boolean esUsado,
                      LocalDate fechaVencimiento, String fotoUrl) {
        this.descripcion = descripcion;
        this.categoria = categoria;
        this.subcategoria = subcategoria;
        this.cantidad = cantidad;
        this.unidadMedida = unidadMedida;
        this.esUsado = esUsado;
        this.fechaVencimiento = fechaVencimiento;
        this.fotoUrl = fotoUrl;
    }

    public boolean estaVencido() {
        return fechaVencimiento != null && fechaVencimiento.isBefore(LocalDate.now());
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }

    public String getSubcategoria() { return subcategoria; }
    public void setSubcategoria(String subcategoria) { this.subcategoria = subcategoria; }

    public double getCantidad() { return cantidad; }
    public void setCantidad(double cantidad) { this.cantidad = cantidad; }

    public String getUnidadMedida() { return unidadMedida; }
    public void setUnidadMedida(String unidadMedida) { this.unidadMedida = unidadMedida; }

    public boolean isEsUsado() { return esUsado; }
    public void setEsUsado(boolean esUsado) { this.esUsado = esUsado; }

    public LocalDate getFechaVencimiento() { return fechaVencimiento; }
    public void setFechaVencimiento(LocalDate fechaVencimiento) { this.fechaVencimiento = fechaVencimiento; }

    public String getFotoUrl() { return fotoUrl; }
    public void setFotoUrl(String fotoUrl) { this.fotoUrl = fotoUrl; }

    public Donacion getDonacion() { return donacion; }
    public void setDonacion(Donacion donacion) { this.donacion = donacion; }
}
