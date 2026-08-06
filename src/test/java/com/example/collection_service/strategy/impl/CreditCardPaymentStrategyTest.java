package com.example.collection_service.strategy.impl;

import com.example.collection_service.dto.ApplicationDetailResponseDTO;
import com.example.collection_service.dto.CustomerCardResponseDTO;
import com.example.collection_service.dto.CustomerResponseDTO;
import com.example.collection_service.dto.PaymentRequestDTO;
import com.example.collection_service.enums.PaymentMethod;
import com.example.collection_service.enums.PaymentStatus;
import com.example.collection_service.exception.BusinessException;
import com.example.collection_service.service.IyzicoPaymentService;
import com.iyzipay.model.Payment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreditCardPaymentStrategyTest {

    @Mock
    private IyzicoPaymentService iyzicoPaymentService;

    @InjectMocks
    private CreditCardPaymentStrategy creditCardPaymentStrategy;

    private PaymentRequestDTO requestDTO;
    private ApplicationDetailResponseDTO appData;
    private CustomerCardResponseDTO customerCard;
    private final String transactionId = "txn-12345";

    @BeforeEach
    void setUp() {
        requestDTO = new PaymentRequestDTO();
        requestDTO.setCvcNo("123");
        requestDTO.setCardId(1L);

        customerCard = new CustomerCardResponseDTO();
        customerCard.setId(1L);

        CustomerResponseDTO customer = new CustomerResponseDTO();
        customer.setCards(List.of(customerCard));

        appData = new ApplicationDetailResponseDTO();
        appData.setCustomer(customer);
    }

    @Test
    void shouldReturnCreditCardAsSupportedMethod() {
        assertEquals(PaymentMethod.CREDIT_CARD, creditCardPaymentStrategy.getSupportedMethod());
    }

    @Test
    void shouldThrowException_WhenCvcIsNull() {
        // Arrange
        requestDTO.setCvcNo(null);

        // Act & Assert
        BusinessException ex = assertThrows(BusinessException.class,
                () -> creditCardPaymentStrategy.process(requestDTO, appData, transactionId));

        assertEquals("Kredi kartı ile ödemelerde CVC numarası zorunludur!", ex.getMessage());
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verifyNoInteractions(iyzicoPaymentService);
    }

    @Test
    void shouldThrowException_WhenCvcIsEmptyOrWhitespace() {
        // Arrange
        requestDTO.setCvcNo("   "); // Boşluk (whitespace) gönderiyoruz

        // Act & Assert
        BusinessException ex = assertThrows(BusinessException.class,
                () -> creditCardPaymentStrategy.process(requestDTO, appData, transactionId));

        assertEquals("Kredi kartı ile ödemelerde CVC numarası zorunludur!", ex.getMessage());
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verifyNoInteractions(iyzicoPaymentService);
    }

    @Test
    void shouldThrowException_WhenCardIdIsNull() {
        // Arrange
        requestDTO.setCardId(null);

        // Act & Assert
        BusinessException ex = assertThrows(BusinessException.class,
                () -> creditCardPaymentStrategy.process(requestDTO, appData, transactionId));

        assertEquals("Kredi kartı ile ödemelerde kart seçimi zorunludur!", ex.getMessage());
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verifyNoInteractions(iyzicoPaymentService);
    }

    @Test
    void shouldThrowException_WhenCardNotFoundInCustomerList() {
        // Arrange: Kullanıcının kart listesinde 1L var ama requestte 999L geliyor.
        requestDTO.setCardId(999L);

        // Act & Assert
        BusinessException ex = assertThrows(BusinessException.class,
                () -> creditCardPaymentStrategy.process(requestDTO, appData, transactionId));

        assertEquals("Seçilen kart bulunamadı veya bu müşteriye ait değil!", ex.getMessage());
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        verifyNoInteractions(iyzicoPaymentService);
    }

    @Test
    void shouldProcessSuccessfully_WhenIyzicoReturnsSuccess() {
        // Arrange
        Payment mockIyzicoResponse = new Payment();
        mockIyzicoResponse.setStatus("success");

        when(iyzicoPaymentService.payWithIyzico(anyString(), any(), any(), any())).thenReturn(mockIyzicoResponse);

        // Act
        PaymentStatus result = creditCardPaymentStrategy.process(requestDTO, appData, transactionId);

        // Assert
        assertEquals(PaymentStatus.SUCCESS, result);
    }

    @Test
    void shouldThrowException_WhenIyzicoReturnsFailure() {
        // Arrange
        Payment mockIyzicoResponse = new Payment();
        mockIyzicoResponse.setStatus("failure");
        mockIyzicoResponse.setErrorMessage("Bakiye yetersiz.");

        when(iyzicoPaymentService.payWithIyzico(anyString(), any(), any(), any())).thenReturn(mockIyzicoResponse);

        // Act & Assert
        BusinessException ex = assertThrows(BusinessException.class,
                () -> creditCardPaymentStrategy.process(requestDTO, appData, transactionId));

        assertTrue(ex.getMessage().contains("Ödeme banka tarafından reddedildi"));
        assertTrue(ex.getMessage().contains("Bakiye yetersiz."));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }
}
