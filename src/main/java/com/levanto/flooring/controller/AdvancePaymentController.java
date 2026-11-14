package com.levanto.flooring.controller;

import com.levanto.flooring.dto.ApiResponse;
import com.levanto.flooring.entity.AdvancePayment;
import com.levanto.flooring.service.AdvancePaymentService;
import com.levanto.flooring.service.AdvanceReceiptPdfService;
import com.levanto.flooring.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class AdvancePaymentController {

    private final AdvancePaymentService advanceService;
    private final ProjectService projectService;
    private final AdvanceReceiptPdfService pdfService;

    @GetMapping("/admin/projects/advances/all")
    public String listAll(Model m) {
        m.addAttribute("advances", advanceService.getAll());
        m.addAttribute("projects", projectService.getActive());
        m.addAttribute("newAdvance", new AdvancePayment());
        return "project/advances";
    }

    @PostMapping("/admin/projects/{projectId}/advances/save")
    public String save(@PathVariable String projectId, @ModelAttribute AdvancePayment advance, RedirectAttributes ra) {
        // Resolve project: check form field first, then path variable
        Long finalProjectId = null;
        if (advance.getProject() != null && advance.getProject().getId() != null && advance.getProject().getId() != 0) {
            finalProjectId = advance.getProject().getId();
        } else if (!"0".equals(projectId)) {
            finalProjectId = Long.parseLong(projectId);
        }

        if (finalProjectId != null) {
            advance.setProject(projectService.getById(finalProjectId));
        } else {
            advance.setProject(null);
        }

        advanceService.save(advance);
        ra.addFlashAttribute("success", "Advance payment recorded.");
        if (!"0".equals(projectId)) {
            return "redirect:/admin/projects/" + projectId + "?tab=advances";
        }
        return "redirect:/admin/projects/advances/all";
    }

    @GetMapping("/admin/projects/{projectId}/advances/{id}/pdf")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable Long id) {
        AdvancePayment ap = advanceService.getById(id);
        byte[] pdf = pdfService.generate(ap);
        String filename = "AdvanceReceipt_" + ap.getReceiptNumber() + ".pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(pdf);
    }

    @DeleteMapping("/admin/projects/{projectId}/advances/{id}")
    @ResponseBody
    public ResponseEntity<ApiResponse<?>> delete(@PathVariable Long id) {
        advanceService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok("Advance payment deleted."));
    }

    @GetMapping("/admin/projects/{projectId}/advances/{id}/delete")
    public String delete(@PathVariable String projectId, @PathVariable Long id, RedirectAttributes ra) {
        try {
            advanceService.delete(id);
            ra.addFlashAttribute("success", "Advance payment deleted.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Error deleting advance: " + e.getMessage());
        }
        
        if (!"0".equals(projectId)) {
            return "redirect:/admin/projects/" + projectId + "?tab=advances";
        }
        return "redirect:/admin/projects/advances/all";
    }

    @GetMapping("/admin/projects/{projectId}/advances/api/unsettled")
    @ResponseBody
    public ResponseEntity<?> getUnsettledAdvances(@PathVariable Long projectId) {
        return ResponseEntity.ok(advanceService.getUnsettledByProject(projectId));
    }
}
