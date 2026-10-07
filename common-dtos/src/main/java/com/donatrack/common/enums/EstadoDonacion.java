package com.donatrack.common.enums;

/**
 * Estados del ciclo de vida de una Donacion segun la maquina de estados de la catedra.
 */
public enum EstadoDonacion {
    EN_DEPOSITO,
    ASIGNACION_REALIZADA,
    LISTA_PARA_ENTREGAR,
    EN_TRASLADO,
    ENTREGADA,
    ENTREGA_FALLIDA,
    VENCIDA
}
