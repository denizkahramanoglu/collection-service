package com.example.collection_service.controller;

import com.example.collection_service.dto.InstallmentPayRequestDTO;
import com.example.collection_service.dto.InstallmentResponseDTO;
import com.example.collection_service.service.InstallmentService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InstallmentControllerTest {

    @Mock
    private InstallmentService installmentService;

    @InjectMocks
    private InstallmentController installmentController;

    @Test
    void shouldPayInstallment_Successfully() {
        Long installmentId = 10L;
        InstallmentPayRequestDTO requestDTO = new InstallmentPayRequestDTO();
        InstallmentResponseDTO mockResponse = new InstallmentResponseDTO();
        when(installmentService.payInstallment(eq(installmentId), any(InstallmentPayRequestDTO.class)))
                .thenReturn(mockResponse);

        ResponseEntity<InstallmentResponseDTO> responseEntity = installmentController.payInstallment(installmentId, requestDTO);
        assertNotNull(responseEntity);
        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertEquals(mockResponse, responseEntity.getBody());
        verify(installmentService).payInstallment(eq(installmentId), any(InstallmentPayRequestDTO.class));
    }
}