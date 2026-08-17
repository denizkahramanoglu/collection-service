package com.example.collection_service.producer;

import com.example.collection_service.config.RabbitMQConfig;
import com.example.collection_service.event.PaymentCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentProducer {

    private final RabbitTemplate rabbitTemplate;

    public void publishPaymentCompletedEvent(PaymentCompletedEvent event) {
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, "payment.completed", event);
        log.info("PaymentCompletedEvent published. eventId={}, paymentId={}", event.getEventId(), event.getPaymentId());
    }
}