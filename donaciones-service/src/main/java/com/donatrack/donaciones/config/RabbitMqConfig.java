package com.donatrack.donaciones.config;

import org.springframework.amqp.core.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    @Value("${donatrack.rabbitmq.exchange:donatrack.events.exchange}")
    private String exchangeName;

    @Value("${donatrack.rabbitmq.queue.donacion-asignada:q.notificaciones.donacion-asignada}")
    private String queueDonacionAsignada;

    @Value("${donatrack.rabbitmq.queue.donante-inactivo:q.notificaciones.donante-inactivo}")
    private String queueDonanteInactivo;

    @Value("${donatrack.rabbitmq.routing-key.donacion-asignada:donacion.asignada}")
    private String routingKeyDonacionAsignada;

    @Value("${donatrack.rabbitmq.routing-key.donante-inactivo:donante.inactivo}")
    private String routingKeyDonanteInactivo;

    @Bean
    public TopicExchange eventsExchange() {
        return new TopicExchange(exchangeName, true, false);
    }

    @Bean
    public Queue donacionAsignadaQueue() {
        return new Queue(queueDonacionAsignada, true);
    }

    @Bean
    public Queue donanteInactivoQueue() {
        return new Queue(queueDonanteInactivo, true);
    }

    @Bean
    public Binding bindingDonacionAsignada(@org.springframework.beans.factory.annotation.Qualifier("donacionAsignadaQueue") Queue donacionAsignadaQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(donacionAsignadaQueue).to(eventsExchange).with(routingKeyDonacionAsignada);
    }

    @Bean
    public Binding bindingDonanteInactivo(@org.springframework.beans.factory.annotation.Qualifier("donanteInactivoQueue") Queue donanteInactivoQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(donanteInactivoQueue).to(eventsExchange).with(routingKeyDonanteInactivo);
    }
}
