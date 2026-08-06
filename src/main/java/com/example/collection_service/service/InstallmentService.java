package com.example.collection_service.service;

import com.example.collection_service.client.ApplicationServiceClient;
import com.example.collection_service.dto.ApplicationDetailResponseDTO;
import com.example.collection_service.dto.CustomerCardResponseDTO;
import com.example.collection_service.dto.InstallmentPayRequestDTO;
import com.example.collection_service.dto.InstallmentResponseDTO;
import com.example.collection_service.entity.InstallmentPlanEntity;
import com.example.collection_service.entity.PaymentEntity;
import com.example.collection_service.enums.InstallmentStatus;
import com.example.collection_service.enums.PaymentStatus;
import com.example.collection_service.exception.BusinessException;
import com.example.collection_service.mapper.InstallmentMapper;
import com.example.collection_service.repository.InstallmentPlanRepository;
import com.example.collection_service.repository.PaymentRepository;
import com.example.collection_service.util.BusinessRuleValidator;
import com.iyzipay.model.Payment;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InstallmentService {

    private final InstallmentPlanRepository installmentRepository;
    private final ApplicationServiceClient applicationServiceClient;
    private final IyzicoPaymentService iyzicoPaymentService;
    private final InstallmentMapper installmentMapper;
    private final Clock clock;
    private final PaymentRepository paymentRepository;

    /**
     * Belirtilen taksit ID'sine ait ödemeyi, İyzico entegrasyonu ve seçilen kart bilgileriyle gerçekleştirir.
     *
     * @param installmentId Ödemesi yapılacak taksit planının benzersiz ID'si
     * @param requestDTO    Kart ID ve CVC bilgilerini barındıran {@link InstallmentPayRequestDTO} nesnesi
     * @return Ödemesi tamamlanan taksitin güncel verilerini içeren {@link InstallmentResponseDTO} nesnesi
     * @throws BusinessException Taksit bulunamazsa, zaten ödenmişse, kart müşteriye ait değilse veya ödeme İyzico tarafından reddedilirse fırlatılır
     */
    @Transactional
    public InstallmentResponseDTO payInstallment(Long installmentId, InstallmentPayRequestDTO requestDTO) {

        InstallmentPlanEntity installment = installmentRepository.findById(installmentId)
                .orElseThrow(() -> new BusinessException("Taksit bulunamadı!", HttpStatus.NOT_FOUND));

        if (installment.getStatus() == InstallmentStatus.PAID) {
            throw new BusinessException("Bu taksit zaten ödenmiş!", HttpStatus.BAD_REQUEST);
        }

        PaymentEntity parentPayment = installment.getPayment();
        // Diğer servisten başvuru ve kart bilgilerini getir
        log.info("Application Service'ten {} ID'li başvuru için kart bilgileri çekiliyor...", parentPayment.getApplicationId());

        ApplicationDetailResponseDTO appData = applicationServiceClient.getApplicationDetails(parentPayment.getApplicationId());

        // Kullanıcının seçtiği kartı listeden bul
        CustomerCardResponseDTO selectedCard = appData.getCustomer()
                .getCards()
                .stream()
                .filter(card -> card.getId().equals(requestDTO.getCardId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("Seçilen kart müşteride bulunamadı!", HttpStatus.NOT_FOUND));

        String newTransactionId = UUID.randomUUID().toString();
        Payment iyzicoResponse = iyzicoPaymentService.paySingleInstallmentWithIyzico(newTransactionId, installment.getAmount(), appData, selectedCard, requestDTO.getCvc());

        if ("success".equalsIgnoreCase(iyzicoResponse.getStatus())) {
            log.info("{} ID'li taksit başarıyla tahsil edildi.", installmentId);
            installment.setStatus(InstallmentStatus.PAID);
        } else {
            log.error("Taksit tahsilatı reddedildi! Hata: {}", iyzicoResponse.getErrorMessage());
            throw new BusinessException("Ödeme reddedildi: " + iyzicoResponse.getErrorMessage(), HttpStatus.BAD_REQUEST);
        }

        InstallmentPlanEntity savedInstallment = installmentRepository.save(installment);
        return installmentMapper.toResponseDTO(savedInstallment);
    }

    /**
     * Belirtilen ödemeyi istenen taksit sayısına göre taksitlendirir.
     *
     * @param paymentId        Taksitlendirilecek ödemenin benzersiz ID'si
     * @param installmentCount Bölünecek toplam taksit sayısı
     * @throws BusinessException Ödeme kaydı bulunamazsa, taksit sayısı 0 veya daha küçükse ya da ödeme zaten taksitlendirilmişse fırlatılır
     */

    @Transactional
    public void splitPaymentIntoInstallments(Long paymentId, int installmentCount) {

        log.info("{} numaralı ödeme {} taksite bölünüyor.", paymentId, installmentCount);

        PaymentEntity payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new BusinessException("Ödeme kaydı bulunamadı! ID: " + paymentId, HttpStatus.NOT_FOUND));

        BusinessRuleValidator.isTrue(installmentCount > 0, "Taksit sayısı 0'dan büyük olmalıdır!", HttpStatus.BAD_REQUEST);
        boolean alreadySplit = payment.getInstallmentPlans() != null && !payment.getInstallmentPlans().isEmpty();
        BusinessRuleValidator.isTrue(!alreadySplit, "Bu ödeme zaten taksitlendirilmiş!", HttpStatus.BAD_REQUEST);
        BigDecimal totalAmount = payment.getAmount();
        BigDecimal baseInstallmentAmount = totalAmount.divide(BigDecimal.valueOf(installmentCount), 2, RoundingMode.DOWN);
        BigDecimal remainder = totalAmount.subtract(baseInstallmentAmount.multiply(BigDecimal.valueOf(installmentCount)));

        List<InstallmentPlanEntity> plans = new ArrayList<>(installmentCount);
        LocalDate today = LocalDate.now(clock);

        for (int i = 1; i <= installmentCount; i++) {

            BigDecimal currentAmount = (i == installmentCount)
                    ? baseInstallmentAmount.add(remainder)
                    : baseInstallmentAmount;

            InstallmentPlanEntity installment = InstallmentPlanEntity.builder()
                    .payment(payment)
                    .installmentNo(i)
                    .amount(currentAmount)
                    .dueDate(today.plusMonths(i - 1L))
                    .status(InstallmentStatus.UNPAID)
                    .build();

            plans.add(installment);
        }

        if (payment.getPaymentStatus() == PaymentStatus.SUCCESS && !plans.isEmpty()) {
            plans.getFirst().setStatus(InstallmentStatus.PAID);
        }

        payment.setInstallmentPlans(plans);
        paymentRepository.save(payment);

        log.info("{} numaralı ödeme için {} taksit planı oluşturuldu.",
                paymentId, installmentCount);
    }
}