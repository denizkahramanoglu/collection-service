package com.example.collection_service.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class CustomerResponseDTO {
    private Long id;
    private String identityNumber;
    private String firstName;
    private String lastName;
    private FullLocationResponseDTO address;
    private Long placeOfBirthCityId;
    private LocalDate dateOfBirth;
    private String phoneNumber;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
    private String gender;
    private String email;
    private List<CustomerCardResponseDTO> cards;

}