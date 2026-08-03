package com.example.collection_service.repository;

import com.example.collection_service.entity.InstallmentPlanEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
@Repository
public interface InstallmentPlanRepository extends JpaRepository<InstallmentPlanEntity, Long> {

}