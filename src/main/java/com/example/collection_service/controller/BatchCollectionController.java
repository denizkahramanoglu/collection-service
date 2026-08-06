package com.example.collection_service.controller;

import com.example.collection_service.service.BatchCollectionService;
import io.swagger.v3.oas.annotations.Operation;
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

    @Operation(summary = "Günlük Tahsilat Sürecini Tetikler")
    @PostMapping("/run-daily")
    public ResponseEntity<String> triggerDailyCollections() {
        batchCollectionService.processDailyDueCollections();
        return ResponseEntity.ok("Günlük tahsilat süreci başarıyla tetiklendi.");
    }
}