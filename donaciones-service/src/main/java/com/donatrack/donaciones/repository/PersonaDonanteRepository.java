package com.donatrack.donaciones.repository;

import com.donatrack.donaciones.domain.PersonaDonante;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PersonaDonanteRepository extends JpaRepository<PersonaDonante, Long> {

    @Query("SELECT d FROM PersonaDonante d JOIN d.mediosContacto m WHERE m.tipo = 'EMAIL' AND m.valor = :email")
    Optional<PersonaDonante> findByEmail(@Param("email") String email);

    @Query("SELECT d FROM PersonaDonante d WHERE d.ultimaActividad <= :fechaLimite")
    List<PersonaDonante> findInactivosAntesDe(@Param("fechaLimite") LocalDateTime fechaLimite);
}
