package com.example.collection_service.repository;

import com.example.collection_service.entity.PaymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<PaymentEntity, Long> {

    Optional<PaymentEntity> findByApplicationId(Long applicationId);
    Optional<PaymentEntity> findByTransactionId(String transactionId);
}