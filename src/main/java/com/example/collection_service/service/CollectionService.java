package com.example.collection_service.service;

import com.example.collection_service.client.ApplicationServiceClient;
import com.example.collection_service.dto.ApplicationDetailResponseDTO;
import com.example.collection_service.dto.CollectionRequestDTO;
import com.example.collection_service.dto.CollectionResponseDTO;
import com.example.collection_service.dto.PaymentRequestDTO;
import com.example.collection_service.dto.PaymentResponseDTO;
import com.example.collection_service.entity.InstallmentPlanEntity;
import com.example.collection_service.entity.PaymentEntity;
import com.example.collection_service.enums.InstallmentStatus;
import com.example.collection_service.enums.PaymentStatus;
import com.example.collection_service.exception.BusinessException;
import com.example.collection_service.mapper.PaymentMapper;
import com.example.collection_service.repository.PaymentRepository;
import com.example.collection_service.strategy.PaymentStrategy;
import com.example.collection_service.strategy.PaymentStrategyFactory;
import com.example.collection_service.util.BusinessRuleValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CollectionService {

    private final PaymentRepository paymentRepository;
    private final PaymentMapper paymentMapper;
    private final Clock clock;
    private final ApplicationServiceClient applicationServiceClient;
    private final PaymentStrategyFactory paymentStrategyFactory;

    @Transactional
    public CollectionResponseDTO initiateCollection(CollectionRequestDTO collectionRequestDTO) {
        log.info("Application {} tarafından tahsilat isteği alındı. Miktar: {} {}", 
                collectionRequestDTO.getApplicationId(), 
                collectionRequestDTO.getAmount(),
                collectionRequestDTO.getCurrency());

        String transactionId = UUID.randomUUID().toString();

        // ApplicationDetailResponseDTO oluştur (CollectionRequestDTO'dan müşteri, kartlar ve ürün bilgisini al)
        ApplicationDetailResponseDTO appData = ApplicationDetailResponseDTO.builder()
                .applicationId(collectionRequestDTO.getApplicationId())
                .price(collectionRequestDTO.getAmount())
                .currency(collectionRequestDTO.getCurrency())
                .customer(collectionRequestDTO.getCustomer())
                .cards(collectionRequestDTO.getCards())
                .product(collectionRequestDTO.getProduct())
                .build();

        // CollectionRequestDTO'dan PaymentRequestDTO oluştur
        PaymentRequestDTO paymentRequestDTO = PaymentRequestDTO.builder()
                .applicationId(collectionRequestDTO.getApplicationId())
                .paymentMethod(collectionRequestDTO.getPaymentMethod())
                .installmentCount(collectionRequestDTO.getInstallmentCount())
                .cardId(collectionRequestDTO.getCardId())
                .cvcNo(collectionRequestDTO.getCvcNo())
                .build();

        // Doğru stratejiyi fabrikadan iste ve çalıştır (Polymorphism)
        PaymentStrategy strategy = paymentStrategyFactory.getStrategy(collectionRequestDTO.getPaymentMethod());
        PaymentStatus finalPaymentStatus = strategy.process(paymentRequestDTO, appData, transactionId);

        // Ödeme kaydını (Entity) oluştur
        PaymentEntity payment = PaymentEntity.builder()
                .applicationId(collectionRequestDTO.getApplicationId())
                .amount(collectionRequestDTO.getAmount())
                .currency(collectionRequestDTO.getCurrency())
                .paymentMethod(collectionRequestDTO.getPaymentMethod())
                .paymentStatus(finalPaymentStatus)
                .transactionId(transactionId)
                .build();

        // Taksit planlarını oluştur ve ödeme başarılıysa ilk taksiti ödendi yap
        List<InstallmentPlanEntity> installments = createInstallmentPlans(payment, collectionRequestDTO.getInstallmentCount());

        if (!installments.isEmpty() && finalPaymentStatus == PaymentStatus.SUCCESS) {
            installments.getFirst().setStatus(InstallmentStatus.PAID);
        }

        // Birbirine bağla ve veritabanına kaydet
        payment.setInstallmentPlans(installments);
        PaymentEntity savedPayment = paymentRepository.save(payment);

        log.info("Application {} için tahsilat işlemi {} statüsü ile tamamlandı. Transaction ID: {}", 
                collectionRequestDTO.getApplicationId(), 
                finalPaymentStatus,
                transactionId);

        CollectionResponseDTO response = paymentMapper.toCollectionResponseDTO(savedPayment);
        response.setMessage(getStatusMessage(finalPaymentStatus));
        return response;
    }

    @Transactional
    public PaymentResponseDTO processCollection(PaymentRequestDTO requestDTO) {
        String transactionId = UUID.randomUUID().toString();
        log.info("Application Service'ten {} ID'li başvuru bilgileri çekiliyor...", requestDTO.getApplicationId());

        // 1. Dış servisten (Application) fiyat ve müşteri bilgilerini güvenli şekilde al
        ApplicationDetailResponseDTO appData = applicationServiceClient.getApplicationDetails(requestDTO.getApplicationId());

        // 2. Doğru stratejiyi fabrikadan iste ve çalıştır (Polymorphism)
        PaymentStrategy strategy = paymentStrategyFactory.getStrategy(requestDTO.getPaymentMethod());
        PaymentStatus finalPaymentStatus = strategy.process(requestDTO, appData, transactionId);

        // 3. Ödeme kaydını (Entity) oluştur
        PaymentEntity payment = PaymentEntity.builder()
                .applicationId(requestDTO.getApplicationId())
                .amount(appData.getPrice())
                .currency(appData.getCurrency())
                .paymentMethod(requestDTO.getPaymentMethod())
                .paymentStatus(finalPaymentStatus)
                .transactionId(transactionId)
                .build();

        // 4. Taksit planlarını oluştur ve ödeme başarılıysa ilk taksiti ödendi yap
        List<InstallmentPlanEntity> installments = createInstallmentPlans(payment, requestDTO.getInstallmentCount());

        if (!installments.isEmpty() && finalPaymentStatus == PaymentStatus.SUCCESS) {
            installments.getFirst().setStatus(InstallmentStatus.PAID);
        }

        // 5. Birbirine bağla ve veritabanına kaydet
        payment.setInstallmentPlans(installments);
        PaymentEntity savedPayment = paymentRepository.save(payment);

        log.info("{} ID'li başvuru için tahsilat işlemi {} statüsü ile tamamlandı.", requestDTO.getApplicationId(), finalPaymentStatus);

        return paymentMapper.toResponseDTO(savedPayment);
    }

    private List<InstallmentPlanEntity> createInstallmentPlans(PaymentEntity payment, int installmentCount) {
        BusinessRuleValidator.isTrue(installmentCount > 0, "Taksit sayısı 0'dan büyük olmalıdır!", HttpStatus.BAD_REQUEST);

        BigDecimal totalAmount = payment.getAmount();

        // Taksitleri aşağı yuvarlayarak bölüyoruz (Örn: 100 / 3 = 33.33)
        BigDecimal baseInstallmentAmount = totalAmount.divide(BigDecimal.valueOf(installmentCount), 2, RoundingMode.DOWN);

        // Kalan küsuratı buluyoruz (Örn: 100 - (33.33 * 3) = 0.01)
        BigDecimal remainder = totalAmount.subtract(baseInstallmentAmount.multiply(BigDecimal.valueOf(installmentCount)));

        List<InstallmentPlanEntity> plans = new ArrayList<>(installmentCount);
        LocalDate today = LocalDate.now(clock);

        for (int i = 1; i <= installmentCount; i++) {
            // Son taksit ise kalan küsuratı ekle
            BigDecimal currentAmount = (i == installmentCount)
                    ? baseInstallmentAmount.add(remainder)
                    : baseInstallmentAmount;

            plans.add(InstallmentPlanEntity.builder()
                    .payment(payment)
                    .installmentNo(i)
                    .amount(currentAmount)
                    .dueDate(today.plusMonths((long) i - 1))
                    .status(InstallmentStatus.UNPAID)
                    .build());
        }

        return plans;
    }

    public PaymentResponseDTO getPaymentByApplicationId(Long applicationId) {
        log.info("{} ID'li başvuruya ait ödeme bilgileri getiriliyor...", applicationId);

        PaymentEntity payment = paymentRepository.findTopByApplicationIdOrderByIdDesc(applicationId)
                .orElseThrow(() -> new BusinessException("Bu başvuruya ait ödeme kaydı bulunamadı! Başvuru ID: " + applicationId, HttpStatus.NOT_FOUND));

        return paymentMapper.toResponseDTO(payment);
    }

    private String getStatusMessage(PaymentStatus status) {
        return switch (status) {
            case SUCCESS -> "Tahsilat işlemi başarıyla tamamlandı.";
            case FAILED -> "Tahsilat işlemi başarısız olmuştur.";
            case REFUNDED -> "Tahsilat işlemi geri iade edilmiştir.";
        };
    }
}