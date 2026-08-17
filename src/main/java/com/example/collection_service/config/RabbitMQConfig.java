package com.example.collection_service.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_NAME = "insurance.exchange";
    public static final String DLQ_EXCHANGE_NAME = "insurance.dlq.exchange";
    public static final String DLQ_QUEUE_NAME = "insurance.dlq";

    // JSON Message Converter (Güncel Sınıf)
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    // Ana Exchange
    @Bean
    public TopicExchange insuranceExchange() {
        return new TopicExchange(EXCHANGE_NAME);
    }

    // Dead Letter Exchange
    @Bean
    public DirectExchange dlqExchange() {
        return new DirectExchange(DLQ_EXCHANGE_NAME);
    }

    // Dead Letter Queue
    @Bean
    public Queue dlqQueue() {
        return QueueBuilder.durable(DLQ_QUEUE_NAME).build();
    }

    // DLQ Binding
    @Bean
    public Binding dlqBinding() {
        return BindingBuilder.bind(dlqQueue()).to(dlqExchange()).with("insurance.dlq.routing");
    }
}