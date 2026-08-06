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

    @Scheduled(cron = "0 15 9 * * ?", zone = "Europe/Istanbul")
    public void scheduleDailyCollections() {
        log.info("Zamanlanmış görev tetiklendi: Sabah 9:15 tahsilatları başlatılıyor...");

        batchCollectionService.processDailyDueCollections();
    }
}//mail atma farklı service , doküman servisi (makbuz) şu kadart alınmıştır attack etme pdf oluşturma
//sözleşme bilgilerini de atmak lazım mailden, policy service yapılcak.