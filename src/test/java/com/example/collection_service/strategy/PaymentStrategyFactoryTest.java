package com.example.collection_service.strategy;

import com.example.collection_service.enums.PaymentMethod;
import com.example.collection_service.exception.BusinessException;
import com.example.collection_service.strategy.impl.CashPaymentStrategy;
import com.example.collection_service.strategy.impl.CreditCardPaymentStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaymentStrategyFactoryTest {

    private PaymentStrategyFactory factory;
    private PaymentStrategy creditCardStrategy;
    private PaymentStrategy cashStrategy;

    @BeforeEach
    void setUp() {
        // Stratejileri mockluyoruz
        creditCardStrategy = mock(CreditCardPaymentStrategy.class);
        when(creditCardStrategy.getSupportedMethod()).thenReturn(PaymentMethod.CREDIT_CARD);

        cashStrategy = mock(CashPaymentStrategy.class);
        when(cashStrategy.getSupportedMethod()).thenReturn(PaymentMethod.CASH);

        // Factory'ye mocklanmış strateji listesini veriyoruz
        List<PaymentStrategy> strategies = List.of(creditCardStrategy, cashStrategy);
        factory = new PaymentStrategyFactory(strategies);
    }

    @Test
    void shouldReturnCreditCardStrategy_WhenMethodIsCreditCard() {
        // Act
        PaymentStrategy result = factory.getStrategy(PaymentMethod.CREDIT_CARD);

        // Assert
        assertNotNull(result);
        assertEquals(PaymentMethod.CREDIT_CARD, result.getSupportedMethod());
    }

    @Test
    void shouldReturnCashStrategy_WhenMethodIsCash() {
        // Act
        PaymentStrategy result = factory.getStrategy(PaymentMethod.CASH);

        // Assert
        assertNotNull(result);
        assertEquals(PaymentMethod.CASH, result.getSupportedMethod());
    }

    @Test
    void shouldThrowException_WhenMethodIsNotSupported() {

        BusinessException exception = assertThrows(BusinessException.class,
                () -> factory.getStrategy(null));

        assertTrue(exception.getMessage().contains("Sistemde bu ödeme yöntemi desteklenmemektedir"));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
    }
}