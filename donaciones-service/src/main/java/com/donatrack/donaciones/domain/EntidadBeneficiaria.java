package com.donatrack.donaciones.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "entidades_beneficiarias")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class EntidadBeneficiaria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "razon_social", nullable = false)
    private String razonSocial;

    @Column(nullable = false)
    private String direccion;

    private Double latitud;

    private Double longitud;

    private String telefono;

    // Campos de baja lógica (CU-25)
    @Column(nullable = false, columnDefinition = "boolean default true")
    private boolean activo = true;

    @Column(name = "fecha_baja")
    private LocalDateTime fechaBaja;

    @Column(name = "motivo_baja", length = 500)
    private String motivoBaja;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "emails_representantes_beneficiaria", joinColumns = @JoinColumn(name = "entidad_id"))
    @Column(name = "email")
    private List<String> emailsRepresentantes = new ArrayList<>();

    @OneToMany(mappedBy = "entidadBeneficiaria", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnoreProperties("entidadBeneficiaria")
    private List<NecesidadMaterial> necesidades = new ArrayList<>();

    @Column(name = "donaciones_recibidas_trimestre", nullable = false)
    private int donacionesRecibidasTrimestre = 0;

    public EntidadBeneficiaria() {
        this.activo = true;
    }

    public EntidadBeneficiaria(String razonSocial, String direccion, String telefono) {
        this.razonSocial = razonSocial;
        this.direccion = direccion;
        this.telefono = telefono;
        this.donacionesRecibidasTrimestre = 0;
        this.activo = true;
    }

    public void darDeBaja(String motivo) {
        if (motivo == null || motivo.trim().isEmpty()) {
            throw new IllegalArgumentException("El motivo de baja es obligatorio (CU-25)");
        }
        this.activo = false;
        this.fechaBaja = LocalDateTime.now();
        this.motivoBaja = motivo;
    }

    public void reactivar() {
        this.activo = true;
        this.fechaBaja = null;
        this.motivoBaja = null;
    }

    public void agregarNecesidad(NecesidadMaterial necesidad) {
        necesidad.setEntidadBeneficiaria(this);
        this.necesidades.add(necesidad);
    }

    public void registrarDonacionRecibida() {
        this.donacionesRecibidasTrimestre++;
    }

    public String getEmailPrincipal() {
        if (emailsRepresentantes != null && !emailsRepresentantes.isEmpty()) {
            return emailsRepresentantes.get(0);
        }
        if (razonSocial != null && !razonSocial.isBlank()) {
            String slug = java.text.Normalizer.normalize(razonSocial, java.text.Normalizer.Form.NFD)
                    .replaceAll("\\p{M}", "")
                    .toLowerCase()
                    .replaceAll("[^a-z0-9]+", ".")
                    .replaceAll("^\\.|\\.$", "");
            return "contacto@" + (slug.isEmpty() ? "entidad" : slug) + ".org";
        }
        return "entidad@donatrack.org";
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getRazonSocial() { return razonSocial; }
    public void setRazonSocial(String razonSocial) { this.razonSocial = razonSocial; }

    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }

    public Double getLatitud() { return latitud; }
    public void setLatitud(Double latitud) { this.latitud = latitud; }

    public Double getLongitud() { return longitud; }
    public void setLongitud(Double longitud) { this.longitud = longitud; }

    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }

    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }

    public LocalDateTime getFechaBaja() { return fechaBaja; }
    public void setFechaBaja(LocalDateTime fechaBaja) { this.fechaBaja = fechaBaja; }

    public String getMotivoBaja() { return motivoBaja; }
    public void setMotivoBaja(String motivoBaja) { this.motivoBaja = motivoBaja; }

    public List<String> getEmailsRepresentantes() { return emailsRepresentantes; }
    public void setEmailsRepresentantes(List<String> emailsRepresentantes) { this.emailsRepresentantes = emailsRepresentantes; }

    public List<NecesidadMaterial> getNecesidades() { return necesidades; }
    public void setNecesidades(List<NecesidadMaterial> necesidades) { this.necesidades = necesidades; }

    public int getDonacionesRecibidasTrimestre() { return donacionesRecibidasTrimestre; }
    public void setDonacionesRecibidasTrimestre(int donacionesRecibidasTrimestre) { this.donacionesRecibidasTrimestre = donacionesRecibidasTrimestre; }
}
