package com.donatrack.donaciones.domain;

import com.donatrack.common.enums.CanalNotificacion;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

@Entity
@Table(name = "medios_contacto")
public class MedioContacto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CanalNotificacion tipo;

    @Column(nullable = false)
    private String valor;

    @Column(name = "es_predeterminado", nullable = false)
    private boolean esPredeterminado;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "donante_id")
    @JsonIgnore
    private PersonaDonante donante;

    public MedioContacto() {}

    public MedioContacto(CanalNotificacion tipo, String valor, boolean esPredeterminado) {
        this.tipo = tipo;
        this.valor = valor;
        this.esPredeterminado = esPredeterminado;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public CanalNotificacion getTipo() { return tipo; }
    public void setTipo(CanalNotificacion tipo) { this.tipo = tipo; }

    public String getValor() { return valor; }
    public void setValor(String valor) { this.valor = valor; }

    public boolean isEsPredeterminado() { return esPredeterminado; }
    public void setEsPredeterminado(boolean esPredeterminado) { this.esPredeterminado = esPredeterminado; }

    public PersonaDonante getDonante() { return donante; }
    public void setDonante(PersonaDonante donante) { this.donante = donante; }
}
