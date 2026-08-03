package com.example.collection_service.dto;

import com.example.collection_service.enums.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
public class PaymentRequestDTO {

    @NotNull(message = "Application ID boş olamaz")
    private Long applicationId;

    @NotNull(message = "Ödeme yöntemi seçilmelidir")
    private PaymentMethod paymentMethod;

    @NotNull(message = "Taksit sayısı boş olamaz")
    @Positive(message = "Taksit sayısı 1 veya daha büyük olmalıdır")
    private Integer installmentCount;

    @NotNull(message = "Kart seçilmelidir")
    private Long cardId;

    private String cvcNo;

}