package com.donatrack.common.events;

import com.donatrack.common.enums.CanalNotificacion;
import java.io.Serializable;
import java.time.LocalDateTime;

public class DonanteRegistradoEvent implements Serializable {
    private Long idDonante;
    private String nombreDonante;
    private String emailDonante;
    private CanalNotificacion canalPreferido;
    private String contactoPreferido;
    private String usernameGenerado;
    private String passwordTemporal;
    private String origenRegistro;
    private LocalDateTime timestamp;

    public DonanteRegistradoEvent() {
        this.timestamp = LocalDateTime.now();
        this.canalPreferido = CanalNotificacion.EMAIL;
    }

    public DonanteRegistradoEvent(Long idDonante, String nombreDonante, String emailDonante,
                                  CanalNotificacion canalPreferido, String contactoPreferido,
                                  String usernameGenerado, String passwordTemporal,
                                  String origenRegistro) {
        this.idDonante = idDonante;
        this.nombreDonante = nombreDonante;
        this.emailDonante = emailDonante;
        this.canalPreferido = canalPreferido != null ? canalPreferido : CanalNotificacion.EMAIL;
        this.contactoPreferido = contactoPreferido != null ? contactoPreferido : emailDonante;
        this.usernameGenerado = usernameGenerado;
        this.passwordTemporal = passwordTemporal;
        this.origenRegistro = origenRegistro;
        this.timestamp = LocalDateTime.now();
    }

    public Long getIdDonante() { return idDonante; }
    public void setIdDonante(Long idDonante) { this.idDonante = idDonante; }

    public String getNombreDonante() { return nombreDonante; }
    public void setNombreDonante(String nombreDonante) { this.nombreDonante = nombreDonante; }

    public String getEmailDonante() { return emailDonante; }
    public void setEmailDonante(String emailDonante) { this.emailDonante = emailDonante; }

    public CanalNotificacion getCanalPreferido() { return canalPreferido; }
    public void setCanalPreferido(CanalNotificacion canalPreferido) { this.canalPreferido = canalPreferido; }

    public String getContactoPreferido() { return contactoPreferido; }
    public void setContactoPreferido(String contactoPreferido) { this.contactoPreferido = contactoPreferido; }

    public String getUsernameGenerado() { return usernameGenerado; }
    public void setUsernameGenerado(String usernameGenerado) { this.usernameGenerado = usernameGenerado; }

    public String getPasswordTemporal() { return passwordTemporal; }
    public void setPasswordTemporal(String passwordTemporal) { this.passwordTemporal = passwordTemporal; }

    public String getOrigenRegistro() { return origenRegistro; }
    public void setOrigenRegistro(String origenRegistro) { this.origenRegistro = origenRegistro; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
