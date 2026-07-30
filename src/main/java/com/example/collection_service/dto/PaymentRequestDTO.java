package com.example.collection_service.dto;

import com.example.collection_service.enums.PaymentMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class PaymentRequestDTO {

    @NotNull(message = "Application ID boş olamaz")
    private Long applicationId;

    @NotNull(message = "Tutar boş olamaz")
    @Positive(message = "Tutar sıfırdan büyük olmalıdır")
    private BigDecimal amount;

    @NotBlank(message = "Para birimi boş olamaz")
    private String currency;

    @NotNull(message = "Ödeme yöntemi seçilmelidir")
    private PaymentMethod paymentMethod;

    @NotNull(message = "Taksit sayısı boş olamaz")
    @Positive(message = "Taksit sayısı 1 veya daha büyük olmalıdır")
    private Integer installmentCount;

    private String cardNumber;
    private Integer expireMonth;
    private Integer expireYear;

}