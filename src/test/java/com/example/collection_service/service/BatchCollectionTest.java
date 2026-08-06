package com.example.collection_service.service;

import com.example.collection_service.entity.InstallmentPlanEntity;
import com.example.collection_service.enums.InstallmentStatus;
import com.example.collection_service.repository.InstallmentPlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BatchCollectionServiceTest {

    @Mock
    private InstallmentPlanRepository installmentRepository;

    @Mock
    private Clock clock;

    @InjectMocks
    private BatchCollectionService batchCollectionService;

    @BeforeEach
    void setUp() {
        // LocalDate.now(clock) çağrısının testlerde tutarlı çalışabilmesi için saati mock'luyoruz
        lenient().when(clock.instant()).thenReturn(Instant.parse("2026-08-06T10:00:00Z"));
        lenient().when(clock.getZone()).thenReturn(ZoneId.systemDefault());
    }

    @Test
    void shouldDoNothing_WhenNoDueInstallmentsFound() {
        // Arrange: Repository'den boş liste dönüldüğünü varsayıyoruz
        when(installmentRepository.findByDueDateLessThanEqualAndStatus(any(LocalDate.class), eq(InstallmentStatus.UNPAID)))
                .thenReturn(Collections.emptyList());

        // Act: Metodu çağır
        batchCollectionService.processDailyDueCollections();

        // Assert: Hiçbir kayıt dönmediği için save metodu hiç çağrılmamalıdır
        verify(installmentRepository, never()).save(any());
    }

    @Test
    void shouldProcessAllInstallments_Successfully() {
        // Arrange: 2 adet ödenmemiş ve vadesi gelmiş taksit simüle ediliyor
        InstallmentPlanEntity installment1 = InstallmentPlanEntity.builder().id(1L).status(InstallmentStatus.UNPAID).build();
        InstallmentPlanEntity installment2 = InstallmentPlanEntity.builder().id(2L).status(InstallmentStatus.UNPAID).build();

        List<InstallmentPlanEntity> dueInstallments = List.of(installment1, installment2);

        when(installmentRepository.findByDueDateLessThanEqualAndStatus(any(LocalDate.class), eq(InstallmentStatus.UNPAID)))
                .thenReturn(dueInstallments);

        // Act
        batchCollectionService.processDailyDueCollections();

        // Assert: Kayıtların statülerinin PAID yapıldığını doğruluyoruz
        assertEquals(InstallmentStatus.PAID, installment1.getStatus());
        assertEquals(InstallmentStatus.PAID, installment2.getStatus());

        // Her iki kayıt için de save metodunun çağrıldığını doğruluyoruz
        verify(installmentRepository, times(1)).save(installment1);
        verify(installmentRepository, times(1)).save(installment2);
    }

    @Test
    void shouldContinueProcessing_WhenExceptionOccursForOneInstallment() {
        // Arrange: İlk taksit güncellenirken hata alacak, ikincisi başarıyla kaydedilecek
        InstallmentPlanEntity installment1 = InstallmentPlanEntity.builder().id(1L).status(InstallmentStatus.UNPAID).build();
        InstallmentPlanEntity installment2 = InstallmentPlanEntity.builder().id(2L).status(InstallmentStatus.UNPAID).build();

        List<InstallmentPlanEntity> dueInstallments = List.of(installment1, installment2);

        when(installmentRepository.findByDueDateLessThanEqualAndStatus(any(LocalDate.class), eq(InstallmentStatus.UNPAID)))
                .thenReturn(dueInstallments);

        // İlk kaydın veritabanına kaydedilmesi aşamasında hata fırlattırıyoruz (catch bloğuna girmesi için)
        doThrow(new RuntimeException("Database timeout error!")).when(installmentRepository).save(installment1);

        // Act
        batchCollectionService.processDailyDueCollections();

        // Assert: İlk taksitte hata alınsa bile, döngü (for) kırılmamalı ve ikinci kayıt başarıyla kaydedilmelidir.
        assertEquals(InstallmentStatus.PAID, installment1.getStatus()); // Bellekte PAID oldu ama save'de hata aldı
        assertEquals(InstallmentStatus.PAID, installment2.getStatus()); // İkincisi başarıyla tamamlandı

        verify(installmentRepository, times(1)).save(installment1);
        verify(installmentRepository, times(1)).save(installment2);
    }
}