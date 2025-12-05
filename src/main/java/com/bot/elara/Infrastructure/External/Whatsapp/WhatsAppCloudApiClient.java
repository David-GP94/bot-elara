package com.bot.elara.Infrastructure.External.Whatsapp;

import com.bot.elara.Infrastructure.External.Whatsapp.Config.WhatsAppConfig;
import com.bot.elara.Infrastructure.External.Whatsapp.Model.TextBody;
import com.bot.elara.Infrastructure.External.Whatsapp.Model.WhatsAppMessageRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class WhatsAppCloudApiClient {

    private final WhatsAppConfig config;
    private final RestTemplate restTemplate = new RestTemplate();
    private String cachedLogoMediaId;

    public void sendText(String to, String text) {
        String normalized = normalizePhone(to);
        if (normalized == null) {
            log.error("Número inválido: {}", to);
            return;
        }

        var request = WhatsAppMessageRequest.builder()
                .messagingProduct("whatsapp")
                .to(normalized)
                .type("text")
                .text(new TextBody(text))
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(config.getAccessToken());
        headers.setContentType(MediaType.APPLICATION_JSON);

        restTemplate.postForEntity(config.getMessagesUrl(), new HttpEntity<>(request, headers), String.class);
    }

    public void sendDocument(String to, String url, String filename, String phoneNumberId) {
        String normalized = normalizePhone(to);
        if (normalized == null) {
            log.error("Número inválido: {}", to);
            return;
        }

        Map<String, Object> message = new HashMap<>();
        message.put("messaging_product", "whatsapp");
        message.put("to", normalized);
        message.put("type", "document");

        Map<String, Object> document = new HashMap<>();
        document.put("link", url);
        document.put("caption", ""); // opcional
        document.put("filename", filename);
        message.put("document", document);

        restTemplate.postForObject(
                "https://graph.facebook.com/v20.0/" + phoneNumberId + "/messages",
                Map.of("messages", List.of(message)),
                String.class
        );
    }

    public void sendReplyButtons(String to, String bodyText, List<String> buttonTitles) {
        String normalized = normalizePhone(to);
        if (normalized == null) {
            log.error("Número inválido: {}", to);
            return;
        }

        if (buttonTitles == null || buttonTitles.isEmpty() || buttonTitles.size() > 3) {
            log.warn("No se enviaron botones → títulos inválidos: {}", buttonTitles);
            // Enviamos texto plano como fallback
            sendText(normalized, bodyText + "\n\n(Respondiendo con texto porque no hay opciones válidas)");
            return;
        }

        Map<String, Object> message = new HashMap<>();
        message.put("messaging_product", "whatsapp");
        message.put("to", normalized);
        message.put("type", "interactive");

        Map<String, Object> interactive = new HashMap<>();
        interactive.put("type", "button");
        interactive.put("body", Map.of("text", bodyText));

        List<Map<String, Object>> buttons = new ArrayList<>();
        for (int i = 0; i < buttonTitles.size(); i++) {
            String title = buttonTitles.get(i);
            if (title == null || title.trim().isEmpty() || title.length() > 20) {
                log.warn("Título de botón inválido, se omite: '{}'", title);
                continue;
            }
            Map<String, Object> button = new HashMap<>();
            button.put("type", "reply");
            button.put("reply", Map.of(
                    "id", "btn_" + (i + 1),
                    "title", title.trim()
            ));
            buttons.add(button);
        }

        if (buttons.isEmpty()) {
            log.warn("No hay botones válidos después del filtro. Enviando texto plano.");
            sendText(normalized, bodyText);
            return;
        }

        interactive.put("action", Map.of("buttons", buttons));
        message.put("interactive", interactive);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(config.getAccessToken());
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            restTemplate.postForEntity(config.getMessagesUrl(), new HttpEntity<>(message, headers), String.class);
            log.info("Botones enviados correctamente a {}: {}", normalized, buttonTitles);
        } catch (Exception e) {
            log.error("Error enviando botones a " + normalized, e);
            sendText(normalized, bodyText + " (Error al mostrar botones)");
        }
    }


    public void sendListMessage(String to, String headerText, String bodyText, String buttonText, List<Map<String, String>> rows) {
        String normalized = normalizePhone(to);
        if (normalized == null) {
            log.error("Número inválido: {}", to);
            return;
        }

        if (rows.size() > 10) {
            throw new IllegalArgumentException("Máximo 10 filas en list message");
        }

        Map<String, Object> message = new HashMap<>();
        message.put("messaging_product", "whatsapp");
        message.put("to", normalized);
        message.put("type", "interactive");

        Map<String, Object> interactive = new HashMap<>();
        interactive.put("type", "list");

        // Header (opcional)
        if (headerText != null) {
            interactive.put("header", Map.of("type", "text", "text", headerText));
        }

        // Body
        interactive.put("body", Map.of("text", bodyText));

        // Action con rows
        Map<String, Object> action = new HashMap<>();
        action.put("button", buttonText);
        action.put("sections", List.of(
                Map.of(
                        "title", "Opciones disponibles",  // Título de la sección
                        "rows", rows  // Tus 7 opciones como filas
                )
        ));
        interactive.put("action", action);

        message.put("interactive", interactive);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(config.getAccessToken());
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            restTemplate.postForEntity(config.getMessagesUrl(), new HttpEntity<>(message, headers), String.class);
            log.info("List message enviado a {} con {} filas", normalized, rows.size());
        } catch (Exception e) {
            log.error("Error enviando list message a " + normalized, e);
        }
    }

    public String getLogoMediaId() {
        if (cachedLogoMediaId != null) {
            return cachedLogoMediaId;
        }
        cachedLogoMediaId = uploadMedia(config.getLogoPath(), "image/jpeg");
        return cachedLogoMediaId;
    }

    // En WhatsAppCloudApiClient.java - modifica uploadMedia
    private String uploadMedia(String filePath, String mimeType) {
        String uploadUrl = config.getBaseUrl() + "/" + config.getPhoneNumberId() + "/media";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(config.getAccessToken());
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("messaging_product", "whatsapp");

        if (filePath == null || filePath.trim().isEmpty()) {
            log.error("filePath es nulo o vacío");
            return null;
        }

        // Manejo de rutas Windows con posible prefijo '/' y classpath
        Resource resource;
        if (filePath.startsWith("classpath:")) {
            resource = new ClassPathResource(filePath.replace("classpath:", ""));
        } else {
            String normalizedPath = filePath;
            if (normalizedPath.startsWith("/") && normalizedPath.length() > 2 && Character.isLetter(normalizedPath.charAt(1)) && normalizedPath.charAt(2) == ':') {
                // caso "/C:/..." -> quitar slash inicial
                normalizedPath = normalizedPath.substring(1);
            }
            resource = new FileSystemResource(normalizedPath);
        }

        if (!resource.exists()) {
            log.error("Archivo no encontrado: {}", filePath);
            return null;
        }

        body.add("file", resource);
        body.add("type", mimeType);

        try {
            var response = restTemplate.postForEntity(uploadUrl, new HttpEntity<>(body, headers), Map.class);
            Map<?, ?> respBody = response.getBody();
            if (respBody == null) {
                log.error("Respuesta vacía al subir media");
                return null;
            }
            String mediaId = (String) respBody.get("id");
            log.info("Logo subido. Media ID: {}", mediaId);
            return mediaId;
        } catch (Exception e) {
            log.error("Error subiendo logo", e);
            return null;
        }
    }


    public void sendWelcomeImage(String to, String caption) {
        String normalized = normalizePhone(to);
        if (normalized == null) {
            log.error("Número inválido: {}", to);
            return;
        }
        String mediaId = getLogoMediaId();
        if (mediaId == null) {
            sendText(normalized, caption);
            return;
        }

        Map<String, Object> message = new HashMap<>();
        message.put("messaging_product", "whatsapp");
        message.put("to", normalized);
        message.put("type", "image");
        message.put("image", Map.of("id", mediaId, "caption", caption));

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(config.getAccessToken());
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            restTemplate.postForEntity(config.getMessagesUrl(), new HttpEntity<>(message, headers), String.class);
            log.info("Imagen welcome enviada a {}", normalized);
        } catch (Exception e) {
            log.error("Error enviando imagen a " + normalized, e);
        }
    }

    private String normalizePhone(String phone) {
        if (phone == null) return null;
        String trimmed = phone.trim();
        if (trimmed.isEmpty()) return null;

        // Remueve todo lo que no sea dígito
        String cleaned = trimmed.replaceAll("[^0-9]", "");

        // Convierte prefijo internacional 00xxxx -> xxxx
        if (cleaned.startsWith("00")) {
            cleaned = cleaned.substring(2);
        }

        // Si tiene 10 dígitos asumimos México (52)
        if (cleaned.length() == 10) {
            cleaned = "52" + cleaned;
        }

        // Rechazar si fuera demasiado corto o largo
        if (cleaned.length() < 11 || cleaned.length() > 15) {
            return null;
        }

        return "+" + cleaned; // formato E.164
    }
}
