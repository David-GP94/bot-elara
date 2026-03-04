package com.bot.elara.Application.Service;

import com.bot.elara.Domain.Model.BotSession;
import com.bot.elara.Domain.Model.OnboardingStep;
import com.bot.elara.Domain.Model.Patient;
import com.bot.elara.Domain.Repository.PatientRepository;
import com.bot.elara.Infrastructure.DTO.Django.AttachStripeSessionRequest;
import com.bot.elara.Infrastructure.DTO.Django.BotCreateConsultaRequest;
import com.bot.elara.Infrastructure.DTO.Django.BotCreateConsultaResponse;
import com.bot.elara.Infrastructure.DTO.Django.BotUserResponse;
import com.bot.elara.Infrastructure.DTO.MercadoPago.PaymentLinkResult;
import com.bot.elara.Infrastructure.DTO.Stripe.StripeCheckoutResult;
import com.bot.elara.Infrastructure.External.Storage.S3Service;
import com.bot.elara.Infrastructure.External.Whatsapp.Model.Image;
import com.bot.elara.Infrastructure.External.Whatsapp.WhatsAppCloudApiClient;
import com.bot.elara.Infrastructure.Persistence.Jpa.BotSessionRepository;
import com.bot.elara.Util.DateParserUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static com.bot.elara.Domain.Constants.MessageConstants.*;


@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class OnboardingService {

    private final PatientRepository patientRepository;
    private final WhatsAppCloudApiClient whatsAppClient;
    private final S3Service s3Service;
    private final DateParserUtil dateParserUtil;
    private final InactivityReminderService inactivityReminderService;
    private final StripeService stripeService;
    private final MercadoPagoService mercadoPagoService;

    private final java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.ScheduledFuture<?>> pendingResponses = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ScheduledExecutorService imageScheduler = java.util.concurrent.Executors.newScheduledThreadPool(2);
    private final java.util.concurrent.ConcurrentHashMap<String, Object> userLocks = new java.util.concurrent.ConcurrentHashMap<>();

    private final java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.ScheduledFuture<?>> inactivityReminders = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ScheduledExecutorService reminderScheduler = java.util.concurrent.Executors.newScheduledThreadPool(2);

    private static final long INACTIVITY_TIMEOUT_MINUTES = 10; // Configurable

    // URLs reales de tus documentos (ponlas en S3 o en tu dominio)
    private static final String TERMINOS_URL = "https://tu-dominio.com/docs/terminos-y-condiciones.pdf";
    private static final String AVISO_PRIVACIDAD_URL = "https://tu-dominio.com/docs/aviso-de-privacidad.pdf";
    private static final String CONSENTIMIENTO_URL = "https://tu-dominio.com/docs/consentimiento-telemedicina.pdf";

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final BotSessionRepository botSessionRepository;

    private final DjangoIntegrationService djangoIntegrationService;
    private final S3StorageService s3StorageService;
    private final WhatsAppMediaService whatsAppMediaService;



    // Método para programar el recordatorio
    private void scheduleInactivityReminder(String whatsappId) {

        BotSession session = botSessionRepository.findByWhatsappId(whatsappId).orElse(null);

        if (session == null ||
                session.getCurrentStep() == OnboardingStep.COMPLETED ||
                session.getCurrentStep() == OnboardingStep.WELCOME) {
            return;
        }

        ScheduledFuture<?> existing = inactivityReminders.remove(whatsappId);
        if (existing != null && !existing.isDone()) {
            existing.cancel(false);
        }

        ScheduledFuture<?> future = reminderScheduler.schedule(() -> {
            inactivityReminderService.sendReminder(whatsappId);
            inactivityReminders.remove(whatsappId);
        }, INACTIVITY_TIMEOUT_MINUTES, TimeUnit.MINUTES);

        inactivityReminders.put(whatsappId, future);
    }

    // ====================== ENTRY POINTS ======================

    public void processText(String from, String text) {

        String normalizedFrom = normalize(from);

        Patient patient = getOrCreatePatient(normalizedFrom);
        BotSession session = getOrCreateSession(normalizedFrom);

        scheduleInactivityReminder(normalizedFrom);

        text = text.trim();

        // ===== INTERCEPTOR DE RECORDATORIO =====
        if (Boolean.TRUE.equals(patient.getPendingInactivityResponse())) {

            String normalizedText = text.trim();

            if (normalizedText.equalsIgnoreCase("Sí") ||
                    normalizedText.equalsIgnoreCase("Si") ||
                    normalizedText.contains("continuar")) {

                patient.setPendingInactivityResponse(false);
                save(patient);

                resendCurrentStepQuestion(session, patient);
                return;
            }

            if (normalizedText.toLowerCase().contains("cancelar")) {

                patient.setPendingInactivityResponse(false);
                save(patient);

                session.setCurrentStep(OnboardingStep.WELCOME);
                saveSession(session);

                sendText(patient.getWhatsappId(),
                        "Consulta cancelada.\n\nSi deseas iniciar nuevamente escribe *HOLA*.");

                return;
            }
        }

        // ===== INTERCEPTOR GLOBAL HOLA =====
        if (text.equalsIgnoreCase("hola")) {

            log.info("Reinicio completo de onboarding para {}", normalizedFrom);

            // 🔥 Limpiar estado de consulta anterior
            session.setConsultaId(null);
            session.setConsultaPublicId(null);
            session.setPaymentConfirmed(false);
            session.setCurrentStep(OnboardingStep.WELCOME);
            saveSession(session);

            // Limpiar flags del paciente
            patient.setPendingInactivityResponse(false);
            patient.setPagoProcesado(false);
            save(patient);

            handleWelcomeMessage(session, patient);
            return;
        }

        switch (session.getCurrentStep()) {

            case WELCOME -> handleWelcomeMessage(session, patient);
            case START -> handleStart(session, patient, text);
            case ASK_PADECIMIENTO -> handlePadecimiento(session, patient, text);
            case CONFIRM_PADECIMIENTO -> handleConfirmPadecimiento(session, patient, text);
            case ASK_EMAIL -> handleEmail(session, patient, text);
            case CONFIRM_EMAIL -> handleConfirmEmail(session, patient, text);
            case ASK_MAYORIA_EDAD -> handleMayoriaEdad(session, patient, text);
            case ASK_NOMBRE -> handleNombre(session, patient, text);
            case ASK_GENERO -> handleGenero(session, patient, text);
            case ASK_FECHA_NAC -> handleFechaNac(session, patient, text);
            case ASK_PESO -> handlePeso(session, patient, text);
            case ASK_ALTURA -> handleAltura(session, patient, text);
            case ASK_FUMA -> handleFuma(session, patient, text);
            case ASK_DESDE_CUANDO -> handleDesdeCuando(session, patient, text);
            case ASK_GRAVEDAD -> handleGravedad(session, patient, text);
            case ASK_TRATAMIENTO_ANTERIOR -> handleTratamientoAnterior(session, patient, text);
            case ASK_TRATAMIENTOS_USADOS -> handleTratamientosUsados(session, patient, text);
            case ASK_ALERGIAS -> handleAlergias(session, patient, text);
            case ASK_ALERGIAS_DETALLES -> handleAlergiasDetalles(session, patient, text);
            case ASK_MEDICAMENTOS -> handleMedicamentos(session, patient, text);
            case ASK_MEDICAMENTOS_DETALLES -> handleMedicamentosDetalles(session, patient, text);
            case ASK_ENFERMEDADES -> handleEnfermedades(session, patient, text);
            case ASK_ENFERMEDADES_DETALLES -> handleEnfermedadesDetalles(session, patient, text);
            case ASK_MEJORA_PRINCIPAL -> handleMejoraPrincipal(session, patient, text);
            case ASK_TIPO_PIEL -> handleTipoPiel(session, patient, text);
            case ASK_SENSIBILIDAD_PIEL -> handleSensibilidadPiel(session, patient, text);
            case ASK_EXPOSICION_SOL -> handleExposicionSol(session, patient, text);
            case ASK_USA_PROTECTOR -> handleUsaProtector(session, patient, text);
            case ASK_AREA_CAIDA -> handleAreaCaida(session, patient, text);
            case ASK_ANTECEDENTES_FAMILIA -> handleAntecedentesFamilia(session, patient, text);
            case ASK_STATUS_EMBARAZO -> handleStatusEmbarazo(session, patient, text);
            case ASK_NOTAS_ADICIONALES -> handleNotasAdicionales(session, patient, text);
            case ASK_NOTAS_ADICIONALES_DETALLES -> handleNotasAdicionalesDetalles(session, patient, text);
            case ASK_FOTOS -> handleFotos(session, patient, text);
            case ASK_MAS_FOTOS -> handleMasFotos(session, patient, text);
            case ASK_EXCESO_FOTOS -> handleExcesoFotos(session, patient, text);
            case ASK_TERMINOS -> handleTerminos(session, patient, text);
            case ASK_AVISO_PRIVACIDAD -> handleAvisoPrivacidad(session, patient, text);
            case ASK_CONSENTIMIENTO -> handleConsentimiento(session, patient, text);
            case ASK_METODO_PAGO -> handleMetodoPago(session, patient, text);
            case ASK_CODIGO_DESCUENTO -> handleCodigoDescuento(session, patient, text);
            case ASK_INGRESAR_CODIGO -> handleIngresarCodigo(session, patient, text);
            case PROCESS_PAYMENT -> handlePayment(session, patient, text);
            case COMPLETED -> sendText(normalizedFrom, "Tu consulta ya está completada.");
            default -> sendText(normalizedFrom, "Escribe *HOLA* para reiniciar.");
        }
    }



    /**
     * Reenvía la pregunta del paso actual cuando el usuario retoma el flujo
     */
    private void resendCurrentStepQuestion(BotSession session, Patient p) {
        String from = p.getWhatsappId();

        switch (session.getCurrentStep()) {
            case ASK_PADECIMIENTO ->
                    askWithList(session, p, OnboardingStep.ASK_PADECIMIENTO, M_1, "Ver opciones", M_1_OPTIONS, "motivo");
            case CONFIRM_PADECIMIENTO ->
                    askWithList(session, p, OnboardingStep.ASK_PADECIMIENTO, M_1, "Ver opciones", M_1_OPTIONS, "motivo");
            case ASK_EMAIL -> askWithText(session, p, OnboardingStep.ASK_EMAIL, M_2);
            case CONFIRM_EMAIL -> askWithText(session, p, OnboardingStep.ASK_EMAIL, M_2);
            case ASK_MAYORIA_EDAD -> askWithButtons(session, p, OnboardingStep.ASK_MAYORIA_EDAD, M_4, M_4_OPTIONS);
            case ASK_NOMBRE ->
                    askWithText(session, p, OnboardingStep.ASK_NOMBRE, p.getConsultaParaOtraPersona() ? "¿Cuál es el nombre completo de la persona para quien es la consulta?" : M_5);
            case ASK_GENERO -> askWithButtons(session, p, OnboardingStep.ASK_GENERO, M_6, M_6_OPTIONS);
            case ASK_FECHA_NAC -> askWithText(session, p, OnboardingStep.ASK_FECHA_NAC, M_7);
            case ASK_PESO -> askWithText(session, p, OnboardingStep.ASK_PESO, M_8);
            case ASK_ALTURA -> askWithText(session, p, OnboardingStep.ASK_ALTURA, M_9);
            case ASK_FUMA -> askWithButtons(session, p, OnboardingStep.ASK_FUMA, M_10, M_10_OPTIONS);
            case ASK_DESDE_CUANDO ->
                    askWithList(session, p, OnboardingStep.ASK_DESDE_CUANDO, M_11, "Elegir tiempo", M_11_OPTIONS, "desde_cuando");
            case ASK_GRAVEDAD -> {
                var options = getGravedadOptions(p.getPadecimiento());
                if (options.size() > 3) {
                    askWithList(session, p, OnboardingStep.ASK_GRAVEDAD, getGravedadMessage(p.getPadecimiento()), "👉Seleccionar opción", options, "gravedad_" + p.getPadecimiento().toLowerCase().replace(" ", "_"));
                } else {
                    askWithButtons(session, p, OnboardingStep.ASK_GRAVEDAD, getGravedadMessage(p.getPadecimiento()), options);
                }
            }
            case ASK_TRATAMIENTO_ANTERIOR ->
                    askWithButtons(session, p, OnboardingStep.ASK_TRATAMIENTO_ANTERIOR, M_13, M_13_OPTIONS);
            case ASK_TRATAMIENTOS_USADOS -> askWithText(session, p, OnboardingStep.ASK_TRATAMIENTOS_USADOS, M_14);
            case ASK_ALERGIAS -> askWithButtons(session, p, OnboardingStep.ASK_ALERGIAS, M_15, M_15_OPTIONS);
            case ASK_ALERGIAS_DETALLES -> askWithText(session, p, OnboardingStep.ASK_ALERGIAS_DETALLES, M_16);
            case ASK_MEDICAMENTOS -> askWithButtons(session,p, OnboardingStep.ASK_MEDICAMENTOS, M_17, M_17_OPTIONS);
            case ASK_MEDICAMENTOS_DETALLES -> askWithText(session, p, OnboardingStep.ASK_MEDICAMENTOS_DETALLES, M_18);
            case ASK_ENFERMEDADES -> askWithButtons(session, p, OnboardingStep.ASK_ENFERMEDADES, M_29, M_29_OPTIONS);
            case ASK_ENFERMEDADES_DETALLES -> askWithText(session, p, OnboardingStep.ASK_ENFERMEDADES_DETALLES, M_43);
            case ASK_MEJORA_PRINCIPAL ->
                    askWithList(session, p, OnboardingStep.ASK_MEJORA_PRINCIPAL, M_26, "Elegir mejora", M_26_OPTIONS, "mejora_principal");
            case ASK_TIPO_PIEL ->
                    askWithList(session, p, OnboardingStep.ASK_TIPO_PIEL, M_27, "👉Seleccionar tipo", M_27_OPTIONS, "tipo_piel");
            case ASK_SENSIBILIDAD_PIEL ->
                    askWithList(session, p, OnboardingStep.ASK_SENSIBILIDAD_PIEL, M_28, "👉Seleccionar opción", M_28_OPTIONS, "sensibilidad_piel");
            case ASK_EXPOSICION_SOL ->
                    askWithList(session, p, OnboardingStep.ASK_EXPOSICION_SOL, M_30, "👉Seleccionar opción", M_30_OPTIONS, "exposicion_sol");
            case ASK_USA_PROTECTOR ->
                    askWithList(session, p, OnboardingStep.ASK_USA_PROTECTOR, M_31, "👉Seleccionar opción", M_31_OPTIONS, "uso-protector");
            case ASK_AREA_CAIDA -> askWithButtons(session, p, OnboardingStep.ASK_AREA_CAIDA, M_34, M_34_OPTIONS);
            case ASK_ANTECEDENTES_FAMILIA ->
                    askWithList(session, p, OnboardingStep.ASK_ANTECEDENTES_FAMILIA, M_35, "👉Seleccionar opción", M_35_OPTIONS, "antecedentes_familia");
            case ASK_STATUS_EMBARAZO ->
                    askWithList(session, p, OnboardingStep.ASK_STATUS_EMBARAZO, M_33, "👉Seleccionar opción", M_33_OPTIONS, "status_embarazo");
            case ASK_NOTAS_ADICIONALES ->
                    askWithButtons(session, p, OnboardingStep.ASK_NOTAS_ADICIONALES, getNotasMessage(p.getPadecimiento()), M_15_OPTIONS);
            case ASK_NOTAS_ADICIONALES_DETALLES -> askWithText(session, p, OnboardingStep.ASK_NOTAS_ADICIONALES_DETALLES, M_44);
            case ASK_FOTOS -> askWithButtons(session, p, OnboardingStep.ASK_FOTOS, M_20, M_20_OPTIONS);
            case ASK_EXCESO_FOTOS ->
                    askWithButtons(session, p, OnboardingStep.ASK_EXCESO_FOTOS, M_EXCESO_FOTOS, M_EXCESO_FOTOS_OPTIONS);
            case ASK_MAS_FOTOS -> sendText(from, M_21);
            case PROCESS_PAYMENT -> {
                // Reenviamos el enlace del método que eligió
                if (p.getMetodoPagoElegido() != null && p.getMetodoPagoElegido() == 1) {
                    goToStripePayment(session,p);
                } else if (p.getMetodoPagoElegido() != null && p.getMetodoPagoElegido() == 2) {
                    goToPaymentMercadoPago(session, p);
                } else {
                    // Seguridad: si por algún motivo no sabe el método, volvemos al menú
                    askWithListSection(
                            session,
                            p,
                            OnboardingStep.ASK_METODO_PAGO,
                            "⏰ ¡Hola de nuevo!\n\nEstábamos eligiendo el método de pago.\n\n¿En cuál prefieres pagar?",
                            "Elegir método de pago",
                            "Seleccionar",
                            M_METODO_PAGO_OPTIONS,
                            "metodo_pago"
                    );
                }
            }
            case ASK_METODO_PAGO -> {
                askWithListSection(
                        session,
                        p,
                        OnboardingStep.ASK_METODO_PAGO,
                        "¡Perfecto! Ya tenemos tus fotos \n\nElige tu método de pago preferido:",
                        "Elegir método de pago",
                        "Seleccionar",
                        M_METODO_PAGO_OPTIONS,
                        "metodo_pago"
                );
            }
            case ASK_CODIGO_DESCUENTO ->
                    askWithButtons(session, p, OnboardingStep.ASK_CODIGO_DESCUENTO, M_CODIGO_DESCUENTO, M_CODIGO_DESCUENTO_OPTIONS);

            case ASK_INGRESAR_CODIGO ->
                    askWithText(session, p, OnboardingStep.ASK_INGRESAR_CODIGO, M_INGRESAR_CODIGO);
            default -> sendText(from, "Continuemos donde te quedaste. ¿En qué puedo ayudarte?");
        }
    }


    public void processImage(String from, Image image) {
        String normalizedFrom = normalize(from);
        Object lock = userLocks.computeIfAbsent(normalizedFrom, k -> new Object());

        synchronized (lock) {
            processImageSync(normalizedFrom, image);
        }
    }

    private void processImageSync(String from, Image image) {

        // 🔹 1. Cargar sesión (flujo)
        BotSession session = getOrCreateSession(from);

        // 🔹 2. Cargar paciente (datos clínicos)
        Patient p = patientRepository.findByWhatsappId(from)
                .orElseGet(() -> getOrCreatePatient(from));

        OnboardingStep currentStep = session.getCurrentStep();

        // 🔹 3. Si ya está en exceso de fotos → ignorar
        if (currentStep == OnboardingStep.ASK_EXCESO_FOTOS) {
            log.info("Imagen ignorada para {} - esperando respuesta de exceso de fotos", from);
            return;
        }

        // 🔹 4. Validar paso correcto
        if (currentStep != OnboardingStep.ASK_MAS_FOTOS) {
            log.warn("Imagen recibida fuera del flujo. Paso actual: {}", currentStep);
            sendText(from, "⚠️ No se esperaban imágenes en este momento. Por favor sigue el flujo actual.");
            return;
        }

        // 🔹 5. Validar límite actual
        int currentCount = p.getPhotoUrls() != null ? p.getPhotoUrls().size() : 0;

        if (currentCount >= 5) {
            log.info("Imagen ignorada para {} - ya tiene {} fotos", from, currentCount);

            ScheduledFuture<?> existing = pendingResponses.remove(from);
            if (existing != null && !existing.isDone()) {
                existing.cancel(false);
            }

            // 🔥 Transición de flujo vive en session
            session.setCurrentStep(OnboardingStep.ASK_EXCESO_FOTOS);
            saveSession(session);

            sendButtons(
                    p,
                    M_EXCESO_FOTOS,
                    M_EXCESO_FOTOS_OPTIONS
            );

            return;
        }

        // 🔹 6. Guardar imagen
        LocalDateTime now = LocalDateTime.now();
        byte[] imageBytes = whatsAppMediaService.downloadMedia(image.getId());

        String s3Url = s3StorageService.upload(
                imageBytes,
                image.getMimeType(),
                from
        );

        p.getPhotoUrls().add(s3Url);
        p.setLastImageReceivedAt(now);

        patientRepository.save(p);
        patientRepository.flush();

        int newCount = p.getPhotoUrls().size();

        log.info("Imagen {} de 5 recibida para {} → {}", newCount, from, s3Url);

        // 🔹 7. Si alcanzó límite → avanzar flujo
        if (newCount >= 5) {

            ScheduledFuture<?> existing = pendingResponses.remove(from);
            if (existing != null && !existing.isDone()) {
                existing.cancel(false);
            }

            session.setCurrentStep(OnboardingStep.ASK_EXCESO_FOTOS);
            saveSession(session);

            sendButtons(
                    p,
                    "✅ 5 imagen(es) guardada(s) correctamente.\n📊 Total: 5 de 5\n\n⚠️ Has alcanzado el límite máximo.",
                    M_EXCESO_FOTOS_OPTIONS
            );

            return;
        }

        // 🔹 8. Programar respuesta diferida
        scheduleImageResponse(from);
    }



    private void scheduleImageResponse(String whatsappId) {
        // Cancelar tarea pendiente anterior (si existe)
        java.util.concurrent.ScheduledFuture<?> existing = pendingResponses.get(whatsappId);
        if (existing != null && !existing.isDone()) {
            existing.cancel(false);
            log.info("Tarea anterior cancelada para {}", whatsappId);
        }

        // Programar nueva tarea con delay de 4 segundos
        java.util.concurrent.ScheduledFuture<?> future = imageScheduler.schedule(() -> {
            sendDelayedImageResponse(whatsappId);
        }, 4, java.util.concurrent.TimeUnit.SECONDS);

        pendingResponses.put(whatsappId, future);
    }

    @Transactional
    public void sendDelayedImageResponse(String whatsappId) {
        try {
            Patient p = patientRepository.findByWhatsappId(whatsappId).orElse(null);
            BotSession session = botSessionRepository.findByWhatsappId(whatsappId).orElse(null);

            if (p == null || session == null ||
                session.getCurrentStep() != OnboardingStep.ASK_MAS_FOTOS) return;

            int totalFotos = p.getPhotoUrls().size();

            // Si ya pasó a pago, no hacer nada
            if (totalFotos >= 5) {
                pendingResponses.remove(whatsappId);
                return;
            }

            String mensaje = String.format("✅ %d imagen(es) guardada(s) correctamente.\n📊 Total: %d de 5",
                    totalFotos, totalFotos);

            sendText(whatsappId, mensaje);
            askWithButtons(session, p, OnboardingStep.ASK_MAS_FOTOS, "¿Deseas cargar más imágenes?", M_22_OPTIONS);

            pendingResponses.remove(whatsappId);
        } catch (Exception e) {
            log.error("Error en respuesta diferida de imágenes para {}", whatsappId, e);
        }
    }


    // ====================== TODOS LOS HANDLERS ======================

    public void handleWelcomeMessage(BotSession session,Patient p) {
        session.setCurrentStep(OnboardingStep.START);
        saveSession(session);
        whatsAppClient.sendWelcomeImage(p.getWhatsappId(), M_WELCOME);
        sendText(p.getWhatsappId(), M_TERMINOS);
        askWithButtons(session, p, OnboardingStep.START, M_ACCEPT_TERMINOS, M_TERMINOS_OPTIONS);
    }

    private void handleStart(BotSession session,Patient p, String text) {
        String selected = getSelectedOption(text, M_TERMINOS_OPTIONS);
        log.info("Opcion de aceptar terminos y condiciones: " + selected);
        if (selected == null) {
            invalidOption(p);
            return;
        }
        if ("No".equals(selected)) {
            sendText(p.getWhatsappId(), M_NO_ACCEPT_TERMINOS);
            askWithButtons(session, p, OnboardingStep.START, M_ACCEPT_TERMINOS, M_TERMINOS_OPTIONS);
            return;
        }
        askWithList(session, p, OnboardingStep.ASK_PADECIMIENTO, M_1, "Ver opciones", M_1_OPTIONS, "motivo");
    }

    private void handlePadecimiento(BotSession session,Patient p, String text) {
        String selected = getSelectedOption(text, M_1_OPTIONS);
        if (selected == null) {
            invalidOption(p);
            return;
        }
        p.setPadecimiento(mapPadecimiento(selected));
        save(p);

        session.setCurrentStep(OnboardingStep.CONFIRM_PADECIMIENTO);
        saveSession(session);
        sendText(p.getWhatsappId(), M_42 + selected + "\n\n¿Es correcto?");
        sendButtons(p, "¿Confirmas el motivo de tu consulta?", List.of("👍Sí", "👎No"));
    }

    private String mapPadecimiento(String selectedText) {
        String lower = selectedText.toLowerCase()
                .replace("á", "a")
                .replace("é", "e")
                .replace("í", "i")
                .replace("ó", "o")
                .replace("ú", "u");

        if (lower.contains("acne")) return "Acne";
        if (lower.contains("caida") || lower.contains("caída") || lower.contains("pelo")) return "Caida de Pelo";
        if (lower.contains("anti-edad") || lower.contains("skincare")) return "Anti-edad";
        if (lower.contains("rosacea") || lower.contains("rosácea")) return "Rosacea";
        if (lower.contains("manchas")) return "Manchas";
        if (lower.contains("dermatitis")) return "Dermatitis";
        return "Otros";
    }

    private void handleConfirmPadecimiento(BotSession session,Patient p, String text) {
        String selected = getSelectedOption(text, List.of("Sí", "No"));

        if (selected == null) {
            invalidOption(p);
            return;
        }

        if (!selected.equalsIgnoreCase("Sí")) {
            askWithList(session, p, OnboardingStep.ASK_PADECIMIENTO, M_1, "Ver opciones", M_1_OPTIONS, "motivo");
            return;
        }

        askWithText(session, p, OnboardingStep.ASK_EMAIL, M_2);

    }

    private void handleEmail(BotSession session,Patient p, String text) {
        if (!text.matches("^[\\w-\\.]+@([\\w-]+\\.)+[\\w-]{2,4}$")) {
            sendText(p.getWhatsappId(), "Por favor ingresa un correo válido (ejemplo: nombre@dominio.com), NOTA: sin espacios, ni acentos.");
            return;
        }
        p.setEmail(text.trim().toLowerCase());
        session.setCurrentStep(OnboardingStep.CONFIRM_EMAIL);
        save(p);
        sendText(p.getWhatsappId(), M_3 + "\n\n" + text.trim().toLowerCase() + "\n\n¿Es correcto?");
        sendButtons(p, "¿Confirmas tu correo?", List.of("👍Sí", "👎No"));
    }

    private void handleConfirmEmail(BotSession session,Patient p, String text) {
        String selected = getSelectedOption(text, M_10_OPTIONS);
        if (selected == null) {
            invalidOption(p);
            return;
        }

        if (!selected.equalsIgnoreCase("Sí")) {
            askWithText(session, p, OnboardingStep.ASK_EMAIL, M_2);
            return;
        }
        askWithButtons(session, p, OnboardingStep.ASK_MAYORIA_EDAD, M_4, M_4_OPTIONS);
    }

    private void handleMayoriaEdad(BotSession session,Patient p, String text) {
        String selected = getSelectedOption(text, M_4_OPTIONS);

        log.info("Opción seleccionada en mayoría de edad: '{}' → mapeado a: '{}'", text, selected);

        // Si no se reconoce la opción
        if (selected == null) {
            sendText(p.getWhatsappId(), "Por favor elige una opción válida.");
            askWithButtons(session, p, OnboardingStep.ASK_MAYORIA_EDAD, M_4, M_4_OPTIONS);
            return;
        }

        // Si es menor de edad
        if (selected.equals("Menor de edad")) {
            sendText(p.getWhatsappId(), "Lo sentimos, debes ser mayor de 18 años o estar autorizado para continuar.\nConsulta terminada.");
            session.setCurrentStep(OnboardingStep.WELCOME);
            saveSession(session);
            return;
        }

        // Si es válido
        boolean paraOtraPersona = selected.equals("Para otra persona.");
        p.setConsultaParaOtraPersona(paraOtraPersona);

        String msg = paraOtraPersona
                ? "¿Cuál es el nombre completo de la persona para quien es la consulta?"
                : M_5;

        askWithText(session, p, OnboardingStep.ASK_NOMBRE, msg);
    }

    private void handleNombre(BotSession session,Patient p, String text) {
        p.setNombreCompleto(text.trim());
        askWithButtons(session, p, OnboardingStep.ASK_GENERO, M_6, M_6_OPTIONS);
    }

    private void handleGenero(BotSession session,Patient p, String text) {
        String selected = getSelectedOption(text, M_6_OPTIONS);
        if (selected == null) {
            invalidOption(p);
            return;
        }
        p.setGenero(selected);
        askWithText(session, p, OnboardingStep.ASK_FECHA_NAC, M_7);
    }

    private void handleFechaNac(BotSession session,Patient p, String text) {
        try {
            LocalDate fecha = dateParserUtil.parseFechaNacimiento(text.trim());

            log.info("Fecha de nacimiento parseada para {}: {}", p.getWhatsappId(), fecha);

            if (fecha == null || fecha.isAfter(LocalDate.now())) {
                sendText(p.getWhatsappId(), """
                        No pude entender la fecha 😅
                        Por favor escríbela de alguna de estas formas:
                        • 15/03/1990
                        • 15-03-1990
                        • 15031990
                        • 15 de marzo de 1990
                        • 15 marzo 1990""");
                return;
            }

            // Validación: debe ser mayor de 18 años (tu bloque original, sin tocar)
            if (fecha.isAfter(LocalDate.now().minusYears(18)) && !p.getConsultaParaOtraPersona()) {
                sendText(p.getWhatsappId(), "Lo sentimos, para continuar con la consulta dermatológica debes ser mayor de 18 años.");
                session.setCurrentStep(OnboardingStep.WELCOME);
                saveSession(session);
                return;
            }
            p.setFechaNacimiento(fecha);
            askWithText(session, p, OnboardingStep.ASK_PESO, M_8);

        } catch (Exception e) {
            log.error("Error inesperado procesando fecha de nacimiento para paciente {} - texto recibido: '{}'",
                    p.getWhatsappId(), text, e);
            sendText(p.getWhatsappId(), """
                    No pude entender la fecha 😅
                    Por favor escríbela de alguna de estas formas:
                    • 15/03/1990
                    • 15-03-1990
                    • 15031990
                    • 15 de marzo de 1990
                    • 15 marzo 1990""");
        }
    }

    private void handlePeso(BotSession session,Patient p, String text) {
        try {
            String cleaned = text.trim()
                    .toLowerCase()
                    .replaceAll("[^0-9,\\.]+", "")  // quita letras, "kg", espacios, etc.
                    .replace(",", ".");             // convierte coma mexicana a punto

            if (cleaned.isEmpty()) {
                throw new NumberFormatException("Sin números");
            }

            double peso = Double.parseDouble(cleaned);

            // Validación de rango realista
            if (peso < 20.0 || peso > 300.0) {
                sendText(p.getWhatsappId(), """
                        El peso debe estar entre 20 y 300 kg
                        Por favor ingresa un valor realista (ej. 70 o 70.5)""");
                return;
            }

            p.setPesoKg(peso);
            log.info("Peso registrado para {}: {} kg", p.getWhatsappId(), peso);
            askWithText(session, p, OnboardingStep.ASK_ALTURA, M_9);

        } catch (Exception e) {
            log.info("Peso no válido para {}: '{}'", p.getWhatsappId(), text);
            sendText(p.getWhatsappId(), """
                    No entendí el peso
                    Por favor escribe solo el número:
                    • 70
                    • 70.5
                    • 70,5 (también funciona)
                    Ejemplos válidos: 65, 72.3, 80.5""");
        }
    }

    private void handleAltura(BotSession session,Patient p, String text) {
        try {
            double altura = Double.parseDouble(text.trim().replace(",", "."));
            if (altura > 2.5) {
                sendText(p.getWhatsappId(), "Ingresa una altura realista (ej. 1.70)");
                return;
            }
            p.setAlturaM(altura);
            askWithButtons(session, p, OnboardingStep.ASK_FUMA, M_10, M_10_OPTIONS);
        } catch (Exception e) {
            sendText(p.getWhatsappId(), "Ingresa un número válido (ej. 1.70)");
        }
    }

    private void handleFuma(BotSession session,Patient p, String text) {
        String selected = getSelectedOption(text, M_10_OPTIONS);
        if (selected == null) {
            invalidOption(p);
            return;
        }
        p.setFuma("Sí".equalsIgnoreCase(selected));
        save(p);
        String padecimiento = p.getPadecimiento();
        if (padecimiento != null && padecimiento.toLowerCase().contains("anti-edad".toLowerCase())) {
            askWithList(session, p, OnboardingStep.ASK_MEJORA_PRINCIPAL, M_26, "Elegir mejora", M_26_OPTIONS, "mejora_principal");
        } else {
            askWithList(session, p, OnboardingStep.ASK_DESDE_CUANDO, M_11, "Elegir tiempo", M_11_OPTIONS, "desde_cuando");
        }
    }

    private void handleDesdeCuando(BotSession session,Patient p, String text) {
        String selected = getSelectedOption(text, M_11_OPTIONS);
        if (selected == null) {
            invalidOption(p);
            return;
        }
        p.setDesdeCuando(selected);
        session.setCurrentStep(nextStepAfterDesdeCuando(p.getPadecimiento()));
        save(p);
        sendNextQuestionAfterDesdeCuando(session, p);
    }

    private void sendNextQuestionAfterDesdeCuando(BotSession session,Patient p) {
        switch (session.getCurrentStep()) {
            case ASK_GRAVEDAD -> {
                var options = getGravedadOptions(p.getPadecimiento());
                if (options.size() > 3) {
                    askWithList(session, p, OnboardingStep.ASK_GRAVEDAD,
                            getGravedadMessage(p.getPadecimiento()),
                            "👉Seleccionar opción",
                            options,
                            "gravedad_" + p.getPadecimiento().toLowerCase().replace(" ", "_"));
                } else {
                    askWithButtons(session, p, OnboardingStep.ASK_GRAVEDAD,
                            getGravedadMessage(p.getPadecimiento()), options);
                }
            }
            case ASK_MEJORA_PRINCIPAL -> {
                // 4 opciones → también lista
                askWithList(session, p, OnboardingStep.ASK_MEJORA_PRINCIPAL,
                        M_26, "Elegir mejora", M_26_OPTIONS, "mejora_principal");
            }
            case ASK_AREA_CAIDA -> {
                // Solo 3 → botones están bien
                askWithButtons(session, p, OnboardingStep.ASK_AREA_CAIDA, M_34, M_34_OPTIONS);
            }
            default -> askWithButtons(session, p, OnboardingStep.ASK_TRATAMIENTO_ANTERIOR, M_13, M_13_OPTIONS);
        }
    }

    private void handleGravedad(BotSession session,Patient p, String text) {
        var options = getGravedadOptions(p.getPadecimiento());
        String selected = getSelectedOption(text, options);
        if (selected == null) {
            invalidOption(p);
            return;
        }

        switch (p.getPadecimiento()) {
            case "Acne" -> p.setGravedadAcne(selected);
            case "Manchas" -> p.setGravedadManchas(selected);
            case "Rosacea" -> p.setGravedadRosacea(selected);
        }
        askWithButtons(session, p, OnboardingStep.ASK_TRATAMIENTO_ANTERIOR, M_13, M_13_OPTIONS);
    }

    private void handleTratamientoAnterior(BotSession session,Patient p, String text) {
        String selected = getSelectedOption(text, M_13_OPTIONS);
        if (selected == null) {
            invalidOption(p);
            return;
        }
        p.setTratamientoAnterior("Sí".equalsIgnoreCase(selected));
        if (p.getTratamientoAnterior()) {
            askWithText(session, p, OnboardingStep.ASK_TRATAMIENTOS_USADOS, M_14);
        } else {
            askWithButtons(session, p, OnboardingStep.ASK_ALERGIAS, M_15, M_15_OPTIONS);
        }
    }

    private void handleTratamientosUsados(BotSession session,Patient p, String text) {
        p.setTratamientosUsados(text.trim());
        askWithButtons(session, p, OnboardingStep.ASK_ALERGIAS, M_15, M_15_OPTIONS);
    }

    private void handleAlergias(BotSession session,Patient p, String text) {
        String selected = getSelectedOption(text, M_15_OPTIONS);
        if (selected == null) {
            invalidOption(p);
            return;
        }
        p.setAlergias("Sí".equalsIgnoreCase(selected));
        if (p.getAlergias()) {
            askWithText(session, p, OnboardingStep.ASK_ALERGIAS_DETALLES, M_16);
        } else {
            askWithButtons(session, p, OnboardingStep.ASK_MEDICAMENTOS, M_17, M_17_OPTIONS);
        }
    }

    private void handleAlergiasDetalles(BotSession session,Patient p, String text) {
        p.setAlergiasDetalles(text.trim());
        askWithButtons(session, p, OnboardingStep.ASK_MEDICAMENTOS, M_17, M_17_OPTIONS);
    }

    private void handleMedicamentos(BotSession session,Patient p, String text) {
        String selected = getSelectedOption(text, M_17_OPTIONS);
        if (selected == null) {
            invalidOption(p);
            return;
        }
        p.setMedicamentos("Sí".equalsIgnoreCase(selected));
        if (p.getMedicamentos()) {
            askWithText(session, p, OnboardingStep.ASK_MEDICAMENTOS_DETALLES, M_18);
        } else {
            askWithButtons(session, p, OnboardingStep.ASK_ENFERMEDADES, M_29, M_29_OPTIONS);
        }
    }

    private void handleMedicamentosDetalles(BotSession session,Patient p, String text) {
        p.setMedicamentosDetalles(text.trim());
        askWithButtons(session, p, OnboardingStep.ASK_ENFERMEDADES, M_29, M_29_OPTIONS);
    }

    private void handleEnfermedades(BotSession session,Patient p, String text) {
        String selected = getSelectedOption(text, M_29_OPTIONS);
        if (selected == null) {
            invalidOption(p);
            return;
        }
        p.setEnfermedades("Sí".equalsIgnoreCase(selected));
        if (p.getEnfermedades()) {
            askWithText(session, p, OnboardingStep.ASK_ENFERMEDADES_DETALLES, M_43);
        } else {
            goToNextAfterEnfermedades(session, p);
        }
    }

    private void handleEnfermedadesDetalles(BotSession session,Patient p, String text) {
        p.setEnfermedadesDetalles(text.trim());
        goToNextAfterEnfermedades(session, p);
    }

    private void goToNextAfterEnfermedades(BotSession session,Patient p) {
        if ("Femenino".equals(p.getGenero())) {
            askWithList(
                    session,
                    p,
                    OnboardingStep.ASK_STATUS_EMBARAZO,
                    M_33,
                    "👉Seleccionar opción",
                    M_33_OPTIONS,
                    "status_embarazo"   // ← clave única para el mapeo
            );
        } else {
            goToNotasAdicionales(session, p);
        }
    }

    // ==================== RAMAS ESPECÍFICAS ====================

    private void handleMejoraPrincipal(BotSession session,Patient p, String text) {
        String selected = getSelectedOption(text, M_26_OPTIONS);
        if (selected == null) {
            invalidOption(p);
            return;
        }
        p.setMejoraPrincipal(selected);
        askWithList(
                session,
                p,
                OnboardingStep.ASK_TIPO_PIEL,
                M_27,
                "👉Seleccionar tipo",
                M_27_OPTIONS,
                "tipo_piel"
        );
    }

    private void handleTipoPiel(BotSession session,Patient p, String text) {
        String selected = getSelectedOption(text, M_27_OPTIONS);
        if (selected == null) {
            invalidOption(p);
            return;
        }
        p.setTipoPiel(selected);
        askWithList(
                session,
                p,
                OnboardingStep.ASK_SENSIBILIDAD_PIEL,
                M_28,
                "👉Seleccionar opción",
                M_28_OPTIONS,
                "sensibilidad_piel"
        );

    }

    private void handleSensibilidadPiel(BotSession session,Patient p, String text) {
        String selected = getSelectedOption(text, M_28_OPTIONS);
        if (selected == null) {
            invalidOption(p);
            return;
        }
        p.setSensibilidadPiel(selected);
        askWithList(
                session,
                p,
                OnboardingStep.ASK_EXPOSICION_SOL,
                M_30,
                "👉Seleccionar opción",
                M_30_OPTIONS,
                "exposicion_sol"
        );
    }

    private void handleExposicionSol(BotSession session,Patient p, String text) {
        String selected = getSelectedOption(text, M_30_OPTIONS);
        if (selected == null) {
            invalidOption(p);
            return;
        }
        p.setExposicionSol(selected);
        askWithList(
                session,
                p,
                OnboardingStep.ASK_USA_PROTECTOR,
                M_31,
                "👉Seleccionar opción",
                M_31_OPTIONS,
                "uso-protector"
        );
    }

    private void handleUsaProtector(BotSession session,Patient p, String text) {
        String selected = getSelectedOption(text, M_31_OPTIONS);
        if (selected == null) {
            invalidOption(p);
            return;
        }
        p.setUsaProtector(selected);
        askWithButtons(session, p, OnboardingStep.ASK_ALERGIAS, M_15, M_15_OPTIONS);
    }

    private void handleAreaCaida(BotSession session,Patient p, String text) {
        String selected = getSelectedOption(text, M_34_OPTIONS);
        if (selected == null) {
            invalidOption(p);
            return;
        }
        p.setAreaCaida(selected);
        askWithList(
                session,
                p,
                OnboardingStep.ASK_ANTECEDENTES_FAMILIA,
                M_35,
                "👉Seleccionar opción",
                M_35_OPTIONS,
                "antecedentes_familia"
        );
    }

    private void handleAntecedentesFamilia(BotSession session,Patient p, String text) {
        p.setAntecedentesFamiliares(text.trim());
        askWithButtons(session, p, OnboardingStep.ASK_ALERGIAS, M_15, M_15_OPTIONS);
    }

    private void handleStatusEmbarazo(BotSession session,Patient p, String text) {
        String selected = getSelectedOption(text, M_33_OPTIONS);
        if (selected == null) {
            invalidOption(p);
            return;
        }
        p.setStatusEmbarazo(selected);
        goToNotasAdicionales(session, p);
    }

    private void goToNotasAdicionales(BotSession session,Patient p) {
        askWithButtons(session, p, OnboardingStep.ASK_NOTAS_ADICIONALES, getNotasMessage(p.getPadecimiento()), M_15_OPTIONS);
    }

    private void handleNotasAdicionales(BotSession session,Patient p, String text) {
        String selected = getSelectedOption(text, M_15_OPTIONS);
        if (selected == null) {
            invalidOption(p);
            return;
        }
        if ("Sí".equalsIgnoreCase(selected)) {
            askWithText(session, p, OnboardingStep.ASK_NOTAS_ADICIONALES_DETALLES, M_44);
            return;
        }
        askWithButtons(session, p, OnboardingStep.ASK_FOTOS, M_20, M_20_OPTIONS);
    }

    private void handleNotasAdicionalesDetalles(BotSession session,Patient p, String text) {
        p.setNotasAdicionales(text.trim());
        askWithButtons(session, p, OnboardingStep.ASK_FOTOS, M_20, M_20_OPTIONS);
    }

    private void handleFotos(BotSession session,Patient p, String text) {
        String selected = getSelectedOption(text, M_20_OPTIONS);
        if (selected == null) {
            invalidOption(p);
            return;
        }
        if ("Cargar ahora".equalsIgnoreCase(selected)) {
            session.setCurrentStep(OnboardingStep.ASK_MAS_FOTOS);
            saveSession(session);
            sendText(p.getWhatsappId(), M_21);
        } else {
            askWithListSection(
                    session,
                    p,
                    OnboardingStep.ASK_METODO_PAGO,
                    "¡Perfecto! Ya tenemos todo listo \n\nElige tu método de pago preferido:",
                    "Elegir método de pago",
                    "Seleccionar",
                    M_METODO_PAGO_OPTIONS,
                    "metodo_pago"
            );
        }
    }

    private void handleMasFotos(BotSession session,Patient p, String text) {
        String selected = getSelectedOption(text, M_22_OPTIONS);
        if (selected == null) {
            invalidOption(p);
            return;
        }
        if ("Sí".equalsIgnoreCase(selected)) {
            sendText(p.getWhatsappId(), M_21);
        } else {
            askWithListSection(
                    session,
                    p,
                    OnboardingStep.ASK_METODO_PAGO,
                    "¡Perfecto! Ya tenemos todo listo \n\nElige tu método de pago preferido:",
                    "Elegir método de pago",
                    "Seleccionar",
                    M_METODO_PAGO_OPTIONS,
                    "metodo_pago"
            );
        }
    }

    private void handleExcesoFotos(BotSession session,Patient p, String text) {
        String selected = getSelectedOption(text, M_EXCESO_FOTOS_OPTIONS);
        if (selected == null) {
            invalidOption(p);
            return;
        }

        if (selected.equalsIgnoreCase("Continuar")) {
            askWithListSection(
                    session,
                    p,
                    OnboardingStep.ASK_METODO_PAGO,
                    "¡Excelente! Ya guardamos tus 5 fotos \n\nElige tu método de pago preferido:",
                    "Elegir método de pago",
                    "Seleccionar",
                    M_METODO_PAGO_OPTIONS,
                    "metodo_pago"
            );
        } else {
            p.getPhotoUrls().clear();
            p.setLastImageReceivedAt(null);
            session.setCurrentStep(OnboardingStep.ASK_MAS_FOTOS);
            saveSession(session);
            sendText(p.getWhatsappId(), "🔄 Se han eliminado todas las fotos.\n\n" + M_21);
        }
    }

    private void handleMetodoPago(BotSession session,Patient p, String text) {
        String selected = getSelectedOption(text, M_METODO_PAGO_OPTIONS);
        log.info("Seleccion de metodo de pago: "+selected);
        if (selected == null) {
            invalidOption(p);
            return;
        }

        // Guardamos el método elegido
        if (selected.contains("Stripe")) {
            p.setMetodoPagoElegido(1); // 1 = Stripe
        } else {
            p.setMetodoPagoElegido(2); // 2 = Mercado Pago
        }
        save(p);

        // AVANZAMOS AL PASO DE CÓDIGO DE DESCUENTO
        askWithButtons(session, p, OnboardingStep.ASK_CODIGO_DESCUENTO, M_CODIGO_DESCUENTO, M_CODIGO_DESCUENTO_OPTIONS);
    }

    private void handleCodigoDescuento(BotSession session,Patient p, String text) {
        String selected = getSelectedOption(text, M_CODIGO_DESCUENTO_OPTIONS);
        if (selected == null) {
            invalidOption(p);
            return;
        }

        if ("Sí".equalsIgnoreCase(selected)) {
            // Pide el código
            askWithText(session, p, OnboardingStep.ASK_INGRESAR_CODIGO, M_INGRESAR_CODIGO);
        } else {
            // No tiene → directo al pago
            procederAlPago(session, p);
        }
    }

    private void handleIngresarCodigo(BotSession session,Patient p, String text) {
        String codigo = text.trim().toUpperCase();

        // POR AHORA: solo guardamos (futuro: validar con backend)
        p.setCodigoDescuento(codigo);
        save(p);

        sendText(p.getWhatsappId(),
                "¡Código recibido: " + codigo + "!\n\n" +
                        "Lo aplicaremos en tu pago (próximamente). Procedemos al pago...");

        // Siempre avanza al pago, aunque no valide el código aún
        procederAlPago(session, p);
    }

    // MÉTODO COMÚN PARA IR AL PAGO SEGÚN MÉTODO ELEGIDO
    private void procederAlPago(BotSession session, Patient p) {

        log.info("ANTES DE CREAR CONSULTA → consultaPublicId={}", session.getConsultaPublicId());
        crearConsultaEnDjango(session, p);


        session.setPaymentConfirmed(false);
        saveSession(session);

        // 🔥 Luego generar el pago
        if (p.getMetodoPagoElegido() == 1) {
            goToStripePayment(session, p);
        } else if (p.getMetodoPagoElegido() == 2) {
            goToPaymentMercadoPago(session, p);
        } else {
            sendText(p.getWhatsappId(),
                    "Hubo un problema con el método de pago. Escribe *HOLA* para reiniciar.");
            session.setCurrentStep(OnboardingStep.WELCOME);
            saveSession(session);
        }
    }

    private void goToStripePayment(BotSession session, Patient p) {

        Double precioFinal = session.getPrecioFinal();

        if (precioFinal == null) {
            sendText(p.getWhatsappId(),
                    "⚠️ No se pudo obtener el precio de la consulta.");
            return;
        }

        StripeCheckoutResult result = stripeService.crearPaymentLink(
                session.getConsultaPublicId(),
                p.getWhatsappId(),
                p.getEmail(),
                precioFinal
        );

        if (result == null) {
            sendText(p.getWhatsappId(),
                    "⚠️ Hubo un problema generando el pago. Intenta más tarde.");
            return;
        }

        AttachStripeSessionRequest attachRequest = new AttachStripeSessionRequest();
        attachRequest.setConsulta_id(session.getConsultaId());
        attachRequest.setStripe_session_id(result.getSessionId());
        attachRequest.setPayment_intent_id(result.getPaymentIntentId());

        djangoIntegrationService.attachStripeSession(attachRequest);

        session.setCurrentStep(OnboardingStep.PROCESS_PAYMENT);
        saveSession(session);

        whatsAppClient.sendCtaUrlButton(
                p.getWhatsappId(),
                "💳 Pago seguro con tarjeta\n\n" +
                        "Costo: $" + precioFinal + " MXN\n\n" +
                        "Da clic para completar tu pago:",
                "Pagar $" + precioFinal,
                result.getCheckoutUrl()
        );
    }

    private void goToPaymentMercadoPago(BotSession session, Patient p) {

        session.setCurrentStep(OnboardingStep.PROCESS_PAYMENT);
        saveSession(session);

        Double precioFinal = session.getPrecioFinal();

        if (precioFinal == null) {
            sendText(p.getWhatsappId(),
                    "⚠️ No se pudo obtener el precio de la consulta. Escribe *HOLA* para reiniciar.");
            return;
        }

        PaymentLinkResult result = mercadoPagoService.crearPaymentLink(
                session.getConsultaPublicId(),
                p.getWhatsappId(),
                p.getEmail(),
                precioFinal
        );

        if (result == null || result.paymentUrl() == null || result.paymentUrl().isBlank()) {
            sendText(p.getWhatsappId(),
                    "⚠️ Ocurrió un problema al generar el enlace de pago.");
            return;
        }

        // 🔥 Aquí guardas datos financieros importantes
        session.setExternalPaymentReference(result.externalReference());
        session.setPaymentProvider("MERCADOPAGO");
        saveSession(session);

        whatsAppClient.sendCtaUrlButton(
                p.getWhatsappId(),
                "¡Todo listo! 🎉\n\n" +
                        "💳 Costo: $" + precioFinal + " MXN\n\n" +
                        "Da clic en el botón para pagar:",
                "Pagar $" + precioFinal + " 💳",
                result.paymentUrl()
        );
    }


    private void handlePayment(BotSession session,Patient p, String text) {
        if (!text.equalsIgnoreCase("PAGADO")) {
            sendText(p.getWhatsappId(), M_25);
            return;
        }
        p.setPagoProcesado(true);
        session.setCurrentStep(OnboardingStep.WELCOME);
        saveSession(session);
        save(p);
        sendText(p.getWhatsappId(), M_24 + "\n\nhttps://panel.tuclinica.com/patient/" + p.getWhatsappId() + "\n\n Si deseas realizar una consulta nueva, escribe: hola");
    }

    // ==================== DOCUMENTOS LEGALES Y PAGO ====================

    private void advanceToLegalDocuments(BotSession session, Patient p) {
        session.setCurrentStep(OnboardingStep.ASK_TERMINOS);
        saveSession(session);
        sendDocument(p.getWhatsappId(), TERMINOS_URL, "Términos y Condiciones.pdf");
        sendText(p.getWhatsappId(), "¿Aceptas los Términos y Condiciones?\n\nResponde *Sí* o *No*");
    }

    private void handleTerminos(BotSession session,Patient p, String text) {
        if (!text.equalsIgnoreCase("Sí") && !text.equalsIgnoreCase("Si")) {
            sendText(p.getWhatsappId(), "Debes aceptar los Términos y Condiciones para continuar.");
            return;
        }
        session.setCurrentStep(OnboardingStep.ASK_AVISO_PRIVACIDAD);
        saveSession(session);
        sendDocument(p.getWhatsappId(), AVISO_PRIVACIDAD_URL, "Aviso de Privacidad.pdf");
        sendText(p.getWhatsappId(), "¿Aceptas el Aviso de Privacidad?\n\nResponde *Sí* o *No*");
    }

    private void handleAvisoPrivacidad(BotSession session,Patient p, String text) {
        if (!text.equalsIgnoreCase("Sí") && !text.equalsIgnoreCase("Si")) {
            sendText(p.getWhatsappId(), "Debes aceptar el Aviso de Privacidad para continuar.");
            return;
        }
        session.setCurrentStep(OnboardingStep.ASK_CONSENTIMIENTO);
        saveSession(session);
        sendDocument(p.getWhatsappId(), CONSENTIMIENTO_URL, "Consentimiento Informado de Telemedicina.pdf");
        sendText(p.getWhatsappId(), "¿Aceptas el Consentimiento Informado de Telemedicina?\n\nResponde *Sí* o *No*");
    }

    private void handleConsentimiento(BotSession session,Patient p, String text) {
        if (!text.equalsIgnoreCase("Sí") && !text.equalsIgnoreCase("Si")) {
            sendText(p.getWhatsappId(), "Debes aceptar el Consentimiento para continuar.");
            return;
        }
        session.setCurrentStep(OnboardingStep.PROCESS_PAYMENT);
        saveSession(session);
        String paymentLink = "https://pago.tuclinica.com/pay/" + p.getWhatsappId(); // aquí pones Stripe, Mercado Pago, etc.
        sendText(p.getWhatsappId(), M_23 + "\n\n" + paymentLink + "\n\nCuando hayas pagado escribe *PAGADO*");
    }

    // ====================== MÉTODOS AUXILIARES ======================


    private OnboardingStep nextStepAfterDesdeCuando(String padecimiento) {
        return switch (padecimiento) {
            case "Acne", "Manchas", "Rosacea" -> OnboardingStep.ASK_GRAVEDAD;
            case "Anti-edad" -> OnboardingStep.ASK_MEJORA_PRINCIPAL;
            case "Caida de Pelo" -> OnboardingStep.ASK_AREA_CAIDA;
            default -> OnboardingStep.ASK_TRATAMIENTO_ANTERIOR;
        };
    }

    private String getGravedadMessage(String p) {
        return switch (p) {
            case "Acne" -> M_12;
            case "Manchas" -> M_37;
            case "Rosacea" -> M_40;
            default -> "";
        };
    }

    private List<String> getGravedadOptions(String p) {
        return switch (p) {
            case "Acne" -> M_12_OPTIONS;
            case "Manchas" -> M_37_OPTIONS;
            case "Rosacea" -> M_40_OPTIONS;
            default -> List.of();
        };
    }

    private String getNotasMessage(String padecimiento) {
        return switch (padecimiento) {
            case "Acne" -> M_19;
            case "Anti-edad" -> M_32;
            case "Caida de Pelo" -> M_36;
            case "Manchas" -> M_38;
            case "Rosacea" -> M_41;
            default -> M_39;
        };
    }

    private void invalidOption(Patient p) {
        sendText(p.getWhatsappId(), "Opción inválida. Por favor elige una de las opciones mostradas.");
    }

    /**
     * Con botones: el título llega EXACTO → solo buscamos coincidencia exacta (case insensitive)
     */
    // java
    private String getSelectedOption(String userText, List<String> options) {
        if (userText == null || userText.isBlank()) return null;

        // Normaliza entrada: trim, toLowerCase, quitar tildes y dejar solo a-z0-9 y espacios
        String input = userText.trim().toLowerCase()
                .replace("á", "a").replace("é", "e").replace("í", "i")
                .replace("ó", "o").replace("ú", "u").replace("ü", "u");
        input = input.replaceAll("[^a-z0-9 ]", "").trim();

        for (String option : options) {
            if (option == null || option.isBlank()) continue;

            // Canonical: quitar prefijos no alfanuméricos (emoji, símbolos, puntuación)
            String canonical = option.trim().replaceAll("^[^\\p{L}\\p{N}]+", "").trim();
            // Normaliza la opción para comparar
            String optionClean = canonical.toLowerCase()
                    .replace("á", "a").replace("é", "e").replace("í", "i")
                    .replace("ó", "o").replace("ú", "u").replace("ü", "u");
            optionClean = optionClean.replaceAll("[^a-z0-9 ]", "").trim();

            if (optionClean.equals(input) || input.contains(optionClean) || optionClean.contains(input)) {
                // Devuelve la opción limpia (sin emoji/prefijos) para uso posterior
                return canonical;
            }
        }
        return null;
    }


    /**
     * Envía mensaje de texto simple
     */
    private void sendText(String to, String text) {
        // Asegura formato internacional (México)
        String phone = to;
        if (!phone.startsWith("52") && !phone.startsWith("1")) {
            phone = "521" + phone.replaceFirst("^0+", "");
        }

        whatsAppClient.sendText(phone, text);
    }

    /**
     * Envía un documento (PDF) usando WhatsApp Cloud API
     */
    private void sendDocument(String to, String documentUrl, String filename) {
        String phone = to;
        if (!phone.startsWith("52") && !phone.startsWith("1")) {
            phone = "521" + phone.replaceFirst("^0+", "");
        }

        whatsAppClient.sendDocument(phone, documentUrl, filename, phone);
    }

    /**
     * Guarda el paciente con timestamp de actualización
     */
    private void save(Patient patient) {
        patient.setUpdatedAt(LocalDateTime.now());
        patientRepository.save(patient);
    }

    /**
     * Crea o recupera el paciente por número de WhatsApp
     */
    private Patient getOrCreatePatient(String whatsappId) {
        String normalized = whatsappId.replaceFirst("^521?", "521");

        return patientRepository.findByWhatsappId(normalized)
                .orElseGet(() -> {
                    Patient nuevo = Patient.builder()
                            .whatsappId(normalized)
                            .photoUrls(new ArrayList<>())
                            .createdAt(LocalDateTime.now())
                            .build();
                    return patientRepository.save(nuevo);
                });
    }


    private void askWithButtons(BotSession session, Patient p,
                                OnboardingStep nextStep,
                                String message,
                                List<String> options) {

        session.setCurrentStep(nextStep);
        saveSession(session);

        sendButtons(p, adaptarMensaje(p, message), options);
    }

    private void askWithText(BotSession session, Patient p,
                             OnboardingStep nextStep, String message) {

        session.setCurrentStep(nextStep);
        saveSession(session);

        sendText(p.getWhatsappId(), adaptarMensaje(p, message));
    }

    private void sendButtons(Patient p, String text, List<String> options) {
        // NUEVA LÓGICA: quitamos SOLO el prefijo "a) ", "b) ", "c) " etc. de forma segura
        List<String> titles = options.stream()
                .map(option -> option.replaceAll("^[a-g]\\)\\s*", "").trim()) // quita "a) ", "b) ", etc.
                .filter(title -> !title.isEmpty() && title.length() <= 20)
                .limit(5)
                .toList();

        if (titles.isEmpty()) {
            log.warn("Botones inválidos después de procesar: {}", titles);
            sendText(p.getWhatsappId(), text + "\n\n(Responde con texto por falla en botones)");
            return;
        }

        whatsAppClient.sendReplyButtons(p.getWhatsappId(), text, titles);
    }

    public void processListSelection(String from, String listId) {

        String normalizedFrom = normalize(from);

        log.info("processListSelection → listId: '{}'", listId);

        if (listId == null || listId.isBlank()) {
            sendText(normalizedFrom, "Error: opción inválida.");
            return;
        }

        BotSession session = botSessionRepository.findByWhatsappId(normalizedFrom)
                .orElseThrow(() -> new RuntimeException("Sesión no encontrada para: " + normalizedFrom));

        Patient patient = patientRepository.findByWhatsappId(normalizedFrom)
                .orElseThrow(() -> new RuntimeException("Paciente no encontrado: " + normalizedFrom));

        String context = session.getLastListContext();

        if (context == null || context.isBlank()) {
            log.error("lastListContext es NULL para {}", normalizedFrom);
            sendText(normalizedFrom, "Error interno. Escribe *HOLA* para reiniciar.");
            return;
        }

        if (!listId.startsWith(context + "_")) {
            log.warn("listId '{}' no coincide con contexto '{}'", listId, context);
            sendText(normalizedFrom, "Error de flujo. Escribe *HOLA* para reiniciar.");
            return;
        }

        int index;
        try {
            index = Integer.parseInt(listId.substring(listId.lastIndexOf("_") + 1)) - 1;
        } catch (NumberFormatException e) {
            sendText(normalizedFrom, "Opción inválida.");
            return;
        }

        List<String> options = getOptionsForContext(context);

        if (index < 0 || index >= options.size()) {
            sendText(normalizedFrom, "Opción inválida.");
            return;
        }

        String selected = options.get(index);

        log.info("✓ Selección correcta → contexto: {}, opción: {}", context, selected);

        processText(normalizedFrom, selected);
    }



    // ==== NUEVO: askWithList GENÉRICO ====

    private void askWithList(
            BotSession session,
            Patient p,
            OnboardingStep nextStep,
            String bodyText,
            String buttonText,
            List<String> options,
            String contextKey
    ) {

        session.setCurrentStep(nextStep);
        session.setLastListContext(contextKey);
        saveSession(session);

        List<Map<String, String>> rows = new ArrayList<>();
        for (int i = 0; i < options.size(); i++) {
            Map<String, String> row = new HashMap<>();
            row.put("id", contextKey + "_" + (i + 1));
            row.put("title", options.get(i));
            row.put("description", "");
            rows.add(row);
        }

        whatsAppClient.sendListMessage(
                p.getWhatsappId(),
                null,
                adaptarMensaje(p, bodyText),
                buttonText,
                rows
        );
    }

    private void askWithListSection(
            BotSession session,
            Patient p,
            OnboardingStep nextStep,
            String bodyText,
            String sectionTitle,
            String buttonText,
            List<String> options,
            String contextKey
    ) {

        session.setCurrentStep(nextStep);
        session.setLastListContext(contextKey);
        saveSession(session);

        List<Map<String, String>> rows = new ArrayList<>();
        for (int i = 0; i < options.size(); i++) {
            Map<String, String> row = new HashMap<>();
            row.put("id", contextKey + "_" + (i + 1));
            row.put("title", options.get(i));
            row.put("description", "");
            rows.add(row);
        }

        Map<String, Object> section = new HashMap<>();
        section.put("title", sectionTitle);
        section.put("rows", rows);

        whatsAppClient.sendListMessageWithSections(
                p.getWhatsappId(),
                "Método de pago",
                bodyText,
                buttonText,
                List.of(section)
        );
    }

    // ==== NUEVO: devuelve las opciones según el contexto ====
    private List<String> getOptionsForContext(String context) {
        return switch (context) {
            case "motivo" -> M_1_OPTIONS;
            case "desde_cuando" -> M_11_OPTIONS;
            case "mejora_principal" -> M_26_OPTIONS;
            case "area_caida" -> M_34_OPTIONS;
            case "antecedentes_familia" -> M_35_OPTIONS;
            case "status_embarazo" -> M_33_OPTIONS;
            case "tipo_piel" -> M_27_OPTIONS;
            case "sensibilidad_piel" -> M_28_OPTIONS;
            case "exposicion_sol" -> M_30_OPTIONS;
            case "uso-protector" -> M_31_OPTIONS;

            // GRAVEDAD DINÁMICA
            case "gravedad_acne" -> M_12_OPTIONS;
            case "gravedad_manchas" -> M_37_OPTIONS;
            case "gravedad_rosacea" -> M_40_OPTIONS;

            // ← NUEVO CASE PARA MÉTODO DE PAGO
            case "metodo_pago" -> M_METODO_PAGO_OPTIONS;

            default -> {
                log.warn("Contexto de lista desconocido: {}", context);
                yield List.of();
            }
        };
    }

    private String adaptarMensaje(Patient p, String mensaje) {
        if (!Boolean.TRUE.equals(p.getConsultaParaOtraPersona())) {
            return mensaje;
        }

        return mensaje
                .replace(" tu ", " su ")
                .replace(" tus ", " sus ")
                .replace("¿Tu ", "¿Su ")
                .replace("¿Tus ", "¿Sus ")
                .replace(" tuyo", " suyo")
                .replace(" tuya", " suya")
                .replace("¿Fumas?", "¿Fuma?")
                .replace("¿Tienes ", "¿Tiene ")
                .replace("tienes ", "tiene ")
                .replace("tienes:", "tiene:")
                .replace("¿Tomas ", "¿Toma ")
                .replace("¿Usas ", "¿Usa ")
                .replace("¿Has ", "¿Ha ")
                .replace("¿Padeces ", "¿Padece ")
                .replace("¿Cuánto pesas?", "¿Cuánto pesa?")
                .replace("¿Cuánto mides?", "¿Cuánto mide?")
                .replace(" te ", " le ")
                .replace(" contigo", " con la persona")
                .replace("Escribe tu ", "Escribe su ")
                .replace("Ingresa tu ", "Ingresa su ")
                .replace("tu piel", "su piel")
                .replace("tu pelo", "su pelo")
                .replace("tu cabello", "su cabello")
                .replace("usaste", "usó")
                .replace("usas", "usa")
                .replace("tu rostro", "su rostro");
    }

    public void sendUnsupportedFormatMessage(String from, String formatType) {
        String message = String.format(
                "⚠️ Lo siento, no puedo procesar %s. " +
                        "Por favor envía solo texto o imágenes según lo solicitado.",
                formatType.equals("audio") ? "audios" :
                        formatType.equals("video") ? "videos" :
                                formatType.equals("documento") ? "documentos" :
                                        formatType.equals("sticker") ? "stickers" :
                                                formatType.equals("ubicación") ? "ubicaciones" : formatType
        );

        sendText(from, message);
    }

    /**
     * Método público llamado desde el webhook de Stripe cuando el pago es exitoso
     */
    public void enviarMensajePagoExitoso(String whatsappId, String panelUrl) {
        // Mensaje principal de texto
        sendText(whatsappId,
                "🎉 ¡Pago recibido correctamente!\n\n" +
                        "Tu dermatóloga revisará tu caso en las próximas horas.\n" +
                        "Puedes seguir el estado de tu consulta aquí:\n\n" +
                        panelUrl + "\n\n" +
                        "¡Gracias por confiar en Elara! 💙");

        // Botón grande azul al panel (experiencia premium)
        whatsAppClient.sendCtaUrlButton(
                whatsappId,
                "Accede directamente a tu panel de paciente para ver el seguimiento de tu consulta",
                "Ir al Panel 👩‍⚕️",
                panelUrl
        );

        log.info("Mensaje de pago exitoso enviado a {}", whatsappId);
    }

    private Map<String, Object> getPayload(BotSession session) {
        try {
            if (session.getPayloadJson() == null) {
                return new HashMap<>();
            }
            return objectMapper.readValue(session.getPayloadJson(), Map.class);
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    private void updatePayload(BotSession session, String key, Object value) {
        try {
            Map<String, Object> payload = getPayload(session);
            payload.put(key, value);
            session.setPayloadJson(objectMapper.writeValueAsString(payload));
            saveSession(session);;
        } catch (Exception e) {
            throw new RuntimeException("Error updating payload", e);
        }
    }

    private BotSession getOrCreateSession(String whatsappId) {

        final String normalizedWhatsappId = normalize(whatsappId);

        return botSessionRepository.findByWhatsappId(normalizedWhatsappId)
                .orElseGet(() -> {

                    BotSession session = BotSession.builder()
                            .whatsappId(normalizedWhatsappId)
                            .currentStep(OnboardingStep.WELCOME)
                            .payloadJson("{}")
                            .pendingInactivityResponse(false)
                            .createdAt(LocalDateTime.now())
                            .build();

                    return botSessionRepository.save(session);
                });
    }


    private void saveSession(BotSession session) {
        session.setUpdatedAt(LocalDateTime.now());
        botSessionRepository.save(session);;
    }

    private String normalize(String phone) {

        if (phone == null || phone.isBlank()) {
            return phone;
        }

        // Si ya viene con 521 no tocar
        if (phone.startsWith("521")) {
            return phone;
        }

        // Si viene con 52 pero no 521
        if (phone.startsWith("52")) {
            return "521" + phone.substring(2);
        }

        // Si viene sin prefijo internacional
        return "521" + phone.replaceFirst("^0+", "");
    }

    private void crearConsultaEnDjango(BotSession session, Patient p) {

        // 1️⃣ Crear o recuperar usuario en Django
        log.info("CREANDO NUEVA CONSULTA EN DJANGO");

        BotUserResponse userResponse =
                djangoIntegrationService.crearUsuario(
                        p.getEmail(),
                        p.getNombreCompleto()
                );

        if (userResponse == null || !Boolean.TRUE.equals(userResponse.getSuccess())) {
            throw new RuntimeException("Error creando usuario en Django");
        }

        // 2️⃣ Construir request de consulta
        BotCreateConsultaRequest request = new BotCreateConsultaRequest();

        request.setUser_id(userResponse.getUser_id());
        request.setMotivo_consulta(
                MOTIVO_MAP.getOrDefault(p.getPadecimiento(), "otros")
        );
        request.setMetodo_pago(
                p.getMetodoPagoElegido() == 1 ? "card" : "oxxo"
        );
        request.setPara_quien(
                Boolean.TRUE.equals(p.getConsultaParaOtraPersona()) ? "otra_persona" : "para_mi"
        );

        request.setNombre_paciente(p.getNombreCompleto());
        request.setGenero_nacimiento(p.getGenero());
        if (p.getFechaNacimiento() != null) {
            request.setFecha_nacimiento(
                    p.getFechaNacimiento().toString()
            );
        }
        request.setPeso(p.getPesoKg() != null ? p.getPesoKg().intValue() : null);
        request.setAltura(p.getAlturaM());
        request.setFuma(p.getFuma());

        request.setTiene_alergias(p.getAlergias());
        request.setAlergias_descripcion(p.getAlergiasDetalles());

        request.setToma_medicamentos(p.getMedicamentos());
        request.setMedicamentos_descripcion(p.getMedicamentosDetalles());

        request.setInformacion_adicional(p.getNotasAdicionales());
        request.setCodigo_descuento(p.getCodigoDescuento());
        request.setPhoto_keys(p.getPhotoUrls());

        // 3️⃣ Crear consulta
        BotCreateConsultaResponse response =
                djangoIntegrationService.crearConsulta(request);

        if (response == null || !Boolean.TRUE.equals(response.getSuccess())) {
            throw new RuntimeException("Error creando consulta en Django");
        }
        log.info("Consulta creada con ID: {} y publicId: {}",
                response.getConsulta_id(),
                response.getPublic_id());

        // 4️⃣ Guardar en sesión del bot
        session.setConsultaId(response.getConsulta_id());
        session.setConsultaPublicId(response.getPublic_id());
        session.setPrecioOriginal(response.getPrecio_original());
        session.setDescuento(response.getDescuento());
        session.setPrecioFinal(response.getPrecio_final());
        saveSession(session);

        // Limpiar fotos del paciente para evitar reenvíos accidentales (si quieren agregarmás, lo harán explícitamente en el paso de fotos)
        p.getPhotoUrls().clear();
        patientRepository.save(p);
    }

    private static final Map<String, String> MOTIVO_MAP = Map.of(
            "Acné", "acne",
            "Caída de pelo", "cabello",
            "Anti-edad", "antiedad_skincare",
            "Rosácea", "rosacea",
            "Manchas", "manchas",
            "Dermatitis", "dermatitis",
            "Otros", "otros"
    );
}