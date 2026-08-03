package com.example.collection_service.service;

import com.example.collection_service.client.ApplicationServiceClient;
import com.example.collection_service.dto.*;
import com.example.collection_service.entity.InstallmentPlanEntity;
import com.example.collection_service.entity.PaymentEntity;
import com.example.collection_service.enums.InstallmentStatus;
import com.example.collection_service.exception.BusinessException;
import com.example.collection_service.mapper.InstallmentMapper;
import com.example.collection_service.repository.InstallmentPlanRepository;
import com.iyzipay.model.Payment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import java.math.BigDecimal;
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

    @InjectMocks
    private InstallmentService installmentService;

    private InstallmentPlanEntity mockInstallment;
    private InstallmentPayRequestDTO requestDTO;
    private ApplicationDetailResponseDTO appData;
    private CustomerCardResponseDTO mockCard;

    @BeforeEach
    void setUp() {
        // Taksit İsteği Hazırlığı
        requestDTO = new InstallmentPayRequestDTO();
        requestDTO.setCardId(1L);
        requestDTO.setCvc("123");

        // Ana Ödeme (Parent Payment) Hazırlığı
        PaymentEntity parentPayment = PaymentEntity.builder()
                .applicationId(99L)
                .build();

        // Taksit Entity Hazırlığı
        mockInstallment = InstallmentPlanEntity.builder()
                .id(10L)
                .amount(new BigDecimal("500.00"))
                .status(InstallmentStatus.UNPAID)
                .payment(parentPayment)
                .build();

        // Kart ve Müşteri Bilgileri Hazırlığı
        mockCard = new CustomerCardResponseDTO();
        mockCard.setId(1L);
        mockCard.setCardNumber("4543********1234");

        CustomerResponseDTO customer = new CustomerResponseDTO();
        customer.setCards(List.of(mockCard));

        appData = new ApplicationDetailResponseDTO();
        appData.setCustomer(customer);
    }

    @Test
    void shouldPayInstallment_Successfully() {
        // Arrange (Hazırlık)
        when(installmentRepository.findById(10L)).thenReturn(Optional.of(mockInstallment));
        when(applicationServiceClient.getApplicationDetails(99L)).thenReturn(appData);

        Payment mockIyzicoResponse = new Payment();
        mockIyzicoResponse.setStatus("success");
        when(iyzicoPaymentService.paySingleInstallmentWithIyzico(anyString(), eq(new BigDecimal("500.00")), eq(appData), eq(mockCard), eq("123")))
                .thenReturn(mockIyzicoResponse);

        when(installmentRepository.save(any(InstallmentPlanEntity.class))).thenReturn(mockInstallment);

        InstallmentResponseDTO expectedResponse = new InstallmentResponseDTO();
        when(installmentMapper.toResponseDTO(mockInstallment)).thenReturn(expectedResponse);

        // Act (Eylem)
        InstallmentResponseDTO result = installmentService.payInstallment(10L, requestDTO);

        // Assert (Doğrulama)
        assertNotNull(result);
        assertEquals(InstallmentStatus.PAID, mockInstallment.getStatus()); // Statünün PAID'e çekildiğini doğrula

        verify(installmentRepository).findById(10L);
        verify(applicationServiceClient).getApplicationDetails(99L);
        verify(installmentRepository).save(mockInstallment);
    }

    @Test
    void shouldThrowException_WhenInstallmentAlreadyPaid() {
        // Arrange
        mockInstallment.setStatus(InstallmentStatus.PAID); // Statüyü önceden PAID yapıyoruz
        when(installmentRepository.findById(10L)).thenReturn(Optional.of(mockInstallment));

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class,
                () -> installmentService.payInstallment(10L, requestDTO));

        assertEquals("Bu taksit zaten ödenmiş!", exception.getMessage());
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());

        // Diğer servislerin çağrılmadığından emin ol (çünkü hata fırlattı ve akış kesildi)
        verifyNoInteractions(applicationServiceClient);
        verifyNoInteractions(iyzicoPaymentService);
    }

    @Test
    void shouldThrowException_WhenCardNotFoundInCustomerList() {
        // Arrange
        requestDTO.setCardId(999L); // Müşteride olmayan rastgele bir kart ID'si veriyoruz
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

        // Veritabanına kaydetme işleminin yapılmadığını doğrula
        verify(installmentRepository, never()).save(any());
    }
}