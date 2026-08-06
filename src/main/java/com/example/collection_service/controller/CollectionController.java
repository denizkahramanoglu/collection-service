package com.example.collection_service.controller;

import com.example.collection_service.dto.CollectionRequestDTO;
import com.example.collection_service.dto.CollectionResponseDTO;
import com.example.collection_service.dto.PaymentRequestDTO;
import com.example.collection_service.dto.PaymentResponseDTO;
import com.example.collection_service.service.CollectionService;
import io.swagger.v3.oas.annotations.Operation;
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

    @Operation(summary = "Tahsilat Talebi Başlat")
    @PostMapping("/request")
    public ResponseEntity<CollectionResponseDTO> initiateCollection(@Valid @RequestBody CollectionRequestDTO collectionRequestDTO) {
        CollectionResponseDTO responseDTO = collectionService.initiateCollection(collectionRequestDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(responseDTO);
    }

    @Operation(summary = "Tahsilat İşlemini Gerçekleştir")
    @PostMapping
    public ResponseEntity<PaymentResponseDTO> processCollection(@Valid @RequestBody PaymentRequestDTO requestDTO) {
        PaymentResponseDTO responseDTO = collectionService.processCollection(requestDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(responseDTO);
    }

    @Operation(summary = "Başvuru ID'sine Göre Ödeme Bilgisi Getir")
    @GetMapping("/by-application/{applicationId}")
    public ResponseEntity<PaymentResponseDTO> getPaymentByApplicationId(@PathVariable Long applicationId) {
        PaymentResponseDTO response = collectionService.getPaymentByApplicationId(applicationId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Ödeme Kaydına Poliçe ID'sini Bağla")
    @PutMapping("/payments/{paymentId}/link-policy")
    public ResponseEntity<Void> linkPolicyToPayment(@PathVariable Long paymentId, @RequestParam Long policyId) {
        collectionService.linkPolicyToPayment(paymentId, policyId);

        return ResponseEntity.ok().build();
    }
}
