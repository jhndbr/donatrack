package com.donatrack.donaciones.scheduler;

import com.donatrack.common.enums.EstadoDonacion;
import com.donatrack.donaciones.domain.Donacion;
import com.donatrack.donaciones.domain.OutboxEvent;
import com.donatrack.donaciones.domain.PersonaHumana;
import com.donatrack.donaciones.repository.DonacionRepository;
import com.donatrack.donaciones.repository.OutboxEventRepository;
import com.donatrack.donaciones.repository.PersonaDonanteRepository;
import com.donatrack.donaciones.service.MatchmakingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DonacionesSchedulerTest {

    @Mock
    private DonacionRepository donacionRepository;

    @Mock
    private PersonaDonanteRepository donanteRepository;

    @Mock
    private MatchmakingService matchmakingService;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private DonacionesScheduler donacionesScheduler;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(donacionesScheduler, "routingKeyDonanteInactivo", "donante.inactivo");
    }

    @Test
    void testEjecutarMatchmakingNocturno() {
        PersonaHumana d = new PersonaHumana("Juan", "Perez", 30, "111", "M", "Dir 1");
        Donacion don1 = new Donacion(d, "Alimentos", 10.0, "kg", false, null);
        don1.setId(10L);
        Donacion don2 = new Donacion(d, "Indumentaria", 5.0, "unidades", false, null);
        don2.setId(20L);

        when(donacionRepository.findByEstado(EstadoDonacion.EN_DEPOSITO)).thenReturn(List.of(don1, don2));

        donacionesScheduler.ejecutarMatchmakingNocturno();

        verify(matchmakingService, times(1)).evaluarDonacion(10L);
        verify(matchmakingService, times(1)).evaluarDonacion(20L);
    }

    @Test
    void testDetectarDonantesInactivosGeneraOutboxEvent() throws Exception {
        PersonaHumana inactivo = new PersonaHumana("Lucas", "Gomez", 40, "222", "M", "Dir 2");
        inactivo.setId(5L);
        ReflectionTestUtils.setField(inactivo, "ultimaActividad", LocalDateTime.now().minusDays(25));

        when(donanteRepository.findInactivosAntesDe(any(LocalDateTime.class))).thenReturn(List.of(inactivo));
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"idDonante\":5}");

        donacionesScheduler.detectarDonantesInactivos();

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository, times(1)).save(captor.capture());
        OutboxEvent event = captor.getValue();
        assertEquals("PersonaDonante", event.getAggregateType());
        assertEquals("5", event.getAggregateId());
        assertEquals("donante.inactivo", event.getRoutingKey());
    }

    @Test
    void testAuditarDonacionesVencidasActualizaEstado() {
        PersonaHumana d = new PersonaHumana("Juan", "Perez", 30, "111", "M", "Dir 1");
        Donacion perecedero = new Donacion(d, "Lácteos", 5.0, "litros", false, LocalDate.now().minusDays(1));
        perecedero.setId(30L);

        when(donacionRepository.findVencidasEnDeposito(any(LocalDate.class))).thenReturn(List.of(perecedero));

        donacionesScheduler.auditarDonacionesVencidas();

        assertEquals(EstadoDonacion.VENCIDA, perecedero.getEstado());
        verify(donacionRepository, times(1)).save(perecedero);
    }
}
