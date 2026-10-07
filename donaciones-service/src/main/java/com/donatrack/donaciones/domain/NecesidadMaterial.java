package com.donatrack.donaciones.domain;

import com.donatrack.common.enums.TipoNecesidad;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

@Entity
@Table(name = "necesidades_materiales")
public class NecesidadMaterial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String subcategoria;

    @Column(nullable = false, length = 1000)
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_necesidad", nullable = false)
    private TipoNecesidad tipo;

    @Column(name = "cantidad_solicitada", nullable = false)
    private double cantidadSolicitada;

    @Column(name = "cantidad_cubierta", nullable = false)
    private double cantidadCubierta = 0.0;

    @Column(name = "unidad_medida", nullable = false)
    private String unidadMedida;

    // Solo para necesidades recurrentes: ej. "SEMANAL", "MENSUAL"
    private String periodo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entidad_id", nullable = false)
    @JsonIgnore
    private EntidadBeneficiaria entidadBeneficiaria;

    public NecesidadMaterial() {}

    public NecesidadMaterial(String subcategoria, String descripcion, TipoNecesidad tipo,
                             double cantidadSolicitada, String unidadMedida, String periodo) {
        this.subcategoria = subcategoria;
        this.descripcion = descripcion;
        this.tipo = tipo;
        this.cantidadSolicitada = cantidadSolicitada;
        this.unidadMedida = unidadMedida;
        this.periodo = periodo;
        this.cantidadCubierta = 0.0;
    }

    public boolean estaSatisfecha() {
        return cantidadCubierta >= cantidadSolicitada;
    }

    public double getCantidadPendiente() {
        return Math.max(0.0, cantidadSolicitada - cantidadCubierta);
    }

    public void registrarCobertura(double cantidad) {
        this.cantidadCubierta += cantidad;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSubcategoria() { return subcategoria; }
    public void setSubcategoria(String subcategoria) { this.subcategoria = subcategoria; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public TipoNecesidad getTipo() { return tipo; }
    public void setTipo(TipoNecesidad tipo) { this.tipo = tipo; }

    public double getCantidadSolicitada() { return cantidadSolicitada; }
    public void setCantidadSolicitada(double cantidadSolicitada) { this.cantidadSolicitada = cantidadSolicitada; }

    public double getCantidadCubierta() { return cantidadCubierta; }
    public void setCantidadCubierta(double cantidadCubierta) { this.cantidadCubierta = cantidadCubierta; }

    public String getUnidadMedida() { return unidadMedida; }
    public void setUnidadMedida(String unidadMedida) { this.unidadMedida = unidadMedida; }

    public String getPeriodo() { return periodo; }
    public void setPeriodo(String periodo) { this.periodo = periodo; }

    public EntidadBeneficiaria getEntidadBeneficiaria() { return entidadBeneficiaria; }
    public void setEntidadBeneficiaria(EntidadBeneficiaria entidadBeneficiaria) { this.entidadBeneficiaria = entidadBeneficiaria; }
}
