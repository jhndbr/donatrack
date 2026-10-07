package com.donatrack.logistica.domain;

import org.springframework.data.annotation.Id;
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
        FINALIZADA
    }

    @Id
    private String id;
    private String idCamion;
    private String patenteCamion;
    private LocalDate fechaPlanificada;
    private EstadoRuta estado;
    private LocalDateTime fechaInicio;
    private LocalDateTime fechaFin;
    private List<Parada> paradas = new ArrayList<>();

    public Ruta() {
        this.estado = EstadoRuta.PLANIFICADA;
        this.fechaPlanificada = LocalDate.now();
    }

    public Ruta(String idCamion, String patenteCamion, LocalDate fechaPlanificada) {
        this();
        this.idCamion = idCamion;
        this.patenteCamion = patenteCamion;
        this.fechaPlanificada = fechaPlanificada;
    }

    public void iniciarRuta() {
        if (this.estado != EstadoRuta.PLANIFICADA) {
            throw new IllegalStateException("Solo se puede iniciar una ruta en estado PLANIFICADA");
        }
        this.estado = EstadoRuta.EN_CURSO;
        this.fechaInicio = LocalDateTime.now();
    }

    public void finalizarRuta() {
        this.estado = EstadoRuta.FINALIZADA;
        this.fechaFin = LocalDateTime.now();
    }

    public void agregarParada(Parada parada) {
        this.paradas.add(parada);
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getIdCamion() { return idCamion; }
    public void setIdCamion(String idCamion) { this.idCamion = idCamion; }

    public String getPatenteCamion() { return patenteCamion; }
    public void setPatenteCamion(String patenteCamion) { this.patenteCamion = patenteCamion; }

    public LocalDate getFechaPlanificada() { return fechaPlanificada; }
    public void setFechaPlanificada(LocalDate fechaPlanificada) { this.fechaPlanificada = fechaPlanificada; }

    public EstadoRuta getEstado() { return estado; }
    public void setEstado(EstadoRuta estado) { this.estado = estado; }

    public LocalDateTime getFechaInicio() { return fechaInicio; }
    public void setFechaInicio(LocalDateTime fechaInicio) { this.fechaInicio = fechaInicio; }

    public LocalDateTime getFechaFin() { return fechaFin; }
    public void setFechaFin(LocalDateTime fechaFin) { this.fechaFin = fechaFin; }

    public List<Parada> getParadas() { return paradas; }
    public void setParadas(List<Parada> paradas) { this.paradas = paradas; }
}
