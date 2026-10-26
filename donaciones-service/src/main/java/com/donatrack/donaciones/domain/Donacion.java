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

    // Control de concurrencia optimista para prevenir doble asignacion concurrente (FT-09)
    @Version
    private Long version;

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

    @Column(name = "fecha_actualizacion_estado", nullable = false)
    private LocalDateTime fechaActualizacionEstado;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoDonacion estado;

    @Column(name = "justificacion_falla", length = 1000)
    private String justificacionFalla;

    @Column(name = "descripcion_general", length = 500)
    private String descripcionGeneral;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "donante_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private PersonaDonante donante;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "entidad_asignada_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "necesidades"})
    private EntidadBeneficiaria entidadAsignada;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "necesidad_asignada_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "entidadBeneficiaria", "donaciones"})
    private NecesidadMaterial necesidadAsignada;

    @OneToMany(mappedBy = "donacion", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BienDonado> bienes = new ArrayList<>();

    @OneToMany(mappedBy = "donacion", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("fechaHora ASC")
    private List<HistorialEstadoDonacion> historialEstados = new ArrayList<>();

    public Donacion() {
        this.fechaRecepcion = LocalDateTime.now();
        this.fechaActualizacionEstado = LocalDateTime.now();
        this.estado = EstadoDonacion.EN_DEPOSITO;
        this.historialEstados.add(new HistorialEstadoDonacion(this, LocalDateTime.now(), null, EstadoDonacion.EN_DEPOSITO, "SISTEMA", "Ingreso inicial en depósito"));
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

    public void asignarA(EntidadBeneficiaria entidad, NecesidadMaterial necesidad, String usuarioResponsable) {
        if (this.estado != EstadoDonacion.EN_DEPOSITO) {
            throw new IllegalStateException("Solo se pueden asignar donaciones en estado EN_DEPOSITO. Estado actual: " + this.estado);
        }
        if (entidad == null) {
            throw new IllegalArgumentException("La entidad beneficiaria no puede ser nula");
        }
        if (necesidad != null && !necesidad.getSubcategoria().equalsIgnoreCase(this.subcategoria)) {
            throw new IllegalArgumentException("La subcategoría de la necesidad (" + necesidad.getSubcategoria()
                    + ") no coincide con la subcategoría de la donación (" + this.subcategoria + ")");
        }

        this.entidadAsignada = entidad;
        this.necesidadAsignada = necesidad;
        if (necesidad != null) {
            necesidad.registrarCobertura(this.cantidad);
        }

        String motivo = "Asignación formal a entidad: " + entidad.getRazonSocial();
        cambiarEstado(EstadoDonacion.ASIGNACION_REALIZADA, motivo, usuarioResponsable);
    }

    public void asignarA(EntidadBeneficiaria entidad, NecesidadMaterial necesidad) {
        asignarA(entidad, necesidad, "OPERADOR");
    }

    public void asignarA(EntidadBeneficiaria entidad) {
        asignarA(entidad, null, "OPERADOR");
    }

    public void desasignar(String motivo, String usuarioResponsable) {
        if (this.estado != EstadoDonacion.ASIGNACION_REALIZADA) {
            throw new IllegalStateException("Solo se puede desasignar una donación en estado ASIGNACION_REALIZADA. Estado actual: " + this.estado);
        }
        if (motivo == null || motivo.trim().isEmpty()) {
            throw new IllegalArgumentException("El motivo de la desasignación es obligatorio");
        }

        if (this.necesidadAsignada != null) {
            this.necesidadAsignada.revertirCobertura(this.cantidad);
        }
        this.entidadAsignada = null;
        this.necesidadAsignada = null;
        cambiarEstado(EstadoDonacion.EN_DEPOSITO, "Desasignación administrativa: " + motivo, usuarioResponsable);
    }

    public void desasignar(String motivo) {
        desasignar(motivo, "ADMIN");
    }

    public void marcarListaParaEntregar(String usuarioResponsable) {
        if (this.estado != EstadoDonacion.ASIGNACION_REALIZADA) {
            throw new IllegalStateException("Para estar lista para entregar, debe estar en ASIGNACION_REALIZADA");
        }
        cambiarEstado(EstadoDonacion.LISTA_PARA_ENTREGAR, "Preparada en depósito para ruta logística", usuarioResponsable);
    }

    public void marcarListaParaEntregar() {
        marcarListaParaEntregar("SISTEMA");
    }

    public void iniciarTraslado(String usuarioResponsable) {
        if (this.estado != EstadoDonacion.LISTA_PARA_ENTREGAR) {
            throw new IllegalStateException("Para iniciar traslado, debe estar LISTA_PARA_ENTREGAR");
        }
        cambiarEstado(EstadoDonacion.EN_TRASLADO, "Cargada en camión e inicio de trayecto", usuarioResponsable);
    }

    public void iniciarTraslado() {
        iniciarTraslado("CHOFER");
    }

    public void confirmarEntrega(String usuarioResponsable) {
        if (this.estado != EstadoDonacion.EN_TRASLADO) {
            throw new IllegalStateException("Para confirmar entrega, la donacion debe encontrarse EN_TRASLADO");
        }
        cambiarEstado(EstadoDonacion.ENTREGADA, "Entrega confirmada y recibida en sede receptora", usuarioResponsable);
        if (this.entidadAsignada != null) {
            this.entidadAsignada.registrarDonacionRecibida();
        }
    }

    public void confirmarEntrega() {
        confirmarEntrega("ENTIDAD_BENEFICIARIA");
    }

    public void registrarEntregaFallida(String justificacion, String usuarioResponsable) {
        if (justificacion == null || justificacion.trim().isEmpty()) {
            throw new IllegalArgumentException("La justificacion de entrega fallida es obligatoria segun las especificaciones");
        }
        if (this.estado != EstadoDonacion.EN_TRASLADO && this.estado != EstadoDonacion.LISTA_PARA_ENTREGAR) {
            throw new IllegalStateException("Solo puede fallar o rechazarse una donación lista para entregar o en traslado. Estado actual: " + this.estado);
        }
        this.justificacionFalla = justificacion;
        cambiarEstado(EstadoDonacion.ENTREGA_FALLIDA, justificacion, usuarioResponsable);
    }

    public void registrarEntregaFallida(String justificacion) {
        registrarEntregaFallida(justificacion, "CHOFER");
    }

    public void reingresarADeposito(String usuarioResponsable) {
        if (this.estado != EstadoDonacion.ENTREGA_FALLIDA) {
            throw new IllegalStateException("Solo se puede reingresar a deposito si provino de ENTREGA_FALLIDA");
        }
        if (this.necesidadAsignada != null) {
            this.necesidadAsignada.revertirCobertura(this.cantidad);
        }
        this.entidadAsignada = null;
        this.necesidadAsignada = null;
        this.justificacionFalla = null;
        cambiarEstado(EstadoDonacion.EN_DEPOSITO, "Reingreso físico a depósito tras entrega fallida", usuarioResponsable);
    }

    public void reingresarADeposito() {
        reingresarADeposito("OPERADOR");
    }

    public void marcarVencida(String usuarioResponsable) {
        if (this.estado == EstadoDonacion.ENTREGADA) {
            throw new IllegalStateException("No se puede marcar como vencida una donacion ya entregada");
        }
        if (this.estado == EstadoDonacion.VENCIDA) {
            throw new IllegalStateException("La donación ya se encuentra marcada como VENCIDA");
        }
        if (this.necesidadAsignada != null) {
            this.necesidadAsignada.revertirCobertura(this.cantidad);
        }
        this.entidadAsignada = null;
        this.necesidadAsignada = null;
        cambiarEstado(EstadoDonacion.VENCIDA, "Caducidad de fecha de vencimiento", usuarioResponsable);
    }

    public void marcarVencida() {
        marcarVencida("SCHEDULER");
    }

    public boolean esEditable() {
        return this.estado == EstadoDonacion.EN_DEPOSITO;
    }

    private void cambiarEstado(EstadoDonacion nuevoEstado, String justificacion, String usuarioResponsable) {
        EstadoDonacion anterior = this.estado;
        this.estado = nuevoEstado;
        this.fechaActualizacionEstado = LocalDateTime.now();
        this.historialEstados.add(new HistorialEstadoDonacion(this, LocalDateTime.now(), anterior, nuevoEstado, usuarioResponsable, justificacion));
    }

    private void cambiarEstado(EstadoDonacion nuevoEstado, String justificacion) {
        cambiarEstado(nuevoEstado, justificacion, "SISTEMA");
    }

    private void cambiarEstado(EstadoDonacion nuevoEstado) {
        cambiarEstado(nuevoEstado, "Transición de ciclo de vida", "SISTEMA");
    }

    public void agregarBien(BienDonado bien) {
        bien.setDonacion(this);
        this.bienes.add(bien);
    }

    // --- Getters y Setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

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

    public String getDescripcionGeneral() { return descripcionGeneral; }
    public void setDescripcionGeneral(String descripcionGeneral) { this.descripcionGeneral = descripcionGeneral; }

    public PersonaDonante getDonante() { return donante; }
    public void setDonante(PersonaDonante donante) { this.donante = donante; }

    public EntidadBeneficiaria getEntidadAsignada() { return entidadAsignada; }
    public void setEntidadAsignada(EntidadBeneficiaria entidadAsignada) { this.entidadAsignada = entidadAsignada; }

    public NecesidadMaterial getNecesidadAsignada() { return necesidadAsignada; }
    public void setNecesidadAsignada(NecesidadMaterial necesidadAsignada) { this.necesidadAsignada = necesidadAsignada; }

    public List<BienDonado> getBienes() { return bienes; }
    public void setBienes(List<BienDonado> bienes) { this.bienes = bienes; }

    public List<HistorialEstadoDonacion> getHistorialEstados() { return historialEstados; }
    public void setHistorialEstados(List<HistorialEstadoDonacion> historialEstados) { this.historialEstados = historialEstados; }
}
