package com.example.collection_service.service;

import com.example.collection_service.dto.*;
import com.example.collection_service.util.NetworkUtil;
import com.iyzipay.Options;
import com.iyzipay.model.*;
import com.iyzipay.request.CreatePaymentRequest;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class IyzicoPaymentService {

    private final Options options;

    public Payment payWithIyzico(String transactionId, PaymentRequestDTO requestDTO, ApplicationDetailResponseDTO appData, CustomerCardResponseDTO selectedCard) {

        PaymentCard paymentCard = createPaymentCard(appData, selectedCard, requestDTO.getCvcNo());
        paymentCard.setRegisterCard(1);

        CreatePaymentRequest request = buildIyzicoRequest(
                transactionId,
                appData.getPrice(),
                requestDTO.getInstallmentCount(),
                appData,
                paymentCard,
                appData.getProduct().getName()
        );

        log.info("[IYZICO] {} ID'li islem gonderiliyor. Tutar: {}", transactionId, appData.getPrice());
        return Payment.create(request, options);
    }
    public Payment paySingleInstallmentWithIyzico(String transactionId, BigDecimal price, ApplicationDetailResponseDTO appData, CustomerCardResponseDTO selectedCard, String cvc) {


        PaymentCard paymentCard = createPaymentCard(appData, selectedCard, cvc);
        CreatePaymentRequest request = buildIyzicoRequest(transactionId, price, 1, appData, paymentCard, appData.getProduct().getName() + " - Taksit Ödemesi");

        log.info("[IYZICO-TAKSIT] {} ID'li tek taksit islemi icin gonderiliyor. Tutar: {} {}", transactionId, price, appData.getCurrency());
        Payment payment = Payment.create(request, options);
        log.info("[IYZICO-TAKSIT] Gelen cevap statusu: {}", payment.getStatus());
        if ("failure".equalsIgnoreCase(payment.getStatus())) {
            log.error("[IYZICO-TAKSIT] Hata detayi: {}", payment.getErrorMessage());
        }

        return payment;
    }
    private CreatePaymentRequest buildIyzicoRequest(String transactionId, BigDecimal price, int installment, ApplicationDetailResponseDTO appData, PaymentCard paymentCard, String basketItemName) {

        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setLocale(Locale.TR.getValue());
        request.setConversationId(transactionId);
        request.setPrice(price);
        request.setPaidPrice(price);
        request.setCurrency(appData.getCurrency());
        request.setInstallment(installment);
        request.setPaymentChannel(PaymentChannel.WEB.name());
        request.setPaymentGroup(PaymentGroup.PRODUCT.name());
        CustomerResponseDTO customer = appData.getCustomer();
        FullLocationResponseDTO addressDto = customer.getAddress();

        String fullName = customer.getFirstName() + " " + customer.getLastName();
        String combinedAddress = addressDto.getDistrictName() + ", " + addressDto.getCityName() + ", " + addressDto.getCountryName();
        String clientIp = NetworkUtil.getClientIp();

        // Kart Bilgisi
        request.setPaymentCard(paymentCard);

        // Müşteri (Buyer) Bilgisi
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

        // Adres Bilgisi
        Address address = new Address();
        address.setContactName(fullName);
        address.setCity(addressDto.getCityName());
        address.setCountry(addressDto.getCountryName());
        address.setAddress(combinedAddress);
        request.setShippingAddress(address);
        request.setBillingAddress(address);

        // Sepet Bilgisi
        List<BasketItem> basketItems = new ArrayList<>();
        BasketItem item = new BasketItem();
        item.setId(appData.getProduct().getCode());
        item.setName(basketItemName);
        item.setCategory1("Sigorta");
        item.setItemType(BasketItemType.VIRTUAL.name());
        item.setPrice(price);
        basketItems.add(item);
        request.setBasketItems(basketItems);

        return request;
    }
    private PaymentCard createPaymentCard(ApplicationDetailResponseDTO appData, CustomerCardResponseDTO selectedCard, String cvc) {
        String fullName = appData.getCustomer().getFirstName() + " " + appData.getCustomer().getLastName();

        PaymentCard paymentCard = new PaymentCard();
        paymentCard.setCardHolderName(fullName);
        paymentCard.setCardNumber(selectedCard.getCardNumber());
        paymentCard.setExpireMonth(String.format("%02d", selectedCard.getExpireMonth()));
        paymentCard.setExpireYear(String.valueOf(selectedCard.getExpireYear()));
        paymentCard.setCvc(cvc);

        return paymentCard;
    }
}