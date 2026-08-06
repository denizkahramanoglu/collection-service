package com.example.collection_service.dto;

import com.example.collection_service.enums.PaymentMethod;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentRequestDTO {

    @NotNull(message = "Application ID boş olamaz")
    private Long applicationId;
    private String cvcNo;
    private Integer installmentCount;

    @JsonIgnore
    private PaymentMethod paymentMethod;

    @JsonIgnore
    private Long cardId;
}
