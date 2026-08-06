package com.example.collection_service.service;

import com.example.collection_service.entity.InstallmentPlanEntity;
import com.example.collection_service.enums.InstallmentStatus;
import com.example.collection_service.repository.InstallmentPlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BatchCollectionService {

    private final InstallmentPlanRepository installmentRepository;
    private final Clock clock;

    /**
     * Vadesi gelen ve ödenmemiş tüm taksit planı kayıtlarını sorgulayarak
     * günlük toplu tahsilat (batch) sürecini yürütür ve durumlarını günceller.
     */
    @Transactional
    public void processDailyDueCollections() {
        log.info("Günlük tahsilat batch işlemi başlatıldı...");

        // 1. Dataları Çek: Vadesi gelmiş (bugün veya daha eski) ve ödenmemiş tüm kayıtlar
        List<InstallmentPlanEntity> dueInstallments = installmentRepository
                .findByDueDateLessThanEqualAndStatus(LocalDate.now(clock), InstallmentStatus.UNPAID);

        log.info("İşlenecek toplam tahsilat kaydı sayısı: {}", dueInstallments.size());

        int successCount = 0;
        int failureCount = 0;

        // 2. Dataları İşle
        for (InstallmentPlanEntity installment : dueInstallments) {
            try {
                log.info("Taksit ID {} işleniyor...", installment.getId());

                // İyzico'yu ve dış servisleri devreden çıkardık, doğrudan PAID (Ödendi) yapıyoruz.
                installment.setStatus(InstallmentStatus.PAID);
                installmentRepository.save(installment);

                log.info("Taksit ID {} başarıyla PAID olarak güncellendi.", installment.getId());
                successCount++;

            } catch (Exception e) {
                log.error("Taksit ID {} güncellenirken hata oluştu. Hata: {}", installment.getId(), e.getMessage(), e);
                failureCount++;
            }
        }

        log.info("Günlük tahsilat batch işlemi tamamlandı. Başarılı: {}, Başarısız: {}", successCount, failureCount);
    }
}