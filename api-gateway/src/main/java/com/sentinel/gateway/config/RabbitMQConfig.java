package com.sentinel.gateway.config;

import com.sentinel.common.dto.RabbitTopology;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Bean
    public DirectExchange scanJobsExchange() {
        return ExchangeBuilder.directExchange(RabbitTopology.SCAN_JOBS_EXCHANGE).durable(true).build();
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return ExchangeBuilder.directExchange(RabbitTopology.DLX_EXCHANGE).durable(true).build();
    }

    @Bean
    public Queue scanJobsQueue() {
        return QueueBuilder.durable(RabbitTopology.SCAN_JOBS_QUEUE)
                .withArgument("x-dead-letter-exchange", RabbitTopology.DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", RabbitTopology.DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue scanJobsDeadLetterQueue() {
        return QueueBuilder.durable(RabbitTopology.SCAN_JOBS_DLQ)
                .withArgument("x-dead-letter-exchange", RabbitTopology.SCAN_JOBS_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", RabbitTopology.SCAN_JOBS_ROUTING_KEY)
                .withArgument("x-message-ttl", RabbitTopology.RETRY_DELAY_MS)
                .build();
    }

    @Bean
    public Binding scanJobsBinding() {
        return BindingBuilder.bind(scanJobsQueue()).to(scanJobsExchange())
                .with(RabbitTopology.SCAN_JOBS_ROUTING_KEY);
    }

    @Bean
    public Binding deadLetterBinding() {
        return BindingBuilder.bind(scanJobsDeadLetterQueue()).to(deadLetterExchange())
                .with(RabbitTopology.DLQ_ROUTING_KEY);
    }

    @Bean
    public Jackson2JsonMessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                          Jackson2JsonMessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        template.setExchange(RabbitTopology.SCAN_JOBS_EXCHANGE);
        template.setRoutingKey(RabbitTopology.SCAN_JOBS_ROUTING_KEY);
        return template;
    }
}
