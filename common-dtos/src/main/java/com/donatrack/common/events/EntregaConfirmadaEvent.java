package com.donatrack.common.events;

import java.time.LocalDateTime;

public class EntregaConfirmadaEvent {
    private String idEntrega;
    private Long idDonacion;
    private Long idEntidadBeneficiaria;
    private String patenteCamion;
    private String comprobanteUrl;
    private String emailDonante;
    private String emailEntidad;
    private LocalDateTime timestamp;

    public EntregaConfirmadaEvent() {
        this.timestamp = LocalDateTime.now();
    }

    public EntregaConfirmadaEvent(String idEntrega, Long idDonacion, Long idEntidadBeneficiaria,
                                  String patenteCamion, String comprobanteUrl,
                                  String emailDonante, String emailEntidad) {
        this.idEntrega = idEntrega;
        this.idDonacion = idDonacion;
        this.idEntidadBeneficiaria = idEntidadBeneficiaria;
        this.patenteCamion = patenteCamion;
        this.comprobanteUrl = comprobanteUrl;
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

    public String getPatenteCamion() { return patenteCamion; }
    public void setPatenteCamion(String patenteCamion) { this.patenteCamion = patenteCamion; }

    public String getComprobanteUrl() { return comprobanteUrl; }
    public void setComprobanteUrl(String comprobanteUrl) { this.comprobanteUrl = comprobanteUrl; }

    public String getEmailDonante() { return emailDonante; }
    public void setEmailDonante(String emailDonante) { this.emailDonante = emailDonante; }

    public String getEmailEntidad() { return emailEntidad; }
    public void setEmailEntidad(String emailEntidad) { this.emailEntidad = emailEntidad; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
