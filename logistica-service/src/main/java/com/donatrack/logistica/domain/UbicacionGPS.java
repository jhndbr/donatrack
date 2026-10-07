package com.donatrack.logistica.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Document(collection = "telemetria_gps")
public class UbicacionGPS {

    @Id
    private String id;
    private String idCamion;
    private String patente;
    private double latitud;
    private double longitud;
    private double velocidadKmH;
    private LocalDateTime timestamp;

    public UbicacionGPS() {
        this.timestamp = LocalDateTime.now();
    }

    public UbicacionGPS(String idCamion, String patente, double latitud, double longitud, double velocidadKmH) {
        this.idCamion = idCamion;
        this.patente = patente;
        this.latitud = latitud;
        this.longitud = longitud;
        this.velocidadKmH = velocidadKmH;
        this.timestamp = LocalDateTime.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getIdCamion() { return idCamion; }
    public void setIdCamion(String idCamion) { this.idCamion = idCamion; }

    public String getPatente() { return patente; }
    public void setPatente(String patente) { this.patente = patente; }

    public double getLatitud() { return latitud; }
    public void setLatitud(double latitud) { this.latitud = latitud; }

    public double getLongitud() { return longitud; }
    public void setLongitud(double longitud) { this.longitud = longitud; }

    public double getVelocidadKmH() { return velocidadKmH; }
    public void setVelocidadKmH(double velocidadKmH) { this.velocidadKmH = velocidadKmH; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
