//package com.bot.elara.Infrastructure.Entrypoints.Rest;
//
//import com.bot.elara.Application.Service.OnboardingRespondIoService;
//import com.fasterxml.jackson.databind.JsonNode;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.*;
//
//@RestController
//@RequestMapping("/webhook/respondio")
//@RequiredArgsConstructor
//@Slf4j
//public class RespondIoWebhookController {
//
//    private final OnboardingRespondIoService onboardingService;
//    private final ObjectMapper objectMapper;
//
//    @PostMapping
//    public ResponseEntity<Void> receive(@RequestBody String rawPayload) {
//        log.info("Webhook Respond.io: {}", rawPayload);
//
//        try {
//            JsonNode payload = objectMapper.readTree(rawPayload);
//
//            if (!"message.incoming".equals(payload.path("event").asText())) {
//                return ResponseEntity.ok().build();
//            }
//
//            JsonNode data = payload.path("data");
//            JsonNode contact = data.path("contact");
//            JsonNode message = data.path("message");
//
//            String contactId = contact.path("id").asText();
//            String type = message.path("type").asText();
//
//            if ("text".equals(type)) {
//                String text = message.path("text").asText();
//                onboardingService.processText(contactId, text);
//            }
//            else if ("image".equals(type)) {
//                JsonNode image = message.path("image");
//                String imageId = image.path("id").asText();
//                String imageUrl = image.path("url").asText(); // Este es el enlace temporal
//
//                if (imageUrl.isEmpty()) {
//                    log.warn("Falta URL de imagen en payload");
//                    return ResponseEntity.ok().build();
//                }
//
//                log.info("Imagen recibida: id={}, url={}", imageId, imageUrl);
//                onboardingService.processImage(contactId, imageUrl, imageId);
//            }
//
//        } catch (Exception e) {
//            log.error("Error procesando webhook de Respond.io", e);
//        }
//
//        return ResponseEntity.ok().build(); // Siempre 200
//    }
//}