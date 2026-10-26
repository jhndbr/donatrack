package com.donatrack.donaciones.repository;

import com.donatrack.common.enums.EstadoDonacion;
import com.donatrack.donaciones.domain.Donacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface DonacionRepository extends JpaRepository<Donacion, Long>, JpaSpecificationExecutor<Donacion> {

    List<Donacion> findByEstado(EstadoDonacion estado);

    List<Donacion> findByDonanteId(Long donanteId);

    @Query("SELECT d FROM Donacion d WHERE d.estado = 'EN_DEPOSITO' AND d.fechaVencimiento IS NOT NULL AND d.fechaVencimiento <= :fecha")
    List<Donacion> findVencidasEnDeposito(@Param("fecha") LocalDate fecha);

    @Query("SELECT d FROM Donacion d WHERE d.estado = 'ASIGNACION_REALIZADA'")
    List<Donacion> findAsignadasListasParaLogistica();

    // Verificacion de donaciones activas para bloqueo de baja logica de Donante (CU-11)
    @Query("SELECT COUNT(d) FROM Donacion d WHERE d.donante.id = :donanteId AND d.estado IN ('EN_DEPOSITO', 'ASIGNACION_REALIZADA', 'LISTA_PARA_ENTREGAR', 'EN_TRASLADO')")
    long countActivasByDonanteId(@Param("donanteId") Long donanteId);

    // Verificacion de donaciones activas para bloqueo de baja logica de Entidad (CU-25)
    @Query("SELECT COUNT(d) FROM Donacion d WHERE d.entidadAsignada.id = :entidadId AND d.estado IN ('ASIGNACION_REALIZADA', 'LISTA_PARA_ENTREGAR', 'EN_TRASLADO')")
    long countActivasByEntidadId(@Param("entidadId") Long entidadId);

    // Conteo dinamico de donaciones entregadas a la entidad en los ultimos 90 dias (CU-32)
    @Query("SELECT COUNT(d) FROM Donacion d WHERE d.entidadAsignada.id = :entidadId AND d.estado = 'ENTREGADA' AND d.fechaActualizacionEstado >= :desde")
    long countEntregadasRecientesByEntidadId(@Param("entidadId") Long entidadId, @Param("desde") LocalDateTime desde);

    // Donaciones entregadas para consulta comunitaria en mapa (CU-03)
    @Query("SELECT d FROM Donacion d WHERE d.estado = 'ENTREGADA' ORDER BY d.fechaActualizacionEstado DESC")
    List<Donacion> findDonacionesEntregadasPublicas();
}
