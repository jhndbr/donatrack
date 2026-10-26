package com.donatrack.common.events;

import com.donatrack.common.enums.CanalNotificacion;
import java.io.Serializable;
import java.time.LocalDateTime;

public class CategoriaAscendidaEvent implements Serializable {
    private Long idDonante;
    private String nombreDonante;
    private String emailDonante;
    private String categoriaPrevia;
    private String nuevaCategoria;
    private CanalNotificacion canalPreferido;
    private String contactoPreferido;
    private LocalDateTime timestamp;

    public CategoriaAscendidaEvent() {
        this.timestamp = LocalDateTime.now();
        this.canalPreferido = CanalNotificacion.EMAIL;
    }

    public CategoriaAscendidaEvent(Long idDonante, String nombreDonante, String emailDonante,
                                   String categoriaPrevia, String nuevaCategoria) {
        this(idDonante, nombreDonante, emailDonante, categoriaPrevia, nuevaCategoria, CanalNotificacion.EMAIL, emailDonante);
    }

    public CategoriaAscendidaEvent(Long idDonante, String nombreDonante, String emailDonante,
                                   String categoriaPrevia, String nuevaCategoria,
                                   CanalNotificacion canalPreferido, String contactoPreferido) {
        this.idDonante = idDonante;
        this.nombreDonante = nombreDonante;
        this.emailDonante = emailDonante;
        this.categoriaPrevia = categoriaPrevia;
        this.nuevaCategoria = nuevaCategoria;
        this.canalPreferido = canalPreferido != null ? canalPreferido : CanalNotificacion.EMAIL;
        this.contactoPreferido = contactoPreferido != null ? contactoPreferido : emailDonante;
        this.timestamp = LocalDateTime.now();
    }

    public Long getIdDonante() { return idDonante; }
    public void setIdDonante(Long idDonante) { this.idDonante = idDonante; }

    public String getNombreDonante() { return nombreDonante; }
    public void setNombreDonante(String nombreDonante) { this.nombreDonante = nombreDonante; }

    public String getEmailDonante() { return emailDonante; }
    public void setEmailDonante(String emailDonante) { this.emailDonante = emailDonante; }

    public String getCategoriaPrevia() { return categoriaPrevia; }
    public void setCategoriaPrevia(String categoriaPrevia) { this.categoriaPrevia = categoriaPrevia; }

    public String getNuevaCategoria() { return nuevaCategoria; }
    public void setNuevaCategoria(String nuevaCategoria) { this.nuevaCategoria = nuevaCategoria; }

    public CanalNotificacion getCanalPreferido() { return canalPreferido; }
    public void setCanalPreferido(CanalNotificacion canalPreferido) { this.canalPreferido = canalPreferido; }

    public String getContactoPreferido() { return contactoPreferido; }
    public void setContactoPreferido(String contactoPreferido) { this.contactoPreferido = contactoPreferido; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
