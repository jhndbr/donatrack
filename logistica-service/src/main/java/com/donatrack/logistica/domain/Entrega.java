package com.donatrack.logistica.domain;

import com.donatrack.common.enums.CanalNotificacion;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "entregas")
public class Entrega {

    public enum EstadoEntrega {
        PENDIENTE,
        EN_TRASLADO,
        ENTREGADA,
        NO_RECIBIDA,
        CANCELADA
    }

    @Id
    private String id;

    @Indexed
    private String idRuta;

    @Indexed
    private Long idDonacion;

    private Long idEntidadBeneficiaria;
    private String nombreEntidad;
    private String emailEntidad;
    private Long idDonante;
    private String emailDonante;
    private CanalNotificacion canalPreferidoDonante = CanalNotificacion.EMAIL;
    private String contactoPreferidoDonante;
    private String patenteCamion;
    private EstadoEntrega estado;

    private String receptorNombre;
    private String receptorDni;
    private String firmaDigitalUrl;
    private List<String> fotosConfirmacionUrls = new ArrayList<>();

    private String motivoFalla;
    private boolean replanificable = false;

    private LocalDateTime fechaCreacion = LocalDateTime.now();
    private LocalDateTime fechaActualizacion = LocalDateTime.now();

    public Entrega() {
        this.estado = EstadoEntrega.PENDIENTE;
        this.fechaCreacion = LocalDateTime.now();
        this.fechaActualizacion = LocalDateTime.now();
    }

    public Entrega(String idRuta, Long idDonacion, Long idEntidadBeneficiaria, String nombreEntidad,
                   String emailEntidad, Long idDonante, String emailDonante, String patenteCamion) {
        this();
        this.idRuta = idRuta;
        this.idDonacion = idDonacion;
        this.idEntidadBeneficiaria = idEntidadBeneficiaria;
        this.nombreEntidad = nombreEntidad;
        this.emailEntidad = emailEntidad;
        this.idDonante = idDonante;
        this.emailDonante = emailDonante;
        this.canalPreferidoDonante = CanalNotificacion.EMAIL;
        this.contactoPreferidoDonante = emailDonante;
        this.patenteCamion = patenteCamion;
    }

    // --- Métodos de Negocio ---

    public void marcarEnTraslado() {
        this.estado = EstadoEntrega.EN_TRASLADO;
        this.fechaActualizacion = LocalDateTime.now();
    }

    public void confirmarRecepcion(List<String> fotosUrls) {
        confirmarRecepcion(fotosUrls, null, null, null);
    }

    public void confirmarRecepcion(List<String> fotosUrls, String receptorNombre, String receptorDni, String firmaDigitalUrl) {
        if (fotosUrls == null || fotosUrls.isEmpty() || fotosUrls.stream().allMatch(String::isBlank)) {
            throw new IllegalArgumentException("Es obligatorio adjuntar al menos una fotografía de los bienes recibidos");
        }
        this.estado = EstadoEntrega.ENTREGADA;
        this.fotosConfirmacionUrls.clear();
        this.fotosConfirmacionUrls.addAll(fotosUrls);
        if (receptorNombre != null) this.receptorNombre = receptorNombre;
        if (receptorDni != null) this.receptorDni = receptorDni;
        if (firmaDigitalUrl != null) this.firmaDigitalUrl = firmaDigitalUrl;
        this.fechaActualizacion = LocalDateTime.now();
    }

    public void registrarNoRecibida(String motivo) {
        registrarNoRecibida(motivo, false);
    }

    public void registrarNoRecibida(String motivo, boolean replanificable) {
        if (motivo == null || motivo.trim().isEmpty()) {
            throw new IllegalArgumentException("El motivo o justificación de fallo es estrictamente obligatorio");
        }
        this.estado = EstadoEntrega.NO_RECIBIDA;
        this.motivoFalla = motivo.trim();
        this.replanificable = replanificable;
        this.fechaActualizacion = LocalDateTime.now();
    }

    /**
     * Flujo de replanificación administrativa (CU-45):
     * Si la donación regresa físicamente al depósito en buen estado, la entrega se restablece a PENDIENTE.
     */
    public void replanificar(String motivoReplanificacion) {
        this.estado = EstadoEntrega.PENDIENTE;
        this.replanificable = false;
        this.motivoFalla = motivoReplanificacion != null ? "Replanificada tras incidencia: " + motivoReplanificacion : "Replanificada";
        this.fechaActualizacion = LocalDateTime.now();
    }

    // --- Getters y Setters ---

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getIdRuta() { return idRuta; }
    public void setIdRuta(String idRuta) { this.idRuta = idRuta; }

    public Long getIdDonacion() { return idDonacion; }
    public void setIdDonacion(Long idDonacion) { this.idDonacion = idDonacion; }

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

    public CanalNotificacion getCanalPreferidoDonante() { return canalPreferidoDonante; }
    public void setCanalPreferidoDonante(CanalNotificacion canalPreferidoDonante) { this.canalPreferidoDonante = canalPreferidoDonante; }

    public String getContactoPreferidoDonante() { return contactoPreferidoDonante; }
    public void setContactoPreferidoDonante(String contactoPreferidoDonante) { this.contactoPreferidoDonante = contactoPreferidoDonante; }

    public String getPatenteCamion() { return patenteCamion; }
    public void setPatenteCamion(String patenteCamion) { this.patenteCamion = patenteCamion; }

    public EstadoEntrega getEstado() { return estado; }
    public void setEstado(EstadoEntrega estado) { this.estado = estado; }

    public String getReceptorNombre() { return receptorNombre; }
    public void setReceptorNombre(String receptorNombre) { this.receptorNombre = receptorNombre; }

    public String getReceptorDni() { return receptorDni; }
    public void setReceptorDni(String receptorDni) { this.receptorDni = receptorDni; }

    public String getFirmaDigitalUrl() { return firmaDigitalUrl; }
    public void setFirmaDigitalUrl(String firmaDigitalUrl) { this.firmaDigitalUrl = firmaDigitalUrl; }

    public List<String> getFotosConfirmacionUrls() { return fotosConfirmacionUrls; }
    public void setFotosConfirmacionUrls(List<String> fotosConfirmacionUrls) { this.fotosConfirmacionUrls = fotosConfirmacionUrls; }

    public String getMotivoFalla() { return motivoFalla; }
    public void setMotivoFalla(String motivoFalla) { this.motivoFalla = motivoFalla; }

    public boolean isReplanificable() { return replanificable; }
    public void setReplanificable(boolean replanificable) { this.replanificable = replanificable; }

    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }

    public LocalDateTime getFechaActualizacion() { return fechaActualizacion; }
    public void setFechaActualizacion(LocalDateTime fechaActualizacion) { this.fechaActualizacion = fechaActualizacion; }
}
