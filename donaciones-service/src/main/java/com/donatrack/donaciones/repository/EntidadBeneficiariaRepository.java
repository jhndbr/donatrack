package com.donatrack.donaciones.repository;

import com.donatrack.donaciones.domain.EntidadBeneficiaria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EntidadBeneficiariaRepository extends JpaRepository<EntidadBeneficiaria, Long> {
    Optional<EntidadBeneficiaria> findByRazonSocial(String razonSocial);
    List<EntidadBeneficiaria> findByActivoTrue();

    @Query("SELECT e FROM EntidadBeneficiaria e JOIN e.emailsRepresentantes em WHERE LOWER(em) = LOWER(:email)")
    List<EntidadBeneficiaria> findByEmailRepresentante(@Param("email") String email);
}

