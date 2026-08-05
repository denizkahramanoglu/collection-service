package com.example.collection_service.controller;

import com.example.collection_service.service.BatchCollectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/batch-collections")
@RequiredArgsConstructor
public class BatchCollectionController {

    private final BatchCollectionService batchCollectionService;


    @PostMapping("/run-daily")
    public ResponseEntity<String> triggerDailyCollections() {
        batchCollectionService.processDailyDueCollections();
        return ResponseEntity.ok("Günlük tahsilat süreci başarıyla tetiklendi.");
    }
}