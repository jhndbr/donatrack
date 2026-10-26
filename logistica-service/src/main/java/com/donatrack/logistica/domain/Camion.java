package com.donatrack.logistica.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "camiones")
public class Camion {

    @Id
    private String id;

    @Indexed(unique = true)
    private String patente;

    private String marcaModelo;
    private double capacidadVolumenM3;
    private double alturaM;
    private double capacidadPesoKg;
    private boolean activo = true;

    private LocalDateTime fechaAlta = LocalDateTime.now();
    private LocalDateTime fechaBaja;
    private String motivoBaja;

    private CoordenadaGPS ubicacionActual;

    public Camion() {
        this.activo = true;
        this.fechaAlta = LocalDateTime.now();
    }

    public Camion(String patente, double capacidadVolumenM3, double alturaM, double capacidadPesoKg) {
        this();
        this.patente = patente;
        this.capacidadVolumenM3 = capacidadVolumenM3;
        this.alturaM = alturaM;
        this.capacidadPesoKg = capacidadPesoKg;
    }

    public Camion(String patente, String marcaModelo, double capacidadVolumenM3, double alturaM, double capacidadPesoKg) {
        this(patente, capacidadVolumenM3, alturaM, capacidadPesoKg);
        this.marcaModelo = marcaModelo;
    }

    // --- Métodos de Negocio ---

    public void darDeBaja(String motivo) {
        if (motivo == null || motivo.trim().isEmpty()) {
            throw new IllegalArgumentException("Debe proporcionar un motivo para dar de baja el camión");
        }
        this.activo = false;
        this.fechaBaja = LocalDateTime.now();
        this.motivoBaja = motivo;
    }

    public void reactivar() {
        this.activo = true;
        this.fechaBaja = null;
        this.motivoBaja = null;
    }

    /**
     * Actualiza la posición geográfica del camión respetando el orden secuencial de marcas de tiempo (CU-41 A4).
     * @return true si la posición fue actualizada, false si el paquete fue descartado por estar fuera de secuencia.
     */
    public boolean actualizarUbicacion(double latitud, double longitud, double velocidadKmH, LocalDateTime timestamp, String direccion) {
        LocalDateTime nuevoTimestamp = timestamp != null ? timestamp : LocalDateTime.now();
        if (this.ubicacionActual != null && this.ubicacionActual.getTimestamp() != null) {
            if (nuevoTimestamp.isBefore(this.ubicacionActual.getTimestamp())) {
                // Paquete desordenado/retrasado, no sobrescribir posición más reciente (CU-41 A4)
                return false;
            }
        }
        this.ubicacionActual = new CoordenadaGPS(latitud, longitud, velocidadKmH, nuevoTimestamp, direccion);
        return true;
    }

    // --- Getters y Setters ---

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getPatente() { return patente; }
    public void setPatente(String patente) { this.patente = patente; }

    public String getMarcaModelo() { return marcaModelo; }
    public void setMarcaModelo(String marcaModelo) { this.marcaModelo = marcaModelo; }

    public double getCapacidadVolumenM3() { return capacidadVolumenM3; }
    public void setCapacidadVolumenM3(double capacidadVolumenM3) { this.capacidadVolumenM3 = capacidadVolumenM3; }

    public double getAlturaM() { return alturaM; }
    public void setAlturaM(double alturaM) { this.alturaM = alturaM; }

    public double getCapacidadPesoKg() { return capacidadPesoKg; }
    public void setCapacidadPesoKg(double capacidadPesoKg) { this.capacidadPesoKg = capacidadPesoKg; }

    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }

    public LocalDateTime getFechaAlta() { return fechaAlta; }
    public void setFechaAlta(LocalDateTime fechaAlta) { this.fechaAlta = fechaAlta; }

    public LocalDateTime getFechaBaja() { return fechaBaja; }
    public void setFechaBaja(LocalDateTime fechaBaja) { this.fechaBaja = fechaBaja; }

    public String getMotivoBaja() { return motivoBaja; }
    public void setMotivoBaja(String motivoBaja) { this.motivoBaja = motivoBaja; }

    public CoordenadaGPS getUbicacionActual() { return ubicacionActual; }
    public void setUbicacionActual(CoordenadaGPS ubicacionActual) { this.ubicacionActual = ubicacionActual; }
}
