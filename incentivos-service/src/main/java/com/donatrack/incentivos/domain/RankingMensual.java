package com.donatrack.incentivos.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "rankings_mensuales")
public class RankingMensual {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private int anio;

    @Column(nullable = false)
    private int mes;

    @Column(nullable = false)
    private int posicion;

    @Column(name = "id_donante", nullable = false)
    private Long idDonante;

    @Column(name = "nombre_donante", nullable = false)
    private String nombreDonante;

    @Column(name = "misiones_completadas_mes", nullable = false)
    private int misionesCompletadasMes;

    @Column(name = "fecha_calculo", nullable = false)
    private LocalDateTime fechaCalculo;

    public RankingMensual() {
        this.fechaCalculo = LocalDateTime.now();
    }

    public RankingMensual(int anio, int mes, int posicion, Long idDonante, String nombreDonante, int misionesCompletadasMes) {
        this();
        this.anio = anio;
        this.mes = mes;
        this.posicion = posicion;
        this.idDonante = idDonante;
        this.nombreDonante = nombreDonante;
        this.misionesCompletadasMes = misionesCompletadasMes;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public int getAnio() { return anio; }
    public void setAnio(int anio) { this.anio = anio; }

    public int getMes() { return mes; }
    public void setMes(int mes) { this.mes = mes; }

    public int getPosicion() { return posicion; }
    public void setPosicion(int posicion) { this.posicion = posicion; }

    public Long getIdDonante() { return idDonante; }
    public void setIdDonante(Long idDonante) { this.idDonante = idDonante; }

    public String getNombreDonante() { return nombreDonante; }
    public void setNombreDonante(String nombreDonante) { this.nombreDonante = nombreDonante; }

    public int getMisionesCompletadasMes() { return misionesCompletadasMes; }
    public void setMisionesCompletadasMes(int misionesCompletadasMes) { this.misionesCompletadasMes = misionesCompletadasMes; }

    public LocalDateTime getFechaCalculo() { return fechaCalculo; }
    public void setFechaCalculo(LocalDateTime fechaCalculo) { this.fechaCalculo = fechaCalculo; }
}
