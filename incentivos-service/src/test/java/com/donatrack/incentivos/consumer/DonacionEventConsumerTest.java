package com.donatrack.incentivos.consumer;

import com.donatrack.common.events.DonacionAsignadaEvent;
import com.donatrack.common.events.EntregaConfirmadaEvent;
import com.donatrack.incentivos.domain.RegistroEventoProcesado;
import com.donatrack.incentivos.repository.RegistroEventoProcesadoRepository;
import com.donatrack.incentivos.service.IncentivosService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DonacionEventConsumerTest {

    @Mock
    private IncentivosService incentivosService;

    @Mock
    private RegistroEventoProcesadoRepository eventoRepo;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private DonacionEventConsumer consumer;

    @Test
    void testRecibirDonacionAsignada() {
        DonacionAsignadaEvent evento = new DonacionAsignadaEvent(
                100L, "Alimentos", 25.0, "kg",
                10L, "Comedor Sol", "comedor@mail.com",
                42L, "donante@mail.com", "Lucía Benítez"
        );

        when(eventoRepo.existsByClaveEvento("donacion-asignada-100-42")).thenReturn(false);

        consumer.recibirDonacionAsignada(evento);

        verify(incentivosService, times(1)).registrarOrganizacionAyudadaPorAsignacion(
                42L, "Lucía Benítez", "donante@mail.com", 10L
        );
        verify(eventoRepo, times(1)).save(any(RegistroEventoProcesado.class));
    }

    @Test
    void testRecibirDonacionAsignadaDuplicadaEsIgnoradaPorIdempotencia() {
        DonacionAsignadaEvent evento = new DonacionAsignadaEvent(
                100L, "Alimentos", 25.0, "kg",
                10L, "Comedor Sol", "comedor@mail.com",
                42L, "donante@mail.com", "Lucía Benítez"
        );

        when(eventoRepo.existsByClaveEvento("donacion-asignada-100-42")).thenReturn(true);

        consumer.recibirDonacionAsignada(evento);

        verify(incentivosService, never()).registrarImpactoDonacionCompleto(any(), any(), any(), anyDouble(), any(), any());
        verify(eventoRepo, never()).save(any());
    }

    @Test
    void testRecibirEntregaConfirmada() {
        EntregaConfirmadaEvent evento = new EntregaConfirmadaEvent(
                "ENT-01", 100L, 10L, "AE789XY",
                "http://comprobante.com", "donante@mail.com", "entidad@mail.com"
        );

        when(eventoRepo.existsByClaveEvento("entrega-confirmada-ENT-01")).thenReturn(false);

        consumer.recibirEntregaConfirmada(evento);

        verify(incentivosService, times(1)).registrarEntregaExitosaPorEmail("donante@mail.com");
        verify(eventoRepo, times(1)).save(any(RegistroEventoProcesado.class));
    }

    @Test
    void testRecibirEntregaConfirmadaDuplicadaEsIgnoradaPorIdempotencia() {
        EntregaConfirmadaEvent evento = new EntregaConfirmadaEvent(
                "ENT-01", 100L, 10L, "AE789XY",
                "http://comprobante.com", "donante@mail.com", "entidad@mail.com"
        );

        when(eventoRepo.existsByClaveEvento("entrega-confirmada-ENT-01")).thenReturn(true);

        consumer.recibirEntregaConfirmada(evento);

        verify(incentivosService, never()).registrarEntregaExitosaPorEmail(anyString());
        verify(eventoRepo, never()).save(any());
    }
}
