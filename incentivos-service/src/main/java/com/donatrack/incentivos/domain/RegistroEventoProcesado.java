package com.donatrack.incentivos.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "registros_eventos_procesados", indexes = {
        @Index(name = "idx_clave_evento", columnList = "clave_evento", unique = true)
})
public class RegistroEventoProcesado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "clave_evento", nullable = false, unique = true)
    private String claveEvento;

    @Column(name = "tipo_evento", nullable = false)
    private String tipoEvento;

    @Column(name = "fecha_procesamiento", nullable = false)
    private LocalDateTime fechaProcesamiento;

    public RegistroEventoProcesado() {
        this.fechaProcesamiento = LocalDateTime.now();
    }

    public RegistroEventoProcesado(String claveEvento, String tipoEvento) {
        this.claveEvento = claveEvento;
        this.tipoEvento = tipoEvento;
        this.fechaProcesamiento = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getClaveEvento() { return claveEvento; }
    public void setClaveEvento(String claveEvento) { this.claveEvento = claveEvento; }

    public String getTipoEvento() { return tipoEvento; }
    public void setTipoEvento(String tipoEvento) { this.tipoEvento = tipoEvento; }

    public LocalDateTime getFechaProcesamiento() { return fechaProcesamiento; }
    public void setFechaProcesamiento(LocalDateTime fechaProcesamiento) { this.fechaProcesamiento = fechaProcesamiento; }
}
