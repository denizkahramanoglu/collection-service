package com.example.collection_service.mapper;

import com.example.collection_service.dto.CollectionResponseDTO;
import com.example.collection_service.dto.InstallmentPlanDTO;
import com.example.collection_service.dto.PaymentResponseDTO;
import com.example.collection_service.entity.InstallmentPlanEntity;
import com.example.collection_service.entity.PaymentEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;


@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PaymentMapper {

    PaymentResponseDTO toResponseDTO(PaymentEntity paymentEntity);

    @Mapping(source = "id", target = "id")
    @Mapping(source = "paymentStatus", target = "status")
    @Mapping(target = "installmentCount", 
            expression = "java(paymentEntity.getInstallmentPlans() != null ? paymentEntity.getInstallmentPlans().size() : 0)")
    CollectionResponseDTO toCollectionResponseDTO(PaymentEntity paymentEntity);

    InstallmentPlanDTO toInstallmentPlanDTO(InstallmentPlanEntity installmentPlanEntity);
}