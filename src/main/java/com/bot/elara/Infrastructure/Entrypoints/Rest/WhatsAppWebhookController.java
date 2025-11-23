package com.bot.elara.Infrastructure.Entrypoints.Rest;

import com.bot.elara.Application.Service.OnboardingService;
import com.bot.elara.Infrastructure.External.Whatsapp.Config.WhatsAppConfig;
import com.bot.elara.Infrastructure.External.Whatsapp.Model.Image;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


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
    public ResponseEntity<Void> receive(@RequestBody JsonNode payload) {
        log.info("=== WEBHOOK RECIBIDO ===");

        try {
            for (JsonNode entry : payload.path("entry")) {
                for (JsonNode change : entry.path("changes")) {
                    JsonNode value = change.path("value");
                    JsonNode messages = value.path("messages");

                    for (JsonNode message : messages) {
                        String from = message.path("from").asText();
                        String type = message.path("type").asText();

                        log.info("Mensaje de {} | Tipo: {}", from, type);

                        if ("text".equals(type)) {
                            String body = message.path("text").path("body").asText();
                            onboardingService.processText(from, body);

                        } else if ("interactive".equals(type)) {
                            JsonNode interactive = message.path("interactive");
                            String interactiveType = interactive.path("type").asText();

                            if ("button_reply".equals(interactiveType)) {
                                String title = interactive.path("button_reply").path("title").asText();
                                log.info("Botón presionado: '{}'", title);
                                onboardingService.processText(from, title);

                            } else if ("list_reply".equals(interactiveType)) {
                                String listId = interactive.path("list_reply").path("id").asText();
                                String title = interactive.path("list_reply").path("title").asText();
                                log.info("Opción de lista seleccionada: id={}, título={}", listId, title);
                                onboardingService.processListSelection(from, listId);

                            } else {
                                log.warn("Tipo interactive no manejado: {}", interactiveType);
                            }

                        } else if ("image".equals(type)) {
                            String imageId = message.path("image").path("id").asText();
                            Image image = new Image();
                            image.setId(imageId);
                            onboardingService.processImage(from, image);

                        } else {
                            log.info("Tipo de mensaje no manejado: {}", type);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error procesando webhook", e);
        }

        return ResponseEntity.ok().build();
    }
}