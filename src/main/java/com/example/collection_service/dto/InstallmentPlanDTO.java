package com.example.collection_service.dto;

import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class InstallmentPlanDTO {
    private Integer installmentNo;
    private LocalDate dueDate;
    private BigDecimal amount;
    private String status;
}