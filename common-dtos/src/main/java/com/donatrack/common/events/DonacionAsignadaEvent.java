package com.donatrack.common.events;

import com.donatrack.common.enums.CanalNotificacion;
import java.io.Serializable;
import java.time.LocalDateTime;

public class DonacionAsignadaEvent implements Serializable {
    private Long idDonacion;
    private String categoria;
    private String subcategoria;
    private Double cantidad;
    private String unidadMedida;
    private Long idEntidadBeneficiaria;
    private String nombreEntidad;
    private String emailEntidad;
    private Long idDonante;
    private String emailDonante;
    private String nombreDonante;
    private CanalNotificacion canalPreferidoDonante;
    private String contactoPreferidoDonante;
    private LocalDateTime timestamp;

    public DonacionAsignadaEvent() {
        this.timestamp = LocalDateTime.now();
        this.canalPreferidoDonante = CanalNotificacion.EMAIL;
    }

    public DonacionAsignadaEvent(Long idDonacion, String subcategoria, Double cantidad, String unidadMedida,
                                 Long idEntidadBeneficiaria, String nombreEntidad, String emailEntidad,
                                 Long idDonante, String emailDonante, String nombreDonante) {
        this(idDonacion, null, subcategoria, cantidad, unidadMedida, idEntidadBeneficiaria, nombreEntidad, emailEntidad,
             idDonante, emailDonante, nombreDonante, CanalNotificacion.EMAIL, emailDonante);
    }

    public DonacionAsignadaEvent(Long idDonacion, String categoria, String subcategoria, Double cantidad, String unidadMedida,
                                 Long idEntidadBeneficiaria, String nombreEntidad, String emailEntidad,
                                 Long idDonante, String emailDonante, String nombreDonante,
                                 CanalNotificacion canalPreferidoDonante, String contactoPreferidoDonante) {
        this.idDonacion = idDonacion;
        this.categoria = categoria;
        this.subcategoria = subcategoria;
        this.cantidad = cantidad;
        this.unidadMedida = unidadMedida;
        this.idEntidadBeneficiaria = idEntidadBeneficiaria;
        this.nombreEntidad = nombreEntidad;
        this.emailEntidad = emailEntidad;
        this.idDonante = idDonante;
        this.emailDonante = emailDonante;
        this.nombreDonante = nombreDonante;
        this.canalPreferidoDonante = canalPreferidoDonante != null ? canalPreferidoDonante : CanalNotificacion.EMAIL;
        this.contactoPreferidoDonante = contactoPreferidoDonante != null ? contactoPreferidoDonante : emailDonante;
        this.timestamp = LocalDateTime.now();
    }

    public Long getIdDonacion() { return idDonacion; }
    public void setIdDonacion(Long idDonacion) { this.idDonacion = idDonacion; }

    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }

    public String getSubcategoria() { return subcategoria; }
    public void setSubcategoria(String subcategoria) { this.subcategoria = subcategoria; }

    public Double getCantidad() { return cantidad; }
    public void setCantidad(Double cantidad) { this.cantidad = cantidad; }

    public String getUnidadMedida() { return unidadMedida; }
    public void setUnidadMedida(String unidadMedida) { this.unidadMedida = unidadMedida; }

    public Long getIdEntidadBeneficiaria() { return idEntidadBeneficiaria; }
    public void setIdEntidadBeneficiaria(Long idEntidadBeneficiaria) { this.idEntidadBeneficiaria = idEntidadBeneficiaria; }

    public String getNombreEntidad() { return nombreEntidad; }
    public void setNombreEntidad(String nombreEntidad) { this.nombreEntidad = nombreEntidad; }

    public String getEmailEntidad() { return emailEntidad; }
    public void setEmailEntidad(String emailEntidad) { this.emailEntidad = emailEntidad; }

    public Long getIdDonante() { return idDonante; }
    public void setIdDonante(Long idDonante) { this.idDonante = idDonante; }

    public String getEmailDonante() { return emailDonante; }
    public void setEmailDonante(String emailDonante) { this.emailDonante = emailDonante; }

    public String getNombreDonante() { return nombreDonante; }
    public void setNombreDonante(String nombreDonante) { this.nombreDonante = nombreDonante; }

    public CanalNotificacion getCanalPreferidoDonante() { return canalPreferidoDonante; }
    public void setCanalPreferidoDonante(CanalNotificacion canalPreferidoDonante) { this.canalPreferidoDonante = canalPreferidoDonante; }

    public String getContactoPreferidoDonante() { return contactoPreferidoDonante; }
    public void setContactoPreferidoDonante(String contactoPreferidoDonante) { this.contactoPreferidoDonante = contactoPreferidoDonante; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
