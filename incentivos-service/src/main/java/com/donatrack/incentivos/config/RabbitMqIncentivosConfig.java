package com.donatrack.incentivos.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqIncentivosConfig {

    @Value("${donatrack.rabbitmq.exchange:donatrack.events.exchange}")
    private String exchangeName;

    @Value("${donatrack.rabbitmq.queues.incentivos-donaciones:q.incentivos.donaciones}")
    private String qIncentivosDonaciones;

    @Value("${donatrack.rabbitmq.queues.incentivos-entregas:q.incentivos.entregas}")
    private String qIncentivosEntregas;

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
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
    public Queue incentivosDonacionesQueue() {
        return QueueBuilder.durable(qIncentivosDonaciones).build();
    }

    @Bean
    public Queue incentivosEntregasQueue() {
        return QueueBuilder.durable(qIncentivosEntregas).build();
    }

    @Bean
    public Binding bindIncentivosDonaciones(Queue incentivosDonacionesQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(incentivosDonacionesQueue).to(eventsExchange).with("donacion.asignada");
    }

    @Bean
    public Binding bindIncentivosEntregas(Queue incentivosEntregasQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(incentivosEntregasQueue).to(eventsExchange).with("entrega.confirmada");
    }
}
