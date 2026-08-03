package com.example.collection_service.service;

import com.example.collection_service.client.ApplicationServiceClient;
import com.example.collection_service.dto.ApplicationDetailResponseDTO;
import com.example.collection_service.dto.CustomerCardResponseDTO;
import com.example.collection_service.dto.InstallmentPayRequestDTO;
import com.example.collection_service.dto.InstallmentResponseDTO;
import com.example.collection_service.entity.InstallmentPlanEntity;
import com.example.collection_service.entity.PaymentEntity;
import com.example.collection_service.enums.InstallmentStatus;
import com.example.collection_service.exception.BusinessException;
import com.example.collection_service.mapper.InstallmentMapper;
import com.example.collection_service.repository.InstallmentPlanRepository;
import com.iyzipay.model.Payment;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InstallmentService {

    private final InstallmentPlanRepository installmentRepository;
    private final ApplicationServiceClient applicationServiceClient;
    private final IyzicoPaymentService iyzicoPaymentService;
    private final InstallmentMapper installmentMapper;

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
        Payment iyzicoResponse = iyzicoPaymentService.paySingleInstallmentWithIyzico(newTransactionId, installment.getAmount(),appData, selectedCard, requestDTO.getCvc());

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
}
