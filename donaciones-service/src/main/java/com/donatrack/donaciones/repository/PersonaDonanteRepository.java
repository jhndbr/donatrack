package com.donatrack.donaciones.repository;

import com.donatrack.donaciones.domain.PersonaDonante;
import com.donatrack.donaciones.domain.PersonaHumana;
import com.donatrack.donaciones.domain.PersonaJuridica;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PersonaDonanteRepository extends JpaRepository<PersonaDonante, Long> {

    List<PersonaDonante> findByActivoTrue();

    @Query("SELECT d FROM PersonaDonante d JOIN d.mediosContacto m WHERE m.tipo = 'EMAIL' AND m.valor = :email ORDER BY d.id DESC")
    List<PersonaDonante> findAllByEmail(@Param("email") String email);

    default Optional<PersonaDonante> findByEmail(String email) {
        List<PersonaDonante> list = findAllByEmail(email);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    @Query("SELECT h FROM PersonaHumana h WHERE h.nroDocumento = :nroDocumento")
    Optional<PersonaHumana> findByNroDocumento(@Param("nroDocumento") String nroDocumento);

    @Query("SELECT j FROM PersonaJuridica j WHERE j.cuit = :cuit")
    Optional<PersonaJuridica> findByCuit(@Param("cuit") String cuit);

    @Query("SELECT d FROM PersonaDonante d WHERE d.activo = true AND d.ultimaActividad <= :fechaLimite AND (d.ultimaAlertaInactividad IS NULL OR d.ultimaAlertaInactividad <= :fechaLimite)")
    List<PersonaDonante> findInactivosAntesDe(@Param("fechaLimite") LocalDateTime fechaLimite);

    @Query("SELECT d FROM PersonaDonante d WHERE d.activo = true AND d.ultimaActividad <= :fechaLimite AND (d.ultimaAlertaInactividad IS NULL OR d.ultimaAlertaInactividad <= :fechaLimite)")
    List<PersonaDonante> findDonantesInactivosDesde(@Param("fechaLimite") LocalDateTime fechaLimite);
}
