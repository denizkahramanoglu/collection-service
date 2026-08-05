package com.example.collection_service.service;

import com.example.collection_service.client.ApplicationServiceClient;
import com.example.collection_service.dto.ApplicationDetailResponseDTO;
import com.example.collection_service.dto.CustomerCardResponseDTO;
import com.example.collection_service.entity.InstallmentPlanEntity;
import com.example.collection_service.entity.PaymentEntity;
import com.example.collection_service.enums.InstallmentStatus;
import com.example.collection_service.exception.BusinessException;
import com.example.collection_service.repository.InstallmentPlanRepository;
import com.example.collection_service.repository.PaymentRepository;
import com.iyzipay.model.Payment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BatchCollectionService {

    private final InstallmentPlanRepository installmentRepository;
    private final PaymentRepository paymentRepository;
    private final ApplicationServiceClient applicationServiceClient;
    private final IyzicoPaymentService iyzicoPaymentService;
    private final Clock clock;

    @Transactional
    public void processDailyDueCollections() {
        log.info("Günlük tahsilat batch işlemi başlatıldı...");

        // 1. Dataları Çek: Vadesi gelmiş ve ödenmemiş tüm kayıtlar
        List<InstallmentPlanEntity> dueInstallments = installmentRepository
                .findByDueDateLessThanEqualAndStatus(LocalDate.now(clock), InstallmentStatus.UNPAID);

        log.info("İşlenecek toplam tahsilat kaydı sayısı: {}", dueInstallments.size());

        // 2. Dataları İşle
        int successCount = 0;
        int failureCount = 0;
        
        for (InstallmentPlanEntity installment : dueInstallments) {
            try {
                log.info("Taksit ID {} için ödeme alınıyor...", installment.getId());

                // Ödeme denemesi yapılıyor (İyzico)
                boolean isSuccess = attemptPayment(installment);

                // Eğer başarılıysa taksit statüsünü ödenmiş olarak işaretle
                if (isSuccess) {
                    log.info("Taksit ID {} tahsilatı başarılı oldu. Statü PAID olarak güncelleniyor.", installment.getId());
                    installment.setStatus(InstallmentStatus.PAID);
                    installmentRepository.save(installment);
                    successCount++;
                } else {
                    log.warn("Taksit ID {} tahsil edilemedi - Iyzico ödeme başarısız.", installment.getId());
                    failureCount++;
                }

            } catch (Exception e) {
                // Hata alınırsa döngü kırılmasın, sonraki müşterinin taksitine geçsin
                log.error("Taksit ID {} tahsil edilemedi. Hata: {}", installment.getId(), e.getMessage(), e);
                failureCount++;
            }
        }
        
        log.info("Günlük tahsilat batch işlemi tamamlandı. Başarılı: {}, Başarısız: {}", successCount, failureCount);
    }

    private boolean attemptPayment(InstallmentPlanEntity installment) {
        // Parent payment kaydını bul
        PaymentEntity payment = installment.getPayment();
        if (payment == null) {
            log.error("Taksit ID {} için parent ödeme kaydı bulunamadı", installment.getId());
            return false;
        }

        try {
            // Application-service'ten başvuru ve müşteri detaylarını çek
            log.info("Application Service'ten {} ID'li başvuru bilgileri çekiliyor...", payment.getApplicationId());
            ApplicationDetailResponseDTO appData = applicationServiceClient.getApplicationDetails(payment.getApplicationId());

            // Müşteri ve ürün bilgilerinin var olup olmadığını kontrol et
            if (appData.getCustomer() == null || appData.getCustomer().getCards() == null || appData.getCustomer().getCards().isEmpty()) {
                log.error("Müşteri veya kartlar bilgisi bulunamadı. Application ID: {}", payment.getApplicationId());
                return false;
            }

            if (appData.getProduct() == null) {
                log.error("Ürün bilgisi bulunamadı. Application ID: {}", payment.getApplicationId());
                return false;
            }

            // Müşterinin kaydedilen kartını bul (payment kaydında cardId varsa)
            CustomerCardResponseDTO selectedCard = appData.getCustomer()
                    .getCards()
                    .stream()
                    .filter(card -> card.getId().equals(appData.getCardId()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException("Müşteri kartı bulunamadı. Card ID: " + appData.getCardId(), HttpStatus.NOT_FOUND));

            // Iyzico ile taksit ödemesini işle (CVC olmadan - kaydedilmiş kart)
            String newTransactionId = UUID.randomUUID().toString();
            log.info("[BATCH] İyzico ödeme işlemi başlatılıyor. Transaction ID: {}, Taksit Tutarı: {}", newTransactionId, installment.getAmount());

            Payment iyzicoResponse = iyzicoPaymentService.paySingleInstallmentWithIyzico(newTransactionId, installment.getAmount(), appData, selectedCard, "");

            // Iyzico sonucunu kontrol et
            if ("success".equalsIgnoreCase(iyzicoResponse.getStatus())) {
                log.info("[BATCH-SUCCESS] Taksit ID {} başarıyla tahsil edildi. Iyzico Transaction ID: {}", 
                        installment.getId(), iyzicoResponse.getConversationId());
                return true;
            } else {
                log.warn("[BATCH-FAILED] Taksit ID {} tahsil edilemedi. Iyzico Hatası: {}", 
                        installment.getId(), iyzicoResponse.getErrorMessage());
                return false;
            }

        } catch (BusinessException e) {
            log.error("[BATCH-ERROR] İş kuralı hatası - Taksit ID {}: {}", installment.getId(), e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("[BATCH-ERROR] Beklenmeyen hata - Taksit ID {}: {}", installment.getId(), e.getMessage(), e);
            return false;
        }
    }
}