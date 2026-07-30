package com.example.collection_service.exception;

import org.springframework.http.HttpStatus;

public class BusinessException extends RuntimeException {

    private final HttpStatus status;

    // 1. Sadece mesaj verirsen varsayılan olarak 400 Bad Request döner
    public BusinessException(String message) {
        super(message);
        this.status = HttpStatus.BAD_REQUEST;
    }

    // 2. Hem mesaj hem de statü kodu verirsen senin verdiğin kodu döner
    public BusinessException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}