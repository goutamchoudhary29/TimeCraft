package com.timecraft.product.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.timecraft.config.AppProperties;
import com.timecraft.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Uploads and deletes product images in Cloudinary using signed REST calls.
 * Never persists binary image data in MySQL — only the secure URL and public_id are stored.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CloudinaryService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/webp"
    );

    private final AppProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public record UploadResult(String url, String publicId) {
    }

    public void validateImageFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("EMPTY_IMAGE_FILE", "Please select an image file to upload");
        }
        long maxBytes = properties.cloudinary().maxFileSizeBytes() > 0
                ? properties.cloudinary().maxFileSizeBytes()
                : 5 * 1024 * 1024L;
        if (file.getSize() > maxBytes) {
            throw BusinessException.badRequest("IMAGE_TOO_LARGE",
                    "Image file size exceeds the maximum allowed limit of " + (maxBytes / (1024 * 1024)) + " MB");
        }
        String ct = file.getContentType() != null ? file.getContentType().toLowerCase(Locale.ROOT) : "";
        if (!ALLOWED_CONTENT_TYPES.contains(ct)) {
            throw BusinessException.badRequest("INVALID_IMAGE_TYPE",
                    "Only JPEG, PNG and WebP image files are permitted");
        }
        try {
            byte[] header = new byte[12];
            int read = file.getInputStream().read(header);
            if (!hasValidMagicBytes(header, read)) {
                throw BusinessException.badRequest("INVALID_IMAGE_CONTENT",
                        "File content does not match a valid JPEG, PNG or WebP image");
            }
        } catch (IOException ex) {
            throw BusinessException.badRequest("IMAGE_READ_ERROR", "Unable to read uploaded image");
        }
    }

    public UploadResult upload(MultipartFile file, String subfolder) {
        validateImageFile(file);
        var cfg = properties.cloudinary();
        if (!cfg.configured()) {
            throw BusinessException.serviceUnavailable("CLOUDINARY_NOT_CONFIGURED",
                    "Cloudinary credentials (CLOUDINARY_CLOUD_NAME, CLOUDINARY_API_KEY, CLOUDINARY_API_SECRET) are not configured. "
                            + "Set them in the environment or supply an existing image URL.");
        }

        try {
            String folder = (cfg.folder() != null && !cfg.folder().isBlank() ? cfg.folder() : "timecraft")
                    + (subfolder != null && !subfolder.isBlank() ? "/" + subfolder : "");
            long timestamp = Instant.now().getEpochSecond();

            Map<String, String> signedParams = new TreeMap<>();
            signedParams.put("folder", folder);
            signedParams.put("timestamp", String.valueOf(timestamp));
            String signature = sign(signedParams, cfg.apiSecret());

            String dataUri = "data:" + file.getContentType() + ";base64,"
                    + Base64.getEncoder().encodeToString(file.getBytes());

            String form = "file=" + urlEncode(dataUri)
                    + "&api_key=" + urlEncode(cfg.apiKey())
                    + "&timestamp=" + timestamp
                    + "&folder=" + urlEncode(folder)
                    + "&signature=" + urlEncode(signature);

            URI endpoint = URI.create("https://api.cloudinary.com/v1_1/" + cfg.cloudName() + "/image/upload");
            HttpRequest req = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form))
                    .build();

            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            JsonNode json = objectMapper.readTree(res.body());
            if (res.statusCode() >= 300 || json.has("error")) {
                String msg = json.path("error").path("message").asText("Cloudinary upload failed");
                throw BusinessException.badRequest("CLOUDINARY_UPLOAD_FAILED", msg);
            }
            return new UploadResult(json.path("secure_url").asText(), json.path("public_id").asText());
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Cloudinary upload error", ex);
            throw BusinessException.serviceUnavailable("CLOUDINARY_ERROR", "Image upload service error");
        }
    }

    public void deleteIfConfigured(String publicId) {
        if (publicId == null || publicId.isBlank() || !properties.cloudinary().configured()) {
            return;
        }
        try {
            var cfg = properties.cloudinary();
            long timestamp = Instant.now().getEpochSecond();
            Map<String, String> params = new TreeMap<>();
            params.put("public_id", publicId);
            params.put("timestamp", String.valueOf(timestamp));
            String signature = sign(params, cfg.apiSecret());

            String form = "public_id=" + urlEncode(publicId)
                    + "&api_key=" + urlEncode(cfg.apiKey())
                    + "&timestamp=" + timestamp
                    + "&signature=" + urlEncode(signature);

            URI endpoint = URI.create("https://api.cloudinary.com/v1_1/" + cfg.cloudName() + "/image/destroy");
            HttpRequest req = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form))
                    .build();
            httpClient.send(req, HttpResponse.BodyHandlers.discarding());
        } catch (Exception ex) {
            log.warn("Could not delete Cloudinary asset {}: {}", publicId, ex.getMessage());
        }
    }

    private static boolean hasValidMagicBytes(byte[] h, int len) {
        if (len < 4) {
            return false;
        }
        // JPEG: FF D8 FF
        if ((h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF) {
            return true;
        }
        // PNG: 89 50 4E 47
        if ((h[0] & 0xFF) == 0x89 && h[1] == 0x50 && h[2] == 0x4E && h[3] == 0x47) {
            return true;
        }
        // WebP: RIFF....WEBP
        return len >= 12 && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P';
    }

    private static String sign(Map<String, String> params, String apiSecret) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (var entry : params.entrySet()) {
            if (!sb.isEmpty()) {
                sb.append('&');
            }
            sb.append(entry.getKey()).append('=').append(entry.getValue());
        }
        sb.append(apiSecret);
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        return HexFormat.of().formatHex(sha1.digest(sb.toString().getBytes(StandardCharsets.UTF_8)));
    }

    private static String urlEncode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
