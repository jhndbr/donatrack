package com.donatrack.incentivos.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "insignias")
public class Insignia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false)
    private String descripcion;

    @Column(name = "imagen_url")
    private String imagenUrl;

    @Column(name = "fecha_obtencion", nullable = false)
    private LocalDateTime fechaObtencion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "perfil_donante_id")
    @JsonIgnore
    private PerfilDonanteIncentivo perfilDonante;

    public Insignia() {
        this.fechaObtencion = LocalDateTime.now();
    }

    public Insignia(String nombre, String descripcion, String imagenUrl) {
        this();
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.imagenUrl = imagenUrl;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public String getImagenUrl() { return imagenUrl; }
    public void setImagenUrl(String imagenUrl) { this.imagenUrl = imagenUrl; }

    public LocalDateTime getFechaObtencion() { return fechaObtencion; }
    public void setFechaObtencion(LocalDateTime fechaObtencion) { this.fechaObtencion = fechaObtencion; }

    public PerfilDonanteIncentivo getPerfilDonante() { return perfilDonante; }
    public void setPerfilDonante(PerfilDonanteIncentivo perfilDonante) { this.perfilDonante = perfilDonante; }
}
