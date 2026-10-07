package com.donatrack.common.events;

import java.time.LocalDateTime;

public class MisionCumplidaEvent {
    private Long idDonante;
    private String nombreDonante;
    private String emailDonante;
    private String tipoMision;
    private String nombreInsignia;
    private String descripcionInsignia;
    private String categoriaActual;
    private LocalDateTime timestamp;

    public MisionCumplidaEvent() {
        this.timestamp = LocalDateTime.now();
    }

    public MisionCumplidaEvent(Long idDonante, String nombreDonante, String emailDonante,
                                String tipoMision, String nombreInsignia,
                                String descripcionInsignia, String categoriaActual) {
        this.idDonante = idDonante;
        this.nombreDonante = nombreDonante;
        this.emailDonante = emailDonante;
        this.tipoMision = tipoMision;
        this.nombreInsignia = nombreInsignia;
        this.descripcionInsignia = descripcionInsignia;
        this.categoriaActual = categoriaActual;
        this.timestamp = LocalDateTime.now();
    }

    public Long getIdDonante() { return idDonante; }
    public void setIdDonante(Long idDonante) { this.idDonante = idDonante; }

    public String getNombreDonante() { return nombreDonante; }
    public void setNombreDonante(String nombreDonante) { this.nombreDonante = nombreDonante; }

    public String getEmailDonante() { return emailDonante; }
    public void setEmailDonante(String emailDonante) { this.emailDonante = emailDonante; }

    public String getTipoMision() { return tipoMision; }
    public void setTipoMision(String tipoMision) { this.tipoMision = tipoMision; }

    public String getNombreInsignia() { return nombreInsignia; }
    public void setNombreInsignia(String nombreInsignia) { this.nombreInsignia = nombreInsignia; }

    public String getDescripcionInsignia() { return descripcionInsignia; }
    public void setDescripcionInsignia(String descripcionInsignia) { this.descripcionInsignia = descripcionInsignia; }

    public String getCategoriaActual() { return categoriaActual; }
    public void setCategoriaActual(String categoriaActual) { this.categoriaActual = categoriaActual; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
