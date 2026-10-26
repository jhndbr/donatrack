package com.donatrack.logistica.domain;

import java.time.LocalDateTime;

public class Parada {

    public enum TipoParada {
        RECOLECCION,
        ENTREGA
    }

    private int orden;
    private TipoParada tipo = TipoParada.ENTREGA;
    private Long idEntidadBeneficiaria;
    private String nombreEntidad;
    private String direccion;
    private double latitud;
    private double longitud;
    private Long idDonacion;
    private boolean completada = false;
    private LocalDateTime fechaLlegadaEstimada;
    private LocalDateTime fechaLlegadaReal;

    public Parada() {}

    public Parada(int orden, Long idEntidadBeneficiaria, String nombreEntidad, String direccion, double latitud, double longitud, Long idDonacion) {
        this.orden = orden;
        this.idEntidadBeneficiaria = idEntidadBeneficiaria;
        this.nombreEntidad = nombreEntidad;
        this.direccion = direccion;
        this.latitud = latitud;
        this.longitud = longitud;
        this.idDonacion = idDonacion;
        this.tipo = TipoParada.ENTREGA;
        this.completada = false;
    }

    public Parada(int orden, TipoParada tipo, Long idEntidadBeneficiaria, String nombreEntidad, String direccion, double latitud, double longitud, Long idDonacion) {
        this(orden, idEntidadBeneficiaria, nombreEntidad, direccion, latitud, longitud, idDonacion);
        this.tipo = tipo != null ? tipo : TipoParada.ENTREGA;
    }

    public void marcarCompletada() {
        this.completada = true;
        this.fechaLlegadaReal = LocalDateTime.now();
    }

    public int getOrden() { return orden; }
    public void setOrden(int orden) { this.orden = orden; }

    public TipoParada getTipo() { return tipo; }
    public void setTipo(TipoParada tipo) { this.tipo = tipo; }

    public Long getIdEntidadBeneficiaria() { return idEntidadBeneficiaria; }
    public void setIdEntidadBeneficiaria(Long idEntidadBeneficiaria) { this.idEntidadBeneficiaria = idEntidadBeneficiaria; }

    public String getNombreEntidad() { return nombreEntidad; }
    public void setNombreEntidad(String nombreEntidad) { this.nombreEntidad = nombreEntidad; }

    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }

    public double getLatitud() { return latitud; }
    public void setLatitud(double latitud) { this.latitud = latitud; }

    public double getLongitud() { return longitud; }
    public void setLongitud(double longitud) { this.longitud = longitud; }

    public Long getIdDonacion() { return idDonacion; }
    public void setIdDonacion(Long idDonacion) { this.idDonacion = idDonacion; }

    public boolean isCompletada() { return completada; }
    public void setCompletada(boolean completada) { this.completada = completada; }

    public LocalDateTime getFechaLlegadaEstimada() { return fechaLlegadaEstimada; }
    public void setFechaLlegadaEstimada(LocalDateTime fechaLlegadaEstimada) { this.fechaLlegadaEstimada = fechaLlegadaEstimada; }

    public LocalDateTime getFechaLlegadaReal() { return fechaLlegadaReal; }
    public void setFechaLlegadaReal(LocalDateTime fechaLlegadaReal) { this.fechaLlegadaReal = fechaLlegadaReal; }
}
