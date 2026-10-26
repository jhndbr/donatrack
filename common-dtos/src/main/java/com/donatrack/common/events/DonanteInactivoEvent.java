package com.donatrack.common.events;

import com.donatrack.common.enums.CanalNotificacion;
import java.io.Serializable;
import java.time.LocalDateTime;

public class DonanteInactivoEvent implements Serializable {
    private Long idDonante;
    private String nombre;
    private String email;
    private long diasInactivo;
    private CanalNotificacion canalPreferido;
    private String contactoPreferido;
    private LocalDateTime timestamp;

    public DonanteInactivoEvent() {
        this.timestamp = LocalDateTime.now();
        this.canalPreferido = CanalNotificacion.EMAIL;
    }

    public DonanteInactivoEvent(Long idDonante, String nombre, String email, long diasInactivo) {
        this(idDonante, nombre, email, diasInactivo, CanalNotificacion.EMAIL, email);
    }

    public DonanteInactivoEvent(Long idDonante, String nombre, String email, long diasInactivo,
                                CanalNotificacion canalPreferido, String contactoPreferido) {
        this.idDonante = idDonante;
        this.nombre = nombre;
        this.email = email;
        this.diasInactivo = diasInactivo;
        this.canalPreferido = canalPreferido != null ? canalPreferido : CanalNotificacion.EMAIL;
        this.contactoPreferido = contactoPreferido != null ? contactoPreferido : email;
        this.timestamp = LocalDateTime.now();
    }

    public Long getIdDonante() { return idDonante; }
    public void setIdDonante(Long idDonante) { this.idDonante = idDonante; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public long getDiasInactivo() { return diasInactivo; }
    public void setDiasInactivo(long diasInactivo) { this.diasInactivo = diasInactivo; }

    public CanalNotificacion getCanalPreferido() { return canalPreferido; }
    public void setCanalPreferido(CanalNotificacion canalPreferido) { this.canalPreferido = canalPreferido; }

    public String getContactoPreferido() { return contactoPreferido; }
    public void setContactoPreferido(String contactoPreferido) { this.contactoPreferido = contactoPreferido; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
