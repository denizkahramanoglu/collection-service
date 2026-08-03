package com.example.collection_service.service;

import com.example.collection_service.dto.*;
import com.iyzipay.Options;
import com.iyzipay.model.Payment;
import com.iyzipay.request.CreatePaymentRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IyzicoPaymentServiceTest {

    @Mock
    private Options options;

    @InjectMocks
    private IyzicoPaymentService iyzicoPaymentService;

    private ApplicationDetailResponseDTO appData;
    private CustomerCardResponseDTO selectedCard;

    @BeforeEach
    void setUp() {


        appData = mock(ApplicationDetailResponseDTO.class);
        CustomerResponseDTO customer = mock(CustomerResponseDTO.class);
        FullLocationResponseDTO address = mock(FullLocationResponseDTO.class);
        InsuranceProductResponseDTO product = mock(InsuranceProductResponseDTO.class);
        selectedCard = mock(CustomerCardResponseDTO.class);

        when(appData.getCustomer()).thenReturn(customer);
        when(appData.getProduct()).thenReturn(product);
        when(customer.getAddress()).thenReturn(address);
        when(customer.getFirstName()).thenReturn("Deniz");
        when(customer.getLastName()).thenReturn("Kahraman");
        when(product.getName()).thenReturn("Sigorta");
        when(appData.getCurrency()).thenReturn("TRY");
    }

    @Test
    void shouldPayWithIyzico_Successfully() {
        // Arrange
        String transactionId = "txn-12345";
        PaymentRequestDTO requestDTO = new PaymentRequestDTO();
        requestDTO.setInstallmentCount(3);
        requestDTO.setCvcNo("123");

        when(appData.getPrice()).thenReturn(new BigDecimal("1500.00"));

        // İyzico'dan dönecek olan sahte (mock) başarılı yanıt
        Payment mockSuccessResponse = new Payment();
        mockSuccessResponse.setStatus("success");

        // Act & Assert
        // Statik metodu try-with-resources bloğu içinde mock'luyoruz ki diğer testleri etkilemesin
        try (MockedStatic<Payment> mockedPayment = Mockito.mockStatic(Payment.class)) {

            mockedPayment.when(() -> Payment.create(any(CreatePaymentRequest.class), eq(options)))
                    .thenReturn(mockSuccessResponse);

            Payment result = iyzicoPaymentService.payWithIyzico(transactionId, requestDTO, appData, selectedCard);

            assertNotNull(result);
            assertEquals("success", result.getStatus());

            // Statik metodun gerçekten 1 kere çağırıldığını doğruluyoruz
            mockedPayment.verify(() -> Payment.create(any(CreatePaymentRequest.class), eq(options)));
        }
    }

    @Test
    void shouldPaySingleInstallmentWithIyzico_Failure() {
        // Arrange
        String transactionId = "txn-98765";
        BigDecimal installmentPrice = new BigDecimal("500.00");
        String cvc = "456";

        // İyzico'dan dönecek olan sahte (mock) reddedilmiş (failure) yanıt
        Payment mockFailureResponse = new Payment();
        mockFailureResponse.setStatus("failure");
        mockFailureResponse.setErrorMessage("Yetersiz bakiye");

        // Act & Assert
        try (MockedStatic<Payment> mockedPayment = Mockito.mockStatic(Payment.class)) {

            mockedPayment.when(() -> Payment.create(any(CreatePaymentRequest.class), eq(options)))
                    .thenReturn(mockFailureResponse);

            Payment result = iyzicoPaymentService.paySingleInstallmentWithIyzico(
                    transactionId, installmentPrice, appData, selectedCard, cvc);

            assertNotNull(result);
            assertEquals("failure", result.getStatus());
            assertEquals("Yetersiz bakiye", result.getErrorMessage());

            mockedPayment.verify(() -> Payment.create(any(CreatePaymentRequest.class), eq(options)));
        }

    }
    @Test
    void shouldPaySingleInstallmentWithIyzico_Successfully() {
        // Arrange
        String transactionId = "txn-67890";
        BigDecimal installmentPrice = new BigDecimal("500.00");
        String cvc = "123";

        // İyzico'dan dönecek olan sahte (mock) BAŞARILI yanıt
        Payment mockSuccessResponse = new Payment();
        mockSuccessResponse.setStatus("success");

        // Act & Assert
        try (org.mockito.MockedStatic<Payment> mockedPayment = org.mockito.Mockito.mockStatic(Payment.class)) {

            mockedPayment.when(() -> Payment.create(org.mockito.ArgumentMatchers.any(CreatePaymentRequest.class), org.mockito.ArgumentMatchers.eq(options)))
                    .thenReturn(mockSuccessResponse);

            Payment result = iyzicoPaymentService.paySingleInstallmentWithIyzico(
                    transactionId, installmentPrice, appData, selectedCard, cvc);

            org.junit.jupiter.api.Assertions.assertNotNull(result);
            org.junit.jupiter.api.Assertions.assertEquals("success", result.getStatus());

            mockedPayment.verify(() -> Payment.create(org.mockito.ArgumentMatchers.any(CreatePaymentRequest.class), org.mockito.ArgumentMatchers.eq(options)));
        }
    }
}