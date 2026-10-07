package com.donatrack.incentivos.repository;

import com.donatrack.incentivos.domain.ProgresoMision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProgresoMisionRepository extends JpaRepository<ProgresoMision, Long> {
    List<ProgresoMision> findByIdDonante(Long idDonante);
    Optional<ProgresoMision> findByIdDonanteAndMisionId(Long idDonante, Long misionId);
    List<ProgresoMision> findByIdDonanteAndCompletadaTrue(Long idDonante);
}
