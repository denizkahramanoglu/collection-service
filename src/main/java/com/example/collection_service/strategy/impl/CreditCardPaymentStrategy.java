package com.example.collection_service.strategy.impl;

import com.example.collection_service.dto.ApplicationDetailResponseDTO;
import com.example.collection_service.dto.CustomerCardResponseDTO;
import com.example.collection_service.dto.PaymentRequestDTO;
import com.example.collection_service.enums.PaymentMethod;
import com.example.collection_service.enums.PaymentStatus;
import com.example.collection_service.exception.BusinessException;
import com.example.collection_service.service.IyzicoPaymentService;
import com.example.collection_service.strategy.PaymentStrategy;
import com.example.collection_service.util.BusinessRuleValidator;
import com.iyzipay.model.Payment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CreditCardPaymentStrategy implements PaymentStrategy {

    private final IyzicoPaymentService iyzicoPaymentService;

    @Override
    public PaymentMethod getSupportedMethod() {
        return PaymentMethod.CREDIT_CARD;
    }

    @Override
    public PaymentStatus process(PaymentRequestDTO requestDTO, ApplicationDetailResponseDTO appData, String transactionId) {


        if (requestDTO.getCvcNo() == null || requestDTO.getCvcNo().trim().isEmpty()) {
            throw new BusinessException("Kredi kartı ile ödemelerde CVC numarası zorunludur!", HttpStatus.BAD_REQUEST);
        }

        // 2. Kart Seçimi Kontrolü
        if (requestDTO.getCardId() == null) {
            throw new BusinessException("Kredi kartı ile ödemelerde kart seçimi zorunludur!", HttpStatus.BAD_REQUEST);
        }


        // 3. Müşterinin kartları arasından seçilen kartı bulma
        CustomerCardResponseDTO selectedCard = appData.getCustomer().getCards().stream()
                .filter(card -> card.getId().equals(requestDTO.getCardId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("Seçilen kart bulunamadı veya bu müşteriye ait değil!", HttpStatus.NOT_FOUND));

        // 4. İyzico Entegrasyonu
        log.info("İyzico ödeme altyapısına {} ID'li işlem gönderiliyor...", transactionId);
        Payment iyzicoResponse = iyzicoPaymentService.payWithIyzico(transactionId, requestDTO, appData, selectedCard);

        if ("success".equalsIgnoreCase(iyzicoResponse.getStatus())) {
            return PaymentStatus.SUCCESS;
        } else {
            log.error("İyzico Ödemesi Reddedildi! Hata: {}", iyzicoResponse.getErrorMessage());
            throw new BusinessException("Ödeme banka tarafından reddedildi: " + iyzicoResponse.getErrorMessage(), HttpStatus.BAD_REQUEST);
        }
    }
}