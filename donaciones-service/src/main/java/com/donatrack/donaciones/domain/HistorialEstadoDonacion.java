package com.donatrack.donaciones.domain;

import com.donatrack.common.enums.EstadoDonacion;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Entidad de auditoria y trazabilidad completa del ciclo de vida de una Donación (CU-21).
 * Registra cada transición de estado con su marca temporal, estado previo y posterior,
 * usuario u operador responsable y la justificación requerida en fallos o cancelaciones.
 */
@Entity
@Table(name = "historial_estados_donacion")
public class HistorialEstadoDonacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "donacion_id", nullable = false)
    @JsonIgnore
    private Donacion donacion;

    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_anterior")
    private EstadoDonacion estadoAnterior;

    @Enumerated(EnumType.STRING)
    @Column(name = "nuevo_estado", nullable = false)
    private EstadoDonacion nuevoEstado;

    @Column(name = "usuario_responsable")
    private String usuarioResponsable;

    @Column(name = "justificacion", length = 1000)
    private String justificacion;

    public HistorialEstadoDonacion() {
        this.fechaHora = LocalDateTime.now();
    }

    public HistorialEstadoDonacion(Donacion donacion, LocalDateTime fechaHora,
                                  EstadoDonacion estadoAnterior, EstadoDonacion nuevoEstado,
                                  String usuarioResponsable, String justificacion) {
        this.donacion = donacion;
        this.fechaHora = fechaHora != null ? fechaHora : LocalDateTime.now();
        this.estadoAnterior = estadoAnterior;
        this.nuevoEstado = nuevoEstado;
        this.usuarioResponsable = (usuarioResponsable != null && !usuarioResponsable.isBlank()) ? usuarioResponsable : "SISTEMA";
        this.justificacion = justificacion != null ? justificacion : "";
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Donacion getDonacion() {
        return donacion;
    }

    public void setDonacion(Donacion donacion) {
        this.donacion = donacion;
    }

    public LocalDateTime getFechaHora() {
        return fechaHora;
    }

    public void setFechaHora(LocalDateTime fechaHora) {
        this.fechaHora = fechaHora;
    }

    public EstadoDonacion getEstadoAnterior() {
        return estadoAnterior;
    }

    public void setEstadoAnterior(EstadoDonacion estadoAnterior) {
        this.estadoAnterior = estadoAnterior;
    }

    public EstadoDonacion getNuevoEstado() {
        return nuevoEstado;
    }

    public void setNuevoEstado(EstadoDonacion nuevoEstado) {
        this.nuevoEstado = nuevoEstado;
    }

    public String getUsuarioResponsable() {
        return usuarioResponsable;
    }

    public void setUsuarioResponsable(String usuarioResponsable) {
        this.usuarioResponsable = usuarioResponsable;
    }

    public String getJustificacion() {
        return justificacion;
    }

    public void setJustificacion(String justificacion) {
        this.justificacion = justificacion;
    }
}
