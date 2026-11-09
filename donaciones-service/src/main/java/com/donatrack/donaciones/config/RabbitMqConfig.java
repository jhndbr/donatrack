package com.donatrack.donaciones.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
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

    public static final String DLX_NAME = "donatrack.dlx";
    public static final String DLQ_ROUTING_KEY = "notificaciones.dlq";

    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        objectMapper.findAndRegisterModules();
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public TopicExchange eventsExchange() {
        return new TopicExchange(exchangeName, true, false);
    }

    @Bean
    public Queue donacionAsignadaQueue() {
        return QueueBuilder.durable(queueDonacionAsignada)
                .withArgument("x-dead-letter-exchange", DLX_NAME)
                .withArgument("x-dead-letter-routing-key", DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue donanteInactivoQueue() {
        return QueueBuilder.durable(queueDonanteInactivo)
                .withArgument("x-dead-letter-exchange", DLX_NAME)
                .withArgument("x-dead-letter-routing-key", DLQ_ROUTING_KEY)
                .build();
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
