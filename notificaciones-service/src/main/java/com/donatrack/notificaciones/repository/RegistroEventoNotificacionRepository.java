package com.donatrack.notificaciones.repository;

import com.donatrack.notificaciones.domain.RegistroEventoNotificacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RegistroEventoNotificacionRepository extends JpaRepository<RegistroEventoNotificacion, Long> {

    boolean existsByClaveEvento(String claveEvento);

    Optional<RegistroEventoNotificacion> findByClaveEvento(String claveEvento);
}
