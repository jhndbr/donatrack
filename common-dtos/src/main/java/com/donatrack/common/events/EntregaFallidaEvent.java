package com.donatrack.common.events;

import java.time.LocalDateTime;

public class EntregaFallidaEvent {
    private String idEntrega;
    private Long idDonacion;
    private Long idEntidadBeneficiaria;
    private String motivoFalla;
    private boolean replanificable;
    private String emailDonante;
    private String emailEntidad;
    private LocalDateTime timestamp;

    public EntregaFallidaEvent() {
        this.timestamp = LocalDateTime.now();
    }

    public EntregaFallidaEvent(String idEntrega, Long idDonacion, Long idEntidadBeneficiaria,
                               String motivoFalla, boolean replanificable,
                               String emailDonante, String emailEntidad) {
        this.idEntrega = idEntrega;
        this.idDonacion = idDonacion;
        this.idEntidadBeneficiaria = idEntidadBeneficiaria;
        this.motivoFalla = motivoFalla;
        this.replanificable = replanificable;
        this.emailDonante = emailDonante;
        this.emailEntidad = emailEntidad;
        this.timestamp = LocalDateTime.now();
    }

    public String getIdEntrega() { return idEntrega; }
    public void setIdEntrega(String idEntrega) { this.idEntrega = idEntrega; }

    public Long getIdDonacion() { return idDonacion; }
    public void setIdDonacion(Long idDonacion) { this.idDonacion = idDonacion; }

    public Long getIdEntidadBeneficiaria() { return idEntidadBeneficiaria; }
    public void setIdEntidadBeneficiaria(Long idEntidadBeneficiaria) { this.idEntidadBeneficiaria = idEntidadBeneficiaria; }

    public String getMotivoFalla() { return motivoFalla; }
    public void setMotivoFalla(String motivoFalla) { this.motivoFalla = motivoFalla; }

    public boolean isReplanificable() { return replanificable; }
    public void setReplanificable(boolean replanificable) { this.replanificable = replanificable; }

    public String getEmailDonante() { return emailDonante; }
    public void setEmailDonante(String emailDonante) { this.emailDonante = emailDonante; }

    public String getEmailEntidad() { return emailEntidad; }
    public void setEmailEntidad(String emailEntidad) { this.emailEntidad = emailEntidad; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
