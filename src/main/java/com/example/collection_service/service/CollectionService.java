package com.example.collection_service.service;

import com.example.collection_service.client.ApplicationServiceClient;
import com.example.collection_service.dto.ApplicationDetailResponseDTO;
import com.example.collection_service.dto.CustomerCardResponseDTO;
import com.example.collection_service.dto.PaymentRequestDTO;
import com.example.collection_service.dto.PaymentResponseDTO;
import com.example.collection_service.entity.InstallmentPlanEntity;
import com.example.collection_service.enums.InstallmentStatus;
import com.example.collection_service.entity.PaymentEntity;
import com.example.collection_service.enums.PaymentStatus;
import com.example.collection_service.exception.BusinessException;
import com.example.collection_service.mapper.PaymentMapper;
import com.example.collection_service.repository.PaymentRepository;
import com.example.collection_service.util.BusinessRuleValidator;
import com.iyzipay.model.Payment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CollectionService {

    private final PaymentRepository paymentRepository;
    private final PaymentMapper paymentMapper;
    private final IyzicoPaymentService iyzicoPaymentService;
    private final Clock clock;
    private final ApplicationServiceClient applicationServiceClient;

    @Transactional
    public PaymentResponseDTO processCollection(PaymentRequestDTO requestDTO) {

        String transactionId = UUID.randomUUID().toString();
        PaymentStatus finalPaymentStatus;

        log.info("Application Service'ten {} ID'li başvuru bilgileri çekiliyor...", requestDTO.getApplicationId());
        ApplicationDetailResponseDTO appData = applicationServiceClient.getApplicationDetails(requestDTO.getApplicationId());

        CustomerCardResponseDTO selectedCard = appData.getCustomer()
                .getCards()
                .stream()
                .filter(card -> card.getId().equals(requestDTO.getCardId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("Seçilen kart bulunamadı!", HttpStatus.NOT_FOUND));

        switch (requestDTO.getPaymentMethod()) {

            case CREDIT_CARD -> {

                boolean isForeignCurrencyInstallment = !"TRY".equalsIgnoreCase(appData.getCurrency()) && requestDTO.getInstallmentCount() > 1;
                BusinessRuleValidator.isFalse(isForeignCurrencyInstallment, "TRY dışındaki para birimleri için taksit yapılamaz!", HttpStatus.BAD_REQUEST);
                Payment iyzicoResponse = iyzicoPaymentService.payWithIyzico(transactionId, requestDTO, appData, selectedCard);

                if ("success".equalsIgnoreCase(iyzicoResponse.getStatus())) {
                    finalPaymentStatus = PaymentStatus.SUCCESS;
                } else {
                    log.error("İyzico Ödemesi Reddedildi! Hata: {}", iyzicoResponse.getErrorMessage());
                    throw new BusinessException("Ödeme banka tarafından reddedildi: " + iyzicoResponse.getErrorMessage(), HttpStatus.BAD_REQUEST);
                }
            }
            case BANK_TRANSFER -> finalPaymentStatus = PaymentStatus.SUCCESS;
            default -> throw new BusinessException("Desteklenmeyen ödeme yöntemi!", HttpStatus.BAD_REQUEST);}

        PaymentEntity payment = PaymentEntity.builder()
                .applicationId(requestDTO.getApplicationId())
                .amount(appData.getPrice())
                .currency(appData.getCurrency())
                .paymentMethod(requestDTO.getPaymentMethod())
                .paymentStatus(finalPaymentStatus)
                .transactionId(transactionId)
                .build();

        List<InstallmentPlanEntity> installments =
                createInstallmentPlans(payment, requestDTO.getInstallmentCount());

        if (!installments.isEmpty()) {
            installments.getFirst().setStatus(InstallmentStatus.PAID);
        }

        payment.setInstallmentPlans(installments);
        PaymentEntity savedPayment = paymentRepository.save(payment);

        return paymentMapper.toResponseDTO(savedPayment);
    }

    public PaymentResponseDTO getPaymentByApplicationId(Long applicationId) {
        PaymentEntity payment = paymentRepository.findTopByApplicationIdOrderByIdDesc(applicationId)
                .orElseThrow(() -> new BusinessException("Bu başvuruya ait ödeme kaydı bulunamadı! ID: " + applicationId, HttpStatus.NOT_FOUND));

        return paymentMapper.toResponseDTO(payment);
    }

    private List<InstallmentPlanEntity> createInstallmentPlans(PaymentEntity payment, int installmentCount) {

        BusinessRuleValidator.isTrue(installmentCount > 0, "Taksit sayısı 0'dan büyük olmalıdır!", HttpStatus.BAD_REQUEST);
        BigDecimal totalAmount = payment.getAmount();
        BigDecimal baseInstallmentAmount = totalAmount.divide(BigDecimal.valueOf(installmentCount), 2, RoundingMode.HALF_UP);
        BigDecimal remainder = totalAmount.subtract(baseInstallmentAmount.multiply(BigDecimal.valueOf(installmentCount)));
        List<InstallmentPlanEntity> plans = new ArrayList<>(installmentCount);
        LocalDate today = LocalDate.now(clock);

        for (int i = 1; i <= installmentCount; i++) {
            BigDecimal currentAmount = (i == installmentCount)
                    ? baseInstallmentAmount.add(remainder)
                    : baseInstallmentAmount;

            plans.add(InstallmentPlanEntity.builder()
                    .payment(payment)
                    .installmentNo(i)
                    .amount(currentAmount)
                    .dueDate(today.plusMonths((long)i - 1))
                    .status(InstallmentStatus.UNPAID)
                    .build());
        }

        return plans;
    }
}
