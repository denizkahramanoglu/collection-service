package com.example.collection_service.dto;

import com.example.collection_service.enums.PaymentMethod;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CollectionRequestDTO {

    @NotNull(message = "Application ID boş olamaz")
    @Positive(message = "Application ID pozitif bir sayı olmalıdır")
    private Long applicationId;

    @NotNull(message = "Miktar boş olamaz")
    @DecimalMin(value = "0.01", message = "Miktar 0.01'den büyük olmalıdır")
    private BigDecimal amount;

    @NotBlank(message = "Para birimi boş olamaz")
    @Size(min = 3, max = 10, message = "Para birimi 3-10 karakter arasında olmalıdır")
    private String currency;

    @NotNull(message = "Ödeme yöntemi seçilmelidir")
    private PaymentMethod paymentMethod;

    private Integer installmentCount;

    private Long cardId;

    @Size(min = 3, max = 4, message = "CVC kodu 3-4 karakter arasında olmalıdır")
    private String cvcNo;

    // Application-service'ten gelen müşteri bilgileri
    private CustomerResponseDTO customer;

    // Müşterinin kartları (kredi kartı ödeme için gerekli)
    private List<CustomerCardResponseDTO> cards;

    // Sigorta ürün bilgileri (Iyzico ödemesi için gerekli)
    private InsuranceProductResponseDTO product;
}
