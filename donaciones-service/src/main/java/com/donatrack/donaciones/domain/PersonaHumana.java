package com.donatrack.donaciones.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "personas_humanas")
public class PersonaHumana extends PersonaDonante {

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false)
    private String apellido;

    private Integer edad;

    @Column(name = "tipo_documento")
    private String tipoDocumento = "DNI";

    @Column(name = "nro_documento", nullable = false, unique = true)
    private String nroDocumento;

    private String genero;

    private String direccion;

    private Double latitud;

    private Double longitud;

    public PersonaHumana() {
        super();
    }

    public PersonaHumana(String nombre, String apellido, Integer edad, String nroDocumento, String genero, String direccion) {
        super();
        this.nombre = nombre;
        this.apellido = apellido;
        this.edad = edad;
        this.nroDocumento = nroDocumento;
        this.genero = genero;
        this.direccion = direccion;
    }

    @Override
    public String getNombreCompleto() {
        return nombre + " " + apellido;
    }

    @Override
    public String getIdentificadorPrincipal() {
        return nroDocumento;
    }

    @Override
    public String getEmailPrincipal() {
        return getMediosContacto().stream()
                .filter(m -> m.getTipo() == com.donatrack.common.enums.CanalNotificacion.EMAIL)
                .map(MedioContacto::getValor)
                .findFirst()
                .orElse(null);
    }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getApellido() { return apellido; }
    public void setApellido(String apellido) { this.apellido = apellido; }

    public Integer getEdad() { return edad; }
    public void setEdad(Integer edad) { this.edad = edad; }

    public String getTipoDocumento() { return tipoDocumento; }
    public void setTipoDocumento(String tipoDocumento) { this.tipoDocumento = tipoDocumento; }

    public String getNroDocumento() { return nroDocumento; }
    public void setNroDocumento(String nroDocumento) { this.nroDocumento = nroDocumento; }

    public String getGenero() { return genero; }
    public void setGenero(String genero) { this.genero = genero; }

    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }

    public Double getLatitud() { return latitud; }
    public void setLatitud(Double latitud) { this.latitud = latitud; }

    public Double getLongitud() { return longitud; }
    public void setLongitud(Double longitud) { this.longitud = longitud; }
}
