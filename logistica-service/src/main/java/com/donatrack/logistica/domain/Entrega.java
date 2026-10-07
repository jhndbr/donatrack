package com.donatrack.logistica.domain;

import org.springframework.data.annotation.Id;
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
        NO_RECIBIDA
    }

    @Id
    private String id;
    private String idRuta;
    private Long idDonacion;
    private Long idEntidadBeneficiaria;
    private String nombreEntidad;
    private String emailEntidad;
    private Long idDonante;
    private String emailDonante;
    private String patenteCamion;
    private EstadoEntrega estado;
    private LocalDateTime fechaActualizacion;
    private String motivoFalla;
    private List<String> fotosConfirmacionUrls = new ArrayList<>();

    public Entrega() {
        this.estado = EstadoEntrega.PENDIENTE;
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
        this.patenteCamion = patenteCamion;
    }

    public void marcarEnTraslado() {
        this.estado = EstadoEntrega.EN_TRASLADO;
        this.fechaActualizacion = LocalDateTime.now();
    }

    public void confirmarRecepcion(List<String> fotosUrls) {
        this.estado = EstadoEntrega.ENTREGADA;
        if (fotosUrls != null) {
            this.fotosConfirmacionUrls.addAll(fotosUrls);
        }
        this.fechaActualizacion = LocalDateTime.now();
    }

    public void registrarNoRecibida(String motivo) {
        this.estado = EstadoEntrega.NO_RECIBIDA;
        this.motivoFalla = motivo;
        this.fechaActualizacion = LocalDateTime.now();
    }

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

    public String getPatenteCamion() { return patenteCamion; }
    public void setPatenteCamion(String patenteCamion) { this.patenteCamion = patenteCamion; }

    public EstadoEntrega getEstado() { return estado; }
    public void setEstado(EstadoEntrega estado) { this.estado = estado; }

    public LocalDateTime getFechaActualizacion() { return fechaActualizacion; }
    public void setFechaActualizacion(LocalDateTime fechaActualizacion) { this.fechaActualizacion = fechaActualizacion; }

    public String getMotivoFalla() { return motivoFalla; }
    public void setMotivoFalla(String motivoFalla) { this.motivoFalla = motivoFalla; }

    public List<String> getFotosConfirmacionUrls() { return fotosConfirmacionUrls; }
    public void setFotosConfirmacionUrls(List<String> fotosConfirmacionUrls) { this.fotosConfirmacionUrls = fotosConfirmacionUrls; }
}
