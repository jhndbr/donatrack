package com.donatrack.notificaciones.domain;

import com.donatrack.common.enums.CanalNotificacion;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Entidad de auditoría y trazabilidad para despachos de notificaciones (CU-56, FT-07).
 */
@Entity
@Table(name = "auditorias_notificaciones")
public class AuditoriaNotificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "clave_evento", length = 150)
    private String claveEvento;

    @Column(nullable = false, length = 150)
    private String destinatario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CanalNotificacion canal;

    @Column(nullable = false, length = 250)
    private String asunto;

    @Column(columnDefinition = "TEXT")
    private String mensaje;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EstadoNotificacion estado;

    @Column(name = "error_detalle", columnDefinition = "TEXT")
    private String errorDetalle;

    private int intentos = 1;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean leida = false;

    @Column(name = "fecha_lectura")
    private LocalDateTime fechaLectura;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    public AuditoriaNotificacion() {
        this.timestamp = LocalDateTime.now();
    }

    public AuditoriaNotificacion(String claveEvento, String destinatario, CanalNotificacion canal,
                                 String asunto, String mensaje, EstadoNotificacion estado, String errorDetalle) {
        this.claveEvento = claveEvento;
        this.destinatario = destinatario;
        this.canal = canal;
        this.asunto = asunto;
        this.mensaje = mensaje;
        this.estado = estado;
        this.errorDetalle = errorDetalle;
        this.intentos = 1;
        this.timestamp = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getClaveEvento() { return claveEvento; }
    public void setClaveEvento(String claveEvento) { this.claveEvento = claveEvento; }

    public String getDestinatario() { return destinatario; }
    public void setDestinatario(String destinatario) { this.destinatario = destinatario; }

    public CanalNotificacion getCanal() { return canal; }
    public void setCanal(CanalNotificacion canal) { this.canal = canal; }

    public String getAsunto() { return asunto; }
    public void setAsunto(String asunto) { this.asunto = asunto; }

    public String getMensaje() { return mensaje; }
    public void setMensaje(String mensaje) { this.mensaje = mensaje; }

    public EstadoNotificacion getEstado() { return estado; }
    public void setEstado(EstadoNotificacion estado) { this.estado = estado; }

    public String getErrorDetalle() { return errorDetalle; }
    public void setErrorDetalle(String errorDetalle) { this.errorDetalle = errorDetalle; }

    public int getIntentos() { return intentos; }
    public void setIntentos(int intentos) { this.intentos = intentos; }

    public boolean isLeida() { return leida; }
    public void setLeida(boolean leida) { this.leida = leida; }

    public LocalDateTime getFechaLectura() { return fechaLectura; }
    public void setFechaLectura(LocalDateTime fechaLectura) { this.fechaLectura = fechaLectura; }

    public void marcarComoLeida() {
        this.leida = true;
        this.fechaLectura = LocalDateTime.now();
    }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
