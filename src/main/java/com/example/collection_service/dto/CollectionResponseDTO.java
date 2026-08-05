package com.example.collection_service.dto;

import com.example.collection_service.enums.PaymentMethod;
import com.example.collection_service.enums.PaymentStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CollectionResponseDTO {

    @JsonProperty("collectionId")
    private Long id;

    @JsonProperty("applicationId")
    private Long applicationId;

    @JsonProperty("amount")
    private BigDecimal amount;

    @JsonProperty("currency")
    private String currency;

    @JsonProperty("paymentMethod")
    private PaymentMethod paymentMethod;

    @JsonProperty("status")
    private PaymentStatus status;

    @JsonProperty("transactionId")
    private String transactionId;

    @JsonProperty("installmentCount")
    private Integer installmentCount;

    @JsonProperty("message")
    private String message;

    @JsonProperty("createdAt")
    private LocalDateTime createdAt;
}
