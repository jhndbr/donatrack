package com.donatrack.incentivos.domain;

import com.donatrack.common.enums.CategoriaDonante;
import jakarta.persistence.*;

@Entity
@Table(name = "misiones")
public class Mision {

    public enum TipoMision {
        RACHA,
        COMPLETITUD,
        HABIL_DONADOR,
        DONACIONES_EXITOSAS
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false)
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_mision", nullable = false)
    private TipoMision tipo;

    @Enumerated(EnumType.STRING)
    @Column(name = "categoria_requerida", nullable = false)
    private CategoriaDonante categoriaRequerida;

    @Column(nullable = false)
    private double objetivo; // Meta cuantitativa

    @Column(name = "orden_secuencia", nullable = false)
    private int ordenSecuencia;

    @Column(name = "nombre_insignia", nullable = false)
    private String nombreInsignia;

    public Mision() {}

    public Mision(String nombre, String descripcion, TipoMision tipo,
                  CategoriaDonante categoriaRequerida, double objetivo,
                  int ordenSecuencia, String nombreInsignia) {
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.tipo = tipo;
        this.categoriaRequerida = categoriaRequerida;
        this.objetivo = objetivo;
        this.ordenSecuencia = ordenSecuencia;
        this.nombreInsignia = nombreInsignia;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public TipoMision getTipo() { return tipo; }
    public void setTipo(TipoMision tipo) { this.tipo = tipo; }

    public CategoriaDonante getCategoriaRequerida() { return categoriaRequerida; }
    public void setCategoriaRequerida(CategoriaDonante categoriaRequerida) { this.categoriaRequerida = categoriaRequerida; }

    public double getObjetivo() { return objetivo; }
    public void setObjetivo(double objetivo) { this.objetivo = objetivo; }

    public int getOrdenSecuencia() { return ordenSecuencia; }
    public void setOrdenSecuencia(int ordenSecuencia) { this.ordenSecuencia = ordenSecuencia; }

    public String getNombreInsignia() { return nombreInsignia; }
    public void setNombreInsignia(String nombreInsignia) { this.nombreInsignia = nombreInsignia; }
}
