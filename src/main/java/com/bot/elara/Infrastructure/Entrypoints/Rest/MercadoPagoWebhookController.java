package com.bot.elara.Infrastructure. Entrypoints.Rest;

import com.bot.elara. Application.Service.MercadoPagoService;
import com.bot.elara.Application.Service.OnboardingService;
import com.bot.elara.Domain.Model.OnboardingStep;
import com.bot. elara.Domain.Model.Patient;
import com.bot.elara.Domain.Repository.PatientRepository;
import com.bot.elara.Infrastructure.DTO.MercadoPago.PaymentResponse;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok. extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org. springframework.web.bind.annotation.*;

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
    private final OnboardingService onboardingService;

    // ============================================
    // ENDPOINTS DE PRUEBA (Para Postman)
    // ============================================

    @GetMapping("/test")
    public ResponseEntity<Map<String, String>> test() {
        return ResponseEntity.ok(Map.of(
                "status", "OK",
                "provider", mercadoPagoService.getProviderName(),
                "message", "MercadoPago controller is working"
        ));
    }

    @GetMapping("/quick-test")
    public ResponseEntity<Map<String, Object>> quickTest() {
        String consultaId = "TEST-" + System.currentTimeMillis();
        String whatsappId = "5215512345678";
        String email = "test@elara.com";

        log.info("🚀 Creando link de prueba rápido");

        String paymentUrl = mercadoPagoService.crearPaymentLink(consultaId, whatsappId, email);

        Map<String, Object> response = new HashMap<>();
        response.put("success", paymentUrl != null);
        response.put("paymentUrl", paymentUrl);
        response.put("consultaId", consultaId);
        response.put("message", "Abre el paymentUrl en tu navegador");
        response.put("testCard", Map.of(
                "number", "5031 7557 3453 0604",
                "cvv", "123",
                "expiry", "11/25",
                "holder", "APRO"
        ));

        return ResponseEntity.ok(response);
    }

    @PostMapping("/create-payment")
    public ResponseEntity<Map<String, Object>> createPayment(@RequestBody PaymentRequest request) {
        log.info("📥 Solicitud de pago:  {}", request);

        String paymentUrl = mercadoPagoService.crearPaymentLink(
                request.getConsultaId(),
                request.getWhatsappId(),
                request. getEmail()
        );

        Map<String, Object> response = new HashMap<>();

        if (paymentUrl != null) {
            response.put("success", true);
            response. put("paymentUrl", paymentUrl);
            response.put("consultaId", request.getConsultaId());
            response. put("instructions", "Abre el paymentUrl en tu navegador");
            return ResponseEntity.ok(response);
        } else {
            response. put("success", false);
            response.put("error", "No se pudo crear el link de pago");
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @GetMapping("/payment/{paymentId}")
    public ResponseEntity<? > getPaymentInfo(@PathVariable Long paymentId) {
        log.info("📊 Consultando pago:  {}", paymentId);

        PaymentResponse payment = mercadoPagoService.obtenerInfoPago(paymentId);

        if (payment != null) {
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "paymentId", payment.getId(),
                    "status", payment.getStatus(),
                    "amount", payment.getTransactionAmount(),
                    "email", payment.getPayer() != null ? payment.getPayer().getEmail() : "N/A",
                    "externalReference", payment.getExternalReference()
            ));
        } else {
            return ResponseEntity.status(404).body(Map.of(
                    "success", false,
                    "error", "Pago no encontrado"
            ));
        }
    }

    // ============================================
    // WEBHOOK DE MERCADOPAGO (Producción)
    // ============================================

    /**
     * Endpoint para recibir notificaciones de MercadoPago
     * MercadoPago envía:  POST /mercadopago/webhook? topic=payment&id=123456789
     */
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
            } else if ("merchant_order". equals(topic) && id != null) {
                log.info("📦 Orden de comercio recibida: {}", id);
            } else {
                log.warn("⚠️ Topic desconocido: {}", topic);
            }

            return ResponseEntity.ok("OK");

        } catch (Exception e) {
            log.error("❌ Error procesando webhook de MercadoPago", e);
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }

    // ============================================
    // LÓGICA DE PROCESAMIENTO (Similar a Stripe)
    // ============================================

    private void procesarPago(Long paymentId) {
        try {
            PaymentResponse payment = mercadoPagoService.obtenerInfoPago(paymentId);

            if (payment == null) {
                log.error("❌ No se pudo obtener información del pago {}", paymentId);
                return;
            }

            String status = payment. getStatus();
            String externalReference = payment.getExternalReference();

            log.info("📊 Estado del pago: {}", status);
            log.info("📝 Referencia externa: {}", externalReference);
            log.info("💰 Monto: {} {}", payment.getTransactionAmount(), payment.getCurrencyId());

            switch (status) {
                case "approved":
                    manejarPagoAprobado(payment);
                    break;
                case "pending":
                    manejarPagoPendiente(payment);
                    break;
                case "rejected":
                    manejarPagoRechazado(payment);
                    break;
                default:
                    log.warn("⚠️ Estado de pago no manejado: {}", status);
            }

        } catch (Exception e) {
            log.error("❌ Error procesando pago {}", paymentId, e);
        }
    }

    private void manejarPagoAprobado(PaymentResponse payment) {
        log.info("✅ PAGO APROBADO");
        log.info("💳 ID: {}", payment.getId());
        log.info("💵 Monto:  ${}", payment.getTransactionAmount());

        String whatsappId = extractWhatsappIdFromReference(payment.getExternalReference());

        if (whatsappId == null) {
            log.warn("⚠️ No se pudo extraer WhatsApp ID de:  {}", payment.getExternalReference());
            return;
        }

        Optional<Patient> patientOpt = patientRepository.findByWhatsappId(whatsappId);

        if (patientOpt. isEmpty()) {
            log.warn("⚠️ Paciente no encontrado:  {}", whatsappId);
            return;
        }

        Patient patient = patientOpt. get();

        // Idempotencia: evitar procesar dos veces
        if (Boolean.TRUE.equals(patient.getPagoProcesado())) {
            log.info("ℹ️ Pago ya procesado previamente para {}", whatsappId);
            return;
        }

        log.info("✅ Procesando pago para paciente WhatsApp: {}", whatsappId);

        // Marcar como pagado y completado
        patient.setPagoProcesado(true);
        patient.setCurrentStep(OnboardingStep. COMPLETED);
        patientRepository. save(patient);

        // Enviar mensaje de éxito usando el servicio
        String panelUrl = "https://panel.elara.com/patient/" + whatsappId;
        onboardingService.enviarMensajePagoExitoso(whatsappId, panelUrl);

        log.info("📱 Mensaje de confirmación enviado a WhatsApp: {}", whatsappId);
    }

    private void manejarPagoPendiente(PaymentResponse payment) {
        log.info("⏳ PAGO PENDIENTE");
        log.info("📋 ID: {}", payment.getId());

        String whatsappId = extractWhatsappIdFromReference(payment.getExternalReference());
    }

    private void manejarPagoRechazado(PaymentResponse payment) {
        log.warn("❌ PAGO RECHAZADO");
        log.warn("📋 ID: {}", payment.getId());

        String whatsappId = extractWhatsappIdFromReference(payment.getExternalReference());
    }

    /**
     * Extrae el WhatsApp ID de la referencia externa
     * Formato:  ELARA-CONSULTA-5215512345678-timestamp
     */
    private String extractWhatsappIdFromReference(String externalReference) {
        if (externalReference == null) {
            return null;
        }

        log.debug("🔍 Extrayendo WhatsApp ID de: {}", externalReference);

        // Formato esperado: ELARA-consultaId-5215512345678-timestamp
        String[] parts = externalReference.split("-");

        for (String part : parts) {
            // Buscar la parte que parece un teléfono mexicano (521 + 10 dígitos)
            if (part.matches("^521\\d{10}$")) {
                log.debug("✅ WhatsApp ID encontrado: {}", part);
                return part;
            }
        }

        log.warn("⚠️ WhatsApp ID no encontrado en:  {}", externalReference);
        return null;
    }

    @Data
    public static class PaymentRequest {
        private String consultaId;
        private String whatsappId;
        private String email;
    }
}