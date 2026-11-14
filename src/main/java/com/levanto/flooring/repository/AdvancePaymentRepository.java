package com.levanto.flooring.repository;

import com.levanto.flooring.entity.AdvancePayment;
import com.levanto.flooring.enums.AdvancePaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AdvancePaymentRepository extends JpaRepository<AdvancePayment, Long> {
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"project"})
    java.util.Optional<AdvancePayment> findById(Long id);

    List<AdvancePayment> findByProjectId(Long projectId);
    List<AdvancePayment> findByProjectIdAndStatus(Long projectId, AdvancePaymentStatus status);
    List<AdvancePayment> findByStatus(AdvancePaymentStatus status);
    java.util.Optional<AdvancePayment> findBySettledBillId(Long transactionId);
    
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"project"})
    List<AdvancePayment> findAllByOrderByIdDesc();
}
