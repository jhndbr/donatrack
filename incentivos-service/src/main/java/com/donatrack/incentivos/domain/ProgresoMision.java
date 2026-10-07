package com.donatrack.incentivos.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "progresos_mision")
public class ProgresoMision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "id_donante", nullable = false)
    private Long idDonante;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "mision_id", nullable = false)
    private Mision mision;

    @Column(name = "progreso_actual", nullable = false)
    private double progresoActual = 0.0;

    @Column(nullable = false)
    private boolean completada = false;

    @Column(name = "fecha_completada")
    private LocalDateTime fechaCompletada;

    public ProgresoMision() {}

    public ProgresoMision(Long idDonante, Mision mision) {
        this.idDonante = idDonante;
        this.mision = mision;
        this.progresoActual = 0.0;
        this.completada = false;
    }

    public void agregarProgreso(double delta) {
        if (!this.completada) {
            this.progresoActual += delta;
            if (this.progresoActual >= mision.getObjetivo()) {
                this.completada = true;
                this.fechaCompletada = LocalDateTime.now();
            }
        }
    }

    public void resetearProgreso() {
        if (!this.completada) {
            this.progresoActual = 0.0;
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getIdDonante() { return idDonante; }
    public void setIdDonante(Long idDonante) { this.idDonante = idDonante; }

    public Mision getMision() { return mision; }
    public void setMision(Mision mision) { this.mision = mision; }

    public double getProgresoActual() { return progresoActual; }
    public void setProgresoActual(double progresoActual) { this.progresoActual = progresoActual; }

    public boolean isCompletada() { return completada; }
    public void setCompletada(boolean completada) { this.completada = completada; }

    public LocalDateTime getFechaCompletada() { return fechaCompletada; }
    public void setFechaCompletada(LocalDateTime fechaCompletada) { this.fechaCompletada = fechaCompletada; }
}
