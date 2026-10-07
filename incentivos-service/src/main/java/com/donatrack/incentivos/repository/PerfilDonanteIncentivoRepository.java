package com.donatrack.incentivos.repository;

import com.donatrack.incentivos.domain.PerfilDonanteIncentivo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PerfilDonanteIncentivoRepository extends JpaRepository<PerfilDonanteIncentivo, Long> {
    Optional<PerfilDonanteIncentivo> findByIdDonante(Long idDonante);
    Optional<PerfilDonanteIncentivo> findByEmail(String email);
}
