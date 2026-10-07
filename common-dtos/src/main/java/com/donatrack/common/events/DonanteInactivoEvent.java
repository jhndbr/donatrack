package com.donatrack.common.events;

import java.time.LocalDateTime;

public class DonanteInactivoEvent {
    private Long idDonante;
    private String nombre;
    private String email;
    private long diasInactivo;
    private LocalDateTime timestamp;

    public DonanteInactivoEvent() {
        this.timestamp = LocalDateTime.now();
    }

    public DonanteInactivoEvent(Long idDonante, String nombre, String email, long diasInactivo) {
        this.idDonante = idDonante;
        this.nombre = nombre;
        this.email = email;
        this.diasInactivo = diasInactivo;
        this.timestamp = LocalDateTime.now();
    }

    public Long getIdDonante() { return idDonante; }
    public void setIdDonante(Long idDonante) { this.idDonante = idDonante; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public long getDiasInactivo() { return diasInactivo; }
    public void setDiasInactivo(long diasInactivo) { this.diasInactivo = diasInactivo; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
