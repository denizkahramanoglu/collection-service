package com.example.collection_service.strategy.impl;

import com.example.collection_service.dto.ApplicationDetailResponseDTO;
import com.example.collection_service.dto.PaymentRequestDTO;
import com.example.collection_service.enums.PaymentMethod;
import com.example.collection_service.enums.PaymentStatus;
import com.example.collection_service.strategy.PaymentStrategy;
import org.springframework.stereotype.Component;

@Component // Spring'in bu sınıfı bulup List içine atabilmesi için gerekli
public class CashPaymentStrategy implements PaymentStrategy {

    @Override
    public PaymentMethod getSupportedMethod() {
        return PaymentMethod.CASH;
    }

    @Override
    public PaymentStatus process(PaymentRequestDTO requestDTO, ApplicationDetailResponseDTO appData, String transactionId) {
        // Nakit ödemelerde banka onayı gerekmediği için doğrudan SUCCESS dönüyoruz.
        return PaymentStatus.SUCCESS;
    }
}