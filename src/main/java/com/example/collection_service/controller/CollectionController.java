package com.example.collection_service.controller;

import com.example.collection_service.dto.CollectionRequestDTO;
import com.example.collection_service.dto.CollectionResponseDTO;
import com.example.collection_service.dto.PaymentRequestDTO;
import com.example.collection_service.dto.PaymentResponseDTO;
import com.example.collection_service.service.CollectionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;



@RestController
@RequestMapping("/api/collections")
@RequiredArgsConstructor
public class CollectionController {

    private final CollectionService collectionService;

    @PostMapping("/request")
    public ResponseEntity<CollectionResponseDTO> initiateCollection(@Valid @RequestBody CollectionRequestDTO collectionRequestDTO) {
        CollectionResponseDTO responseDTO = collectionService.initiateCollection(collectionRequestDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(responseDTO);
    }

    @PostMapping
    public ResponseEntity<PaymentResponseDTO> processCollection(@Valid @RequestBody PaymentRequestDTO requestDTO) {

        PaymentResponseDTO responseDTO = collectionService.processCollection(requestDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(responseDTO);
    }
    @GetMapping("/by-application/{applicationId}")
    public ResponseEntity<PaymentResponseDTO> getPaymentByApplicationId(@PathVariable Long applicationId) {
        PaymentResponseDTO response = collectionService.getPaymentByApplicationId(applicationId);
        return ResponseEntity.ok(response);
    }
}