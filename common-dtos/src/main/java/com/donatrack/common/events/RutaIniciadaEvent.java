package com.donatrack.common.events;

import java.time.LocalDateTime;
import java.util.List;

public class RutaIniciadaEvent {
    private String idRuta;
    private String patenteCamion;
    private List<Long> idsDonaciones;
    private List<String> emailsNotificar;
    private String linkSeguimiento;
    private LocalDateTime timestamp;

    public RutaIniciadaEvent() {
        this.timestamp = LocalDateTime.now();
    }

    public RutaIniciadaEvent(String idRuta, String patenteCamion, List<Long> idsDonaciones,
                              List<String> emailsNotificar, String linkSeguimiento) {
        this.idRuta = idRuta;
        this.patenteCamion = patenteCamion;
        this.idsDonaciones = idsDonaciones;
        this.emailsNotificar = emailsNotificar;
        this.linkSeguimiento = linkSeguimiento;
        this.timestamp = LocalDateTime.now();
    }

    public String getIdRuta() { return idRuta; }
    public void setIdRuta(String idRuta) { this.idRuta = idRuta; }

    public String getPatenteCamion() { return patenteCamion; }
    public void setPatenteCamion(String patenteCamion) { this.patenteCamion = patenteCamion; }

    public List<Long> getIdsDonaciones() { return idsDonaciones; }
    public void setIdsDonaciones(List<Long> idsDonaciones) { this.idsDonaciones = idsDonaciones; }

    public List<String> getEmailsNotificar() { return emailsNotificar; }
    public void setEmailsNotificar(List<String> emailsNotificar) { this.emailsNotificar = emailsNotificar; }

    public String getLinkSeguimiento() { return linkSeguimiento; }
    public void setLinkSeguimiento(String linkSeguimiento) { this.linkSeguimiento = linkSeguimiento; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
