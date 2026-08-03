package com.example.collection_service.mapper;

import com.example.collection_service.dto.PaymentResponseDTO;
import com.example.collection_service.entity.PaymentEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;


@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PaymentMapper {

    @Mapping(source = "paymentStatus", target = "paymentStatus")
    PaymentResponseDTO toResponseDTO(PaymentEntity paymentEntity);

    }
