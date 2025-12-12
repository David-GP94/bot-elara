package com.bot.elara.Infrastructure.Entrypoints.Rest;

import com.bot.elara.Application.Service.OnboardingService;
import com.bot.elara.Domain.Model.OnboardingStep;
import com.bot.elara.Domain.Model.Patient;
import com.bot.elara.Domain.Repository.PatientRepository;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/stripe")
@RequiredArgsConstructor
@Slf4j
public class StripeWebhookController {

    @Value("${stripe.webhook-secret}")
    private String stripeWebhookSecret;

    private final PatientRepository patientRepository;
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
            Session session = (Session) event.getData().getObject();
            var metadata = session.getMetadata();

            if (metadata == null || !"whatsapp_bot".equals(metadata.get("channel"))) {
                log.info("Evento ignorado - no pertenece al canal whatsapp_bot");
                return ResponseEntity.ok().build();
            }

            String whatsappId = metadata != null ? metadata.get("whatsapp_id") : "5215545830244";  // ← Tu número fijo para pruebas
            if (whatsappId == null || whatsappId.isBlank()) {
                log.warn("No hay whatsapp_id, usando default para pruebas");
                whatsappId = "5215545830244";
            }

            Patient patient = patientRepository.findByWhatsappId(whatsappId).orElse(null);
            if (patient == null) {
                log.warn("Paciente no encontrado para whatsapp_id: {}", whatsappId);
                return ResponseEntity.ok().build();
            }

            // Idempotencia: evitar procesar dos veces
            if (Boolean.TRUE.equals(patient.getPagoProcesado())) {
                log.info("Pago ya procesado previamente para {}", whatsappId);
                return ResponseEntity.ok().build();
            }

            log.info("Pago exitoso procesado para paciente ID: {} (WhatsApp: {})", "ID-CLIENTE-UNICO", whatsappId);  //TODO: AQUI VA EL ID DE CLIEBNTE UNICO
            // Marcar como pagado y completado
            patient.setPagoProcesado(true);
            patient.setCurrentStep(OnboardingStep.COMPLETED);
            patientRepository.save(patient);

            // Enviar mensaje de éxito usando el método público del servicio
            String panelUrl = "https://panel.elara.com/patient/" + whatsappId; // Ajusta si usas otro formato
            onboardingService.enviarMensajePagoExitoso(whatsappId, panelUrl);
        }

        return ResponseEntity.ok().build();
    }
}