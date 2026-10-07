package com.donatrack.incentivos.repository;

import com.donatrack.common.enums.CategoriaDonante;
import com.donatrack.incentivos.domain.Mision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MisionRepository extends JpaRepository<Mision, Long> {
    List<Mision> findByCategoriaRequeridaOrderByOrdenSecuenciaAsc(CategoriaDonante categoria);
}
