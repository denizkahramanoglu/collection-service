package com.example.collection_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationDetailResponseDTO {

    // Başvuruya ait temel bilgiler
    private Long applicationId;
    private BigDecimal price;
    private String currency;
    private LocalDateTime createdAt;

    // Dış servislerden gelecek zenginleştirilmiş nesneler
    private CustomerResponseDTO customer;
    private InsuranceProductResponseDTO product;
}