package com.donatrack.notificaciones.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Entidad para control de idempotencia y deduplicación de eventos AMQP (FT-06).
 */
@Entity
@Table(name = "registros_eventos_notificaciones")
public class RegistroEventoNotificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "clave_evento", nullable = false, unique = true, length = 150)
    private String claveEvento;

    @Column(name = "tipo_evento", nullable = false, length = 80)
    private String tipoEvento;

    @Column(name = "fecha_procesado", nullable = false)
    private LocalDateTime fechaProcesado;

    public RegistroEventoNotificacion() {
        this.fechaProcesado = LocalDateTime.now();
    }

    public RegistroEventoNotificacion(String claveEvento, String tipoEvento) {
        this.claveEvento = claveEvento;
        this.tipoEvento = tipoEvento;
        this.fechaProcesado = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getClaveEvento() { return claveEvento; }
    public void setClaveEvento(String claveEvento) { this.claveEvento = claveEvento; }

    public String getTipoEvento() { return tipoEvento; }
    public void setTipoEvento(String tipoEvento) { this.tipoEvento = tipoEvento; }

    public LocalDateTime getFechaProcesado() { return fechaProcesado; }
    public void setFechaProcesado(LocalDateTime fechaProcesado) { this.fechaProcesado = fechaProcesado; }
}
