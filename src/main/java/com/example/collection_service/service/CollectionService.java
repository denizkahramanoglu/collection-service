package com.example.collection_service.service;

import com.example.collection_service.client.ApplicationServiceClient;
import com.example.collection_service.dto.PaymentRequestDTO;
import com.example.collection_service.dto.PaymentResponseDTO;
import com.example.collection_service.entity.InstallmentPlanEntity;
import com.example.collection_service.enums.InstallmentStatus;
import com.example.collection_service.entity.PaymentEntity;
import com.example.collection_service.enums.PaymentStatus;
import com.example.collection_service.exception.BusinessException;
import com.example.collection_service.mapper.PaymentMapper;
import com.example.collection_service.repository.PaymentRepository;
import com.example.collection_service.util.CreditCardValidationUtil;
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

        switch (requestDTO.getPaymentMethod()) {
            case CREDIT_CARD -> {
                // 1. Kendi içimizdeki güvenlik kontrolleri (Luhn vs.)
                CreditCardValidationUtil.validateCreditCard(
                        requestDTO.getCardNumber(),
                        requestDTO.getExpireMonth(),
                        requestDTO.getExpireYear(),
                        clock
                );

                // 2. GERÇEK İYZİCO İSTEĞİNİ AT (SENKRON - Bekler ve cevabı alır)
                Payment iyzicoResponse = iyzicoPaymentService.payWithIyzico(
                        transactionId,
                        requestDTO.getAmount(),
                        requestDTO.getCardNumber(),
                        String.format("%02d", requestDTO.getExpireMonth()), // 1 ise "01" yapar
                        String.valueOf(requestDTO.getExpireYear())
                );

                // 3. İyzico'dan dönen cevaba göre statüyü belirle
                if ("success".equalsIgnoreCase(iyzicoResponse.getStatus())) {
                    finalPaymentStatus = PaymentStatus.SUCCESS;
                    log.info("İyzico Ödemesi Başarılı! Transaction ID: {}", transactionId);
                } else {
                    log.error("İyzico Ödemesi Reddedildi! Hata: {}", iyzicoResponse.getErrorMessage());
                    // Kullanıcıya anında hatayı dönüyoruz
                    throw new BusinessException("Ödeme banka tarafından reddedildi: " + iyzicoResponse.getErrorMessage(), HttpStatus.BAD_REQUEST);
                }
            }
            case BANK_TRANSFER -> finalPaymentStatus = PaymentStatus.SUCCESS;
            default -> throw new BusinessException("Desteklenmeyen ödeme yöntemi!", HttpStatus.BAD_REQUEST);

        // ENTITY OLUŞTURMA (transactionId ile)
        PaymentEntity payment = PaymentEntity.builder()
                .applicationId(requestDTO.getApplicationId())
                .amount(requestDTO.getAmount())
                .currency(requestDTO.getCurrency())
                .paymentMethod(requestDTO.getPaymentMethod())
                .paymentStatus(finalPaymentStatus)
                .transactionId(transactionId)
                .build();

        List<InstallmentPlanEntity> installments = createInstallmentPlans(payment, requestDTO.getInstallmentCount());

        // Ödeme başarılıysa ve taksit planı varsa ilk taksiti PAID yap
        if (!installments.isEmpty()) {
            installments.getFirst().setStatus(InstallmentStatus.PAID);
        }

        payment.setInstallmentPlans(installments);
        PaymentEntity savedPayment = paymentRepository.save(payment);

        return paymentMapper.toResponseDTO(savedPayment);
    }

    public PaymentResponseDTO getPaymentByApplicationId(Long applicationId) {
        PaymentEntity payment = paymentRepository.findByApplicationId(applicationId)
                .orElseThrow(() -> new BusinessException("Bu başvuruya ait ödeme kaydı bulunamadı! ID: " + applicationId, HttpStatus.NOT_FOUND));

        return paymentMapper.toResponseDTO(payment);
    }

    private List<InstallmentPlanEntity> createInstallmentPlans(PaymentEntity payment, int installmentCount) {
        if (installmentCount <= 0) {
            throw new BusinessException("Taksit sayısı 0'dan büyük olmalıdır!", HttpStatus.BAD_REQUEST);
        }

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
