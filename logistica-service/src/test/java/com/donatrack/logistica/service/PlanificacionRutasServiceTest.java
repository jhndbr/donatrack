package com.donatrack.logistica.service;

import com.donatrack.common.enums.EstadoDonacion;
import com.donatrack.logistica.client.DonacionesServiceClient;
import com.donatrack.logistica.client.RutaOptimizerClient;
import com.donatrack.logistica.domain.Camion;
import com.donatrack.logistica.domain.DonacionPendienteLote;
import com.donatrack.logistica.domain.Ruta;
import com.donatrack.logistica.dto.PlanificadorCallbackDto;
import com.donatrack.logistica.repository.DonacionPendienteLoteRepository;
import com.donatrack.logistica.repository.EntregaRepository;
import com.donatrack.logistica.repository.RutaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PlanificacionRutasServiceTest {

    @Mock
    private FlotaService flotaService;

    @Mock
    private RutaOptimizerClient rutaOptimizerClient;

    @Mock
    private DonacionesServiceClient donacionesServiceClient;

    @Mock
    private RutaRepository rutaRepository;

    @Mock
    private EntregaRepository entregaRepository;

    @Mock
    private DonacionPendienteLoteRepository pendienteLoteRepository;

    @InjectMocks
    private PlanificacionRutasServiceImpl planificacionService;

    @Test
    void testCallbackIdempotenteNoDuplicaRutas() {
        Ruta rutaExistente = new Ruta();
        rutaExistente.setLoteId("LOTE-DUPLICADO");
        when(rutaRepository.findByLoteId("LOTE-DUPLICADO")).thenReturn(List.of(rutaExistente));

        PlanificadorCallbackDto dto = new PlanificadorCallbackDto();
        dto.setLoteId("LOTE-DUPLICADO");

        Map<String, Object> respuesta = planificacionService.procesarCallback(dto);

        assertEquals("DUPLICADO_IGNORADO", respuesta.get("status"));
        verify(rutaRepository, never()).save(any(Ruta.class));
    }

    @Test
    void testCallbackCustodiaDonacionesSinAsignar() {
        when(rutaRepository.findByLoteId("LOTE-NUEVO")).thenReturn(List.of());
        when(rutaRepository.save(any(Ruta.class))).thenAnswer(inv -> inv.getArgument(0));

        PlanificadorCallbackDto dto = new PlanificadorCallbackDto();
        dto.setLoteId("LOTE-NUEVO");

        PlanificadorCallbackDto.RutaPropuestaDto prop = new PlanificadorCallbackDto.RutaPropuestaDto();
        prop.setIdCamion("CAM-01");
        prop.setPatenteCamion("AF123CD");

        PlanificadorCallbackDto.ParadaPropuestaDto parada = new PlanificadorCallbackDto.ParadaPropuestaDto();
        parada.setOrden(1);
        parada.setIdDonacion(101L);
        parada.setIdEntidad(1L);
        parada.setNombreEntidad("Entidad");
        parada.setDireccion("Direccion");
        parada.setLatitud(-34.0);
        parada.setLongitud(-58.0);
        prop.setParadas(List.of(parada));

        dto.setRutasGeneradas(List.of(prop));
        dto.setDonacionesSinAsignar(List.of(201L, 202L)); // Donaciones que excedieron capacidad

        Map<String, Object> respuesta = planificacionService.procesarCallback(dto);

        assertEquals("PROCESADO", respuesta.get("status"));
        assertEquals(2, respuesta.get("donacionesSinAsignarCustodiadas"));

        // Verificar que sincronizó a LISTA_PARA_ENTREGAR la donación asignada
        verify(donacionesServiceClient, times(1)).actualizarEstadoLote(
                eq(List.of(101L)), eq(EstadoDonacion.LISTA_PARA_ENTREGAR), anyString()
        );

        // Verificar que custodió las 2 donaciones no asignadas
        verify(pendienteLoteRepository, times(2)).save(any(DonacionPendienteLote.class));
    }
}
