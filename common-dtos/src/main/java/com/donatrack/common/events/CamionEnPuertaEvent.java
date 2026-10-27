package com.donatrack.common.events;

import java.io.Serializable;
import java.time.LocalDateTime;

public class CamionEnPuertaEvent implements Serializable {
    private String idEntrega;
    private Long idDonacion;
    private Long idEntidadBeneficiaria;
    private String nombreEntidad;
    private String emailEntidad;
    private String patenteCamion;
    private String observaciones;
    private String linkConfirmacion;
    private LocalDateTime timestamp;

    public CamionEnPuertaEvent() {
        this.timestamp = LocalDateTime.now();
    }

    public CamionEnPuertaEvent(String idEntrega, Long idDonacion, Long idEntidadBeneficiaria,
                               String nombreEntidad, String emailEntidad, String patenteCamion,
                               String observaciones, String linkConfirmacion) {
        this.idEntrega = idEntrega;
        this.idDonacion = idDonacion;
        this.idEntidadBeneficiaria = idEntidadBeneficiaria;
        this.nombreEntidad = nombreEntidad;
        this.emailEntidad = emailEntidad;
        this.patenteCamion = patenteCamion;
        this.observaciones = observaciones;
        this.linkConfirmacion = linkConfirmacion;
        this.timestamp = LocalDateTime.now();
    }

    public String getIdEntrega() { return idEntrega; }
    public void setIdEntrega(String idEntrega) { this.idEntrega = idEntrega; }

    public Long getIdDonacion() { return idDonacion; }
    public void setIdDonacion(Long idDonacion) { this.idDonacion = idDonacion; }

    public Long getIdEntidadBeneficiaria() { return idEntidadBeneficiaria; }
    public void setIdEntidadBeneficiaria(Long idEntidadBeneficiaria) { this.idEntidadBeneficiaria = idEntidadBeneficiaria; }

    public String getNombreEntidad() { return nombreEntidad; }
    public void setNombreEntidad(String nombreEntidad) { this.nombreEntidad = nombreEntidad; }

    public String getEmailEntidad() { return emailEntidad; }
    public void setEmailEntidad(String emailEntidad) { this.emailEntidad = emailEntidad; }

    public String getPatenteCamion() { return patenteCamion; }
    public void setPatenteCamion(String patenteCamion) { this.patenteCamion = patenteCamion; }

    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }

    public String getLinkConfirmacion() { return linkConfirmacion; }
    public void setLinkConfirmacion(String linkConfirmacion) { this.linkConfirmacion = linkConfirmacion; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
