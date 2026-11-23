package com.timecraft.order.service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.timecraft.order.entity.Order;
import com.timecraft.order.entity.OrderItem;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Generates downloadable PDF invoices from the immutable order snapshot.
 */
@Service
public class InvoicePdfService {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm 'UTC'").withZone(ZoneId.of("UTC"));

    public byte[] generateInvoice(Order order) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 42, 42, 42, 42);
        PdfWriter.getInstance(doc, out);
        doc.open();

        Font titleFont = FontFactory.getFont(FontFactory.TIMES_BOLD, 20, new Color(26, 26, 26));
        Font subFont = FontFactory.getFont(FontFactory.HELVETICA, 9, new Color(110, 108, 102));
        Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, new Color(26, 26, 26));
        Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 9, new Color(26, 26, 26));
        Font boldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, new Color(26, 26, 26));

        Paragraph brand = new Paragraph("TIMECRAFT FINE HOROLOGY", titleFont);
        brand.setSpacingAfter(4);
        doc.add(brand);

        Paragraph meta = new Paragraph(
                "Tax Invoice · Order " + order.getOrderNumber()
                        + " · Placed " + DATE_FMT.format(order.getPlacedAt())
                        + " · Status: " + order.getStatus() + " (" + order.getPaymentStatus() + ")",
                subFont);
        meta.setSpacingAfter(18);
        doc.add(meta);

        Paragraph shipHeader = new Paragraph("Billed & Shipped To", sectionFont);
        shipHeader.setSpacingAfter(4);
        doc.add(shipHeader);

        StringBuilder addr = new StringBuilder();
        addr.append(order.getShipFullName()).append(" (").append(order.getCustomerEmail()).append(")\n");
        addr.append(order.getShipLine1());
        if (order.getShipLine2() != null && !order.getShipLine2().isBlank()) {
            addr.append(", ").append(order.getShipLine2());
        }
        addr.append("\n").append(order.getShipCity()).append(", ").append(order.getShipState())
                .append(" - ").append(order.getShipPostalCode()).append(" (").append(order.getShipCountry()).append(")\n");
        addr.append("Phone: ").append(order.getShipPhone());
        Paragraph shipBody = new Paragraph(addr.toString(), bodyFont);
        shipBody.setSpacingAfter(18);
        doc.add(shipBody);

        PdfPTable table = new PdfPTable(new float[]{3.4f, 1.5f, 0.8f, 1.3f, 1.3f});
        table.setWidthPercentage(100);
        addHeader(table, "Timepiece / Variant", boldFont);
        addHeader(table, "SKU", boldFont);
        addHeader(table, "Qty", boldFont);
        addHeader(table, "Unit (" + order.getCurrency() + ")", boldFont);
        addHeader(table, "Line Total", boldFont);

        for (OrderItem item : order.getItems()) {
            addCell(table, item.getBrandName() + " " + item.getProductName() + "\n" + item.getVariantName(), bodyFont, Element.ALIGN_LEFT);
            addCell(table, item.getSku(), bodyFont, Element.ALIGN_LEFT);
            addCell(table, String.valueOf(item.getQuantity()), bodyFont, Element.ALIGN_RIGHT);
            addCell(table, item.getUnitPrice().toPlainString(), bodyFont, Element.ALIGN_RIGHT);
            addCell(table, item.getLineTotal().toPlainString(), bodyFont, Element.ALIGN_RIGHT);
        }
        table.setSpacingAfter(16);
        doc.add(table);

        PdfPTable totals = new PdfPTable(new float[]{3.5f, 1.5f});
        totals.setWidthPercentage(48);
        totals.setHorizontalAlignment(Element.ALIGN_RIGHT);
        addTotalRow(totals, "Subtotal", order.getCurrency() + " " + order.getSubtotal(), bodyFont);
        addTotalRow(totals, "Discount" + (order.getCouponCode() != null ? " (" + order.getCouponCode() + ")" : ""),
                "- " + order.getCurrency() + " " + order.getDiscountTotal(), bodyFont);
        addTotalRow(totals, order.isTaxInclusive() ? "Included GST" : "GST",
                order.getCurrency() + " " + order.getTaxTotal(), bodyFont);
        addTotalRow(totals, "Insured Shipping", order.getCurrency() + " " + order.getShippingTotal(), bodyFont);
        addTotalRow(totals, "Grand Total", order.getCurrency() + " " + order.getGrandTotal(), boldFont);
        doc.add(totals);

        Paragraph footer = new Paragraph(
                "\nThank you for choosing TimeCraft. Every timepiece is backed by our authenticity guarantee.",
                subFont);
        footer.setSpacingBefore(24);
        doc.add(footer);

        doc.close();
        return out.toByteArray();
    }

    private static void addHeader(PdfPTable table, String label, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(label, font));
        cell.setBackgroundColor(new Color(240, 239, 234));
        cell.setBorderColor(new Color(229, 226, 220));
        cell.setPadding(7);
        table.addCell(cell);
    }

    private static void addCell(PdfPTable table, String text, Font font, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(align);
        cell.setBorderColor(new Color(229, 226, 220));
        cell.setPadding(7);
        table.addCell(cell);
    }

    private static void addTotalRow(PdfPTable table, String label, String value, Font font) {
        addCell(table, label, font, Element.ALIGN_LEFT);
        addCell(table, value, font, Element.ALIGN_RIGHT);
    }
}
