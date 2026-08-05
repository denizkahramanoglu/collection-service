package com.example.collection_service.strategy;

import com.example.collection_service.dto.ApplicationDetailResponseDTO;
import com.example.collection_service.dto.PaymentRequestDTO;
import com.example.collection_service.enums.PaymentMethod;
import com.example.collection_service.enums.PaymentStatus;

public interface PaymentStrategy {

    // Bu stratejinin hangi ödeme yöntemini (CASH, CREDIT_CARD vb.) desteklediğini döner.
    PaymentMethod getSupportedMethod();

    // Asıl iş kuralının ve ödeme işleminin yapılacağı metot.
    PaymentStatus process(PaymentRequestDTO requestDTO, ApplicationDetailResponseDTO appData, String transactionId);
}