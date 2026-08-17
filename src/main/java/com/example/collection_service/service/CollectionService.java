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
import com.example.collection_service.producer.PaymentProducer;
import com.example.collection_service.repository.PaymentRepository;
import com.example.collection_service.strategy.PaymentStrategy;
import com.example.collection_service.strategy.PaymentStrategyFactory;
import com.example.collection_service.util.BusinessRuleValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.collection_service.service.InstallmentService;
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
    private final InstallmentService installmentService;
    private final PaymentProducer paymentProducer;

    /**
     * Yeni bir tahsilat süreci başlatır, ödeme stratejisini çalıştırır, ana ödeme kaydını oluşturur
     * ve talep edilmişse taksitlendirme sürecini tetikler.
     *
     * @param requestDTO Tahsilat isteği için gerekli verileri barındıran {@link CollectionRequestDTO} nesnesi
     * @return Tahsilat işlem sonucunu ve mesajını içeren {@link CollectionResponseDTO} nesnesi
     */
    @Transactional
    public CollectionResponseDTO initiateCollection(CollectionRequestDTO requestDTO) {
        log.info("Application {} tarafından tahsilat isteği alındı. Miktar: {} {}",
                requestDTO.getApplicationId(),
                requestDTO.getAmount(),
                requestDTO.getCurrency());

        String transactionId = UUID.randomUUID().toString();

        ApplicationDetailResponseDTO appData = ApplicationDetailResponseDTO.builder()
                .applicationId(requestDTO.getApplicationId())
                .price(requestDTO.getAmount())
                .currency(requestDTO.getCurrency())
                .customer(requestDTO.getCustomer())
                .cards(requestDTO.getCards())
                .product(requestDTO.getProduct())
                .build();

        PaymentRequestDTO paymentRequestDTO = PaymentRequestDTO.builder()
                .applicationId(requestDTO.getApplicationId())
                .paymentMethod(requestDTO.getPaymentMethod())
                .installmentCount(1)
                .cardId(requestDTO.getCardId())
                .cvcNo(requestDTO.getCvcNo())
                .build();

        PaymentStrategy strategy = paymentStrategyFactory.getStrategy(requestDTO.getPaymentMethod());
        PaymentStatus finalPaymentStatus = strategy.process(paymentRequestDTO, appData, transactionId);

        PaymentEntity savedPayment = savePayment(
                requestDTO.getApplicationId(),
                requestDTO.getAmount(),
                requestDTO.getCurrency(),
                requestDTO.getPaymentMethod(),
                finalPaymentStatus,
                transactionId
        );

        handleInstallmentSplittingIfPresent(savedPayment, requestDTO.getInstallmentCount());
        if (PaymentStatus.SUCCESS.equals(finalPaymentStatus)) {
            com.example.collection_service.event.PaymentCompletedEvent event =
                    com.example.collection_service.event.PaymentCompletedEvent.builder()
                            .eventId(UUID.randomUUID().toString())
                            .paymentId(savedPayment.getId())
                            .applicationId(savedPayment.getApplicationId())
                            .amount(appData.getPrice())
                            .currency(appData.getCurrency())
                            .paymentMethod(appData.getPaymentMethod() != null ? appData.getPaymentMethod().name() : null)
                            .installmentCount(requestDTO.getInstallmentCount())
                            .transactionId(transactionId)
                            .paymentDate(java.time.LocalDateTime.now(clock))
                            .build();

            paymentProducer.publishPaymentCompletedEvent(event);
        }

        log.info("Application {} için tahsilat işlemi {} statüsü ile tamamlandı. Transaction ID: {}",
                requestDTO.getApplicationId(),
                finalPaymentStatus,
                transactionId);

        CollectionResponseDTO response = paymentMapper.toCollectionResponseDTO(savedPayment);
        response.setMessage(getStatusMessage(finalPaymentStatus));
        return response;
    }

    /**
     * Başvuru servisinden detayları çekerek gerçek tahsilat işlemini yürütür ve talep edilmişse taksitlendirir.
     *
     * @param requestDTO Ödeme sürecini yürütmek için gerekli verileri barındıran {@link PaymentRequestDTO} nesnesi
     * @return Gerçekleştirilen ödemenin detaylarını içeren {@link PaymentResponseDTO} nesnesi
     * @throws BusinessException Ödeme bulunamazsa fırlatılır
     */
    @Transactional
    public PaymentResponseDTO processCollection(PaymentRequestDTO requestDTO) {
        String transactionId = UUID.randomUUID().toString();
        log.info("Application Service'ten {} ID'li başvuru bilgileri çekiliyor...", requestDTO.getApplicationId());

        ApplicationDetailResponseDTO appData = applicationServiceClient.getApplicationDetails(requestDTO.getApplicationId());

        PaymentRequestDTO iyzicoRequest = PaymentRequestDTO.builder()
                .applicationId(requestDTO.getApplicationId())
                .paymentMethod(appData.getPaymentMethod())
                .installmentCount(1)
                .cardId(appData.getCardId())
                .cvcNo(requestDTO.getCvcNo())
                .build();

        PaymentStrategy strategy = paymentStrategyFactory.getStrategy(appData.getPaymentMethod());
        PaymentStatus finalPaymentStatus = strategy.process(iyzicoRequest, appData, transactionId);

        PaymentEntity savedPayment = savePayment(
                requestDTO.getApplicationId(),
                appData.getPrice(),
                appData.getCurrency(),
                appData.getPaymentMethod(),
                finalPaymentStatus,
                transactionId
        );

        handleInstallmentSplittingIfPresent(savedPayment, requestDTO.getInstallmentCount());

        if (PaymentStatus.SUCCESS.equals(finalPaymentStatus)) {
            com.example.collection_service.event.PaymentCompletedEvent event =
                    com.example.collection_service.event.PaymentCompletedEvent.builder()
                            .eventId(UUID.randomUUID().toString())
                            .paymentId(savedPayment.getId())
                            .applicationId(savedPayment.getApplicationId())
                            .amount(appData.getPrice())
                            .currency(appData.getCurrency())
                            .paymentMethod(appData.getPaymentMethod() != null ? appData.getPaymentMethod().name() : null)
                            .installmentCount(requestDTO.getInstallmentCount())
                            .transactionId(transactionId)
                            .paymentDate(java.time.LocalDateTime.now(clock))
                            .build();

            paymentProducer.publishPaymentCompletedEvent(event);
        }

        log.info("{} ID'li başvuru için tahsilat işlemi {} statüsü ile tamamlandı.", requestDTO.getApplicationId(), finalPaymentStatus);

        return paymentMapper.toResponseDTO(savedPayment);
    }
    /**
     * Başarıyla oluşturulan poliçenin ID'sini ilgili ödeme kaydına bağlar.
     *
     * @param paymentId Güncellenecek ödemenin ID'si
     * @param policyId  Bağlanacak poliçe ID'si
     */
    @Transactional
    public void linkPolicyToPayment(Long paymentId, Long policyId) {
        log.info("{} ID'li ödemeye {} ID'li poliçe bağlanıyor...", paymentId, policyId);

        PaymentEntity payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new BusinessException("Ödeme bulunamadı! ID: " + paymentId, HttpStatus.NOT_FOUND));

        payment.setPolicyId(policyId);
        paymentRepository.save(payment);

        log.info("{} ID'li ödeme başarıyla {} ID'li poliçeye bağlandı.", paymentId, policyId);
    }

    /**
     * Ödeme entity nesnesini oluşturur ve veritabanına kaydeder.
     */
    private PaymentEntity savePayment(Long applicationId, BigDecimal amount, String currency,
                                      com.example.collection_service.enums.PaymentMethod paymentMethod,
                                      PaymentStatus paymentStatus, String transactionId) {
        PaymentEntity payment = PaymentEntity.builder()
                .applicationId(applicationId)
                .amount(amount)
                .currency(currency)
                .paymentMethod(paymentMethod)
                .paymentStatus(paymentStatus)
                .transactionId(transactionId)
                .build();

        return paymentRepository.save(payment);
    }

    /**
     * Taksit sayısı 1'den büyükse, ödemeyi taksitlere bölen süreci tetikler.
     */
    private void handleInstallmentSplittingIfPresent(PaymentEntity payment, Integer installmentCount) {
        if (installmentCount != null && installmentCount > 1) {
            installmentService.splitPaymentIntoInstallments(payment.getId(), installmentCount);

            PaymentEntity updatedPayment = paymentRepository.findById(payment.getId())
                    .orElseThrow(() -> new BusinessException("Ödeme bulunamadı", HttpStatus.NOT_FOUND));

            payment.setInstallmentPlans(updatedPayment.getInstallmentPlans());
        }
    }

    /**
     * Belirtilen başvuru ID'sine ait en güncel ödeme bilgilerini getirir.
     *
     * @param applicationId Ödeme bilgileri sorgulanacak başvurunun benzersiz ID'si
     * @return Bulunan ödemenin detaylarını içeren {@link PaymentResponseDTO} nesnesi
     * @throws BusinessException Başvuruya ait ödeme kaydı bulunamazsa fırlatılır
     */
    public PaymentResponseDTO getPaymentByApplicationId(Long applicationId) {
        log.info("{} ID'li başvuruya ait ödeme bilgileri getiriliyor...", applicationId);

        PaymentEntity payment = paymentRepository.findTopByApplicationIdOrderByIdDesc(applicationId)
                .orElseThrow(() -> new BusinessException("Bu başvuruya ait ödeme kaydı bulunamadı! Başvuru ID: " + applicationId, HttpStatus.NOT_FOUND));

        return paymentMapper.toResponseDTO(payment);
    }

    /**
     * Ödeme statüsüne karşılık gelen kullanıcı dostu durum mesajını döner.
     *
     * @param status Durumu değerlendirilecek {@link PaymentStatus} nesnesi
     * @return Duruma uygun açıklayıcı metin
     */
    private String getStatusMessage(PaymentStatus status) {
        return switch (status) {
            case SUCCESS -> "Tahsilat işlemi başarıyla tamamlandı.";
            case FAILED -> "Tahsilat işlemi başarısız olmuştur.";
            case REFUNDED -> "Tahsilat işlemi geri iade edilmiştir.";
        };
    }
}