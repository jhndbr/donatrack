package com.donatrack.donaciones.repository;

import com.donatrack.donaciones.domain.NecesidadMaterial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NecesidadMaterialRepository extends JpaRepository<NecesidadMaterial, Long> {

    @Query("SELECT n FROM NecesidadMaterial n JOIN FETCH n.entidadBeneficiaria WHERE LOWER(n.subcategoria) = LOWER(:subcategoria) AND n.cantidadCubierta < n.cantidadSolicitada AND n.activa = true AND n.entidadBeneficiaria.activo = true")
    List<NecesidadMaterial> findPendientesPorSubcategoria(@Param("subcategoria") String subcategoria);

    List<NecesidadMaterial> findByEntidadBeneficiariaIdAndActivaTrue(Long entidadId);

    List<NecesidadMaterial> findByTipo(com.donatrack.common.enums.TipoNecesidad tipo);

    List<NecesidadMaterial> findByTipoAndPeriodoIgnoreCase(com.donatrack.common.enums.TipoNecesidad tipo, String periodo);
}
