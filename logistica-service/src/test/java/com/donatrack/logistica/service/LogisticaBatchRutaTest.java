package com.donatrack.logistica.service;

import com.donatrack.logistica.domain.*;
import com.donatrack.logistica.dto.PlanificadorCallbackDto;
import com.donatrack.logistica.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class LogisticaBatchRutaTest {

    @Mock
    private RutaRepository rutaRepository;

    @Mock
    private EntregaRepository entregaRepository;

    @Mock
    private CamionRepository camionRepository;

    @Mock
    private UbicacionGPSRepository gpsRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private LogisticaService logisticaService;

    private Camion camion;

    @BeforeEach
    void setUp() {
        camion = new Camion("AE789XY", 20.0, 3.0, 3500.0);
        camion.setId("CAMION-01");
        org.springframework.test.util.ReflectionTestUtils.setField(logisticaService, "exchange", "donatrack.events.exchange");
        org.springframework.test.util.ReflectionTestUtils.setField(logisticaService, "routingKeyRutaIniciada", "ruta.iniciada");
    }

    @Test
    void testParticionamientoEstrictoLotesMaximo100() {
        when(camionRepository.findById("CAMION-01")).thenReturn(Optional.of(camion));

        // Crear lista de 250 donaciones
        List<Long> donaciones250 = new ArrayList<>();
        for (long i = 1; i <= 250; i++) {
            donaciones250.add(i);
        }

        List<PlanificadorCallbackDto> lotes = logisticaService.planificarLotes(donaciones250, "CAMION-01");

        // Debe haber generado 3 lotes: 100, 100 y 50
        assertEquals(3, lotes.size());
        assertEquals(100, lotes.get(0).getRutasGeneradas().get(0).getParadas().size());
        assertEquals(100, lotes.get(1).getRutasGeneradas().get(0).getParadas().size());
        assertEquals(50, lotes.get(2).getRutasGeneradas().get(0).getParadas().size());
    }

    @Test
    void testIniciarRutaActualizaEntregasYEmiteEvento() {
        Ruta ruta = new Ruta("CAMION-01", "AE789XY", java.time.LocalDate.now());
        ruta.setId("RUTA-01");

        when(rutaRepository.findById("RUTA-01")).thenReturn(Optional.of(ruta));
        when(rutaRepository.save(any(Ruta.class))).thenAnswer(inv -> inv.getArgument(0));

        Entrega entrega = new Entrega("RUTA-01", 101L, 1L, "Comedor", "donante@mail.com", null, "entidad@mail.com", "AE789XY");
        when(entregaRepository.findByIdRuta("RUTA-01")).thenReturn(List.of(entrega));

        Ruta iniciada = logisticaService.iniciarRuta("RUTA-01");

        assertEquals(Ruta.EstadoRuta.EN_CURSO, iniciada.getEstado());
        assertEquals(Entrega.EstadoEntrega.EN_TRASLADO, entrega.getEstado());
        verify(rabbitTemplate, times(1)).convertAndSend(anyString(), anyString(), any(Object.class));
    }
}
