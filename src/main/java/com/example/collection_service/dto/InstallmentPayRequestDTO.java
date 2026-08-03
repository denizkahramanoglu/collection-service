package com.example.collection_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;


@Data
public class InstallmentPayRequestDTO {

    @NotNull(message = "Kart seçimi zorunludur!")
    private Long cardId;

    @NotBlank(message = "CVC boş olamaz!")
    @Size(min = 3, max = 4, message = "Geçerli bir CVC giriniz")
    private String cvc;
}