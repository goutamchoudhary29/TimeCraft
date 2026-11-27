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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Generates downloadable PDF invoices and email-client-safe luxury HTML tax
 * invoice emails
 * from the immutable order snapshot.
 */
@Service
public class InvoicePdfService {

  private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm 'UTC'")
      .withZone(ZoneId.of("UTC"));
  private static final Locale IN_LOCALE = Locale.forLanguageTag("en-IN");

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

    PdfPTable table = new PdfPTable(new float[] { 3.4f, 1.5f, 0.8f, 1.3f, 1.3f });
    table.setWidthPercentage(100);
    addHeader(table, "Timepiece / Variant", boldFont);
    addHeader(table, "SKU", boldFont);
    addHeader(table, "Qty", boldFont);
    addHeader(table, "Unit (" + order.getCurrency() + ")", boldFont);
    addHeader(table, "Line Total", boldFont);

    for (OrderItem item : order.getItems()) {
      addCell(table, item.getBrandName() + " " + item.getProductName() + "\n" + item.getVariantName(), bodyFont,
          Element.ALIGN_LEFT);
      addCell(table, item.getSku(), bodyFont, Element.ALIGN_LEFT);
      addCell(table, String.valueOf(item.getQuantity()), bodyFont, Element.ALIGN_RIGHT);
      addCell(table, formatBigDecimalAmount(item.getUnitPrice()), bodyFont, Element.ALIGN_RIGHT);
      addCell(table, formatBigDecimalAmount(item.getLineTotal()), bodyFont, Element.ALIGN_RIGHT);
    }
    table.setSpacingAfter(16);
    doc.add(table);

    PdfPTable totals = new PdfPTable(new float[] { 3.5f, 1.5f });
    totals.setWidthPercentage(48);
    totals.setHorizontalAlignment(Element.ALIGN_RIGHT);
    addTotalRow(totals, "Subtotal", order.getCurrency() + " " + formatBigDecimalAmount(order.getSubtotal()), bodyFont);
    addTotalRow(totals, "Discount" + (order.getCouponCode() != null ? " (" + order.getCouponCode() + ")" : ""),
        "- " + order.getCurrency() + " " + formatBigDecimalAmount(order.getDiscountTotal()), bodyFont);
    addTotalRow(totals, order.isTaxInclusive() ? "Included GST" : "GST",
        order.getCurrency() + " " + formatBigDecimalAmount(order.getTaxTotal()), bodyFont);
    addTotalRow(totals, "Insured Shipping",
        order.getCurrency() + " " + formatBigDecimalAmount(order.getShippingTotal()), bodyFont);
    addTotalRow(totals, "Grand Total", order.getCurrency() + " " + formatBigDecimalAmount(order.getGrandTotal()),
        boldFont);
    doc.add(totals);

    Paragraph footer = new Paragraph(
        "\nThank you for choosing TimeCraft. Every timepiece is serial-recorded and backed by our authenticity guarantee.\n"
            + "Warm regards,\nGoutam Gurjar · Founder, TimeCraft",
        subFont);
    footer.setSpacingBefore(24);
    doc.add(footer);

    doc.close();
    return out.toByteArray();
  }

  /**
   * Renders an email-client-safe luxury HTML tax invoice & order confirmation
   * email
   * featuring the inline TimeCraft confirmation animation GIF and product images.
   */
  public String buildHtmlInvoice(Order order, String orderUrl) {
    return buildHtmlInvoice(order, orderUrl, null);
  }

  public String buildHtmlInvoice(Order order, String orderUrl, String frontendBaseUrl) {
    StringBuilder rows = new StringBuilder();
    for (OrderItem item : order.getItems()) {
      String resolvedImage = resolveImageUrl(item.getImageUrl(), frontendBaseUrl);
      String imageCellHtml = resolvedImage != null
          ? "<img src=\"" + esc(resolvedImage) + "\" alt=\"" + esc(item.getProductName())
              + "\" width=\"58\" height=\"70\" style=\"display:block;width:58px;height:70px;object-fit:cover;border:1px solid #E5E2DC;border-radius:2px;\">"
          : "<div style=\"width:58px;height:70px;background:#F4F1EA;border:1px solid #E5E2DC;text-align:center;line-height:70px;font-family:Georgia,serif;font-size:18px;color:#B08D57;\">TC</div>";

      rows.append("<tr>")
          .append(
              "<td style=\"padding:14px 10px 14px 0;border-bottom:1px solid #EFECE6;vertical-align:top;width:68px;\">")
          .append(imageCellHtml)
          .append("</td>")
          .append("<td style=\"padding:14px 10px 14px 0;border-bottom:1px solid #EFECE6;vertical-align:top;\">")
          .append(
              "<div style=\"font-size:10px;line-height:14px;letter-spacing:1.6px;text-transform:uppercase;color:#B08D57;font-weight:bold;margin:0 0 3px 0;\">")
          .append(esc(item.getBrandName())).append("</div>")
          .append(
              "<div style=\"font-family:Georgia,'Times New Roman',serif;font-size:16px;line-height:20px;color:#141311;margin:0 0 4px 0;\">")
          .append(esc(item.getProductName())).append("</div>")
          .append("<div style=\"font-size:12px;line-height:17px;color:#6E6B64;\">")
          .append(esc(item.getVariantName())).append("<br>SKU: ").append(esc(item.getSku()))
          .append(" &middot; Qty: <strong style=\"color:#141311;\">").append(item.getQuantity())
          .append("</strong></div>")
          .append("</td>")
          .append(
              "<td style=\"padding:14px 0;border-bottom:1px solid #EFECE6;vertical-align:top;text-align:right;white-space:nowrap;\">")
          .append("<div style=\"font-size:14px;line-height:20px;font-weight:bold;color:#141311;\">")
          .append(formatInr(item.getLineTotal())).append("</div>")
          .append("<div style=\"font-size:11px;line-height:16px;color:#6E6B64;\">")
          .append(formatInr(item.getUnitPrice())).append(" each</div>")
          .append("</td>")
          .append("</tr>");
    }

    String addressLine = "<strong style=\"color:#141311;\">" + esc(order.getShipFullName()) + "</strong><br>"
        + esc(order.getShipLine1())
        + (order.getShipLine2() != null && !order.getShipLine2().isBlank() ? ", " + esc(order.getShipLine2()) : "")
        + "<br>" + esc(order.getShipCity()) + ", " + esc(order.getShipState()) + " - " + esc(order.getShipPostalCode())
        + " (" + esc(order.getShipCountry()) + ")<br>Phone: " + esc(order.getShipPhone());

    String taxLabel = order.isTaxInclusive() ? "Included GST" : "GST";
    String shippingDisplay = order.getShippingTotal() != null
        && BigDecimal.ZERO.compareTo(order.getShippingTotal()) == 0
            ? "Complimentary"
            : formatInr(order.getShippingTotal());
    String customerGreeting = order.getCustomerName() != null && !order.getCustomerName().isBlank()
        ? esc(order.getCustomerName())
        : "Valued Collector";
    String paymentSummary = order.getPaymentMethod() + " &middot; " + order.getPaymentStatus();
    String discountSuffix = order.getCouponCode() != null ? " (" + esc(order.getCouponCode()) + ")" : "";

    return """
        <!DOCTYPE html>
        <html lang="en">
        <head>
          <meta charset="UTF-8">
          <meta name="viewport" content="width=device-width, initial-scale=1.0">
        </head>
        <body style="margin:0;padding:0;background-color:#F4F1EA;color:#141311;font-family:Arial,Helvetica,sans-serif;">
          <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" style="background-color:#F4F1EA;">
            <tr>
              <td align="center" style="padding:28px 12px;">
                <table role="presentation" width="620" cellpadding="0" cellspacing="0" border="0" style="width:100%%;max-width:620px;background-color:#FFFFFF;border:1px solid #DFDAD0;">
                  <!-- Obsidian Brand Header -->
                  <tr>
                    <td align="center" style="background-color:#141311;padding:24px 28px;border-bottom:3px solid #B08D57;">
                      <div style="font-size:10px;line-height:14px;letter-spacing:3px;color:#DFC28C;text-transform:uppercase;margin:0 0 6px 0;">
                        FINE HOROLOGY &middot; TAX INVOICE &amp; ORDER CONFIRMATION
                      </div>
                      <div style="font-family:Georgia,'Times New Roman',serif;font-size:28px;line-height:34px;letter-spacing:5px;color:#FAF9F6;text-transform:uppercase;">
                        TIMECRAFT
                      </div>
                    </td>
                  </tr>

                  <!-- Animated Order Confirmation GIF -->
                  <tr>
                    <td align="center" style="background-color:#141311;padding:0 24px 24px 24px;border-bottom:1px solid #2A2620;">
                      <img src="cid:orderConfirmedGif"
                           alt="TimeCraft Order Confirmed"
                           width="360"
                           style="display:block;width:100%%;max-width:360px;height:auto;border:1px solid #B08D57;border-radius:3px;">
                    </td>
                  </tr>

                  <!-- Greeting & Intro -->
                  <tr>
                    <td style="padding:30px 28px 16px 28px;">
                      <h1 style="font-family:Georgia,'Times New Roman',serif;font-weight:normal;margin:0 0 10px 0;font-size:25px;line-height:32px;color:#141311;">
                        Thank You for Your Acquisition
                      </h1>
                      <p style="font-size:14px;line-height:22px;color:#4A4945;margin:0;">
                        Dear %s, your TimeCraft order <strong style="color:#141311;">%s</strong> is confirmed and our horologists are preparing your timepiece for insured armoured dispatch.
                      </p>
                    </td>
                  </tr>

                  <!-- Order Metadata Grid -->
                  <tr>
                    <td style="padding:8px 28px 20px 28px;">
                      <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" style="background-color:#FAF9F6;border:1px solid #E5E2DC;">
                        <tr>
                          <td width="50%%" style="padding:12px 14px;border-bottom:1px solid #E5E2DC;border-right:1px solid #E5E2DC;vertical-align:top;">
                            <div style="font-size:10px;letter-spacing:1.6px;text-transform:uppercase;color:#B08D57;margin:0 0 3px 0;">Order Reference</div>
                            <div style="font-size:13px;font-weight:bold;color:#141311;">%s</div>
                          </td>
                          <td width="50%%" style="padding:12px 14px;border-bottom:1px solid #E5E2DC;vertical-align:top;">
                            <div style="font-size:10px;letter-spacing:1.6px;text-transform:uppercase;color:#B08D57;margin:0 0 3px 0;">Placed On</div>
                            <div style="font-size:13px;font-weight:bold;color:#141311;">%s</div>
                          </td>
                        </tr>
                        <tr>
                          <td width="50%%" style="padding:12px 14px;border-right:1px solid #E5E2DC;vertical-align:top;">
                            <div style="font-size:10px;letter-spacing:1.6px;text-transform:uppercase;color:#B08D57;margin:0 0 3px 0;">Order Status</div>
                            <div style="font-size:13px;font-weight:bold;color:#235D3A;">%s</div>
                          </td>
                          <td width="50%%" style="padding:12px 14px;vertical-align:top;">
                            <div style="font-size:10px;letter-spacing:1.6px;text-transform:uppercase;color:#B08D57;margin:0 0 3px 0;">Payment Method</div>
                            <div style="font-size:13px;font-weight:bold;color:#141311;">%s</div>
                          </td>
                        </tr>
                      </table>
                    </td>
                  </tr>

                  <!-- Itemized Timepieces with Product Images -->
                  <tr>
                    <td style="padding:4px 28px 18px 28px;">
                      <div style="font-size:10.5px;letter-spacing:1.8px;text-transform:uppercase;color:#B08D57;font-weight:bold;border-bottom:2px solid #141311;padding-bottom:8px;">
                        Confirmed Timepieces
                      </div>
                      <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0">
                        %s
                      </table>
                    </td>
                  </tr>

                  <!-- Totals Breakdown -->
                  <tr>
                    <td style="padding:4px 28px 22px 28px;">
                      <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" style="background-color:#FAF9F6;border:1px solid #E5E2DC;">
                        <tr>
                          <td style="padding:14px 16px;">
                            <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" style="font-size:13px;line-height:22px;">
                              <tr>
                                <td style="color:#6E6B64;">Subtotal</td>
                                <td align="right" style="color:#141311;">%s</td>
                              </tr>
                              <tr>
                                <td style="color:#6E6B64;">Discount%s</td>
                                <td align="right" style="color:#235D3A;">- %s</td>
                              </tr>
                              <tr>
                                <td style="color:#6E6B64;">%s</td>
                                <td align="right" style="color:#141311;">%s</td>
                              </tr>
                              <tr>
                                <td style="color:#6E6B64;">Insured Armoured Shipping</td>
                                <td align="right" style="color:#141311;">%s</td>
                              </tr>
                              <tr>
                                <td colspan="2" style="padding-top:10px;border-top:2px solid #141311;"></td>
                              </tr>
                              <tr>
                                <td style="font-size:16px;font-weight:bold;color:#141311;">Grand Total (INR)</td>
                                <td align="right" style="font-size:17px;font-weight:bold;color:#141311;">%s</td>
                              </tr>
                            </table>
                          </td>
                        </tr>
                      </table>
                    </td>
                  </tr>

                  <!-- Delivery Destination -->
                  <tr>
                    <td style="padding:0 28px 24px 28px;">
                      <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" style="background-color:#FAF9F6;border:1px solid #E5E2DC;">
                        <tr>
                          <td style="width:4px;background-color:#B08D57;"></td>
                          <td style="padding:14px 16px;font-size:13px;line-height:20px;color:#4A4945;">
                            <div style="font-size:10px;letter-spacing:1.8px;text-transform:uppercase;color:#B08D57;font-weight:bold;margin:0 0 5px 0;">
                              Insured Delivery Destination
                            </div>
                            %s
                          </td>
                        </tr>
                      </table>
                    </td>
                  </tr>

                  <!-- CTA & PDF Notice -->
                  <tr>
                    <td align="center" style="padding:0 28px 26px 28px;">
                      <a href="%s" style="display:inline-block;background-color:#141311;color:#FAF9F6;border:1px solid #B08D57;text-decoration:none;padding:13px 28px;font-size:12px;letter-spacing:1.8px;text-transform:uppercase;font-weight:bold;">
                        View Order &amp; Download Invoice
                      </a>
                      <p style="font-size:12px;line-height:18px;color:#6E6B64;margin:14px 0 0 0;">
                        Your official PDF tax invoice (<strong style="color:#141311;">TimeCraft-Invoice-%s.pdf</strong>) is attached to this email.
                      </p>
                    </td>
                  </tr>

                  <!-- Founder Closing Sign-Off -->
                  <tr>
                    <td style="padding:22px 28px 26px 28px;border-top:1px solid #E5E2DC;">
                      <p style="font-size:13px;line-height:21px;color:#4A4945;margin:0 0 12px 0;">
                        Every watch leaves our atelier inspected, serial-recorded, and insured until it reaches your wrist. Thank you for entrusting TimeCraft with a chapter of your collection.
                      </p>
                      <div style="font-family:Georgia,'Times New Roman',serif;font-size:18px;color:#141311;">Goutam Gurjar</div>
                      <div style="font-size:10.5px;letter-spacing:1.6px;text-transform:uppercase;color:#B08D57;margin-top:2px;">Founder, TimeCraft</div>
                    </td>
                  </tr>

                  <!-- Footer -->
                  <tr>
                    <td align="center" style="background-color:#FAF9F6;border-top:1px solid #E5E2DC;padding:14px 24px;font-size:11px;color:#7A776E;">
                      TimeCraft Fine Horology &middot; Insured Armoured Delivery &middot; 7-Day Inspection Guarantee
                    </td>
                  </tr>
                </table>
              </td>
            </tr>
          </table>
        </body>
        </html>
        """
        .formatted(
            customerGreeting,
            esc(order.getOrderNumber()),
            esc(order.getOrderNumber()),
            esc(DATE_FMT.format(order.getPlacedAt())),
            esc(String.valueOf(order.getStatus())),
            paymentSummary,
            rows,
            formatInr(order.getSubtotal()),
            discountSuffix,
            formatInr(order.getDiscountTotal()),
            esc(taxLabel),
            formatInr(order.getTaxTotal()),
            esc(shippingDisplay),
            formatInr(order.getGrandTotal()),
            addressLine,
            esc(orderUrl),
            esc(order.getOrderNumber()));
  }

  private static String resolveImageUrl(String rawUrl, String frontendBaseUrl) {
    if (rawUrl == null || rawUrl.isBlank()) {
      return null;
    }
    String trimmed = rawUrl.trim();
    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
      return trimmed;
    }
    if (trimmed.startsWith("/") && frontendBaseUrl != null && !frontendBaseUrl.isBlank()) {
      String base = frontendBaseUrl.endsWith("/") ? frontendBaseUrl.substring(0, frontendBaseUrl.length() - 1)
          : frontendBaseUrl;
      return base + trimmed;
    }
    return trimmed;
  }

  private static String formatInr(BigDecimal amount) {
    return "&#8377;" + formatBigDecimalAmount(amount);
  }

  private static String formatBigDecimalAmount(BigDecimal amount) {
    BigDecimal safe = (amount != null ? amount : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    NumberFormat nf = NumberFormat.getNumberInstance(IN_LOCALE);
    nf.setMinimumFractionDigits(2);
    nf.setMaximumFractionDigits(2);
    return nf.format(safe);
  }

  private static String esc(String s) {
    if (s == null)
      return "";
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
