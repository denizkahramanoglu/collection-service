package com.example.collection_service.util;

import com.example.collection_service.exception.BusinessException;
import org.springframework.http.HttpStatus;

public final class BusinessRuleValidator {

    private BusinessRuleValidator() {}

    public static void isTrue(boolean expression, String message, HttpStatus status) {
        if (!expression) {
            throw new BusinessException(message, status);
        }
    }

    public static void isFalse(boolean expression, String message, HttpStatus status) {
        if (expression) {
            throw new BusinessException(message, status);
        }
    }
}