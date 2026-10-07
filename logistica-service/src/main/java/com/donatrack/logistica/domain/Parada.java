package com.donatrack.logistica.domain;

public class Parada {
    private int orden;
    private Long idEntidadBeneficiaria;
    private String nombreEntidad;
    private String direccion;
    private double latitud;
    private double longitud;
    private Long idDonacion;

    public Parada() {}

    public Parada(int orden, Long idEntidadBeneficiaria, String nombreEntidad, String direccion, double latitud, double longitud, Long idDonacion) {
        this.orden = orden;
        this.idEntidadBeneficiaria = idEntidadBeneficiaria;
        this.nombreEntidad = nombreEntidad;
        this.direccion = direccion;
        this.latitud = latitud;
        this.longitud = longitud;
        this.idDonacion = idDonacion;
    }

    public int getOrden() { return orden; }
    public void setOrden(int orden) { this.orden = orden; }

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
}
