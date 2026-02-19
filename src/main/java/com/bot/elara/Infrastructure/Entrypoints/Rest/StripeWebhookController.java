package com.bot.elara.Infrastructure.Entrypoints.Rest;

import com.bot.elara.Application.Service.OnboardingService;
import com.bot.elara.Domain.Model.BotSession;
import com.bot.elara.Domain.Model.OnboardingStep;
import com.bot.elara.Domain.Model.Patient;
import com.bot.elara.Domain.Repository.PatientRepository;
import com.bot.elara.Infrastructure.Persistence.Jpa.BotSessionRepository;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/stripe")
@RequiredArgsConstructor
@Slf4j
public class StripeWebhookController {

    @Value("${stripe.webhook-secret}")
    private String stripeWebhookSecret;

    private final PatientRepository patientRepository;
    private final BotSessionRepository botSessionRepository;   // ✅ NUEVO
    private final OnboardingService onboardingService;

    @PostMapping("/webhook")
    public ResponseEntity<String> webhook(@RequestBody String payload,
                                          @RequestHeader("Stripe-Signature") String sigHeader) {

        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, stripeWebhookSecret);
        } catch (SignatureVerificationException e) {
            log.warn("Firma de webhook inválida: {}", e.getMessage());
            return ResponseEntity.status(400).body("Invalid signature");
        } catch (Exception e) {
            log.error("Error procesando webhook de Stripe", e);
            return ResponseEntity.status(400).body("Webhook error");
        }

        if ("checkout.session.completed".equals(event.getType())) {

            Session sessionStripe = (Session) event.getData().getObject();
            var metadata = sessionStripe.getMetadata();

            if (metadata == null || !"whatsapp_bot".equals(metadata.get("channel"))) {
                log.info("Evento ignorado - no pertenece al canal whatsapp_bot");
                return ResponseEntity.ok().build();
            }

            String whatsappId = metadata.get("whatsapp_id");

            if (whatsappId == null || whatsappId.isBlank()) {
                log.warn("No hay whatsapp_id en metadata");
                return ResponseEntity.ok().build();
            }

            Patient patient = patientRepository.findByWhatsappId(whatsappId).orElse(null);

            if (patient == null) {
                log.warn("Paciente no encontrado para whatsapp_id: {}", whatsappId);
                return ResponseEntity.ok().build();
            }

            // 🔁 Idempotencia
            if (Boolean.TRUE.equals(patient.getPagoProcesado())) {
                log.info("Pago ya procesado previamente para {}", whatsappId);
                return ResponseEntity.ok().build();
            }

            log.info("Pago exitoso procesado para WhatsApp: {}", whatsappId);

            // ✅ Marcar paciente como pagado
            patient.setPagoProcesado(true);
            patientRepository.save(patient);

            // ✅ Actualizar estado en BotSession (NO en Patient)
            BotSession botSession = botSessionRepository
                    .findByWhatsappId(whatsappId)
                    .orElse(null);

            if (botSession != null) {
                botSession.setCurrentStep(OnboardingStep.COMPLETED);
                botSessionRepository.save(botSession);
            }

            // ✅ Enviar mensaje de éxito
            String panelUrl = "https://panel.elara.com/patient/" + whatsappId;
            onboardingService.enviarMensajePagoExitoso(whatsappId, panelUrl);
        }

        return ResponseEntity.ok().build();
    }
}
