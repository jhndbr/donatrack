package com.donatrack.donaciones.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "entidades_beneficiarias")
public class EntidadBeneficiaria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "razon_social", nullable = false)
    private String razonSocial;

    @Column(nullable = false)
    private String direccion;

    private String telefono;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "emails_representantes_beneficiaria", joinColumns = @JoinColumn(name = "entidad_id"))
    @Column(name = "email")
    private List<String> emailsRepresentantes = new ArrayList<>();

    @OneToMany(mappedBy = "entidadBeneficiaria", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnoreProperties("entidadBeneficiaria")
    private List<NecesidadMaterial> necesidades = new ArrayList<>();

    @Column(name = "donaciones_recibidas_trimestre", nullable = false)
    private int donacionesRecibidasTrimestre = 0;

    public EntidadBeneficiaria() {}

    public EntidadBeneficiaria(String razonSocial, String direccion, String telefono) {
        this.razonSocial = razonSocial;
        this.direccion = direccion;
        this.telefono = telefono;
        this.donacionesRecibidasTrimestre = 0;
    }

    public void agregarNecesidad(NecesidadMaterial necesidad) {
        necesidad.setEntidadBeneficiaria(this);
        this.necesidades.add(necesidad);
    }

    public void registrarDonacionRecibida() {
        this.donacionesRecibidasTrimestre++;
    }

    public String getEmailPrincipal() {
        return emailsRepresentantes.isEmpty() ? null : emailsRepresentantes.get(0);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getRazonSocial() { return razonSocial; }
    public void setRazonSocial(String razonSocial) { this.razonSocial = razonSocial; }

    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }

    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }

    public List<String> getEmailsRepresentantes() { return emailsRepresentantes; }
    public void setEmailsRepresentantes(List<String> emailsRepresentantes) { this.emailsRepresentantes = emailsRepresentantes; }

    public List<NecesidadMaterial> getNecesidades() { return necesidades; }
    public void setNecesidades(List<NecesidadMaterial> necesidades) { this.necesidades = necesidades; }

    public int getDonacionesRecibidasTrimestre() { return donacionesRecibidasTrimestre; }
    public void setDonacionesRecibidasTrimestre(int donacionesRecibidasTrimestre) { this.donacionesRecibidasTrimestre = donacionesRecibidasTrimestre; }
}
