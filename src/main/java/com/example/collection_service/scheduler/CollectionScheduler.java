package com.example.collection_service.scheduler;

import com.example.collection_service.service.BatchCollectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CollectionScheduler {

    private final BatchCollectionService batchCollectionService;

    @Scheduled(cron = "0 49 16 * * ?", zone = "Europe/Istanbul")
    public void scheduleDailyCollections() {
        log.info("Zamanlanmış görev tetiklendi: Sabah 09:15 tahsilatları başlatılıyor...");

        // Controller'a istek atmak yerine direkt servisi çağırıyoruz. (Daha performanslı ve güvenli)
        batchCollectionService.processDailyDueCollections();
    }
}