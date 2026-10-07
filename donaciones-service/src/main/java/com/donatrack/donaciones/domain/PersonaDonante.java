package com.donatrack.donaciones.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "personas_donantes")
@Inheritance(strategy = InheritanceType.JOINED)
public abstract class PersonaDonante {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fecha_registro", nullable = false)
    private LocalDateTime fechaRegistro;

    @Column(name = "ultima_actividad", nullable = false)
    private LocalDateTime ultimaActividad;

    @OneToMany(mappedBy = "donante", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<MedioContacto> mediosContacto = new ArrayList<>();

    public PersonaDonante() {
        this.fechaRegistro = LocalDateTime.now();
        this.ultimaActividad = LocalDateTime.now();
    }

    public abstract String getNombreCompleto();
    public abstract String getIdentificadorPrincipal();
    public abstract String getEmailPrincipal();

    public void registrarActividad() {
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

    public List<MedioContacto> getMediosContacto() { return mediosContacto; }
    public void setMediosContacto(List<MedioContacto> mediosContacto) { this.mediosContacto = mediosContacto; }
}
