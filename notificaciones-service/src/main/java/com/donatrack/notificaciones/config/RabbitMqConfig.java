package com.donatrack.notificaciones.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RabbitMqConfig {

    @Value("${donatrack.rabbitmq.exchange:donatrack.events.exchange}")
    private String exchangeName;

    @Value("${donatrack.rabbitmq.queues.donacion-registrada:q.notificaciones.donacion-registrada}")
    private String qDonacionRegistrada;

    @Value("${donatrack.rabbitmq.queues.donacion-asignada:q.notificaciones.donacion-asignada}")
    private String qDonacionAsignada;

    @Value("${donatrack.rabbitmq.queues.donante-inactivo:q.notificaciones.donante-inactivo}")
    private String qDonanteInactivo;

    @Value("${donatrack.rabbitmq.queues.donante-registrado:q.notificaciones.donante-registrado}")
    private String qDonanteRegistrado;

    @Value("${donatrack.rabbitmq.queues.ruta-iniciada:q.notificaciones.ruta-iniciada}")
    private String qRutaIniciada;

    @Value("${donatrack.rabbitmq.queues.entrega-confirmada:q.notificaciones.entrega-confirmada}")
    private String qEntregaConfirmada;

    @Value("${donatrack.rabbitmq.queues.entrega-fallida:q.notificaciones.entrega-fallida}")
    private String qEntregaFallida;

    @Value("${donatrack.rabbitmq.queues.camion-en-puerta:q.notificaciones.camion-en-puerta}")
    private String qCamionEnPuerta;

    @Value("${donatrack.rabbitmq.queues.mision-cumplida:q.notificaciones.mision-cumplida}")
    private String qMisionCumplida;

    @Value("${donatrack.rabbitmq.queues.categoria-ascendida:q.notificaciones.categoria-ascendida}")
    private String qCategoriaAscendida;

    public static final String DLX_NAME = "donatrack.dlx";
    public static final String DLQ_NAME = "dlq.notificaciones";
    public static final String DLQ_ROUTING_KEY = "notificaciones.dlq";

    @Bean
    public MessageConverter jsonMessageConverter(com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        objectMapper.findAndRegisterModules();
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        return template;
    }

    @Bean
    public TopicExchange eventsExchange() {
        return new TopicExchange(exchangeName, true, false);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DLX_NAME, true, false);
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(DLQ_NAME).build();
    }

    @Bean
    public Binding dlqBinding(Queue deadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with(DLQ_ROUTING_KEY);
    }

    private Queue crearCola(String nombreCola) {
        return QueueBuilder.durable(nombreCola)
                .withArgument("x-dead-letter-exchange", DLX_NAME)
                .withArgument("x-dead-letter-routing-key", DLQ_ROUTING_KEY)
                .build();
    }

    // --- Colas de Notificaciones ---

    @Bean
    public Queue donacionRegistradaQueue() {
        return crearCola(qDonacionRegistrada);
    }

    @Bean
    public Queue donacionAsignadaQueue() {
        return crearCola(qDonacionAsignada);
    }

    @Bean
    public Queue donanteInactivoQueue() {
        return crearCola(qDonanteInactivo);
    }

    @Bean
    public Queue donanteRegistradoQueue() {
        return crearCola(qDonanteRegistrado);
    }

    @Bean
    public Queue rutaIniciadaQueue() {
        return crearCola(qRutaIniciada);
    }

    @Bean
    public Queue entregaConfirmadaQueue() {
        return crearCola(qEntregaConfirmada);
    }

    @Bean
    public Queue entregaFallidaQueue() {
        return crearCola(qEntregaFallida);
    }

    @Bean
    public Queue camionEnPuertaQueue() {
        return crearCola(qCamionEnPuerta);
    }

    @Bean
    public Queue misionCumplidaQueue() {
        return crearCola(qMisionCumplida);
    }

    @Bean
    public Queue categoriaAscendidaQueue() {
        return crearCola(qCategoriaAscendida);
    }

    // --- Bindings con el Exchange Central ---

    @Bean
    public Binding bindDonacionRegistrada(Queue donacionRegistradaQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(donacionRegistradaQueue).to(eventsExchange).with("donacion.registrada");
    }

    @Bean
    public Binding bindDonacionAsignada(Queue donacionAsignadaQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(donacionAsignadaQueue).to(eventsExchange).with("donacion.asignada");
    }

    @Bean
    public Binding bindDonanteInactivo(Queue donanteInactivoQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(donanteInactivoQueue).to(eventsExchange).with("donante.inactivo");
    }

    @Bean
    public Binding bindDonanteRegistrado(Queue donanteRegistradoQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(donanteRegistradoQueue).to(eventsExchange).with("donante.registrado");
    }

    @Bean
    public Binding bindRutaIniciada(Queue rutaIniciadaQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(rutaIniciadaQueue).to(eventsExchange).with("ruta.iniciada");
    }

    @Bean
    public Binding bindEntregaConfirmada(Queue entregaConfirmadaQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(entregaConfirmadaQueue).to(eventsExchange).with("entrega.confirmada");
    }

    @Bean
    public Binding bindEntregaFallida(Queue entregaFallidaQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(entregaFallidaQueue).to(eventsExchange).with("entrega.fallida");
    }

    @Bean
    public Binding bindCamionEnPuerta(Queue camionEnPuertaQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(camionEnPuertaQueue).to(eventsExchange).with("camion.en-puerta");
    }

    @Bean
    public Binding bindMisionCumplida(Queue misionCumplidaQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(misionCumplidaQueue).to(eventsExchange).with("mision.cumplida");
    }

    @Bean
    public Binding bindCategoriaAscendida(Queue categoriaAscendidaQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(categoriaAscendidaQueue).to(eventsExchange).with("categoria.ascendida");
    }
}
