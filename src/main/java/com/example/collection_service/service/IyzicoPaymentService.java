package com.example.collection_service.service;

import com.iyzipay.Options;
import com.iyzipay.model.*;
import com.iyzipay.request.CreatePaymentRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class IyzicoPaymentService {

    // Konfigürasyonda hazırladığımız anahtarları enjekte ediyoruz
    private final Options options;

    public Payment payWithIyzico(String transactionId, BigDecimal price, String cardNumber, String expireMonth, String expireYear) {

        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setLocale(Locale.TR.getValue());
        request.setConversationId(transactionId);
        request.setPrice(price);
        request.setPaidPrice(price); // Taksit komisyonu eklemediğimizi varsayıyoruz
        request.setCurrency(Currency.TRY.name());
        request.setInstallment(1); // Test için peşin çekim yapıyoruz
        request.setPaymentChannel(PaymentChannel.WEB.name());
        request.setPaymentGroup(PaymentGroup.PRODUCT.name());

        // 1. KART BİLGİLERİ
        PaymentCard paymentCard = new PaymentCard();
        paymentCard.setCardHolderName("Deniz Kahramanoglu"); // Gerçekte DTO'dan gelmeli
        paymentCard.setCardNumber(cardNumber);
        paymentCard.setExpireMonth(expireMonth);
        paymentCard.setExpireYear(expireYear);
        paymentCard.setCvc("123"); // Test ortamında 123 geçerlidir
        paymentCard.setRegisterCard(0);
        request.setPaymentCard(paymentCard);

        // 2. MÜŞTERİ BİLGİLERİ (Zorunlu)
        Buyer buyer = new Buyer();
        buyer.setId("BY789");
        buyer.setName("Deniz");
        buyer.setSurname("Kahramanoglu");
        buyer.setGsmNumber("+905555555555");
        buyer.setEmail("deniz@example.com");
        buyer.setIdentityNumber("74300864791");
        buyer.setLastLoginDate("2026-07-30 11:55:16");
        buyer.setRegistrationDate("2026-07-06 12:00:00");
        buyer.setRegistrationAddress("Corlu Muhendislik Fakultesi");
        buyer.setIp("85.34.78.112");
        buyer.setCity("Tekirdag");
        buyer.setCountry("Turkey");
        buyer.setZipCode("59860");
        request.setBuyer(buyer);

        // 3. ADRES BİLGİLERİ (Zorunlu)
        Address address = new Address();
        address.setContactName("Deniz Kahramanoglu");
        address.setCity("Tekirdag");
        address.setCountry("Turkey");
        address.setAddress("Corlu Muhendislik Fakultesi, Namik Kemal Universitesi");
        address.setZipCode("59860");
        request.setShippingAddress(address);
        request.setBillingAddress(address);

        // 4. SEPET BİLGİLERİ (Zorunlu)
        List<BasketItem> basketItems = new ArrayList<>();
        BasketItem item = new BasketItem();
        item.setId("BI101");
        item.setName("Hayat Sigortasi Policesi");
        item.setCategory1("Sigorta");
        item.setItemType(BasketItemType.VIRTUAL.name()); // Fiziksel ürün değil
        item.setPrice(price);
        basketItems.add(item);
        request.setBasketItems(basketItems);

        // 5. ISTEGI IYZICO'YA GONDER
        log.info("[IYZICO] {} ID'li islem iyzico'ya gonderiliyor...", transactionId);

        // Asıl sihir burada gerçekleşiyor!
        Payment payment = Payment.create(request, options);

        log.info("[IYZICO] Gelen cevap statusu: {}", payment.getStatus());
        if ("failure".equalsIgnoreCase(payment.getStatus())) {
            log.error("[IYZICO] Hata detayi: {}", payment.getErrorMessage());
        }

        return payment;
    }
}