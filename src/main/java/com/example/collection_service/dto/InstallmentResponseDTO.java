package com.example.collection_service.dto;

import com.example.collection_service.enums.InstallmentStatus;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
public class InstallmentResponseDTO {

    private Long id;
    private Long paymentId;
    private Integer installmentNo;
    private BigDecimal amount;
    private LocalDate dueDate;
    private InstallmentStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}