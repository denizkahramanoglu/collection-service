package com.example.collection_service.service;

import com.example.collection_service.dto.*;
import com.iyzipay.Options;
import com.iyzipay.model.*;
import com.iyzipay.request.CreatePaymentRequest;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class IyzicoPaymentService {

    private final Options options;

    public Payment payWithIyzico(String transactionId, PaymentRequestDTO requestDTO, ApplicationDetailResponseDTO appData, CustomerCardResponseDTO selectedCard) {

        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setLocale(Locale.TR.getValue());
        request.setConversationId(transactionId);
        request.setPrice(appData.getPrice());
        request.setPaidPrice(appData.getPrice());
        request.setCurrency(appData.getCurrency());
        request.setInstallment(requestDTO.getInstallmentCount());

        request.setPaymentChannel(PaymentChannel.WEB.name());
        request.setPaymentGroup(PaymentGroup.PRODUCT.name());

        // Dış servisten gelen verileri ayıklıyoruz
        CustomerResponseDTO customer = appData.getCustomer();
        FullLocationResponseDTO addressDto = customer.getAddress();
        InsuranceProductResponseDTO product = appData.getProduct();

        String fullName = customer.getFirstName() + " " + customer.getLastName();
        String combinedAddress = addressDto.getDistrictName() + ", " +
                addressDto.getCityName() + ", " +
                addressDto.getCountryName();

        // 1. KART BİLGİLERİ (Kullanıcının gönderdiği güvenli DTO'dan)
        PaymentCard paymentCard = new PaymentCard();
        paymentCard.setCardHolderName(fullName);
        paymentCard.setCardNumber(selectedCard.getCardNumber());
        paymentCard.setExpireMonth(String.format("%02d", selectedCard.getExpireMonth()));
        paymentCard.setExpireYear(String.valueOf(selectedCard.getExpireYear()));
        paymentCard.setCvc(requestDTO.getCvcNo());
        paymentCard.setRegisterCard(1);
        request.setPaymentCard(paymentCard);

        // --- DİNAMİK IP ÇÖZÜMLEME BAŞLANGICI ---
        String clientIp = getString();

        //MÜŞTERİ BİLGİLERİ (Application servisinden)
        Buyer buyer = new Buyer();
        buyer.setId(customer.getIdentityNumber());
        buyer.setName(customer.getFirstName());
        buyer.setSurname(customer.getLastName());
        buyer.setGsmNumber(customer.getPhoneNumber());
        buyer.setEmail(customer.getEmail());
        buyer.setIdentityNumber(customer.getIdentityNumber());
        buyer.setRegistrationAddress(combinedAddress);
        buyer.setCity(addressDto.getCityName());
        buyer.setCountry(addressDto.getCountryName());
        buyer.setIp(clientIp);
        request.setBuyer(buyer);
        Address address = new Address();
        address.setContactName(fullName);
        address.setCity(addressDto.getCityName());
        address.setCountry(addressDto.getCountryName());
        address.setAddress(combinedAddress);
        request.setShippingAddress(address);
        request.setBillingAddress(address);
        List<BasketItem> basketItems = new ArrayList<>();
        BasketItem item = new BasketItem();
        item.setId(product.getCode());
        item.setName(product.getName());
        item.setCategory1("Sigorta");
        item.setItemType(BasketItemType.VIRTUAL.name());
        item.setPrice(appData.getPrice());
        basketItems.add(item);

        request.setBasketItems(basketItems);

        // 5. ISTEGI IYZICO'YA GONDER
        log.info("[IYZICO] {} ID'li islem ({}) kullanicisi icin gonderiliyor. Tutar: {} {}, IP: {}",
                transactionId, fullName, appData.getPrice(), appData.getCurrency(), clientIp);

        Payment payment = Payment.create(request, options);

        log.info("[IYZICO] Gelen cevap statusu: {}", payment.getStatus());
        if ("failure".equalsIgnoreCase(payment.getStatus())) {
            log.error("[IYZICO] Hata detayi: {}", payment.getErrorMessage());
        }

        return payment;
    }

    private static String getString() {
        String clientIp = "127.0.0.1";
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest httpRequest = attributes.getRequest();
            clientIp = httpRequest.getHeader("X-Forwarded-For");
            if (clientIp == null || clientIp.isEmpty()) {
                clientIp = httpRequest.getRemoteAddr();
            }
            if (clientIp != null && clientIp.contains(",")) {
                clientIp = clientIp.split(",")[0].trim();
            }
        }
        return clientIp;
    }
}