package com.example.collection_service.service;

import com.example.collection_service.client.ApplicationServiceClient;
import com.example.collection_service.dto.*;
import com.example.collection_service.entity.PaymentEntity;
import com.example.collection_service.enums.PaymentStatus;
import com.example.collection_service.exception.BusinessException;
import com.example.collection_service.mapper.PaymentMapper;
import com.example.collection_service.repository.PaymentRepository;
import com.example.collection_service.strategy.PaymentStrategy;
import com.example.collection_service.strategy.PaymentStrategyFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CollectionServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentMapper paymentMapper;

    @Mock
    private Clock clock; // Sınıfta enjekte edilmiş ama kullanılmıyor, yine de mocklanmalı.

    @Mock
    private ApplicationServiceClient applicationServiceClient;

    @Mock
    private PaymentStrategyFactory paymentStrategyFactory;

    @Mock
    private InstallmentService installmentService;

    @Mock
    private PaymentStrategy paymentStrategy;

    @InjectMocks
    private CollectionService collectionService;

    private CollectionRequestDTO collectionRequestDTO;
    private PaymentRequestDTO paymentRequestDTO;
    private PaymentEntity savedPayment;
    private ApplicationDetailResponseDTO appData;

    // Projenizdeki Enum'a göre burayı uyarlayabilirsiniz (Örn: CREDIT_CARD)
    // Varsayılan olarak null kalmaması için any() matcher kullanacağız veya bir enum set edeceğiz.
    // Ancak DTO'larda set edilmesi gerektiği için null bırakıyoruz, kod null check yapmıyor.

    @BeforeEach
    void setUp() {
        collectionRequestDTO = new CollectionRequestDTO();
        collectionRequestDTO.setApplicationId(100L);
        collectionRequestDTO.setAmount(new BigDecimal("1000.00"));
        collectionRequestDTO.setCurrency("TRY");
        collectionRequestDTO.setCardId(1L);
        collectionRequestDTO.setCvcNo("123");
        collectionRequestDTO.setInstallmentCount(1); // Taksitsiz varsayılan

        paymentRequestDTO = PaymentRequestDTO.builder()
                .applicationId(100L)
                .cvcNo("123")
                .installmentCount(1)
                .build();

        savedPayment = PaymentEntity.builder()
                .id(10L)
                .applicationId(100L)
                .amount(new BigDecimal("1000.00"))
                .currency("TRY")
                .paymentStatus(PaymentStatus.SUCCESS)
                .installmentPlans(new ArrayList<>())
                .build();

        appData = ApplicationDetailResponseDTO.builder()
                .applicationId(100L)
                .price(new BigDecimal("1000.00"))
                .currency("TRY")
                .cardId(1L)
                .build();
    }

    // =========================================================================
    // initiateCollection() TESTLERİ
    // =========================================================================

    @Test
    void shouldInitiateCollection_Successfully_WithoutInstallments() {
        // Arrange
        when(paymentStrategyFactory.getStrategy(any())).thenReturn(paymentStrategy);
        when(paymentStrategy.process(any(PaymentRequestDTO.class), any(ApplicationDetailResponseDTO.class), anyString()))
                .thenReturn(PaymentStatus.SUCCESS);
        when(paymentRepository.save(any(PaymentEntity.class))).thenReturn(savedPayment);

        CollectionResponseDTO mockResponse = new CollectionResponseDTO();
        when(paymentMapper.toCollectionResponseDTO(savedPayment)).thenReturn(mockResponse);

        // Act
        CollectionResponseDTO result = collectionService.initiateCollection(collectionRequestDTO);

        // Assert
        assertNotNull(result);
        assertEquals("Tahsilat işlemi başarıyla tamamlandı.", result.getMessage());

        verify(paymentRepository).save(any(PaymentEntity.class));
        verify(installmentService, never()).splitPaymentIntoInstallments(anyLong(), anyInt());
    }

    @Test
    void shouldInitiateCollection_Successfully_WithInstallments() {
        // Arrange
        collectionRequestDTO.setInstallmentCount(3); // Taksit talebi var
        when(paymentStrategyFactory.getStrategy(any())).thenReturn(paymentStrategy);
        when(paymentStrategy.process(any(), any(), anyString())).thenReturn(PaymentStatus.SUCCESS);

        when(paymentRepository.save(any(PaymentEntity.class))).thenReturn(savedPayment);
        // handleInstallmentSplittingIfPresent içindeki findById için mock:
        when(paymentRepository.findById(savedPayment.getId())).thenReturn(Optional.of(savedPayment));

        CollectionResponseDTO mockResponse = new CollectionResponseDTO();
        when(paymentMapper.toCollectionResponseDTO(savedPayment)).thenReturn(mockResponse);

        // Act
        CollectionResponseDTO result = collectionService.initiateCollection(collectionRequestDTO);

        // Assert
        assertNotNull(result);
        assertEquals("Tahsilat işlemi başarıyla tamamlandı.", result.getMessage());

        verify(installmentService).splitPaymentIntoInstallments(savedPayment.getId(), 3);
        verify(paymentRepository).findById(savedPayment.getId());
    }

    @Test
    void shouldInitiateCollection_ReturnsFailedMessage_WhenPaymentFails() {
        // Arrange
        when(paymentStrategyFactory.getStrategy(any())).thenReturn(paymentStrategy);
        when(paymentStrategy.process(any(), any(), anyString())).thenReturn(PaymentStatus.FAILED); // FAILED DÖNÜYOR
        when(paymentRepository.save(any(PaymentEntity.class))).thenReturn(savedPayment);

        CollectionResponseDTO mockResponse = new CollectionResponseDTO();
        when(paymentMapper.toCollectionResponseDTO(savedPayment)).thenReturn(mockResponse);

        // Act
        CollectionResponseDTO result = collectionService.initiateCollection(collectionRequestDTO);

        // Assert
        assertNotNull(result);
        assertEquals("Tahsilat işlemi başarısız olmuştur.", result.getMessage()); // Failed mesajı kontrolü
    }

    @Test
    void shouldInitiateCollection_ReturnsRefundedMessage_WhenPaymentRefunded() {
        // Arrange
        when(paymentStrategyFactory.getStrategy(any())).thenReturn(paymentStrategy);
        when(paymentStrategy.process(any(), any(), anyString())).thenReturn(PaymentStatus.REFUNDED); // REFUNDED DÖNÜYOR
        when(paymentRepository.save(any(PaymentEntity.class))).thenReturn(savedPayment);

        CollectionResponseDTO mockResponse = new CollectionResponseDTO();
        when(paymentMapper.toCollectionResponseDTO(savedPayment)).thenReturn(mockResponse);

        // Act
        CollectionResponseDTO result = collectionService.initiateCollection(collectionRequestDTO);

        // Assert
        assertNotNull(result);
        assertEquals("Tahsilat işlemi geri iade edilmiştir.", result.getMessage()); // Refunded mesajı kontrolü
    }

    @Test
    void shouldThrowException_WhenSplittingPaymentButUpdatedPaymentNotFound() {
        // Arrange
        collectionRequestDTO.setInstallmentCount(3);
        when(paymentStrategyFactory.getStrategy(any())).thenReturn(paymentStrategy);
        when(paymentStrategy.process(any(), any(), anyString())).thenReturn(PaymentStatus.SUCCESS);
        when(paymentRepository.save(any(PaymentEntity.class))).thenReturn(savedPayment);

        // Veritabanından güncel kayıt bulunamazsa
        when(paymentRepository.findById(savedPayment.getId())).thenReturn(Optional.empty());

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class,
                () -> collectionService.initiateCollection(collectionRequestDTO));

        assertEquals("Ödeme bulunamadı", exception.getMessage());
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
    }

    // =========================================================================
    // processCollection() TESTLERİ
    // =========================================================================

    @Test
    void shouldProcessCollection_Successfully() {
        // Arrange
        when(applicationServiceClient.getApplicationDetails(100L)).thenReturn(appData);
        when(paymentStrategyFactory.getStrategy(any())).thenReturn(paymentStrategy);
        when(paymentStrategy.process(any(PaymentRequestDTO.class), eq(appData), anyString())).thenReturn(PaymentStatus.SUCCESS);
        when(paymentRepository.save(any(PaymentEntity.class))).thenReturn(savedPayment);

        PaymentResponseDTO mockResponse = new PaymentResponseDTO();
        when(paymentMapper.toResponseDTO(savedPayment)).thenReturn(mockResponse);

        // Act
        PaymentResponseDTO result = collectionService.processCollection(paymentRequestDTO);

        // Assert
        assertNotNull(result);
        verify(applicationServiceClient).getApplicationDetails(100L);
        verify(paymentRepository).save(any(PaymentEntity.class));
    }

    @Test
    void shouldProcessCollection_Successfully_WithInstallments() {
        // Arrange
        paymentRequestDTO.setInstallmentCount(6); // 6 Taksit

        when(applicationServiceClient.getApplicationDetails(100L)).thenReturn(appData);
        when(paymentStrategyFactory.getStrategy(any())).thenReturn(paymentStrategy);
        when(paymentStrategy.process(any(), eq(appData), anyString())).thenReturn(PaymentStatus.SUCCESS);
        when(paymentRepository.save(any(PaymentEntity.class))).thenReturn(savedPayment);

        when(paymentRepository.findById(savedPayment.getId())).thenReturn(Optional.of(savedPayment));

        PaymentResponseDTO mockResponse = new PaymentResponseDTO();
        when(paymentMapper.toResponseDTO(savedPayment)).thenReturn(mockResponse);

        // Act
        PaymentResponseDTO result = collectionService.processCollection(paymentRequestDTO);

        // Assert
        assertNotNull(result);
        verify(installmentService).splitPaymentIntoInstallments(savedPayment.getId(), 6);
        verify(paymentRepository).findById(savedPayment.getId());
    }

    // =========================================================================
    // getPaymentByApplicationId() TESTLERİ
    // =========================================================================

    @Test
    void shouldGetPaymentByApplicationId_Successfully() {
        // Arrange
        when(paymentRepository.findTopByApplicationIdOrderByIdDesc(100L)).thenReturn(Optional.of(savedPayment));

        PaymentResponseDTO mockResponse = new PaymentResponseDTO();
        when(paymentMapper.toResponseDTO(savedPayment)).thenReturn(mockResponse);

        // Act
        PaymentResponseDTO result = collectionService.getPaymentByApplicationId(100L);

        // Assert
        assertNotNull(result);
        verify(paymentRepository).findTopByApplicationIdOrderByIdDesc(100L);
    }

    @Test
    void shouldThrowException_WhenGetPaymentByApplicationId_NotFound() {
        // Arrange
        when(paymentRepository.findTopByApplicationIdOrderByIdDesc(100L)).thenReturn(Optional.empty());

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class,
                () -> collectionService.getPaymentByApplicationId(100L));

        assertTrue(exception.getMessage().contains("Bu başvuruya ait ödeme kaydı bulunamadı!"));
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
    }
}