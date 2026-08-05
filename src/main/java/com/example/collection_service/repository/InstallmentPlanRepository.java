package com.example.collection_service.repository;

import com.example.collection_service.entity.InstallmentPlanEntity;
import com.example.collection_service.enums.InstallmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface InstallmentPlanRepository extends JpaRepository<InstallmentPlanEntity, Long> {

    List<InstallmentPlanEntity> findByDueDateLessThanEqualAndStatus(LocalDate date, InstallmentStatus status);

}