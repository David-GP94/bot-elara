package com.bot.elara.Infrastructure.Entrypoints.Rest;

import com.bot.elara.Application.Service.MercadoPagoService;
import com.bot.elara.Application.Service.OnboardingService;
import com.bot.elara.Domain.Model.BotSession;
import com.bot.elara.Domain.Model.OnboardingStep;
import com.bot.elara.Domain.Model.Patient;
import com.bot.elara.Domain.Repository.PatientRepository;
import com.bot.elara.Infrastructure.DTO.MercadoPago.PaymentResponse;
import com.bot.elara.Infrastructure.Persistence.Jpa.BotSessionRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/mercadopago")
@RequiredArgsConstructor
@Slf4j
public class MercadoPagoWebhookController {

    private final MercadoPagoService mercadoPagoService;
    private final PatientRepository patientRepository;
    private final BotSessionRepository botSessionRepository;   // 🔥 NUEVO
    private final OnboardingService onboardingService;

    // ===============================
    // WEBHOOK MERCADOPAGO
    // ===============================

    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(
            @RequestParam(required = false) String topic,
            @RequestParam(required = false) Long id,
            @RequestBody(required = false) Map<String, Object> body
    ) {

        try {

            log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            log.info("✅ Webhook recibido de MercadoPago");
            log.info("📋 Topic: {}", topic);
            log.info("🆔 ID: {}", id);
            log.info("📦 Body: {}", body);
            log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

            if ("payment".equals(topic) && id != null) {
                procesarPago(id);
            }

            return ResponseEntity.ok("OK");

        } catch (Exception e) {
            log.error("❌ Error procesando webhook", e);
            return ResponseEntity.status(500).body("Error");
        }
    }

    private void procesarPago(Long paymentId) {

        PaymentResponse payment = mercadoPagoService.obtenerInfoPago(paymentId);

        if (payment == null) {
            log.error("❌ No se pudo obtener información del pago {}", paymentId);
            return;
        }

        if ("approved".equals(payment.getStatus())) {
            manejarPagoAprobado(payment);
        }
    }

    private void manejarPagoAprobado(PaymentResponse payment) {

        log.info("✅ PAGO APROBADO: {}", payment.getId());

        String whatsappId = extractWhatsappIdFromReference(payment.getExternalReference());

        if (whatsappId == null) {
            log.warn("⚠️ No se pudo extraer WhatsApp ID");
            return;
        }

        Optional<Patient> patientOpt = patientRepository.findByWhatsappId(whatsappId);

        if (patientOpt.isEmpty()) {
            log.warn("⚠️ Paciente no encontrado: {}", whatsappId);
            return;
        }

        Patient patient = patientOpt.get();

        // 🔥 IDEMPOTENCIA
        if (Boolean.TRUE.equals(patient.getPagoProcesado())) {
            log.info("ℹ️ Pago ya procesado previamente para {}", whatsappId);
            return;
        }

        log.info("Procesando pago para {}", whatsappId);

        // ==========================
        // 🔥 ACTUALIZAR PATIENT
        // ==========================
        patient.setPagoProcesado(true);
        patientRepository.save(patient);

        // ==========================
        // 🔥 ACTUALIZAR SESSION (YA NO EN PATIENT)
        // ==========================
        BotSession session = botSessionRepository
                .findByWhatsappId(whatsappId)
                .orElse(null);

        if (session != null) {
            session.setCurrentStep(OnboardingStep.COMPLETED);
            botSessionRepository.save(session);
        }

        // ==========================
        // 🔥 ENVIAR MENSAJE
        // ==========================
        String panelUrl = "https://panel.elara.com/patient/" + whatsappId;
        onboardingService.enviarMensajePagoExitoso(whatsappId, panelUrl);

        log.info("📱 Confirmación enviada a {}", whatsappId);
    }

    private String extractWhatsappIdFromReference(String externalReference) {

        if (externalReference == null) return null;

        String[] parts = externalReference.split("-");

        for (String part : parts) {
            if (part.matches("^521\\d{10}$")) {
                return part;
            }
        }

        return null;
    }

    @Data
    public static class PaymentRequest {
        private String consultaId;
        private String whatsappId;
        private String email;
    }
}
