package com.donatrack.common.events;

import com.donatrack.common.enums.CanalNotificacion;
import java.io.Serializable;
import java.time.LocalDateTime;

public class DonacionRegistradaEvent implements Serializable {
    private Long idDonacion;
    private Long idDonante;
    private String nombreDonante;
    private String emailDonante;
    private CanalNotificacion canalPreferido = CanalNotificacion.EMAIL;
    private String contactoPreferido;
    private CanalNotificacion canalPreferidoDonante = CanalNotificacion.EMAIL;
    private String contactoPreferidoDonante;
    private String categoria;
    private String subcategoria;
    private double cantidad;
    private String unidadMedida;
    private LocalDateTime timestamp;

    public DonacionRegistradaEvent() {
        this.timestamp = LocalDateTime.now();
        this.canalPreferido = CanalNotificacion.EMAIL;
        this.canalPreferidoDonante = CanalNotificacion.EMAIL;
    }

    public DonacionRegistradaEvent(Long idDonacion, Long idDonante, String nombreDonante, String emailDonante,
                                  CanalNotificacion canalPreferido, String contactoPreferido,
                                  String categoria, String subcategoria, double cantidad, String unidadMedida) {
        this.idDonacion = idDonacion;
        this.idDonante = idDonante;
        this.nombreDonante = nombreDonante;
        this.emailDonante = emailDonante;
        this.canalPreferido = canalPreferido != null ? canalPreferido : CanalNotificacion.EMAIL;
        this.contactoPreferido = contactoPreferido != null ? contactoPreferido : emailDonante;
        this.canalPreferidoDonante = this.canalPreferido;
        this.contactoPreferidoDonante = this.contactoPreferido;
        this.categoria = categoria;
        this.subcategoria = subcategoria;
        this.cantidad = cantidad;
        this.unidadMedida = unidadMedida;
        this.timestamp = LocalDateTime.now();
    }

    public Long getIdDonacion() { return idDonacion; }
    public void setIdDonacion(Long idDonacion) { this.idDonacion = idDonacion; }

    public Long getIdDonante() { return idDonante; }
    public void setIdDonante(Long idDonante) { this.idDonante = idDonante; }

    public String getNombreDonante() { return nombreDonante; }
    public void setNombreDonante(String nombreDonante) { this.nombreDonante = nombreDonante; }

    public String getEmailDonante() { return emailDonante; }
    public void setEmailDonante(String emailDonante) { this.emailDonante = emailDonante; }

    public CanalNotificacion getCanalPreferido() { return canalPreferido != null ? canalPreferido : canalPreferidoDonante; }
    public void setCanalPreferido(CanalNotificacion canalPreferido) {
        this.canalPreferido = canalPreferido;
        this.canalPreferidoDonante = canalPreferido;
    }

    public String getContactoPreferido() { return contactoPreferido != null ? contactoPreferido : contactoPreferidoDonante; }
    public void setContactoPreferido(String contactoPreferido) {
        this.contactoPreferido = contactoPreferido;
        this.contactoPreferidoDonante = contactoPreferido;
    }

    public CanalNotificacion getCanalPreferidoDonante() { return canalPreferidoDonante != null ? canalPreferidoDonante : canalPreferido; }
    public void setCanalPreferidoDonante(CanalNotificacion canalPreferidoDonante) {
        this.canalPreferidoDonante = canalPreferidoDonante;
        this.canalPreferido = canalPreferidoDonante;
    }

    public String getContactoPreferidoDonante() { return contactoPreferidoDonante != null ? contactoPreferidoDonante : contactoPreferido; }
    public void setContactoPreferidoDonante(String contactoPreferidoDonante) {
        this.contactoPreferidoDonante = contactoPreferidoDonante;
        this.contactoPreferido = contactoPreferidoDonante;
    }

    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }

    public String getSubcategoria() { return subcategoria; }
    public void setSubcategoria(String subcategoria) { this.subcategoria = subcategoria; }

    public double getCantidad() { return cantidad; }
    public void setCantidad(double cantidad) { this.cantidad = cantidad; }

    public String getUnidadMedida() { return unidadMedida; }
    public void setUnidadMedida(String unidadMedida) { this.unidadMedida = unidadMedida; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
