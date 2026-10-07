package com.donatrack.logistica.dto;

import java.util.List;

public class PlanificadorCallbackDto {

    public static class RutaPropuestaDto {
        private String idCamion;
        private String patenteCamion;
        private List<ParadaPropuestaDto> paradas;

        public String getIdCamion() { return idCamion; }
        public void setIdCamion(String idCamion) { this.idCamion = idCamion; }

        public String getPatenteCamion() { return patenteCamion; }
        public void setPatenteCamion(String patenteCamion) { this.patenteCamion = patenteCamion; }

        public List<ParadaPropuestaDto> getParadas() { return paradas; }
        public void setParadas(List<ParadaPropuestaDto> paradas) { this.paradas = paradas; }
    }

    public static class ParadaPropuestaDto {
        private int orden;
        private Long idEntidad;
        private String nombreEntidad;
        private String direccion;
        private double latitud;
        private double longitud;
        private Long idDonacion;

        public int getOrden() { return orden; }
        public void setOrden(int orden) { this.orden = orden; }

        public Long getIdEntidad() { return idEntidad; }
        public void setIdEntidad(Long idEntidad) { this.idEntidad = idEntidad; }

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

    private String loteId;
    private List<RutaPropuestaDto> rutasGeneradas;
    private List<Long> donacionesSinAsignar; // Donaciones que no entraron en el lote y deben replanificarse

    public String getLoteId() { return loteId; }
    public void setLoteId(String loteId) { this.loteId = loteId; }

    public List<RutaPropuestaDto> getRutasGeneradas() { return rutasGeneradas; }
    public void setRutasGeneradas(List<RutaPropuestaDto> rutasGeneradas) { this.rutasGeneradas = rutasGeneradas; }

    public List<Long> getDonacionesSinAsignar() { return donacionesSinAsignar; }
    public void setDonacionesSinAsignar(List<Long> donacionesSinAsignar) { this.donacionesSinAsignar = donacionesSinAsignar; }
}
