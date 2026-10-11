package com.donatrack.incentivos.consumer;

import com.donatrack.common.events.DonacionAsignadaEvent;
import com.donatrack.common.events.EntregaConfirmadaEvent;
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

        consumer.recibirDonacionAsignada(evento);

        verify(incentivosService, times(1)).registrarImpactoDonacion(
                42L, "Lucía Benítez", "donante@mail.com", 25.0
        );
    }

    @Test
    void testRecibirEntregaConfirmada() {
        EntregaConfirmadaEvent evento = new EntregaConfirmadaEvent(
                "ENT-01", 100L, 10L, "AE789XY",
                "http://comprobante.com", "donante@mail.com", "entidad@mail.com"
        );

        consumer.recibirEntregaConfirmada(evento);

        verify(incentivosService, times(1)).registrarEntregaExitosaPorEmail("donante@mail.com");
    }
}
