package com.example.collection_service.dto;

import com.example.collection_service.enums.PaymentMethod;
import com.example.collection_service.enums.PaymentStatus;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
public class PaymentResponseDTO {
    private Long id;
    private Long applicationId;
    private BigDecimal amount;
    private String currency;
    private String transactionId;
    private PaymentMethod paymentMethod;
    private PaymentStatus paymentStatus;
    private List<InstallmentPlanDTO> installmentPlans;
}