package com.donatrack.donaciones.repository;

import com.donatrack.common.enums.EstadoDonacion;
import com.donatrack.donaciones.domain.Donacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface DonacionRepository extends JpaRepository<Donacion, Long> {

    List<Donacion> findByEstado(EstadoDonacion estado);

    List<Donacion> findByDonanteId(Long donanteId);

    @Query("SELECT d FROM Donacion d WHERE d.estado = 'EN_DEPOSITO' AND d.fechaVencimiento IS NOT NULL AND d.fechaVencimiento <= :fecha")
    List<Donacion> findVencidasEnDeposito(@Param("fecha") LocalDate fecha);

    @Query("SELECT d FROM Donacion d WHERE d.estado = 'ASIGNACION_REALIZADA'")
    List<Donacion> findAsignadasListasParaLogistica();
}
