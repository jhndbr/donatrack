package com.donatrack.donaciones.repository;

import com.donatrack.donaciones.domain.NecesidadMaterial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NecesidadMaterialRepository extends JpaRepository<NecesidadMaterial, Long> {

    @Query("SELECT n FROM NecesidadMaterial n WHERE LOWER(n.subcategoria) = LOWER(:subcategoria) AND n.cantidadCubierta < n.cantidadSolicitada")
    List<NecesidadMaterial> findPendientesPorSubcategoria(@Param("subcategoria") String subcategoria);
}
