package com.example.collection_service.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentCompletedEvent {
    private String eventId;
    private Long paymentId;
    private Long applicationId;
    private BigDecimal amount;
    private String currency;
    private String paymentMethod;
    private Integer installmentCount;
    private String transactionId;
    private LocalDateTime paymentDate;
}