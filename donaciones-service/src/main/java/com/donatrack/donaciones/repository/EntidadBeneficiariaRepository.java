package com.donatrack.donaciones.repository;

import com.donatrack.donaciones.domain.EntidadBeneficiaria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EntidadBeneficiariaRepository extends JpaRepository<EntidadBeneficiaria, Long> {
    Optional<EntidadBeneficiaria> findByRazonSocial(String razonSocial);
}
