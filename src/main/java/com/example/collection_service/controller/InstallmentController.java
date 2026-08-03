package com.example.collection_service.controller;

import com.example.collection_service.dto.InstallmentPayRequestDTO;
import com.example.collection_service.dto.InstallmentResponseDTO;
import com.example.collection_service.service.InstallmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/installments")
@RequiredArgsConstructor
public class InstallmentController {

    private final InstallmentService installmentService;

    @PutMapping("/{installmentId}/pay")
    public ResponseEntity<InstallmentResponseDTO> payInstallment(@PathVariable Long installmentId, @Valid @RequestBody InstallmentPayRequestDTO requestDTO) {

        InstallmentResponseDTO response = installmentService.payInstallment(installmentId, requestDTO);
        return ResponseEntity.ok(response);
    }
}