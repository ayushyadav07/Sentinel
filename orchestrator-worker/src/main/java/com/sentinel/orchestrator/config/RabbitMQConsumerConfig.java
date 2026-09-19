package com.sentinel.orchestrator.config;

import com.sentinel.common.dto.RabbitTopology;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConsumerConfig {

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
    public SimpleMessageListenerContainer scanJobsListenerContainer(
            ConnectionFactory connectionFactory, @Qualifier("scanJobListener")
            org.springframework.amqp.core.MessageListener scanJobMessageListener) {

        SimpleMessageListenerContainer container =
                new SimpleMessageListenerContainer(connectionFactory);

        container.setQueueNames(RabbitTopology.SCAN_JOBS_QUEUE);
        container.setPrefetchCount(1);
        container.setConcurrentConsumers(1);
        container.setMaxConcurrentConsumers(1);
        container.setAcknowledgeMode(org.springframework.amqp.core.AcknowledgeMode.MANUAL);
        container.setMessageListener(scanJobMessageListener);

        return container;
    }
}
