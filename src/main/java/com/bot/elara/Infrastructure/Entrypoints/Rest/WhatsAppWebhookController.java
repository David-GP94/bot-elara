package com.bot.elara.Infrastructure.Entrypoints.Rest;

import com.bot.elara.Application.Service.OnboardingService;
import com.bot.elara.Infrastructure.External.Whatsapp.Config.WhatsAppConfig;
import com.bot.elara.Infrastructure.External.Whatsapp.Model.Image;
import com.bot.elara.Infrastructure.External.Whatsapp.Model.WhatsAppWebhookPayload;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Objects;

@RestController
@RequestMapping("/webhook")
@RequiredArgsConstructor
@Slf4j
public class WhatsAppWebhookController {

    private final OnboardingService onboardingService;
    private final WhatsAppConfig config;
    private final ObjectMapper objectMapper;  // ← AÑADIDO

    @GetMapping
    public ResponseEntity<String> verify(
            @RequestParam("hub.mode") String mode,
            @RequestParam("hub.challenge") String challenge,
            @RequestParam("hub.verify_token") String verifyToken) {

        log.info("Verificación: mode={}, challenge={}, token={}", mode, challenge, verifyToken);

        if ("subscribe".equals(mode) && config.getWebhookVerifyToken().equals(verifyToken)) {
            log.info("Webhook verificado OK");
            return ResponseEntity.ok(challenge);
        }

        log.warn("Verificación fallida");
        return ResponseEntity.status(403).body("Forbidden");
    }

    @PostMapping
    public ResponseEntity<Void> receive(@RequestBody String rawPayload) {  // ← CAMBIO: String
        log.info("=== WEBHOOK CRUDO ===");
        log.info("Raw: {}", rawPayload);

        try {
            WhatsAppWebhookPayload payload = objectMapper.readValue(rawPayload, WhatsAppWebhookPayload.class);
            log.info("Parseado OK");

            if (payload.getEntry() == null || payload.getEntry().isEmpty()) {
                log.warn("Entry vacío");
                return ResponseEntity.ok().build();
            }

            payload.getEntry().forEach(entry -> {
                if (entry.getChanges() == null || entry.getChanges().isEmpty()) return;

                entry.getChanges().forEach(change -> {
                    var value = change.getValue();
                    if (value == null || value.getMessages() == null || value.getMessages().isEmpty()) return;

                    value.getMessages().forEach(message -> {
                        String from = message.getFrom();
                        String type = message.getType();

                        log.info("MENSAJE → from: {} | type: {}", from, type);

                        if ("text".equals(type) && message.getText() != null) {
                            String body = message.getText().getBody().trim();
                            log.info("Texto: '{}'", body);
                            onboardingService.processText(from, body);
                        } else if ("image".equals(type) && message.getImage() != null) {
                            Image image = message.getImage();
                            log.info("Imagen: id={}", image.getId());
                            onboardingService.processImage(from, image);
                        } else {
                            log.warn("Tipo no soportado: {}", type);
                        }
                    });
                });
            });

        } catch (JsonProcessingException e) {
            log.error("JSON inválido", e);
        } catch (Exception e) {
            log.error("Error webhook", e);
        }

        return ResponseEntity.ok().build();
    }
}