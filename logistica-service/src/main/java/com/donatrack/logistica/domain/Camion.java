package com.donatrack.logistica.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "camiones")
public class Camion {

    @Id
    private String id;
    private String patente;
    private double capacidadVolumenM3;
    private double alturaM;
    private double capacidadPesoKg;
    private boolean activo = true;

    public Camion() {}

    public Camion(String patente, double capacidadVolumenM3, double alturaM, double capacidadPesoKg) {
        this.patente = patente;
        this.capacidadVolumenM3 = capacidadVolumenM3;
        this.alturaM = alturaM;
        this.capacidadPesoKg = capacidadPesoKg;
        this.activo = true;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getPatente() { return patente; }
    public void setPatente(String patente) { this.patente = patente; }

    public double getCapacidadVolumenM3() { return capacidadVolumenM3; }
    public void setCapacidadVolumenM3(double capacidadVolumenM3) { this.capacidadVolumenM3 = capacidadVolumenM3; }

    public double getAlturaM() { return alturaM; }
    public void setAlturaM(double alturaM) { this.alturaM = alturaM; }

    public double getCapacidadPesoKg() { return capacidadPesoKg; }
    public void setCapacidadPesoKg(double capacidadPesoKg) { this.capacidadPesoKg = capacidadPesoKg; }

    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
}
