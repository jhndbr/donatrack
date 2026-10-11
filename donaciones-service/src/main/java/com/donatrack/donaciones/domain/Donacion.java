package com.donatrack.donaciones.domain;

import com.donatrack.common.enums.EstadoDonacion;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "donaciones")
public class Donacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Unidad minima de asignacion exigida por la catedra
    @Column(nullable = false)
    private String subcategoria;

    @Column(nullable = false)
    private double cantidad;

    @Column(name = "unidad_medida", nullable = false)
    private String unidadMedida;

    @Column(name = "es_usado", nullable = false)
    private boolean esUsado;

    @Column(name = "fecha_vencimiento")
    private LocalDate fechaVencimiento;

    @Column(name = "fecha_recepcion", nullable = false)
    private LocalDateTime fechaRecepcion;

    @Column(name = "fecha_actualizacion_estado")
    private LocalDateTime fechaActualizacionEstado;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoDonacion estado;

    @Column(name = "justificacion_falla", length = 1000)
    private String justificacionFalla;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "donante_id", nullable = false)
    private PersonaDonante donante;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "entidad_asignada_id")
    @JsonIgnoreProperties("necesidades")
    private EntidadBeneficiaria entidadAsignada;

    @OneToMany(mappedBy = "donacion", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BienDonado> bienes = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "donaciones_historial_estados", joinColumns = @JoinColumn(name = "donacion_id"))
    private List<RegistroHistorialEstado> historialEstados = new ArrayList<>();

    public Donacion() {
        this.fechaRecepcion = LocalDateTime.now();
        this.fechaActualizacionEstado = LocalDateTime.now();
        this.estado = EstadoDonacion.EN_DEPOSITO;
        this.historialEstados.add(new RegistroHistorialEstado(LocalDateTime.now(), null, EstadoDonacion.EN_DEPOSITO, "Ingreso inicial en depósito"));
    }

    public Donacion(PersonaDonante donante, String subcategoria, double cantidad,
                    String unidadMedida, boolean esUsado, LocalDate fechaVencimiento) {
        this();
        this.donante = donante;
        this.subcategoria = subcategoria;
        this.cantidad = cantidad;
        this.unidadMedida = unidadMedida;
        this.esUsado = esUsado;
        this.fechaVencimiento = fechaVencimiento;
    }

    // --- Metodos de Negocio / Maquina de Estados ---

    public void asignarA(EntidadBeneficiaria entidad) {
        if (this.estado != EstadoDonacion.EN_DEPOSITO) {
            throw new IllegalStateException("Solo se pueden asignar donaciones en estado EN_DEPOSITO. Estado actual: " + this.estado);
        }
        this.entidadAsignada = entidad;
        cambiarEstado(EstadoDonacion.ASIGNACION_REALIZADA);
    }

    public void marcarListaParaEntregar() {
        if (this.estado != EstadoDonacion.ASIGNACION_REALIZADA) {
            throw new IllegalStateException("Para estar lista para entregar, debe estar en ASIGNACION_REALIZADA");
        }
        cambiarEstado(EstadoDonacion.LISTA_PARA_ENTREGAR);
    }

    public void iniciarTraslado() {
        if (this.estado != EstadoDonacion.LISTA_PARA_ENTREGAR) {
            throw new IllegalStateException("Para iniciar traslado, debe estar LISTA_PARA_ENTREGAR");
        }
        cambiarEstado(EstadoDonacion.EN_TRASLADO);
    }

    public void confirmarEntrega() {
        if (this.estado != EstadoDonacion.EN_TRASLADO) {
            throw new IllegalStateException("Para confirmar entrega, la donacion debe encontrarse EN_TRASLADO");
        }
        cambiarEstado(EstadoDonacion.ENTREGADA);
        if (this.entidadAsignada != null) {
            this.entidadAsignada.registrarDonacionRecibida();
        }
    }

    public void registrarEntregaFallida(String justificacion) {
        if (justificacion == null || justificacion.trim().isEmpty()) {
            throw new IllegalArgumentException("La justificacion de entrega fallida es obligatoria segun las especificaciones");
        }
        if (this.estado != EstadoDonacion.EN_TRASLADO) {
            throw new IllegalStateException("Solo puede fallar una entrega que se encontraba EN_TRASLADO");
        }
        this.justificacionFalla = justificacion;
        cambiarEstado(EstadoDonacion.ENTREGA_FALLIDA);
    }

    public void reingresarADeposito() {
        if (this.estado != EstadoDonacion.ENTREGA_FALLIDA) {
            throw new IllegalStateException("Solo se puede reingresar a deposito si provino de ENTREGA_FALLIDA");
        }
        this.entidadAsignada = null;
        this.justificacionFalla = null;
        cambiarEstado(EstadoDonacion.EN_DEPOSITO);
    }

    public void marcarVencida() {
        if (this.estado == EstadoDonacion.ENTREGADA) {
            throw new IllegalStateException("No se puede marcar como vencida una donacion ya entregada");
        }
        cambiarEstado(EstadoDonacion.VENCIDA);
    }

    private void cambiarEstado(EstadoDonacion nuevoEstado, String justificacion) {
        EstadoDonacion anterior = this.estado;
        this.estado = nuevoEstado;
        this.fechaActualizacionEstado = LocalDateTime.now();
        this.historialEstados.add(new RegistroHistorialEstado(LocalDateTime.now(), anterior, nuevoEstado, justificacion));
    }

    private void cambiarEstado(EstadoDonacion nuevoEstado) {
        cambiarEstado(nuevoEstado, "Transición de ciclo de vida");
    }

    public void agregarBien(BienDonado bien) {
        bien.setDonacion(this);
        this.bienes.add(bien);
    }

    // --- Getters y Setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSubcategoria() { return subcategoria; }
    public void setSubcategoria(String subcategoria) { this.subcategoria = subcategoria; }

    public double getCantidad() { return cantidad; }
    public void setCantidad(double cantidad) { this.cantidad = cantidad; }

    public String getUnidadMedida() { return unidadMedida; }
    public void setUnidadMedida(String unidadMedida) { this.unidadMedida = unidadMedida; }

    public boolean isEsUsado() { return esUsado; }
    public void setEsUsado(boolean esUsado) { this.esUsado = esUsado; }

    public LocalDate getFechaVencimiento() { return fechaVencimiento; }
    public void setFechaVencimiento(LocalDate fechaVencimiento) { this.fechaVencimiento = fechaVencimiento; }

    public LocalDateTime getFechaRecepcion() { return fechaRecepcion; }
    public void setFechaRecepcion(LocalDateTime fechaRecepcion) { this.fechaRecepcion = fechaRecepcion; }

    public LocalDateTime getFechaActualizacionEstado() { return fechaActualizacionEstado; }
    public void setFechaActualizacionEstado(LocalDateTime fechaActualizacionEstado) { this.fechaActualizacionEstado = fechaActualizacionEstado; }

    public EstadoDonacion getEstado() { return estado; }
    public void setEstado(EstadoDonacion estado) { this.estado = estado; }

    public String getJustificacionFalla() { return justificacionFalla; }
    public void setJustificacionFalla(String justificacionFalla) { this.justificacionFalla = justificacionFalla; }

    public PersonaDonante getDonante() { return donante; }
    public void setDonante(PersonaDonante donante) { this.donante = donante; }

    public EntidadBeneficiaria getEntidadAsignada() { return entidadAsignada; }
    public void setEntidadAsignada(EntidadBeneficiaria entidadAsignada) { this.entidadAsignada = entidadAsignada; }

    public List<BienDonado> getBienes() { return bienes; }
    public void setBienes(List<BienDonado> bienes) { this.bienes = bienes; }

    public List<RegistroHistorialEstado> getHistorialEstados() { return historialEstados; }
    public void setHistorialEstados(List<RegistroHistorialEstado> historialEstados) { this.historialEstados = historialEstados; }

    @Embeddable
    public static class RegistroHistorialEstado {
        private LocalDateTime fechaHora;
        @Enumerated(EnumType.STRING)
        private EstadoDonacion estadoAnterior;
        @Enumerated(EnumType.STRING)
        private EstadoDonacion nuevoEstado;
        private String justificacion;

        public RegistroHistorialEstado() {
            this.fechaHora = LocalDateTime.now();
        }

        public RegistroHistorialEstado(LocalDateTime fechaHora, EstadoDonacion estadoAnterior, EstadoDonacion nuevoEstado, String justificacion) {
            this.fechaHora = fechaHora != null ? fechaHora : LocalDateTime.now();
            this.estadoAnterior = estadoAnterior;
            this.nuevoEstado = nuevoEstado;
            this.justificacion = justificacion != null ? justificacion : "";
        }

        public LocalDateTime getFechaHora() { return fechaHora; }
        public void setFechaHora(LocalDateTime fechaHora) { this.fechaHora = fechaHora; }

        public EstadoDonacion getEstadoAnterior() { return estadoAnterior; }
        public void setEstadoAnterior(EstadoDonacion estadoAnterior) { this.estadoAnterior = estadoAnterior; }

        public EstadoDonacion getNuevoEstado() { return nuevoEstado; }
        public void setNuevoEstado(EstadoDonacion nuevoEstado) { this.nuevoEstado = nuevoEstado; }

        public String getJustificacion() { return justificacion; }
        public void setJustificacion(String justificacion) { this.justificacion = justificacion; }
    }
}
