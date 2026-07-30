package com.example.collection_service.util;

import com.example.collection_service.exception.BusinessException;
import org.springframework.http.HttpStatus;
import java.time.Clock;
import java.time.YearMonth;


public final class CreditCardValidationUtil {
    private CreditCardValidationUtil() {}

    public static void validateCreditCard(String cardNumber, Integer expireMonth, Integer expireYear, Clock clock) {
        if (cardNumber == null || expireMonth == null || expireYear == null) {
            throw new BusinessException("Kredi kartı bilgileri eksik!", HttpStatus.BAD_REQUEST);
        }

        YearMonth expiryDate = YearMonth.of(expireYear, expireMonth);
        YearMonth currentMonth = YearMonth.now(clock);

        if (expiryDate.isBefore(currentMonth)) {
            throw new BusinessException("Kredi kartının son kullanma tarihi geçmiş!", HttpStatus.BAD_REQUEST);
        }

        if (!isValidLuhn(cardNumber)) {
            throw new BusinessException("Geçersiz kredi kartı numarası!", HttpStatus.BAD_REQUEST);
        }
    }

    private static boolean isValidLuhn(String cardNumber) {
        String cleanNumber = cardNumber.replaceAll("\\D", "");
        if (cleanNumber.isEmpty()) {
            return false;
        }

        int sum = 0;
        boolean alternate = false;
        for (int i = cleanNumber.length() - 1; i >= 0; i--) {
            int n = cleanNumber.charAt(i) - '0';
            if (alternate) {
                n *= 2;
                if (n > 9) {
                    n -= 9;
                }
            }
            sum += n;
            alternate = !alternate;
        }
        return (sum % 10 == 0);
    }
}