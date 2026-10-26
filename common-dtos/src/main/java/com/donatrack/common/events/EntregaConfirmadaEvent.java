package com.donatrack.common.events;

import com.donatrack.common.enums.CanalNotificacion;
import java.io.Serializable;
import java.time.LocalDateTime;

public class EntregaConfirmadaEvent implements Serializable {
    private String idEntrega;
    private Long idDonacion;
    private Long idDonante;
    private Long idEntidadBeneficiaria;
    private String patenteCamion;
    private String comprobanteUrl;
    private String emailDonante;
    private String emailEntidad;
    private CanalNotificacion canalPreferidoDonante;
    private String contactoPreferidoDonante;
    private LocalDateTime timestamp;

    public EntregaConfirmadaEvent() {
        this.timestamp = LocalDateTime.now();
        this.canalPreferidoDonante = CanalNotificacion.EMAIL;
    }

    public EntregaConfirmadaEvent(String idEntrega, Long idDonacion, Long idEntidadBeneficiaria,
                                  String patenteCamion, String comprobanteUrl,
                                  String emailDonante, String emailEntidad) {
        this(idEntrega, idDonacion, null, idEntidadBeneficiaria, patenteCamion, comprobanteUrl, emailDonante, emailEntidad);
    }

    public EntregaConfirmadaEvent(String idEntrega, Long idDonacion, Long idDonante, Long idEntidadBeneficiaria,
                                  String patenteCamion, String comprobanteUrl,
                                  String emailDonante, String emailEntidad) {
        this(idEntrega, idDonacion, idDonante, idEntidadBeneficiaria, patenteCamion, comprobanteUrl,
             emailDonante, emailEntidad, CanalNotificacion.EMAIL, emailDonante);
    }

    public EntregaConfirmadaEvent(String idEntrega, Long idDonacion, Long idEntidadBeneficiaria,
                                  String patenteCamion, String comprobanteUrl,
                                  String emailDonante, String emailEntidad,
                                  CanalNotificacion canalPreferidoDonante, String contactoPreferidoDonante) {
        this(idEntrega, idDonacion, null, idEntidadBeneficiaria, patenteCamion, comprobanteUrl,
             emailDonante, emailEntidad, canalPreferidoDonante, contactoPreferidoDonante);
    }

    public EntregaConfirmadaEvent(String idEntrega, Long idDonacion, Long idDonante, Long idEntidadBeneficiaria,
                                  String patenteCamion, String comprobanteUrl,
                                  String emailDonante, String emailEntidad,
                                  CanalNotificacion canalPreferidoDonante, String contactoPreferidoDonante) {
        this.idEntrega = idEntrega;
        this.idDonacion = idDonacion;
        this.idDonante = idDonante;
        this.idEntidadBeneficiaria = idEntidadBeneficiaria;
        this.patenteCamion = patenteCamion;
        this.comprobanteUrl = comprobanteUrl;
        this.emailDonante = emailDonante;
        this.emailEntidad = emailEntidad;
        this.canalPreferidoDonante = canalPreferidoDonante != null ? canalPreferidoDonante : CanalNotificacion.EMAIL;
        this.contactoPreferidoDonante = (contactoPreferidoDonante != null && !contactoPreferidoDonante.isBlank()) ? contactoPreferidoDonante : emailDonante;
        this.timestamp = LocalDateTime.now();
    }

    public String getIdEntrega() { return idEntrega; }
    public void setIdEntrega(String idEntrega) { this.idEntrega = idEntrega; }

    public Long getIdDonacion() { return idDonacion; }
    public void setIdDonacion(Long idDonacion) { this.idDonacion = idDonacion; }

    public Long getIdDonante() { return idDonante; }
    public void setIdDonante(Long idDonante) { this.idDonante = idDonante; }

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

    public CanalNotificacion getCanalPreferidoDonante() { return canalPreferidoDonante; }
    public void setCanalPreferidoDonante(CanalNotificacion canalPreferidoDonante) { this.canalPreferidoDonante = canalPreferidoDonante; }

    public String getContactoPreferidoDonante() { return contactoPreferidoDonante; }
    public void setContactoPreferidoDonante(String contactoPreferidoDonante) { this.contactoPreferidoDonante = contactoPreferidoDonante; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
