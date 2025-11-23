package com.bot.elara.Infrastructure.External.Whatsapp;

import com.bot.elara.Infrastructure.External.Whatsapp.Config.WhatsAppConfig;
import com.bot.elara.Infrastructure.External.Whatsapp.Model.TextBody;
import com.bot.elara.Infrastructure.External.Whatsapp.Model.WhatsAppMessageRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
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

    public void sendText(String to, String text) {
        var request = WhatsAppMessageRequest.builder()
                .messagingProduct("whatsapp")
                .to(to)
                .type("text")
                .text(new TextBody(text))
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(config.getAccessToken());
        headers.setContentType(MediaType.APPLICATION_JSON);

        restTemplate.postForEntity(config.getMessagesUrl(), new HttpEntity<>(request, headers), String.class);
    }

    public void sendDocument(String to, String url, String filename, String phoneNumberId) {
        Map<String, Object> message = new HashMap<>();
        message.put("messaging_product", "whatsapp");
        message.put("to", to);
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
        if (buttonTitles == null || buttonTitles.isEmpty() || buttonTitles.size() > 3) {
            log.warn("No se enviaron botones → títulos inválidos: {}", buttonTitles);
            // Enviamos texto plano como fallback
            sendText(to, bodyText + "\n\n(Respondiendo con texto porque no hay opciones válidas)");
            return;
        }

        Map<String, Object> message = new HashMap<>();
        message.put("messaging_product", "whatsapp");
        message.put("to", to);
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
            sendText(to, bodyText);
            return;
        }

        interactive.put("action", Map.of("buttons", buttons));
        message.put("interactive", interactive);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(config.getAccessToken());
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            restTemplate.postForEntity(config.getMessagesUrl(), new HttpEntity<>(message, headers), String.class);
            log.info("Botones enviados correctamente a {}: {}", to, buttonTitles);
        } catch (Exception e) {
            log.error("Error enviando botones a " + to, e);
            sendText(to, bodyText + " (Error al mostrar botones)");
        }
    }


    public void sendListMessage(String to, String headerText, String bodyText, String buttonText, List<Map<String, String>> rows) {
        if (rows.size() > 10) {
            throw new IllegalArgumentException("Máximo 10 filas en list message");
        }

        Map<String, Object> message = new HashMap<>();
        message.put("messaging_product", "whatsapp");
        message.put("to", to);
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
            log.info("List message enviado a {} con {} filas", to, rows.size());
        } catch (Exception e) {
            log.error("Error enviando list message a " + to, e);
        }
    }
}