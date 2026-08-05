package com.example.collection_service.strategy;

import com.example.collection_service.enums.PaymentMethod;
import com.example.collection_service.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class PaymentStrategyFactory {

    // Spring, @Component ile işaretlenmiş ve PaymentStrategy arayüzünü uygulayan
    // tüm sınıfları (Cash, CreditCard) bulup bu listeye otomatik olarak doldurur.
    private final List<PaymentStrategy> strategies;

    public PaymentStrategy getStrategy(PaymentMethod method) {
        return strategies.stream()
                .filter(strategy -> strategy.getSupportedMethod() == method)
                .findFirst()
                .orElseThrow(() -> new BusinessException("Sistemde bu ödeme yöntemi desteklenmemektedir: " + method, HttpStatus.BAD_REQUEST));
    }
}