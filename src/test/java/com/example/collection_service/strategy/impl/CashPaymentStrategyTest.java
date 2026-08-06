package com.example.collection_service.strategy.impl;

import com.example.collection_service.dto.ApplicationDetailResponseDTO;
import com.example.collection_service.dto.PaymentRequestDTO;
import com.example.collection_service.enums.PaymentMethod;
import com.example.collection_service.enums.PaymentStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CashPaymentStrategyTest {

    private final CashPaymentStrategy cashPaymentStrategy = new CashPaymentStrategy();

    @Test
    void shouldReturnCashAsSupportedMethod() {
        assertEquals(PaymentMethod.CASH, cashPaymentStrategy.getSupportedMethod());
    }

    @Test
    void shouldProcessSuccessfully_Always() {
        // Arrange
        PaymentRequestDTO mockRequest = new PaymentRequestDTO();
        ApplicationDetailResponseDTO mockApp = new ApplicationDetailResponseDTO();
        String txId = "tx-123";

        // Act
        PaymentStatus result = cashPaymentStrategy.process(mockRequest, mockApp, txId);

        // Assert
        assertEquals(PaymentStatus.SUCCESS, result);
    }
}