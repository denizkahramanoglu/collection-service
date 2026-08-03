package com.example.collection_service.service;

import com.example.collection_service.client.ApplicationServiceClient;
import com.example.collection_service.dto.*;
import com.example.collection_service.entity.PaymentEntity;
import com.example.collection_service.enums.PaymentMethod;
import com.example.collection_service.exception.BusinessException;
import com.example.collection_service.mapper.PaymentMapper;
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
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CollectionServiceTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PaymentMapper paymentMapper;
    @Mock
    private IyzicoPaymentService iyzicoPaymentService;
    @Mock
    private Clock clock;
    @Mock
    private ApplicationServiceClient applicationServiceClient;

    @InjectMocks
    private CollectionService collectionService;

    private PaymentRequestDTO requestDTO;
    private ApplicationDetailResponseDTO appData;
    private CustomerCardResponseDTO mockCard;

    @BeforeEach
    void setUp() {
        // Zamanı sabitliyoruz (Örn: 3 Ağustos 2026). Böylece taksit tarihleri hep öngörülebilir olacak.
        Clock fixedClock = Clock.fixed(Instant.parse("2026-08-03T10:00:00Z"), ZoneId.of("UTC"));
        lenient().when(clock.instant()).thenReturn(fixedClock.instant());
        lenient().when(clock.getZone()).thenReturn(fixedClock.getZone());

        // Ortak Request Nesnesi Hazırlığı
        requestDTO = new PaymentRequestDTO();
        requestDTO.setApplicationId(100L);
        requestDTO.setCardId(1L);
        requestDTO.setPaymentMethod(PaymentMethod.CREDIT_CARD); // Senin enum adına göre burayı güncelle
        requestDTO.setInstallmentCount(3);

        // Ortak Application Data Nesnesi Hazırlığı
        appData = new ApplicationDetailResponseDTO();
        appData.setPrice(new BigDecimal("1500.00"));
        appData.setCurrency("TRY");

        mockCard = new CustomerCardResponseDTO();
        mockCard.setId(1L);
        mockCard.setCardNumber("4543********1234");

        CustomerResponseDTO customer = new CustomerResponseDTO();
        customer.setCards(List.of(mockCard));
        appData.setCustomer(customer);
    }

    @Test
    void shouldProcessCollection_CreditCard_Successfully() {
        // Arrange
        when(applicationServiceClient.getApplicationDetails(100L)).thenReturn(appData);

        Payment mockIyzicoResponse = new Payment();
        mockIyzicoResponse.setStatus("success");
        when(iyzicoPaymentService.payWithIyzico(anyString(), eq(requestDTO), eq(appData), eq(mockCard)))
                .thenReturn(mockIyzicoResponse);

        PaymentEntity savedPayment = PaymentEntity.builder().build();
        when(paymentRepository.save(any(PaymentEntity.class))).thenReturn(savedPayment);

        PaymentResponseDTO expectedResponse = new PaymentResponseDTO();
        when(paymentMapper.toResponseDTO(savedPayment)).thenReturn(expectedResponse);

        // Act
        PaymentResponseDTO result = collectionService.processCollection(requestDTO);

        // Assert
        assertNotNull(result);
        verify(paymentRepository).save(any(PaymentEntity.class));
        verify(iyzicoPaymentService).payWithIyzico(anyString(), eq(requestDTO), eq(appData), eq(mockCard));
    }

    @Test
    void shouldProcessCollection_BankTransfer_Successfully() {
        // Arrange
        requestDTO.setPaymentMethod(PaymentMethod.BANK_TRANSFER); // Enum adını kendi projene göre düzenle
        when(applicationServiceClient.getApplicationDetails(100L)).thenReturn(appData);

        PaymentEntity savedPayment = PaymentEntity.builder().build();
        when(paymentRepository.save(any(PaymentEntity.class))).thenReturn(savedPayment);
        when(paymentMapper.toResponseDTO(savedPayment)).thenReturn(new PaymentResponseDTO());

        // Act
        PaymentResponseDTO result = collectionService.processCollection(requestDTO);

        // Assert
        assertNotNull(result);
        // Havale işleminde İyzico'nun hiç çağrılmadığını doğruluyoruz
        verifyNoInteractions(iyzicoPaymentService);
    }

    @Test
    void shouldThrowException_WhenForeignCurrencyAndInstallmentRequested() {
        // Arrange
        appData.setCurrency("USD"); // Döviz kuralını ihlal ediyoruz
        requestDTO.setInstallmentCount(3);
        when(applicationServiceClient.getApplicationDetails(100L)).thenReturn(appData);

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class,
                () -> collectionService.processCollection(requestDTO));

        assertEquals("TRY dışındaki para birimleri için taksit yapılamaz!", exception.getMessage());
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());

        // Hata fırladığı için İyzico'ya gitmemeli
        verifyNoInteractions(iyzicoPaymentService);
    }

    @Test
    void shouldThrowException_WhenIyzicoPaymentFails() {
        // Arrange
        when(applicationServiceClient.getApplicationDetails(100L)).thenReturn(appData);

        Payment mockIyzicoResponse = new Payment();
        mockIyzicoResponse.setStatus("failure");
        mockIyzicoResponse.setErrorMessage("Geçersiz Kart Bilgisi");

        when(iyzicoPaymentService.payWithIyzico(anyString(), eq(requestDTO), eq(appData), eq(mockCard)))
                .thenReturn(mockIyzicoResponse);

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class,
                () -> collectionService.processCollection(requestDTO));

        assertTrue(exception.getMessage().contains("Geçersiz Kart Bilgisi"));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());

        // Ödeme reddedildiği için veritabanına kayıt atılmamalı
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void shouldThrowException_WhenInstallmentCountIsInvalid() {
        // Arrange
        requestDTO.setInstallmentCount(0); // Taksit sayısını 0 yaparak kuralı bozuyoruz
        when(applicationServiceClient.getApplicationDetails(100L)).thenReturn(appData);

        Payment mockIyzicoResponse = new Payment();
        mockIyzicoResponse.setStatus("success");
        when(iyzicoPaymentService.payWithIyzico(anyString(), eq(requestDTO), eq(appData), eq(mockCard)))
                .thenReturn(mockIyzicoResponse);

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class,
                () -> collectionService.processCollection(requestDTO));

        assertEquals("Taksit sayısı 0'dan büyük olmalıdır!", exception.getMessage());
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
    }

    @Test
    void shouldGetPaymentByApplicationId_Successfully() {
        // Arrange
        PaymentEntity mockPayment = PaymentEntity.builder().build();
        when(paymentRepository.findTopByApplicationIdOrderByIdDesc(100L)).thenReturn(Optional.of(mockPayment));
        when(paymentMapper.toResponseDTO(mockPayment)).thenReturn(new PaymentResponseDTO());

        // Act
        PaymentResponseDTO result = collectionService.getPaymentByApplicationId(100L);

        // Assert
        assertNotNull(result);
        verify(paymentRepository).findTopByApplicationIdOrderByIdDesc(100L);
    }

    @Test
    void shouldThrowException_WhenPaymentNotFoundByApplicationId() {
        // Arrange
        when(paymentRepository.findTopByApplicationIdOrderByIdDesc(100L)).thenReturn(Optional.empty());

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class,
                () -> collectionService.getPaymentByApplicationId(100L));

        assertTrue(exception.getMessage().contains("Bu başvuruya ait ödeme kaydı bulunamadı"));
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
    }
}