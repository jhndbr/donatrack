package com.donatrack.incentivos.repository;

import com.donatrack.incentivos.domain.RegistroEventoProcesado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RegistroEventoProcesadoRepository extends JpaRepository<RegistroEventoProcesado, Long> {
    boolean existsByClaveEvento(String claveEvento);
    Optional<RegistroEventoProcesado> findByClaveEvento(String claveEvento);
}
