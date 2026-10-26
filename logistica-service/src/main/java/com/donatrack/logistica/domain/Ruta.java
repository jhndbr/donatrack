package com.donatrack.logistica.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "rutas")
public class Ruta {

    public enum EstadoRuta {
        PLANIFICADA,
        EN_CURSO,
        FINALIZADA,
        CANCELADA
    }

    @Id
    private String id;

    @Indexed
    private String loteId;

    private String idCamion;
    private String patenteCamion;
    private LocalDate fechaPlanificada;
    private EstadoRuta estado;
    private Double distanciaKmTotal;
    private Integer tiempoEstimadoMinutos;
    private LocalDateTime fechaCreacion = LocalDateTime.now();
    private LocalDateTime fechaInicio;
    private LocalDateTime fechaFin;
    private String motivoCancelacion;
    private List<Parada> paradas = new ArrayList<>();

    public Ruta() {
        this.estado = EstadoRuta.PLANIFICADA;
        this.fechaPlanificada = LocalDate.now();
        this.fechaCreacion = LocalDateTime.now();
    }

    public Ruta(String idCamion, String patenteCamion, LocalDate fechaPlanificada) {
        this();
        this.idCamion = idCamion;
        this.patenteCamion = patenteCamion;
        this.fechaPlanificada = fechaPlanificada;
    }

    public Ruta(String loteId, String idCamion, String patenteCamion, LocalDate fechaPlanificada) {
        this(idCamion, patenteCamion, fechaPlanificada);
        this.loteId = loteId;
    }

    public void iniciarRuta() {
        if (this.estado != EstadoRuta.PLANIFICADA) {
            throw new IllegalStateException("Solo se puede iniciar una ruta en estado PLANIFICADA. Estado actual: " + this.estado);
        }
        this.estado = EstadoRuta.EN_CURSO;
        this.fechaInicio = LocalDateTime.now();
    }

    public void finalizarRuta() {
        if (this.estado != EstadoRuta.EN_CURSO) {
            throw new IllegalStateException("Solo se puede finalizar una ruta en estado EN_CURSO. Estado actual: " + this.estado);
        }
        this.estado = EstadoRuta.FINALIZADA;
        this.fechaFin = LocalDateTime.now();
    }

    public void cancelarRuta(String motivo) {
        if (this.estado == EstadoRuta.FINALIZADA) {
            throw new IllegalStateException("No se puede cancelar una ruta ya FINALIZADA");
        }
        this.estado = EstadoRuta.CANCELADA;
        this.motivoCancelacion = motivo;
    }

    public void agregarParada(Parada parada) {
        this.paradas.add(parada);
    }

    public void completarParada(int orden) {
        this.paradas.stream()
                .filter(p -> p.getOrden() == orden)
                .findFirst()
                .ifPresent(Parada::marcarCompletada);
    }

    // --- Getters y Setters ---

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getLoteId() { return loteId; }
    public void setLoteId(String loteId) { this.loteId = loteId; }

    public String getIdCamion() { return idCamion; }
    public void setIdCamion(String idCamion) { this.idCamion = idCamion; }

    public String getPatenteCamion() { return patenteCamion; }
    public void setPatenteCamion(String patenteCamion) { this.patenteCamion = patenteCamion; }

    public LocalDate getFechaPlanificada() { return fechaPlanificada; }
    public void setFechaPlanificada(LocalDate fechaPlanificada) { this.fechaPlanificada = fechaPlanificada; }

    public EstadoRuta getEstado() { return estado; }
    public void setEstado(EstadoRuta estado) { this.estado = estado; }

    public Double getDistanciaKmTotal() { return distanciaKmTotal; }
    public void setDistanciaKmTotal(Double distanciaKmTotal) { this.distanciaKmTotal = distanciaKmTotal; }

    public Integer getTiempoEstimadoMinutos() { return tiempoEstimadoMinutos; }
    public void setTiempoEstimadoMinutos(Integer tiempoEstimadoMinutos) { this.tiempoEstimadoMinutos = tiempoEstimadoMinutos; }

    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }

    public LocalDateTime getFechaInicio() { return fechaInicio; }
    public void setFechaInicio(LocalDateTime fechaInicio) { this.fechaInicio = fechaInicio; }

    public LocalDateTime getFechaFin() { return fechaFin; }
    public void setFechaFin(LocalDateTime fechaFin) { this.fechaFin = fechaFin; }

    public String getMotivoCancelacion() { return motivoCancelacion; }
    public void setMotivoCancelacion(String motivoCancelacion) { this.motivoCancelacion = motivoCancelacion; }

    public List<Parada> getParadas() { return paradas; }
    public void setParadas(List<Parada> paradas) { this.paradas = paradas; }
}
