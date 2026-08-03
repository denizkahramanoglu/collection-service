package com.example.collection_service.controller;

import com.example.collection_service.dto.PaymentRequestDTO;
import com.example.collection_service.dto.PaymentResponseDTO;
import com.example.collection_service.service.CollectionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CollectionControllerTest {

    @Mock
    private CollectionService collectionService;

    @InjectMocks
    private CollectionController collectionController;

    @Test
    void shouldProcessCollection_Successfully() {
        // Arrange
        PaymentRequestDTO requestDTO = new PaymentRequestDTO();
        PaymentResponseDTO mockResponse = new PaymentResponseDTO();
        when(collectionService.processCollection(any(PaymentRequestDTO.class))).thenReturn(mockResponse);
        // Act
        ResponseEntity<PaymentResponseDTO> responseEntity = collectionController.processCollection(requestDTO);
        // Assert
        assertNotNull(responseEntity);
        assertEquals(HttpStatus.CREATED, responseEntity.getStatusCode());
        assertEquals(mockResponse, responseEntity.getBody());
        // Servisin çağrıldığını doğrula
        verify(collectionService).processCollection(any(PaymentRequestDTO.class));
    }

    @Test
    void shouldGetPaymentByApplicationId_Successfully() {
        // Arrange
        Long applicationId = 100L;
        PaymentResponseDTO mockResponse = new PaymentResponseDTO();
        when(collectionService.getPaymentByApplicationId((applicationId))).thenReturn(mockResponse);
        // Act
        ResponseEntity<PaymentResponseDTO> responseEntity = collectionController.getPaymentByApplicationId(applicationId);
        // Assert
        assertNotNull(responseEntity);
        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertEquals(mockResponse, responseEntity.getBody());
        verify(collectionService).getPaymentByApplicationId(applicationId);
    }
}