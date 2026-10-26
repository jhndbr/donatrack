package com.donatrack.donaciones.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "personas_donantes")
@Inheritance(strategy = InheritanceType.JOINED)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public abstract class PersonaDonante {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fecha_registro", nullable = false)
    private LocalDateTime fechaRegistro;

    @Column(name = "ultima_actividad", nullable = false)
    private LocalDateTime ultimaActividad;

    @Column(name = "ultima_alerta_inactividad")
    private LocalDateTime ultimaAlertaInactividad;

    // Campos de baja lógica (CU-11)
    @Column(nullable = false, columnDefinition = "boolean default true")
    private boolean activo = true;

    @Column(name = "fecha_baja")
    private LocalDateTime fechaBaja;

    @Column(name = "motivo_baja", length = 500)
    private String motivoBaja;

    @OneToMany(mappedBy = "donante", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<MedioContacto> mediosContacto = new ArrayList<>();

    public PersonaDonante() {
        this.fechaRegistro = LocalDateTime.now();
        this.ultimaActividad = LocalDateTime.now();
        this.activo = true;
    }

    public abstract String getNombreCompleto();
    public abstract String getIdentificadorPrincipal();
    public abstract String getEmailPrincipal();

    public void registrarActividad() {
        this.ultimaActividad = LocalDateTime.now();
    }

    public void darDeBaja(String motivo) {
        if (motivo == null || motivo.trim().isEmpty()) {
            throw new IllegalArgumentException("El motivo de baja es obligatorio (CU-11)");
        }
        this.activo = false;
        this.fechaBaja = LocalDateTime.now();
        this.motivoBaja = motivo;
    }

    public void reactivar() {
        this.activo = true;
        this.fechaBaja = null;
        this.motivoBaja = null;
        this.ultimaActividad = LocalDateTime.now();
    }

    public void agregarMedioContacto(MedioContacto medio) {
        medio.setDonante(this);
        this.mediosContacto.add(medio);
    }

    public MedioContacto getMedioContactoPredeterminado() {
        return mediosContacto.stream()
                .filter(MedioContacto::isEsPredeterminado)
                .findFirst()
                .orElse(mediosContacto.isEmpty() ? null : mediosContacto.get(0));
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public LocalDateTime getFechaRegistro() { return fechaRegistro; }
    public void setFechaRegistro(LocalDateTime fechaRegistro) { this.fechaRegistro = fechaRegistro; }

    public LocalDateTime getUltimaActividad() { return ultimaActividad; }
    public void setUltimaActividad(LocalDateTime ultimaActividad) { this.ultimaActividad = ultimaActividad; }

    public LocalDateTime getUltimaAlertaInactividad() { return ultimaAlertaInactividad; }
    public void setUltimaAlertaInactividad(LocalDateTime ultimaAlertaInactividad) { this.ultimaAlertaInactividad = ultimaAlertaInactividad; }

    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }

    public LocalDateTime getFechaBaja() { return fechaBaja; }
    public void setFechaBaja(LocalDateTime fechaBaja) { this.fechaBaja = fechaBaja; }

    public String getMotivoBaja() { return motivoBaja; }
    public void setMotivoBaja(String motivoBaja) { this.motivoBaja = motivoBaja; }

    public List<MedioContacto> getMediosContacto() { return mediosContacto; }
    public void setMediosContacto(List<MedioContacto> mediosContacto) { this.mediosContacto = mediosContacto; }
}
