package com.example.collection_service.util;

import com.example.collection_service.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.*;

class BusinessRuleValidatorTest {

    @Test
    void shouldNotThrowException_WhenIsTrue_ExpressionIsTrue() {
        // İfade true olduğunda hata fırlatmamalı (sessizce geçmeli)
        assertDoesNotThrow(() ->
                BusinessRuleValidator.isTrue(true, "Hata olmamalı", HttpStatus.BAD_REQUEST)
        );
    }

    @Test
    void shouldThrowBusinessException_WhenIsTrue_ExpressionIsFalse() {
        // İfade false olduğunda BusinessException fırlatmalı
        BusinessException exception = assertThrows(BusinessException.class, () ->
                BusinessRuleValidator.isTrue(false, "Beklenen hata mesajı", HttpStatus.NOT_FOUND)
        );

        assertEquals("Beklenen hata mesajı", exception.getMessage());
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
    }

    @Test
    void shouldNotThrowException_WhenIsFalse_ExpressionIsFalse() {
        // İfade false olduğunda hata fırlatmamalı
        assertDoesNotThrow(() ->
                BusinessRuleValidator.isFalse(false, "Hata olmamalı", HttpStatus.BAD_REQUEST)
        );
    }

    @Test
    void shouldThrowBusinessException_WhenIsFalse_ExpressionIsTrue() {
        // İfade true olduğunda BusinessException fırlatmalı
        BusinessException exception = assertThrows(BusinessException.class, () ->
                BusinessRuleValidator.isFalse(true, "Yanlış durum hatası", HttpStatus.BAD_REQUEST)
        );

        assertEquals("Yanlış durum hatası", exception.getMessage());
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
    }
}