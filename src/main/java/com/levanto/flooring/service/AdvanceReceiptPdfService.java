package com.levanto.flooring.service;

import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;
import com.levanto.flooring.config.CompanyProperties;
import com.levanto.flooring.entity.AdvancePayment;
import com.levanto.flooring.entity.Project;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdvanceReceiptPdfService {

    private final CompanyProperties co;

    private static final BaseColor NAVY = new BaseColor(15, 35, 56);
    private static final BaseColor NAVY2 = new BaseColor(26, 58, 92);
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

    public byte[] generate(AdvancePayment ap) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document doc = new Document(PageSize.A4, 36, 36, 36, 54);
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            writer.setPageEvent(new PdfService.PageFooter(co));
            doc.open();

            header(doc, ap);
            receiptDetails(doc, ap);
            doc.add(Chunk.NEWLINE);
            paymentInfo(doc, ap);
            doc.add(Chunk.NEWLINE);
            footer(doc);

            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Advance Receipt PDF generation failed: {}", e.getMessage(), e);
            throw new RuntimeException("Advance Receipt PDF generation failed", e);
        }
    }

    private void header(Document doc, AdvancePayment ap) throws DocumentException {
        PdfPTable t = new PdfPTable(2);
        t.setWidthPercentage(100);
        t.setWidths(new float[] { 3.8f, 1.6f });
        t.setSpacingAfter(20);

        PdfPCell left = cell(Rectangle.NO_BORDER);
        left.setBackgroundColor(NAVY);
        left.setPadding(15);
        left.addElement(p(co.getName(), f(22, Font.BOLD, BaseColor.WHITE)));
        left.addElement(p(co.getTagline(), f(9, Font.NORMAL, BaseColor.WHITE)));
        left.addElement(p(co.getAddress(), f(8, Font.NORMAL, new BaseColor(170, 195, 225))));
        left.addElement(p(co.getPhone() + "  |  " + co.getEmail(), f(8, Font.NORMAL, new BaseColor(170, 195, 225))));
        left.addElement(p("GSTIN: " + co.getGstNumber(), f(8, Font.NORMAL, new BaseColor(170, 195, 225))));
        t.addCell(left);

        PdfPCell right = cell(Rectangle.NO_BORDER);
        right.setBackgroundColor(GOLD);
        right.setVerticalAlignment(Element.ALIGN_MIDDLE);
        right.setPadding(10);
        Paragraph title = p("ADVANCE RECEIPT", f(12, Font.BOLD, BaseColor.WHITE));
        title.setAlignment(Element.ALIGN_CENTER);
        right.addElement(title);
        Paragraph num = p(ap.getReceiptNumber(), f(10, Font.NORMAL, BaseColor.WHITE));
        num.setAlignment(Element.ALIGN_CENTER);
        right.addElement(num);
        Paragraph date = p(ap.getPaymentDate() != null ? ap.getPaymentDate().format(DateTimeFormatter.ofPattern("dd-MM-yyyy")) : "", f(9, Font.NORMAL, BaseColor.WHITE));
        date.setAlignment(Element.ALIGN_CENTER);
        right.addElement(date);
        t.addCell(right);

        doc.add(t);
    }

    private void receiptDetails(Document doc, AdvancePayment ap) throws DocumentException {
        PdfPTable t = new PdfPTable(1);
        t.setWidthPercentage(100);
        
        PdfPCell c = cell(Rectangle.BOX);
        c.setBorderColor(BORDER);
        c.setPadding(15);
        c.setBackgroundColor(LIGHT);
        
        Project p = ap.getProject();
        String recipientName = ap.getCollectedFrom();
        if (recipientName == null || recipientName.isBlank()) {
            recipientName = (p != null && p.getClientName() != null) ? p.getClientName() : "Client";
        }
        
        c.addElement(p("RECEIVED FROM", f(8, Font.BOLD, GOLD)));
        c.addElement(p(recipientName, f(12, Font.BOLD, DARK)));
        
        if (p != null) {
            c.addElement(p("Project: " + p.getName(), f(10, Font.NORMAL, DARK)));
            if (p.getLocation() != null)
                c.addElement(p("Location: " + p.getLocation(), f(9, Font.NORMAL, MUTED)));
        }
            
        t.addCell(c);
        doc.add(t);
    }

    private void paymentInfo(Document doc, AdvancePayment ap) throws DocumentException {
        PdfPTable t = new PdfPTable(2);
        t.setWidthPercentage(100);
        t.setWidths(new float[]{1.5f, 1f});
        
        PdfPCell descCell = cell(Rectangle.BOX);
        descCell.setBorderColor(BORDER);
        descCell.setPadding(15);
        descCell.addElement(p("PAYMENT FOR", f(8, Font.BOLD, GOLD)));
        descCell.addElement(p(ap.getDescription() != null ? ap.getDescription() : "Advance payment towards project works.", f(10, Font.NORMAL, DARK)));
        t.addCell(descCell);
        
        PdfPCell amtCell = cell(Rectangle.BOX);
        amtCell.setBorderColor(BORDER);
        amtCell.setBackgroundColor(NAVY);
        amtCell.setPadding(15);
        amtCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        Paragraph lbl = p("AMOUNT RECEIVED", f(8, Font.BOLD, BaseColor.WHITE));
        lbl.setAlignment(Element.ALIGN_CENTER);
        amtCell.addElement(lbl);
        Paragraph amt = p("₹ " + fmt(ap.getAmount()), f(18, Font.BOLD, GOLD));
        amt.setAlignment(Element.ALIGN_CENTER);
        amtCell.addElement(amt);
        t.addCell(amtCell);
        
        doc.add(t);
        
        Paragraph words = p("Amount in words: " + inWords(ap.getAmount()) + " Only", f(9, Font.ITALIC, MUTED));
        words.setSpacingBefore(10);
        doc.add(words);
    }

    private void footer(Document doc) throws DocumentException {
        PdfPTable t = new PdfPTable(2);
        t.setWidthPercentage(100);
        t.setSpacingBefore(40);
        
        PdfPCell client = cell(Rectangle.NO_BORDER);
        client.addElement(p("________________________", f(10, Font.NORMAL, DARK)));
        client.addElement(p("Customer Signature", f(8, Font.NORMAL, MUTED)));
        t.addCell(client);
        
        PdfPCell company = cell(Rectangle.NO_BORDER);
        company.setHorizontalAlignment(Element.ALIGN_RIGHT);
        company.addElement(p("For " + co.getName(), f(10, Font.BOLD, DARK)));
        company.addElement(new Paragraph("\n\n"));
        company.addElement(p("Authorized Signatory", f(8, Font.NORMAL, MUTED)));
        t.addCell(company);
        
        doc.add(t);
    }

    private PdfPCell cell(int border) {
        PdfPCell c = new PdfPCell();
        c.setBorder(border);
        return c;
    }

    private Paragraph p(String txt, Font f) {
        return new Paragraph(nv(txt), f);
    }

    private String nv(String s) {
        return s == null ? "" : s;
    }

    private String fmt(BigDecimal v) {
        return v == null ? "0.00" : v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String inWords(BigDecimal amount) {
        if (amount == null) return "Zero Rupees";
        long r = amount.longValue();
        long p = amount.remainder(BigDecimal.ONE).multiply(BigDecimal.valueOf(100)).longValue();
        String res = words(r) + " Rupees";
        if (p > 0) res += " and " + words(p) + " Paise";
        return res;
    }

    private String words(long n) {
        if (n == 0) return "Zero";
        String[] o = {"", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten",
                "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen"};
        String[] t = {"", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"};
        if (n < 20) return o[(int) n];
        if (n < 100) return t[(int) (n / 10)] + (n % 10 != 0 ? " " + o[(int) (n % 10)] : "");
        if (n < 1000) return o[(int) (n / 100)] + " Hundred" + (n % 100 != 0 ? " " + words(n % 100) : "");
        if (n < 100000) return words(n / 1000) + " Thousand" + (n % 1000 != 0 ? " " + words(n % 1000) : "");
        if (n < 10000000) return words(n / 100000) + " Lakh" + (n % 100000 != 0 ? " " + words(n % 100000) : "");
        return words(n / 10000000) + " Crore" + (n % 10000000 != 0 ? " " + words(n % 10000000) : "");
    }
}
