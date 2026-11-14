package com.levanto.flooring.controller;

import com.levanto.flooring.dto.ApiResponse;
import com.levanto.flooring.entity.*;
import com.levanto.flooring.enums.*;
import com.levanto.flooring.repository.AppUserRepository;
import com.levanto.flooring.service.*;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService       projectService;
    private final AppUserRepository    userRepo;
    private final com.levanto.flooring.repository.EmployeeRepository employeeRepo;
    private final LabourWagePdfService labourPdfService;
    private final AdvancePaymentService advanceService;
    private final com.levanto.flooring.repository.TransactionRepository txRepo;
    private final com.levanto.flooring.repository.IncomeTransactionRepository incomeRepo;
    private final PaymentService paymentService;
    private final ProjectIncomePdfService projectIncomePdfService;

    @GetMapping("/{id}/income-report/pdf")
    public ResponseEntity<byte[]> incomeReportPdf(@PathVariable Long id) {
        Project p = projectService.getById(id);
        List<Transaction> bills = txRepo.findByProject_IdOrderByCreatedAtDesc(id);
        List<AdvancePayment> allAdvances = advanceService.getByProject(id);
        
        java.util.Map<Long, AdvancePayment> settledAdvancesMap = allAdvances.stream()
                .filter(a -> a.getStatus() == AdvancePaymentStatus.SETTLED && a.getSettledBill() != null)
                .collect(java.util.stream.Collectors.toMap(a -> a.getSettledBill().getId(), a -> a, (a1, a2) -> a1));

        List<AdvancePayment> unsettledAdvances = allAdvances.stream()
                .filter(a -> a.getStatus() == AdvancePaymentStatus.RECEIVED)
                .toList();

        List<IncomeTransaction> allIncomes = incomeRepo.findAllByProjectId(id);
        java.util.Map<Long, BigDecimal> billTotalsMap = new java.util.HashMap<>();
        for (IncomeTransaction inc : allIncomes) {
            Long txId = (inc.getTransaction() != null) ? inc.getTransaction().getId() : 
                        (inc.getAdvancePayment() != null && inc.getAdvancePayment().getSettledBill() != null) ? 
                        inc.getAdvancePayment().getSettledBill().getId() : null;
            if (txId != null) billTotalsMap.put(txId, billTotalsMap.getOrDefault(txId, BigDecimal.ZERO).add(inc.getAmount()));
        }

        byte[] pdf = projectIncomePdfService.generate(p, bills, unsettledAdvances, settledAdvancesMap, billTotalsMap);
        
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_PDF);
        headers.set(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, 
                    "attachment; filename=\"IncomeReport_" + p.getName().replaceAll("\\s+", "_") + ".pdf\"");
        
        return ResponseEntity.ok().headers(headers).body(pdf);
    }

    // ── Project list ─────────────────────────────────────────────────────────
    @GetMapping
    public String list(@RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "20") int size,
                       @RequestParam(required = false) String search,
                       Model m) {
        m.addAttribute("projectsPage", projectService.getAll(page, size, search));
        m.addAttribute("search",       search);
        m.addAttribute("currentPage",  page);
        m.addAttribute("ProjectStatus", ProjectStatus.values());
        return "project/list";
    }

    // ── Project detail + analytics ────────────────────────────────────────────
    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model m) {
        Project p = projectService.getById(id);
        m.addAttribute("project",        p);
        m.addAttribute("jobCards",        projectService.getJobCards(id));
        m.addAttribute("projectLabours",  projectService.getProjectLabours(id));
        m.addAttribute("totalWork",       projectService.totalWorkValue(id));
        m.addAttribute("totalApproved",   projectService.totalApprovedExpenses(id));
        m.addAttribute("totalPending",    projectService.totalPendingExpenses(id));
        m.addAttribute("netProfit",       projectService.netProfitability(id));
        m.addAttribute("JobCardStatus",   JobCardStatus.values());
        m.addAttribute("ProjectStatus",   ProjectStatus.values());
        m.addAttribute("advances",        advanceService.getByProject(id));
        m.addAttribute("newAdvance",      new AdvancePayment());
        m.addAttribute("bills",           txRepo.findByProject_IdOrderByCreatedAtDesc(id));
        return "project/detail";
    }

    @GetMapping("/{id}/income-report")
    public String incomeReport(@PathVariable Long id, Model m) {
        Project p = projectService.getById(id);
        
        // Fetch all bills for the project
        List<Transaction> bills = txRepo.findByProject_IdOrderByCreatedAtDesc(id);
        
        // Fetch all advances for the project
        List<AdvancePayment> allAdvances = advanceService.getByProject(id);
        
        // Map settled advances for easy access in template
        java.util.Map<Long, AdvancePayment> settledAdvancesMap = allAdvances.stream()
                .filter(a -> a.getStatus() == AdvancePaymentStatus.SETTLED && a.getSettledBill() != null)
                .collect(java.util.stream.Collectors.toMap(a -> a.getSettledBill().getId(), a -> a, (a1, a2) -> a1));

        // Filter unsettled advances
        List<AdvancePayment> unsettledAdvances = allAdvances.stream()
                .filter(a -> a.getStatus() == AdvancePaymentStatus.RECEIVED)
                .toList();

        // Optimization: Fetch all income transactions for this project at once
        List<IncomeTransaction> allIncomes = incomeRepo.findAllByProjectId(id);
        
        // Pre-calculate total received per bill to avoid N+1 queries in template
        java.util.Map<Long, BigDecimal> billTotalsMap = new java.util.HashMap<>();
        for (IncomeTransaction inc : allIncomes) {
            Long txId = null;
            if (inc.getTransaction() != null) txId = inc.getTransaction().getId();
            else if (inc.getAdvancePayment() != null && inc.getAdvancePayment().getSettledBill() != null) {
                txId = inc.getAdvancePayment().getSettledBill().getId();
            }
            
            if (txId != null) {
                billTotalsMap.put(txId, billTotalsMap.getOrDefault(txId, BigDecimal.ZERO).add(inc.getAmount()));
            }
        }

        m.addAttribute("project", p);
        m.addAttribute("bills", bills);
        m.addAttribute("settledAdvancesMap", settledAdvancesMap);
        m.addAttribute("unsettledAdvances", unsettledAdvances);
        m.addAttribute("billTotalsMap", billTotalsMap);
        
        return "project/income-report";
    }

    // ── New / Edit form ───────────────────────────────────────────────────────
    @GetMapping("/new")
    public String newForm(Model m) {
        m.addAttribute("project",       new Project());
        m.addAttribute("ProjectStatus", ProjectStatus.values());
        return "project/form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model m) {
        m.addAttribute("project",       projectService.getById(id));
        m.addAttribute("ProjectStatus", ProjectStatus.values());
        return "project/form";
    }

    @PostMapping("/save")
    public String save(@ModelAttribute Project project, RedirectAttributes ra) {
        Project saved = projectService.saveProject(project);
        ra.addFlashAttribute("success", "Project saved.");
        return "redirect:/admin/projects/" + saved.getId();
    }

    @DeleteMapping("/{id}") @ResponseBody
    public ResponseEntity<ApiResponse<?>> delete(@PathVariable Long id) {
        projectService.deleteProject(id);
        return ResponseEntity.ok(ApiResponse.ok("Project deleted."));
    }

    // ── Job Card: new form ────────────────────────────────────────────────────
    @GetMapping("/{projectId}/jobcards/new")
    public String newJobCard(@PathVariable Long projectId, Model m) {
        JobCard jc = new JobCard();
        jc.setProject(projectService.getById(projectId));
        m.addAttribute("jobCard",       jc);
        m.addAttribute("project",       projectService.getById(projectId));
        m.addAttribute("engineers",     employeeRepo.findAll());
        m.addAttribute("JobCardStatus", JobCardStatus.values());
        return "jobcard/form";
    }

    @GetMapping("/jobcards/{id}/edit")
    public String editJobCard(@PathVariable Long id, Model m) {
        JobCard jc = projectService.getJobCard(id);
        m.addAttribute("jobCard",       jc);
        m.addAttribute("project",       jc.getProject());
        m.addAttribute("engineers",     employeeRepo.findAll());
        m.addAttribute("JobCardStatus", JobCardStatus.values());
        return "jobcard/form";
    }

    @PostMapping("/jobcards/save")
    public String saveJobCard(@ModelAttribute JobCard jobCard,
                              @RequestParam Long projectId,
                              @RequestParam(required = false) Long engineerId,
                              RedirectAttributes ra) {
        jobCard.setProject(projectService.getById(projectId));
        if (engineerId != null) {
            jobCard.setAssignedEngineer(
                employeeRepo.findById(engineerId).orElse(null));
        }
        projectService.saveJobCard(jobCard);
        ra.addFlashAttribute("success", "Job card saved.");
        return "redirect:/admin/projects/" + projectId;
    }

    @DeleteMapping("/jobcards/{id}") @ResponseBody
    public ResponseEntity<ApiResponse<?>> deleteJobCard(@PathVariable Long id) {
        projectService.deleteJobCard(id);
        return ResponseEntity.ok(ApiResponse.ok("Job card deleted."));
    }

    // ── Project Labours ───────────────────────────────────────────────────────
    @PostMapping("/{projectId}/labours/save")
    public String saveProjectLabour(@PathVariable Long projectId,
                                    @ModelAttribute ProjectLabour labour,
                                    RedirectAttributes ra) {
        labour.setProject(projectService.getById(projectId));
        projectService.saveProjectLabour(labour);
        ra.addFlashAttribute("success", "Project Labour added.");
        return "redirect:/admin/projects/" + projectId + "?tab=labours";
    }

    @DeleteMapping("/labours/{id}") @ResponseBody
    public ResponseEntity<ApiResponse<?>> deleteProjectLabour(@PathVariable Long id) {
        projectService.deleteProjectLabour(id);
        return ResponseEntity.ok(ApiResponse.ok("Project Labour deleted."));
    }

    @GetMapping("/{projectId}/labours/{labourId}")
    public String viewProjectLabour(@PathVariable Long projectId, @PathVariable Long labourId, Model m) {
        m.addAttribute("project", projectService.getById(projectId));
        m.addAttribute("labour", projectService.getProjectLabour(labourId));
        m.addAttribute("logs", projectService.getDailyLabourLogs(labourId));
        return "project/labour_detail";
    }

    @GetMapping("/{projectId}/labours/wages")
    public String projectLabourWages(@PathVariable Long projectId,
                                     @RequestParam(required = false) java.time.LocalDate from,
                                     @RequestParam(required = false) java.time.LocalDate to,
                                     @RequestParam(required = false) String labourName,
                                     Model m) {
        Project p = projectService.getById(projectId);
        List<DailyLabourLog> logs = projectService.getApprovedFilteredLabourWages(projectId, from, to, labourName);
        m.addAttribute("project", p);
        m.addAttribute("logs", logs);
        m.addAttribute("from", from);
        m.addAttribute("to", to);
        m.addAttribute("labourName", labourName);
        return "project/labour_wages";
    }

    @GetMapping("/{projectId}/labours/wages/export/pdf")
    public ResponseEntity<byte[]> exportLabourWagesPdf(@PathVariable Long projectId,
                                                       @RequestParam(required = false) java.time.LocalDate from,
                                                       @RequestParam(required = false) java.time.LocalDate to,
                                                       @RequestParam(required = false) String labourName) {
        Project p = projectService.getById(projectId);
        List<DailyLabourLog> logs = projectService.getApprovedFilteredLabourWages(projectId, from, to, labourName);
        byte[] pdf = labourPdfService.generate(logs, p, from, to);
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_PDF);
        org.springframework.http.ContentDisposition contentDisposition = org.springframework.http.ContentDisposition.attachment()
                .filename("LabourWageReport_" + p.getName() + ".pdf", java.nio.charset.StandardCharsets.UTF_8)
                .build();
        headers.set(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString());
        return ResponseEntity.ok().headers(headers).body(pdf);
    }
}
