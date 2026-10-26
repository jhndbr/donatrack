package com.donatrack.logistica.domain;

import java.time.LocalDateTime;

/**
 * Value Object para geolocalización satelital y telemetría de campo.
 */
public class CoordenadaGPS {

    private double latitud;
    private double longitud;
    private double velocidadKmH;
    private LocalDateTime timestamp;
    private String direccionAproximada;

    public CoordenadaGPS() {
        this.timestamp = LocalDateTime.now();
    }

    public CoordenadaGPS(double latitud, double longitud, double velocidadKmH, LocalDateTime timestamp, String direccionAproximada) {
        this.latitud = latitud;
        this.longitud = longitud;
        this.velocidadKmH = velocidadKmH;
        this.timestamp = timestamp != null ? timestamp : LocalDateTime.now();
        this.direccionAproximada = direccionAproximada;
    }

    /**
     * Calcula la distancia en kilómetros hasta otra coordenada utilizando la fórmula de Haversine.
     */
    public double distanciaKmHasta(double otraLat, double otraLon) {
        final int R = 6371; // Radio de la Tierra en km
        double latDist = Math.toRadians(otraLat - this.latitud);
        double lonDist = Math.toRadians(otraLon - this.longitud);
        double a = Math.sin(latDist / 2) * Math.sin(latDist / 2)
                + Math.cos(Math.toRadians(this.latitud)) * Math.cos(Math.toRadians(otraLat))
                * Math.sin(lonDist / 2) * Math.sin(lonDist / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    public double getLatitud() { return latitud; }
    public void setLatitud(double latitud) { this.latitud = latitud; }

    public double getLongitud() { return longitud; }
    public void setLongitud(double longitud) { this.longitud = longitud; }

    public double getVelocidadKmH() { return velocidadKmH; }
    public void setVelocidadKmH(double velocidadKmH) { this.velocidadKmH = velocidadKmH; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public String getDireccionAproximada() { return direccionAproximada; }
    public void setDireccionAproximada(String direccionAproximada) { this.direccionAproximada = direccionAproximada; }
}
