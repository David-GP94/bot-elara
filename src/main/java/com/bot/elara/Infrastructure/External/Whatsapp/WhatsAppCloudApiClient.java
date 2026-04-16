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
    private String cachedVideoMediaId;

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

    public void sendListMessageWithSections(
            String to,
            String headerText,
            String bodyText,
            String buttonText,
            List<Map<String, Object>> sections
    ) {
        String normalized = normalizePhone(to);
        if (normalized == null) {
            log.error("Número inválido: {}", to);
            return;
        }

        Map<String, Object> message = new HashMap<>();
        message.put("messaging_product", "whatsapp");
        message.put("to", normalized);
        message.put("type", "interactive");

        Map<String, Object> interactive = new HashMap<>();
        interactive.put("type", "list");

        if (headerText != null && !headerText.isBlank()) {
            interactive.put("header", Map.of("type", "text", "text", headerText));
        }

        interactive.put("body", Map.of("text", bodyText));

        Map<String, Object> action = new HashMap<>();
        action.put("button", buttonText);
        action.put("sections", sections);
        interactive.put("action", action);

        message.put("interactive", interactive);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(config.getAccessToken());
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            restTemplate.postForEntity(config.getMessagesUrl(), new HttpEntity<>(message, headers), String.class);
            log.info("List message con secciones enviado a {}", normalized);
        } catch (Exception e) {
            log.error("Error enviando list message con secciones a " + normalized, e);
            sendText(normalized, "Error mostrando opciones. Escribe *HOLA* para reiniciar.");
        }
    }

    public String getLogoMediaId() {
        if (cachedLogoMediaId != null) {
            return cachedLogoMediaId;
        }
        cachedLogoMediaId = uploadMedia(config.getLogoPath(), "image/jpeg");
        return cachedLogoMediaId;
    }

    public String getVideoMediaId() {
        if (cachedVideoMediaId != null) {
            return cachedVideoMediaId;
        }
        cachedVideoMediaId = uploadMedia(config.getVideoPath(), "video/mp4");
        return cachedVideoMediaId;
    }

    public void sendVideo(String to, String caption) {
        String normalized = normalizePhone(to);
        if (normalized == null) {
            log.error("Número inválido: {}", to);
            return;
        }

        String mediaId = getVideoMediaId();
        if (mediaId == null) {
            log.error("No se pudo obtener mediaId del video");
            return;
        }

        Map<String, Object> message = new HashMap<>();
        message.put("messaging_product", "whatsapp");
        message.put("to", normalized);
        message.put("type", "video");
        message.put("video", Map.of(
                "id", mediaId,
                "caption", caption
        ));

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(config.getAccessToken());
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            restTemplate.postForEntity(
                    config.getMessagesUrl(),
                    new HttpEntity<>(message, headers),
                    String.class
            );
            log.info("Video enviado a {}", normalized);
        } catch (Exception e) {
            log.error("Error enviando video a " + normalized, e);
        }
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

        // 1. Remueve todo lo que no sea dígito
        String cleaned = trimmed.replaceAll("[^0-9]", "");

        // 2. Convierte prefijo internacional 00xxxx -> xxxx
        if (cleaned.startsWith("00")) {
            cleaned = cleaned.substring(2);
        }

        // --- REGLAS ESPECÍFICAS DE PAÍSES PARA WHATSAPP ---

        // 3A. Argentina: Quitar el '9' intermedio generado por WhatsApp (549 -> 54)
        if (cleaned.startsWith("549") && cleaned.length() == 13) {
            cleaned = "54" + cleaned.substring(3);
        }

        // 3B. México: Quitar el '1' intermedio antiguo (521 -> 52)
        else if (cleaned.startsWith("521") && cleaned.length() == 13) {
            cleaned = "52" + cleaned.substring(3);
        }

        // 3C. UK (Reino Unido): Quitar el '0' troncal si el cliente lo escribió por error (+44 0 75... -> 44 75...)
        else if (cleaned.startsWith("440")) {
            cleaned = "44" + cleaned.substring(3);
        }

        // 4. México Default
        // Si vienen 10 dígitos cerrados, asumimos que es México (+52)
        else if (cleaned.length() == 10) {
            cleaned = "52" + cleaned;
        }

        // --- VALIDACIÓN FINAL ---

        // Rechazar si es demasiado corto o largo
        if (cleaned.length() < 11 || cleaned.length() > 15) {
            return null;
        }

        return "+" + cleaned; // formato E.164
    }

    /**
     * Envía un mensaje con botón grande azul (CTA URL) - ideal para pagos con Stripe
     */
    public void sendCtaUrlButton(String to, String bodyText, String buttonDisplayText, String url) {
        String normalized = normalizePhone(to);
        if (normalized == null) {
            log.error("Número inválido al enviar CTA URL: {}", to);
            return;
        }

        // Validación básica del texto del botón (máx 20 caracteres según Meta)
        if (buttonDisplayText == null || buttonDisplayText.trim().isEmpty() || buttonDisplayText.length() > 20) {
            log.warn("Texto del botón CTA inválido (máx 20 chars): '{}'. Usando fallback.", buttonDisplayText);
            buttonDisplayText = "Pagar ahora";
        }

        Map<String, Object> message = new HashMap<>();
        message.put("messaging_product", "whatsapp");
        message.put("to", normalized);
        message.put("type", "interactive");

        Map<String, Object> interactive = new HashMap<>();
        interactive.put("type", "cta_url");

        // Cuerpo del mensaje
        interactive.put("body", Map.of("text", bodyText));

        // Acción con el botón grande
        Map<String, Object> action = new HashMap<>();
        action.put("name", "cta_url");
        action.put("parameters", Map.of(
                "display_text", buttonDisplayText.trim(),
                "url", url
        ));
        interactive.put("action", action);

        message.put("interactive", interactive);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(config.getAccessToken());
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            restTemplate.postForEntity(config.getMessagesUrl(), new HttpEntity<>(message, headers), String.class);
            log.info("Botón CTA URL enviado correctamente a {}: {} → {}", normalized, buttonDisplayText, url);
        } catch (Exception e) {
            log.error("Error enviando botón CTA URL a " + normalized, e);
            // Fallback: enviar como texto normal con la URL
            sendText(normalized, bodyText + "\n\nRealiza tu pago aquí:\n" + url);
        }
    }

}
