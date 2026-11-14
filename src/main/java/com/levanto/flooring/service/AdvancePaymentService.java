package com.levanto.flooring.service;

import com.levanto.flooring.entity.AdvancePayment;
import com.levanto.flooring.entity.IncomeTransaction;
import com.levanto.flooring.entity.Project;
import com.levanto.flooring.enums.AdvancePaymentStatus;
import com.levanto.flooring.repository.AdvancePaymentRepository;
import com.levanto.flooring.repository.IncomeTransactionRepository;
import com.levanto.flooring.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

@Service
@RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class AdvancePaymentService {

    private final AdvancePaymentRepository advanceRepo;
    private final IncomeTransactionRepository incomeRepo;
    private final ProjectRepository projectRepo;

    public List<AdvancePayment> getAll() {
        return advanceRepo.findAllByOrderByIdDesc();
    }

    public List<AdvancePayment> getByProject(Long projectId) {
        return advanceRepo.findByProjectId(projectId);
    }

    public List<AdvancePayment> getUnsettledByProject(Long projectId) {
        if (projectId == null || projectId == 0) {
            return advanceRepo.findByStatus(AdvancePaymentStatus.RECEIVED);
        }
        return advanceRepo.findByProjectIdAndStatus(projectId, AdvancePaymentStatus.RECEIVED);
    }

    public AdvancePayment getById(Long id) {
        return advanceRepo.findById(id).orElseThrow(() -> new RuntimeException("Advance payment not found"));
    }

    @Transactional
    public AdvancePayment save(AdvancePayment ap) {
        Project project = null;
        if (ap.getProject() != null && ap.getProject().getId() != null) {
            project = projectRepo.findById(ap.getProject().getId())
                    .orElseThrow(() -> new RuntimeException("Project not found"));
            ap.setProject(project);
        } else {
            ap.setProject(null);
        }

        if (ap.getReceiptNumber() == null) {
            ap.setReceiptNumber("ADV-" + System.currentTimeMillis() % 1000000 + (new Random().nextInt(90) + 10));
        }

        AdvancePayment saved = advanceRepo.save(ap);

        // Create Income record
        log.info("Creating income transaction for advance payment: {}", saved.getReceiptNumber());
        
        String title = "Advance Payment";
        StringBuilder desc = new StringBuilder("Advance received");
        if (saved.getCollectedFrom() != null && !saved.getCollectedFrom().isBlank()) {
            desc.append(" from ").append(saved.getCollectedFrom());
        }
        if (project != null) {
            title += ": " + project.getName();
            desc.append(" for Project: ").append(project.getName());
            if (project.getClientName() != null) {
                desc.append(" (").append(project.getClientName()).append(")");
            }
        }

        IncomeTransaction income = IncomeTransaction.builder()
                .advancePayment(saved)
                .amount(saved.getAmount())
                .date(saved.getPaymentDate())
                .title(title)
                .inventoryNumber(saved.getReceiptNumber())
                .description(desc.toString())
                .build();
        incomeRepo.save(income);

        return saved;
    }

    @Transactional
    public void delete(Long id) {
        AdvancePayment ap = getById(id);
        if (ap.getStatus() == AdvancePaymentStatus.SETTLED) {
            throw new RuntimeException("Cannot delete settled advance payment");
        }
        
        // Delete associated income record
        incomeRepo.deleteByAdvancePaymentId(id);
        
        advanceRepo.delete(ap);
    }
}
