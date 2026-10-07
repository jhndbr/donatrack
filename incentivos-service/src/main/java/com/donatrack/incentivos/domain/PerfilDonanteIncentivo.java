package com.donatrack.incentivos.domain;

import com.donatrack.common.enums.CategoriaDonante;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "perfiles_donantes_incentivo")
public class PerfilDonanteIncentivo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "id_donante", nullable = false, unique = true)
    private Long idDonante;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = true)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CategoriaDonante categoria = CategoriaDonante.COLABORADOR;

    private int totalDonaciones = 0;
    private double totalBienesDonados = 0;
    private int organizacionesAyudadas = 0;
    private int rachaMesesConsecutivos = 0;
    private LocalDate ultimaDonacionFecha;

    @OneToMany(mappedBy = "perfilDonante", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Insignia> insignias = new ArrayList<>();

    public PerfilDonanteIncentivo() {}

    public PerfilDonanteIncentivo(Long idDonante, String nombre, String email) {
        this.idDonante = idDonante;
        this.nombre = (nombre != null && !nombre.isBlank()) ? nombre : "Donante " + idDonante;
        this.email = (email != null && !email.isBlank()) ? email : "donante" + idDonante + "@donatrack.org";
        this.categoria = CategoriaDonante.COLABORADOR;
    }

    public void agregarInsignia(Insignia insignia) {
        insignia.setPerfilDonante(this);
        this.insignias.add(insignia);
    }

    public void ascenderCategoria() {
        if (this.categoria == CategoriaDonante.COLABORADOR) {
            this.categoria = CategoriaDonante.SOSTENEDOR;
        } else if (this.categoria == CategoriaDonante.SOSTENEDOR) {
            this.categoria = CategoriaDonante.TRANSFORMADOR;
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getIdDonante() { return idDonante; }
    public void setIdDonante(Long idDonante) { this.idDonante = idDonante; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public CategoriaDonante getCategoria() { return categoria; }
    public void setCategoria(CategoriaDonante categoria) { this.categoria = categoria; }

    public int getTotalDonaciones() { return totalDonaciones; }
    public void setTotalDonaciones(int totalDonaciones) { this.totalDonaciones = totalDonaciones; }

    public double getTotalBienesDonados() { return totalBienesDonados; }
    public void setTotalBienesDonados(double totalBienesDonados) { this.totalBienesDonados = totalBienesDonados; }

    public int getOrganizacionesAyudadas() { return organizacionesAyudadas; }
    public void setOrganizacionesAyudadas(int organizacionesAyudadas) { this.organizacionesAyudadas = organizacionesAyudadas; }

    public int getRachaMesesConsecutivos() { return rachaMesesConsecutivos; }
    public void setRachaMesesConsecutivos(int rachaMesesConsecutivos) { this.rachaMesesConsecutivos = rachaMesesConsecutivos; }

    public LocalDate getUltimaDonacionFecha() { return ultimaDonacionFecha; }
    public void setUltimaDonacionFecha(LocalDate ultimaDonacionFecha) { this.ultimaDonacionFecha = ultimaDonacionFecha; }

    public List<Insignia> getInsignias() { return insignias; }
    public void setInsignias(List<Insignia> insignias) { this.insignias = insignias; }
}
