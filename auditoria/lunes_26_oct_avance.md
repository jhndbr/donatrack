 ### 1. Auditoría de Notificaciones / Mails e Indicador Visual Numérico Rojo (Badge)

  • Estado de lectura y conteo en notificaciones-service:
      • En AuditoriaNotificacion se incorporaron los atributos leida (boolean, por defecto false), fechaLectura y el
      método de dominio marcarComoLeida().
      • En NotificacionesController se agregaron los endpoints:
          • GET /api/v1/notificaciones/no-leidas/count?destinatario=...
          • POST /api/v1/notificaciones/{id}/leer
          • POST /api/v1/notificaciones/leer-todas?destinatario=...
      • Además, se conectó el evento donacion.registrada en RabbitMqConfig, DonacionNotificacionesConsumer y
      NotificacionesService.notificarDonacionRegistrada para notificar al donante apenas registra una donación en
      depósito (EN_DEPOSITO).
  • Indicador rojo (badge) global en frontend-service:
      • Se creó GlobalNotificationsAdvice (@ControllerAdvice) que inyecta automáticamente
      notificacionesNoLeidasCount en todas las vistas según el usuario autenticado.
      • En layout.html:178-245 se agregaron los contadores rojos (badge rounded-pill bg-danger) en los enlaces de
      navegación (Mis Notificaciones, Notificaciones, Auditoría Notificaciones, Buzón Correos) y el botón de campana
      en la barra superior.
      • En notificaciones.html, notificaciones.html y notificaciones.html se añadió la etiqueta roja NUEVA, vista
      previa del mensaje en modal y botones para marcar como leída individualmente o todas a la vez.
  ──────
  ### 2. Mejoras en Registro y Actualización de Necesidades Materiales (/beneficiaria/necesidades)

  • Enum PeriodoNecesidad y Menú Desplegable:
      • Se creó PeriodoNecesidad en common-dtos (DIARIA, SEMANAL, QUINCENAL, MENSUAL, BIMESTRAL, TRIMESTRAL,
      SEMESTRAL, ANUAL).
  • Activación Condicional de Periodicidad (EXTRAORDINARIA vs RECURRENTE):
      • En necesidades.html:50-108, el campo Periodicidad es un <select> que permanece deshabilitado (disabled)
      cuando el tipo es EXTRAORDINARIA, y solo se habilita y vuelve obligatorio (required) cuando se selecciona
      RECURRENTE.
      • En EntidadBeneficiariaService y EntidadBeneficiariaController, si el tipo es EXTRAORDINARIA el período se
      limpia a null, y si es RECURRENTE se normaliza contra PeriodoNecesidad.
  • Autocompletado Semántico + Modal de Actualización con Cuadro de Confirmación:
      • Se incorporó un <datalist> con las subcategorías estándar de donaciones para garantizar coincidencia
      semántica en el Matchmaking.
      • Se reparó la actualización integral de necesidades (subcategoria, cantidadSolicitada >= cantidadCubierta,
      unidadMedida, tipo, periodo, descripcion) e incorporó un Modal de Edición con Cuadro de Confirmación
      (#modalEditarNecesidad-{id}) y un Modal de Confirmación de Baja (#modalEliminarNecesidad-{id}) en
      necesidades.html:160-295.

  ──────
  ### 3. Flujo de Chofer: Geolocalización del Navegador, Mapa en Vivo y Cronjob cada 5 Minutos

  • Cronjob cada 5 minutos en Modo Demo + Disparo Bajo Demanda:
      • En LogisticaScheduler y application.yml:36-38, el planificador automático de rutas se configuró con
      ${donatrack.cron.planificacion-rutas:0 */5 * * * *} (cada 5 minutos).
      • Además, se expuso POST /api/v1/logistica/rutas/planificar-pendientes en RutaController y el botón "Generar
      Rutas Pendientes Ahora" en ruta.html:17-21 para generar rutas al instante durante demostraciones.
      • En PlanificacionRutasServiceImpl.procesarCallback se enriquecen también las Paradas de la Ruta con el nombre
      real de la entidad, dirección normalizada, coordenadas y detalle de la donación.
  • Geolocalización desde el Navegador (navigator.geolocation) y Mapa Leaflet:
      • En ruta.html:156-218 y ChoferWebController.reportarTelemetriaAjax, el chofer puede:
          1. Presionar "Tomar Mi Ubicación GPS del Navegador y Transmitir" (navigator.geolocation.
	  getCurrentPosition).
	  2. Activar el switch "Compartir ubicación en vivo automáticamente (cada 15 seg)".
	  3. Hacer clic en cualquier punto del Mapa Interactivo Leaflet o en "Mover GPS aquí" sobre una parada para
	  simular el desplazamiento del camión sin recargar la página.


  ──────
  ### 4. Contrato Sincronizado de Recepción entre Chofer y Receptor de Entidad Beneficiaria

  • Sincronización bidireccional Ruta (Parada) <-> Entrega <-> Donacion:
      • En EntregaServiceImpl, cada cambio de etapa sincroniza automáticamente el estado de la Parada dentro de la
      Ruta, el documento Entrega en MongoDB y la Donacion en PostgreSQL:
	  1. Retiro en Depósito (CARGADA_EN_CAMION / EN_TRASLADO): El chofer confirma el retiro e inicia la ruta.
	  2. Arribo a Puerta de Sede (EN_PUERTA_SEDE): El chofer presiona "En Puerta" (POST
	  /chofer/entregas/{idDonacion}/arribo), posicionando el GPS del camión en la sede y mostrando en
          confirmar.html:64-87 el aviso 🚚 ¡CHOFER EN PUERTA ESPERANDO RECEPCIÓN!.
	  3. Acta de Entrega (RECEPCION_CONFIRMADA / ENTREGADA) o Registro de Incidencia por Etapa
	  (INCIDENCIA_REGISTRADA / ENTREGA_FALLIDA): Tanto si confirma/rechaza el Chofer como si lo hace el Receptor
	  de la Entidad Beneficiaria, ambas vistas quedan 100% sincronizadas registrando nombre y DNI del receptor,
	  fotografía comprobante o etapa y motivo justificado del fallo.
