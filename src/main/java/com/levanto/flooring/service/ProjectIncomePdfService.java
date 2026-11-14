package com.levanto.flooring.service;

import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;
import com.levanto.flooring.config.CompanyProperties;
import com.levanto.flooring.entity.AdvancePayment;
import com.levanto.flooring.entity.Project;
import com.levanto.flooring.entity.Transaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectIncomePdfService {

    private final CompanyProperties co;

    private static final BaseColor NAVY = new BaseColor(15, 35, 56);
    private static final BaseColor GOLD = new BaseColor(200, 151, 42);
    private static final BaseColor LIGHT = new BaseColor(243, 246, 250);
    private static final BaseColor BORDER = new BaseColor(210, 218, 228);
    private static final BaseColor DARK = new BaseColor(25, 25, 35);
    private static final BaseColor MUTED = new BaseColor(100, 118, 140);

    private Font f(int size, int style, BaseColor c) {
        try {
            BaseFont bf = (style == Font.BOLD)
                    ? BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.WINANSI, false)
                    : BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, false);
            return new Font(bf, size, style, c);
        } catch (Exception e) {
            return new Font(Font.FontFamily.HELVETICA, size, style, c);
        }
    }

    public byte[] generate(Project project, List<Transaction> bills, List<AdvancePayment> unsettledAdvances, 
                          Map<Long, AdvancePayment> settledAdvancesMap, Map<Long, BigDecimal> billTotalsMap) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document doc = new Document(PageSize.A4, 36, 36, 36, 54);
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            writer.setPageEvent(new PdfService.PageFooter(co));
            doc.open();

            header(doc, project);
            projectSummary(doc, project);
            doc.add(Chunk.NEWLINE);
            
            incomeTable(doc, bills, unsettledAdvances, settledAdvancesMap, billTotalsMap);
            
            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Project Income Report PDF generation failed", e);
            throw new RuntimeException("Project Income Report PDF generation failed", e);
        }
    }

    private void header(Document doc, Project project) throws DocumentException {
        PdfPTable t = new PdfPTable(2);
        t.setWidthPercentage(100);
        t.setWidths(new float[] { 3.8f, 1.8f });
        t.setSpacingAfter(20);

        PdfPCell left = cell(Rectangle.NO_BORDER);
        left.setBackgroundColor(NAVY);
        left.setPadding(15);
        left.addElement(p(co.getName(), f(20, Font.BOLD, BaseColor.WHITE)));
        left.addElement(p(co.getTagline(), f(9, Font.NORMAL, BaseColor.WHITE)));
        left.addElement(p(co.getAddress(), f(8, Font.NORMAL, new BaseColor(170, 195, 225))));
        left.addElement(p(co.getPhone() + "  |  " + co.getEmail(), f(8, Font.NORMAL, new BaseColor(170, 195, 225))));
        t.addCell(left);

        PdfPCell right = cell(Rectangle.NO_BORDER);
        right.setBackgroundColor(GOLD);
        right.setVerticalAlignment(Element.ALIGN_MIDDLE);
        right.setPadding(10);
        Paragraph title = p("INCOME REPORT", f(14, Font.BOLD, BaseColor.WHITE));
        title.setAlignment(Element.ALIGN_CENTER);
        right.addElement(title);
        Paragraph pName = p(project.getName(), f(10, Font.NORMAL, BaseColor.WHITE));
        pName.setAlignment(Element.ALIGN_CENTER);
        right.addElement(pName);
        t.addCell(right);

        doc.add(t);
    }

    private void projectSummary(Document doc, Project project) throws DocumentException {
        PdfPTable t = new PdfPTable(3);
        t.setWidthPercentage(100);
        t.setSpacingAfter(10);
        
        summaryCell(t, "CLIENT", project.getClientName() != null ? project.getClientName() : "—");
        summaryCell(t, "LOCATION", project.getLocation() != null ? project.getLocation() : "—");
        summaryCell(t, "CONTRACT VALUE", "₹ " + fmt(project.getTotalContractValue()));
        
        doc.add(t);
    }

    private void summaryCell(PdfPTable t, String label, String value) {
        PdfPCell c = cell(Rectangle.NO_BORDER);
        c.setPadding(5);
        c.addElement(p(label, f(7, Font.BOLD, GOLD)));
        c.addElement(p(value, f(10, Font.BOLD, DARK)));
        t.addCell(c);
    }

    private void incomeTable(Document doc, List<Transaction> bills, List<AdvancePayment> unsettledAdvances, 
                             Map<Long, AdvancePayment> settledAdvancesMap, Map<Long, BigDecimal> billTotalsMap) throws DocumentException {
        
        String[] hdrs = {"Date", "Description", "Bill Amount", "Received", "Pending"};
        float[] wts = {1.2f, 2.5f, 1.2f, 1.2f, 1.2f};

        PdfPTable t = new PdfPTable(hdrs.length);
        t.setWidthPercentage(100);
        t.setWidths(wts);

        for (String h : hdrs) {
            PdfPCell c = new PdfPCell(new Phrase(h, f(9, Font.BOLD, BaseColor.WHITE)));
            c.setBackgroundColor(NAVY);
            c.setPadding(8);
            c.setBorderColor(NAVY);
            if (h.contains("Amount") || h.equals("Received") || h.equals("Pending")) {
                c.setHorizontalAlignment(Element.ALIGN_RIGHT);
            }
            t.addCell(c);
        }

        BigDecimal totalBill = BigDecimal.ZERO;
        BigDecimal totalRecv = BigDecimal.ZERO;
        BigDecimal totalPend = BigDecimal.ZERO;

        // Bills
        for (Transaction b : bills) {
            BigDecimal recv = billTotalsMap.getOrDefault(b.getId(), BigDecimal.ZERO);
            BigDecimal pend = b.getGrandTotal().subtract(recv);
            
            totalBill = totalBill.add(b.getGrandTotal());
            totalRecv = totalRecv.add(recv);
            totalPend = totalPend.add(pend);

            String desc = (b.getInvoiceNumber() != null ? b.getInvoiceNumber() : b.getQuotationNumber());
            if (settledAdvancesMap.containsKey(b.getId())) {
                desc += "\n(Advance: " + settledAdvancesMap.get(b.getId()).getReceiptNumber() + ")";
            }

            addRow(t, b.getCreatedAt().format(DateTimeFormatter.ofPattern("dd MMM yyyy")), desc, fmt(b.getGrandTotal()), fmt(recv), fmt(pend));
        }

        // Unsettled Advances
        for (AdvancePayment ap : unsettledAdvances) {
            totalRecv = totalRecv.add(ap.getAmount());
            addRow(t, ap.getPaymentDate().format(DateTimeFormatter.ofPattern("dd MMM yyyy")), ap.getReceiptNumber() + "\n(Unsettled Advance)", "—", fmt(ap.getAmount()), "—");
        }

        // Footer
        PdfPCell fl = new PdfPCell(new Phrase("TOTAL", f(10, Font.BOLD, DARK)));
        fl.setColspan(2);
        fl.setPadding(8);
        fl.setBackgroundColor(LIGHT);
        t.addCell(fl);

        t.addCell(amtCell(fmt(totalBill), true));
        t.addCell(amtCell(fmt(totalRecv), true));
        t.addCell(amtCell(fmt(totalPend), true));

        doc.add(t);
    }

    private void addRow(PdfPTable t, String date, String desc, String bill, String recv, String pend) {
        t.addCell(txtCell(date, false));
        t.addCell(txtCell(desc, false));
        t.addCell(amtCell(bill, false));
        t.addCell(amtCell(recv, false));
        t.addCell(amtCell(pend, false));
    }

    private PdfPCell txtCell(String txt, boolean bold) {
        PdfPCell c = new PdfPCell(new Phrase(txt, f(9, bold ? Font.BOLD : Font.NORMAL, DARK)));
        c.setPadding(6);
        c.setBorderColor(BORDER);
        return c;
    }

    private PdfPCell amtCell(String amt, boolean bold) {
        PdfPCell c = new PdfPCell(new Phrase(amt, f(9, bold ? Font.BOLD : Font.NORMAL, DARK)));
        c.setPadding(6);
        c.setBorderColor(BORDER);
        c.setHorizontalAlignment(Element.ALIGN_RIGHT);
        if (bold) c.setBackgroundColor(LIGHT);
        return c;
    }

    private PdfPCell cell(int border) {
        PdfPCell c = new PdfPCell();
        c.setBorder(border);
        return c;
    }

    private Paragraph p(String txt, Font f) {
        return new Paragraph(txt != null ? txt : "", f);
    }

    private String fmt(BigDecimal v) {
        return v == null ? "0.00" : v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
