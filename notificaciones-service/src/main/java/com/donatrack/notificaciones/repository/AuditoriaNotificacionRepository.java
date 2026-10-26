package com.donatrack.notificaciones.repository;

import com.donatrack.common.enums.CanalNotificacion;
import com.donatrack.notificaciones.domain.AuditoriaNotificacion;
import com.donatrack.notificaciones.domain.EstadoNotificacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditoriaNotificacionRepository extends JpaRepository<AuditoriaNotificacion, Long> {

    List<AuditoriaNotificacion> findByDestinatarioOrderByTimestampDesc(String destinatario);

    List<AuditoriaNotificacion> findByDestinatarioIgnoreCaseOrderByTimestampDesc(String destinatario);

    List<AuditoriaNotificacion> findByDestinatarioIgnoreCaseAndLeidaFalse(String destinatario);

    List<AuditoriaNotificacion> findByLeidaFalse();

    List<AuditoriaNotificacion> findByCanalOrderByTimestampDesc(CanalNotificacion canal);

    List<AuditoriaNotificacion> findByEstadoOrderByTimestampDesc(EstadoNotificacion estado);

    List<AuditoriaNotificacion> findTop50ByOrderByTimestampDesc();

    long countByEstado(EstadoNotificacion estado);

    long countByCanal(CanalNotificacion canal);

    long countByDestinatarioIgnoreCaseAndLeidaFalse(String destinatario);

    long countByLeidaFalse();
}
