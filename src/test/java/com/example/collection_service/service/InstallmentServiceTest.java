package com.example.collection_service.service;

import com.example.collection_service.client.ApplicationServiceClient;
import com.example.collection_service.dto.*;
import com.example.collection_service.entity.InstallmentPlanEntity;
import com.example.collection_service.entity.PaymentEntity;
import com.example.collection_service.enums.InstallmentStatus;
import com.example.collection_service.enums.PaymentStatus;
import com.example.collection_service.exception.BusinessException;
import com.example.collection_service.mapper.InstallmentMapper;
import com.example.collection_service.repository.InstallmentPlanRepository;
import com.example.collection_service.repository.PaymentRepository;
import com.iyzipay.model.Payment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InstallmentServiceTest {

    @Mock
    private InstallmentPlanRepository installmentRepository;

    @Mock
    private ApplicationServiceClient applicationServiceClient;

    @Mock
    private IyzicoPaymentService iyzicoPaymentService;

    @Mock
    private InstallmentMapper installmentMapper;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private Clock clock;

    @InjectMocks
    private InstallmentService installmentService;

    private PaymentEntity payment;
    private InstallmentPlanEntity mockInstallment;
    private InstallmentPayRequestDTO requestDTO;
    private ApplicationDetailResponseDTO appData;
    private CustomerCardResponseDTO mockCard;

    @BeforeEach
    void setUp() {
        requestDTO = new InstallmentPayRequestDTO();
        requestDTO.setCardId(1L);
        requestDTO.setCvc("123");

        // payInstallment() testleri için
        PaymentEntity parentPayment = PaymentEntity.builder()
                .applicationId(99L)
                .build();

        mockInstallment = InstallmentPlanEntity.builder()
                .id(10L)
                .amount(new BigDecimal("500.00"))
                .status(InstallmentStatus.UNPAID)
                .payment(parentPayment)
                .build();

        mockCard = new CustomerCardResponseDTO();
        mockCard.setId(1L);

        CustomerResponseDTO customer = new CustomerResponseDTO();
        customer.setCards(List.of(mockCard));

        appData = new ApplicationDetailResponseDTO();
        appData.setCustomer(customer);

        // splitPaymentIntoInstallments() testleri için
        payment = PaymentEntity.builder()
                .id(1L)
                .amount(new BigDecimal("1000.00"))
                .paymentStatus(PaymentStatus.SUCCESS)
                .installmentPlans(new ArrayList<>())
                .build();

        // DÜZELTME 3: Tüm testler Clock'u kullanmadığı için Mockito "UnnecessaryStubbingException"
        // hatası verir. Bunu engellemek için lenient() kullanıyoruz.
        lenient().when(clock.instant()).thenReturn(Instant.parse("2026-01-01T00:00:00Z"));
        lenient().when(clock.getZone()).thenReturn(ZoneId.systemDefault());
    }

    @Test
    void shouldPayInstallment_Successfully() {
        // Arrange
        when(installmentRepository.findById(10L)).thenReturn(Optional.of(mockInstallment));
        when(applicationServiceClient.getApplicationDetails(99L)).thenReturn(appData);

        Payment mockIyzicoResponse = new Payment();
        mockIyzicoResponse.setStatus("success");
        when(iyzicoPaymentService.paySingleInstallmentWithIyzico(anyString(), eq(new BigDecimal("500.00")), eq(appData), eq(mockCard), eq("123")))
                .thenReturn(mockIyzicoResponse);

        when(installmentRepository.save(any(InstallmentPlanEntity.class))).thenReturn(mockInstallment);

        InstallmentResponseDTO expectedResponse = new InstallmentResponseDTO();
        when(installmentMapper.toResponseDTO(mockInstallment)).thenReturn(expectedResponse);

        // Act
        InstallmentResponseDTO result = installmentService.payInstallment(10L, requestDTO);

        // Assert
        assertNotNull(result);
        assertEquals(InstallmentStatus.PAID, mockInstallment.getStatus());

        verify(installmentRepository).findById(10L);
        verify(applicationServiceClient).getApplicationDetails(99L);
        verify(installmentRepository).save(mockInstallment);
    }

    @Test
    void shouldThrowException_WhenInstallmentAlreadyPaid() {
        // Arrange
        mockInstallment.setStatus(InstallmentStatus.PAID);
        when(installmentRepository.findById(10L)).thenReturn(Optional.of(mockInstallment));

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class,
                () -> installmentService.payInstallment(10L, requestDTO));

        assertEquals("Bu taksit zaten ödenmiş!", exception.getMessage());
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());

        verifyNoInteractions(applicationServiceClient);
        verifyNoInteractions(iyzicoPaymentService);
    }

    @Test
    void shouldThrowException_WhenCardNotFoundInCustomerList() {
        // Arrange
        requestDTO.setCardId(999L);
        when(installmentRepository.findById(10L)).thenReturn(Optional.of(mockInstallment));
        when(applicationServiceClient.getApplicationDetails(99L)).thenReturn(appData);

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class,
                () -> installmentService.payInstallment(10L, requestDTO));

        assertEquals("Seçilen kart müşteride bulunamadı!", exception.getMessage());
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());

        verifyNoInteractions(iyzicoPaymentService);
    }

    @Test
    void shouldThrowException_WhenIyzicoPaymentFails() {
        // Arrange
        when(installmentRepository.findById(10L)).thenReturn(Optional.of(mockInstallment));
        when(applicationServiceClient.getApplicationDetails(99L)).thenReturn(appData);

        Payment mockFailureResponse = new Payment();
        mockFailureResponse.setStatus("failure");
        mockFailureResponse.setErrorMessage("Bakiye Yetersiz");

        when(iyzicoPaymentService.paySingleInstallmentWithIyzico(anyString(), any(), any(), any(), any()))
                .thenReturn(mockFailureResponse);

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class,
                () -> installmentService.payInstallment(10L, requestDTO));

        assertTrue(exception.getMessage().contains("Ödeme reddedildi: Bakiye Yetersiz"));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());

        verify(installmentRepository, never()).save(any());
    }

    @Test
    void shouldSplitPaymentIntoInstallmentsSuccessfully() {
        // Arrange
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        // Not: paymentRepository.save() dönüş değeri kullanılmadığı için mock'lamaya gerek yoktur.

        // Act
        installmentService.splitPaymentIntoInstallments(1L, 4);

        // Assert
        assertEquals(4, payment.getInstallmentPlans().size());
        assertEquals(new BigDecimal("250.00"), payment.getInstallmentPlans().get(0).getAmount());
        assertEquals(InstallmentStatus.PAID, payment.getInstallmentPlans().get(0).getStatus());

        verify(paymentRepository).save(payment);
    }

    @Test
    void shouldThrowExceptionWhenPaymentNotFound() {
        // Arrange
        when(paymentRepository.findById(1L)).thenReturn(Optional.empty());

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class,
                () -> installmentService.splitPaymentIntoInstallments(1L, 3));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
        assertTrue(exception.getMessage().contains("Ödeme kaydı bulunamadı"));
    }

    @Test
    void shouldThrowExceptionWhenInstallmentCountIsZero() {
        // Arrange
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class,
                () -> installmentService.splitPaymentIntoInstallments(1L, 0));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertEquals("Taksit sayısı 0'dan büyük olmalıdır!", exception.getMessage());

        verify(paymentRepository, never()).save(any());
    }

    @Test
    void shouldThrowExceptionWhenPaymentAlreadySplit() {
        // Arrange
        payment.setInstallmentPlans(new ArrayList<>(List.of(new InstallmentPlanEntity())));
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class,
                () -> installmentService.splitPaymentIntoInstallments(1L, 3));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertEquals("Bu ödeme zaten taksitlendirilmiş!", exception.getMessage());

        verify(paymentRepository, never()).save(any());
    }

    @Test
    void shouldNotMarkFirstInstallmentAsPaidWhenPaymentIsNotSuccess() {
        // Arrange
        payment.setPaymentStatus(PaymentStatus.FAILED);
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

        // Act
        installmentService.splitPaymentIntoInstallments(1L, 2);

        // Assert
        assertEquals(InstallmentStatus.UNPAID, payment.getInstallmentPlans().get(0).getStatus());
        verify(paymentRepository).save(payment);
    }
    @Test
    void shouldThrowException_WhenInstallmentNotFound() {
        // Arrange: findById Optional.empty() dönmeli
        when(installmentRepository.findById(10L)).thenReturn(Optional.empty());

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class,
                () -> installmentService.payInstallment(10L, requestDTO));

        assertEquals("Taksit bulunamadı!", exception.getMessage());
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());

        // Diğer servislerin çağrılmadığını doğrulayalım
        verifyNoInteractions(applicationServiceClient);
        verifyNoInteractions(iyzicoPaymentService);
    }

    @Test
    void shouldSplitPayment_WhenInstallmentPlansIsNull() {
        // Arrange: payment'ın installmentPlans listesi null olmalı
        payment.setInstallmentPlans(null);
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

        // Act
        installmentService.splitPaymentIntoInstallments(1L, 3);

        // Assert
        assertNotNull(payment.getInstallmentPlans());
        assertEquals(3, payment.getInstallmentPlans().size());
        assertEquals(InstallmentStatus.PAID, payment.getInstallmentPlans().get(0).getStatus());

        verify(paymentRepository).save(payment);
    }
}