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
                        + " · Method: " + order.getPaymentMethod()
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

    /**
     * Renders a complete luxury HTML tax invoice email from the immutable order & order_items snapshots.
     */
    public String buildHtmlInvoice(Order order, String orderUrl) {
        StringBuilder rows = new StringBuilder();
        for (OrderItem item : order.getItems()) {
            rows.append("<tr>")
                    .append("<td style=\"padding:10px 8px;border-bottom:1px solid #EFECE6;\">")
                    .append("<strong>").append(esc(item.getBrandName())).append(" · ").append(esc(item.getProductName())).append("</strong><br>")
                    .append("<span style=\"font-size:12px;color:#6E6B64;\">").append(esc(item.getVariantName()))
                    .append(" · SKU: ").append(esc(item.getSku())).append("</span></td>")
                    .append("<td style=\"padding:10px 8px;border-bottom:1px solid #EFECE6;text-align:right;\">").append(item.getQuantity()).append("</td>")
                    .append("<td style=\"padding:10px 8px;border-bottom:1px solid #EFECE6;text-align:right;\">₹").append(item.getUnitPrice().toPlainString()).append("</td>")
                    .append("<td style=\"padding:10px 8px;border-bottom:1px solid #EFECE6;text-align:right;font-weight:600;\">₹").append(item.getLineTotal().toPlainString()).append("</td>")
                    .append("</tr>");
        }

        String addressLine = esc(order.getShipFullName()) + "<br>"
                + esc(order.getShipLine1())
                + (order.getShipLine2() != null && !order.getShipLine2().isBlank() ? ", " + esc(order.getShipLine2()) : "")
                + "<br>" + esc(order.getShipCity()) + ", " + esc(order.getShipState()) + " - " + esc(order.getShipPostalCode())
                + " (" + esc(order.getShipCountry()) + ")<br>Phone: " + esc(order.getShipPhone());

        String taxLabel = order.isTaxInclusive() ? "Included GST" : "GST";
        String paymentSummary = order.getPaymentMethod() + " (" + order.getPaymentStatus() + ")";

        return """
                <div style="font-family:'Helvetica Neue',Arial,sans-serif;background:#FAF9F6;padding:28px 16px;color:#1A1A1A;">
                  <div style="max-width:640px;margin:0 auto;background:#FFFFFF;border:1px solid #E5E2DC;padding:32px;">
                    <div style="font-size:11px;letter-spacing:3px;color:#B08D57;text-transform:uppercase;margin-bottom:6px;">TIMECRAFT FINE HOROLOGY · TAX INVOICE</div>
                    <h1 style="font-family:Georgia,serif;font-weight:400;margin:0 0 6px;font-size:26px;">Order Confirmed · %s</h1>
                    <p style="font-size:13px;color:#6E6B64;margin:0 0 22px;">
                      Placed %s · Payment: <strong>%s</strong>
                    </p>
                    <div style="background:#FAF9F6;border:1px solid #E5E2DC;padding:14px 16px;margin-bottom:22px;font-size:13px;line-height:1.55;">
                      <div style="font-size:10px;letter-spacing:2px;text-transform:uppercase;color:#B08D57;margin-bottom:4px;">Delivery Address</div>
                      %s
                    </div>
                    <table style="width:100%%;border-collapse:collapse;font-size:13px;margin-bottom:20px;">
                      <thead>
                        <tr style="background:#F0EFEA;text-align:left;font-size:11px;text-transform:uppercase;letter-spacing:1px;color:#6E6B64;">
                          <th style="padding:9px 8px;">Timepiece</th>
                          <th style="padding:9px 8px;text-align:right;">Qty</th>
                          <th style="padding:9px 8px;text-align:right;">Unit</th>
                          <th style="padding:9px 8px;text-align:right;">Total</th>
                        </tr>
                      </thead>
                      <tbody>%s</tbody>
                    </table>
                    <table style="width:100%%;font-size:13px;border-collapse:collapse;margin-bottom:24px;">
                      <tr><td style="padding:4px 0;color:#6E6B64;">Subtotal</td><td style="padding:4px 0;text-align:right;">₹%s</td></tr>
                      <tr><td style="padding:4px 0;color:#6E6B64;">Discount%s</td><td style="padding:4px 0;text-align:right;">- ₹%s</td></tr>
                      <tr><td style="padding:4px 0;color:#6E6B64;">%s</td><td style="padding:4px 0;text-align:right;">₹%s</td></tr>
                      <tr><td style="padding:4px 0;color:#6E6B64;">Insured Shipping</td><td style="padding:4px 0;text-align:right;">₹%s</td></tr>
                      <tr style="border-top:2px solid #1A1A1A;font-size:16px;font-weight:700;">
                        <td style="padding:10px 0 0;">Grand Total (INR)</td>
                        <td style="padding:10px 0 0;text-align:right;">₹%s</td>
                      </tr>
                    </table>
                    <a href="%s" style="display:inline-block;background:#1A1A1A;color:#FAF9F6;text-decoration:none;padding:12px 24px;font-size:12px;letter-spacing:1.5px;text-transform:uppercase;">View Order &amp; Track Shipment</a>
                    <p style="font-size:12px;color:#6E6B64;margin:22px 0 0;">Your downloadable PDF invoice is attached to this email for your records.</p>
                  </div>
                </div>
                """.formatted(
                esc(order.getOrderNumber()),
                esc(DATE_FMT.format(order.getPlacedAt())),
                esc(paymentSummary),
                addressLine,
                rows,
                order.getSubtotal().toPlainString(),
                order.getCouponCode() != null ? " (" + esc(order.getCouponCode()) + ")" : "",
                order.getDiscountTotal().toPlainString(),
                esc(taxLabel),
                order.getTaxTotal().toPlainString(),
                order.getShippingTotal().toPlainString(),
                order.getGrandTotal().toPlainString(),
                esc(orderUrl)
        );
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
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
