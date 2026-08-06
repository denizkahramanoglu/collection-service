package com.example.collection_service.dto;

import com.example.collection_service.enums.PaymentMethod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationDetailResponseDTO {

    private Long applicationId;
    private BigDecimal price;
    private String currency;
    private LocalDateTime createdAt;
    private CustomerResponseDTO customer;
    private InsuranceProductResponseDTO product;
    private List<CustomerCardResponseDTO> cards;
    private PaymentMethod paymentMethod;
    private Long cardId;

}