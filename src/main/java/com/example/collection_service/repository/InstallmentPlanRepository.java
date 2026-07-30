package com.example.collection_service.repository;

import com.example.collection_service.entity.InstallmentPlanEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InstallmentPlanRepository extends JpaRepository<InstallmentPlanEntity, Long> {
    List<InstallmentPlanEntity> findByPaymentId(Long paymentId);

}