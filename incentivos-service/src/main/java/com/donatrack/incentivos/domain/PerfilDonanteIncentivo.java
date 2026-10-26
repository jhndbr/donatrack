package com.donatrack.incentivos.domain;

import com.donatrack.common.enums.CanalNotificacion;
import com.donatrack.common.enums.CategoriaDonante;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    @Column(name = "canal_preferido")
    private CanalNotificacion canalPreferido = CanalNotificacion.EMAIL;

    @Column(name = "contacto_preferido")
    private String contactoPreferido;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CategoriaDonante categoria = CategoriaDonante.COLABORADOR;

    private int totalDonaciones = 0;
    private double totalBienesDonados = 0;
    private int organizacionesAyudadas = 0;
    private int rachaMesesConsecutivos = 0;
    private LocalDate ultimaDonacionFecha;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "perfil_subcategorias_donadas", joinColumns = @JoinColumn(name = "perfil_id"))
    @Column(name = "subcategoria")
    private Set<String> subcategoriasDonadas = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "perfil_organizaciones_ayudadas", joinColumns = @JoinColumn(name = "perfil_id"))
    @Column(name = "organizacion_id")
    private Set<Long> organizacionesAyudadasIds = new HashSet<>();

    @OneToMany(mappedBy = "perfilDonante", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Insignia> insignias = new ArrayList<>();

    public PerfilDonanteIncentivo() {}

    public PerfilDonanteIncentivo(Long idDonante, String nombre, String email) {
        this.idDonante = idDonante;
        this.nombre = (nombre != null && !nombre.isBlank()) ? nombre : "Donante " + idDonante;
        this.email = (email != null && !email.isBlank()) ? email : "donante" + idDonante + "@donatrack.org";
        this.canalPreferido = CanalNotificacion.EMAIL;
        this.contactoPreferido = this.email;
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

    public CanalNotificacion getCanalPreferido() { return canalPreferido; }
    public void setCanalPreferido(CanalNotificacion canalPreferido) { this.canalPreferido = canalPreferido; }

    public String getContactoPreferido() { return contactoPreferido; }
    public void setContactoPreferido(String contactoPreferido) { this.contactoPreferido = contactoPreferido; }

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

    public Set<String> getSubcategoriasDonadas() { return subcategoriasDonadas; }
    public void setSubcategoriasDonadas(Set<String> subcategoriasDonadas) { this.subcategoriasDonadas = subcategoriasDonadas; }

    public Set<Long> getOrganizacionesAyudadasIds() { return organizacionesAyudadasIds; }
    public void setOrganizacionesAyudadasIds(Set<Long> organizacionesAyudadasIds) { this.organizacionesAyudadasIds = organizacionesAyudadasIds; }

    public boolean registrarSubcategoria(String subcat) {
        if (subcat != null && !subcat.isBlank()) {
            return this.subcategoriasDonadas.add(subcat.trim().toLowerCase());
        }
        return false;
    }

    public void registrarOrganizacionAyudada(Long entidadId) {
        if (entidadId != null) {
            this.organizacionesAyudadasIds.add(entidadId);
            this.organizacionesAyudadas = this.organizacionesAyudadasIds.size();
        }
    }

    public void actualizarRachaMensual(LocalDate fechaActual) {
        if (fechaActual == null) fechaActual = LocalDate.now();

        if (this.ultimaDonacionFecha == null) {
            this.rachaMesesConsecutivos = 1;
        } else {
            // Verificar si la última donación fue el mes inmediatamente anterior
            LocalDate mesAnterior = fechaActual.minusMonths(1);
            if (this.ultimaDonacionFecha.getYear() == mesAnterior.getYear() &&
                this.ultimaDonacionFecha.getMonthValue() == mesAnterior.getMonthValue()) {
                this.rachaMesesConsecutivos++;
            } else if (this.ultimaDonacionFecha.getYear() == fechaActual.getYear() &&
                       this.ultimaDonacionFecha.getMonthValue() == fechaActual.getMonthValue()) {
                // Misma mensualidad: se mantiene la racha sin alterar
                if (this.rachaMesesConsecutivos == 0) this.rachaMesesConsecutivos = 1;
            } else {
                // Hubo discontinuidad o inactividad mensual: reseteo a 1
                this.rachaMesesConsecutivos = 1;
            }
        }
        this.ultimaDonacionFecha = fechaActual;
    }

    public List<Insignia> getInsignias() { return insignias; }
    public void setInsignias(List<Insignia> insignias) { this.insignias = insignias; }
}
