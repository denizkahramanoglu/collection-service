package com.example.collection_service.mapper;

import com.example.collection_service.dto.InstallmentResponseDTO;
import com.example.collection_service.entity.InstallmentPlanEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface InstallmentMapper {


    @Mapping(source = "payment.id", target = "paymentId")
    InstallmentResponseDTO toResponseDTO(InstallmentPlanEntity entity);
    List<InstallmentResponseDTO> toResponseDTOList(List<InstallmentPlanEntity> entities);
}