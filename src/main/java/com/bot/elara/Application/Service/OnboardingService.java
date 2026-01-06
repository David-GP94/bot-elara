package com.bot.elara.Application.Service;

import com.bot.elara.Domain.Model.OnboardingStep;
import com.bot.elara.Domain.Model.Patient;
import com.bot.elara.Domain.Repository.PatientRepository;
import com.bot.elara.Infrastructure.External.Clients.ElaraApi.ElaraApiPatientClient;
import com.bot.elara.Infrastructure.External.Storage.S3Service;
import com.bot.elara.Infrastructure.External.Whatsapp.Model.Image;
import com.bot.elara.Infrastructure.External.Whatsapp.WhatsAppCloudApiClient;
import com.bot.elara.Util.DateParserUtil;
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
    
    // Feign Clients para API externa de Elara
    private final com.bot.elara.Infrastructure.External.Clients.ElaraApi.ElaraApiPatientClient elaraPatientClient;

    private final java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.ScheduledFuture<?>> pendingResponses = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ScheduledExecutorService imageScheduler = java.util.concurrent.Executors.newScheduledThreadPool(2);
    private final java.util.concurrent.ConcurrentHashMap<String, Object> userLocks = new java.util.concurrent.ConcurrentHashMap<>();

    private final java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.ScheduledFuture<?>> inactivityReminders = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ScheduledExecutorService reminderScheduler = java.util.concurrent.Executors.newScheduledThreadPool(2);

    private static final long INACTIVITY_TIMEOUT_MINUTES = 1; // Configurable

    // URLs reales de tus documentos (ponlas en S3 o en tu dominio)
    private static final String TERMINOS_URL = "https://tu-dominio.com/docs/terminos-y-condiciones.pdf";
    private static final String AVISO_PRIVACIDAD_URL = "https://tu-dominio.com/docs/aviso-de-privacidad.pdf";
    private static final String CONSENTIMIENTO_URL = "https://tu-dominio.com/docs/consentimiento-telemedicina.pdf";

    // Método para programar el recordatorio
    private void scheduleInactivityReminder(String whatsappId) {
        log.info(">>> scheduleInactivityReminder llamado para {}", whatsappId); // ← LOG

        java.util.concurrent.ScheduledFuture<?> existing = inactivityReminders.remove(whatsappId);
        if (existing != null && !existing.isDone()) {
            existing.cancel(false);
            log.info("Recordatorio anterior cancelado para {}", whatsappId);
        }

        Patient p = patientRepository.findByWhatsappId(whatsappId).orElse(null);
        if (p == null || p.getCurrentStep() == OnboardingStep.COMPLETED ||
                p.getCurrentStep() == OnboardingStep.WELCOME) {
            log.info("No se programa recordatorio - paso: {}", p != null ? p.getCurrentStep() : "null");
            return;
        }

        log.info("Programando recordatorio para {} en {} minuto(s)", whatsappId, INACTIVITY_TIMEOUT_MINUTES);

        java.util.concurrent.ScheduledFuture<?> future = reminderScheduler.schedule(() -> {
            log.info(">>> Timer expirado, ejecutando sendReminder para {}", whatsappId); // ← LOG
            inactivityReminderService.sendReminder(whatsappId);
            inactivityReminders.remove(whatsappId);
        }, INACTIVITY_TIMEOUT_MINUTES, java.util.concurrent.TimeUnit.MINUTES);

        inactivityReminders.put(whatsappId, future);
        log.info("Recordatorio programado exitosamente. Total activos: {}", inactivityReminders.size());
    }

    // ====================== ENTRY POINTS ======================

    public void processText(String from, String text) {
        Patient patient = getOrCreatePatient(from);

        // Manejar respuesta de recordatorio de inactividad
        if (Boolean.TRUE.equals(patient.getPendingInactivityResponse())) {
            patient.setPendingInactivityResponse(false);
            save(patient);

            String normalized = text.toLowerCase().trim();
            if (normalized.contains("no") || normalized.contains("cancelar")) {
                sendText(from, "😊 Entendido. Proceso terminado, si deseas reiniciar, escribe *HOLA*.");
                patient.setCurrentStep(OnboardingStep.WELCOME);
                save(patient);
                return;
            }

            // Si quiere continuar → reenviar la pregunta del paso actual
            resendCurrentStepQuestion(patient);
            scheduleInactivityReminder(from);
            return;

        }
        scheduleInactivityReminder(from);

        text = text.trim();

        switch (patient.getCurrentStep()) {
            case WELCOME -> handleWelcomeMessage(patient);
            case START -> handleStart(patient, text);
            case ASK_PADECIMIENTO -> handlePadecimiento(patient, text);
            case CONFIRM_PADECIMIENTO -> handleConfirmPadecimiento(patient, text);
            case ASK_EMAIL -> handleEmail(patient, text);
            case CONFIRM_EMAIL -> handleConfirmEmail(patient, text);
            case ASK_MAYORIA_EDAD -> handleMayoriaEdad(patient, text);
            case ASK_NOMBRE -> handleNombre(patient, text);
            case ASK_GENERO -> handleGenero(patient, text);
            case ASK_FECHA_NAC -> handleFechaNac(patient, text);
            case ASK_PESO -> handlePeso(patient, text);
            case ASK_ALTURA -> handleAltura(patient, text);
            case ASK_FUMA -> handleFuma(patient, text);
            case ASK_DESDE_CUANDO -> handleDesdeCuando(patient, text);
            case ASK_GRAVEDAD -> handleGravedad(patient, text);
            case ASK_TRATAMIENTO_ANTERIOR -> handleTratamientoAnterior(patient, text);
            case ASK_TRATAMIENTOS_USADOS -> handleTratamientosUsados(patient, text);
            case ASK_ALERGIAS -> handleAlergias(patient, text);
            case ASK_ALERGIAS_DETALLES -> handleAlergiasDetalles(patient, text);
            case ASK_MEDICAMENTOS -> handleMedicamentos(patient, text);
            case ASK_MEDICAMENTOS_DETALLES -> handleMedicamentosDetalles(patient, text);
            case ASK_ENFERMEDADES -> handleEnfermedades(patient, text);
            case ASK_ENFERMEDADES_DETALLES -> handleEnfermedadesDetalles(patient, text);
            case ASK_MEJORA_PRINCIPAL -> handleMejoraPrincipal(patient, text);
            case ASK_TIPO_PIEL -> handleTipoPiel(patient, text);
            case ASK_SENSIBILIDAD_PIEL -> handleSensibilidadPiel(patient, text);
            case ASK_EXPOSICION_SOL -> handleExposicionSol(patient, text);
            case ASK_USA_PROTECTOR -> handleUsaProtector(patient, text);
            case ASK_AREA_CAIDA -> handleAreaCaida(patient, text);
            case ASK_ANTECEDENTES_FAMILIA -> handleAntecedentesFamilia(patient, text);
            case ASK_STATUS_EMBARAZO -> handleStatusEmbarazo(patient, text);
            case ASK_NOTAS_ADICIONALES -> handleNotasAdicionales(patient, text);
            case ASK_NOTAS_ADICIONALES_DETALLES -> handleNotasAdicionalesDetalles(patient, text);
            case ASK_FOTOS -> handleFotos(patient, text);
            case ASK_MAS_FOTOS -> handleMasFotos(patient, text);
            case ASK_EXCESO_FOTOS -> handleExcesoFotos(patient, text);
            case ASK_TERMINOS -> handleTerminos(patient, text);
            case ASK_AVISO_PRIVACIDAD -> handleAvisoPrivacidad(patient, text);
            case ASK_CONSENTIMIENTO -> handleConsentimiento(patient, text);
            case ASK_METODO_PAGO -> handleMetodoPago(patient, text);
            case ASK_CODIGO_DESCUENTO -> handleCodigoDescuento(patient, text);
            case ASK_INGRESAR_CODIGO -> handleIngresarCodigo(patient, text);
            case PROCESS_PAYMENT -> handlePayment(patient, text);
            case COMPLETED ->
                    sendText(from, "¡Tu consulta ya está completada! Tu dermatóloga la revisará pronto. Te avisaremos cuando esté lista.");
            default -> sendText(from, "Algo salió mal. Escribe *HOLA* para reiniciar el proceso.");
        }
    }


    /**
     * Reenvía la pregunta del paso actual cuando el usuario retoma el flujo
     */
    private void resendCurrentStepQuestion(Patient p) {
        String from = p.getWhatsappId();

        switch (p.getCurrentStep()) {
            case ASK_PADECIMIENTO ->
                    askWithList(p, OnboardingStep.ASK_PADECIMIENTO, M_1, "Ver opciones", M_1_OPTIONS, "motivo");
            case CONFIRM_PADECIMIENTO ->
                    askWithList(p, OnboardingStep.ASK_PADECIMIENTO, M_1, "Ver opciones", M_1_OPTIONS, "motivo");
            case ASK_EMAIL -> askWithText(p, OnboardingStep.ASK_EMAIL, M_2);
            case CONFIRM_EMAIL -> askWithText(p, OnboardingStep.ASK_EMAIL, M_2);
            case ASK_MAYORIA_EDAD -> askWithButtons(p, OnboardingStep.ASK_MAYORIA_EDAD, M_4, M_4_OPTIONS);
            case ASK_NOMBRE ->
                    askWithText(p, OnboardingStep.ASK_NOMBRE, p.getConsultaParaOtraPersona() ? "¿Cuál es el nombre completo de la persona para quien es la consulta?" : M_5);
            case ASK_GENERO -> askWithButtons(p, OnboardingStep.ASK_GENERO, M_6, M_6_OPTIONS);
            case ASK_FECHA_NAC -> askWithText(p, OnboardingStep.ASK_FECHA_NAC, M_7);
            case ASK_PESO -> askWithText(p, OnboardingStep.ASK_PESO, M_8);
            case ASK_ALTURA -> askWithText(p, OnboardingStep.ASK_ALTURA, M_9);
            case ASK_FUMA -> askWithButtons(p, OnboardingStep.ASK_FUMA, M_10, M_10_OPTIONS);
            case ASK_DESDE_CUANDO ->
                    askWithList(p, OnboardingStep.ASK_DESDE_CUANDO, M_11, "Elegir tiempo", M_11_OPTIONS, "desde_cuando");
            case ASK_GRAVEDAD -> {
                var options = getGravedadOptions(p.getPadecimiento());
                if (options.size() > 3) {
                    askWithList(p, OnboardingStep.ASK_GRAVEDAD, getGravedadMessage(p.getPadecimiento()), "👉Seleccionar opción", options, "gravedad_" + p.getPadecimiento().toLowerCase().replace(" ", "_"));
                } else {
                    askWithButtons(p, OnboardingStep.ASK_GRAVEDAD, getGravedadMessage(p.getPadecimiento()), options);
                }
            }
            case ASK_TRATAMIENTO_ANTERIOR ->
                    askWithButtons(p, OnboardingStep.ASK_TRATAMIENTO_ANTERIOR, M_13, M_13_OPTIONS);
            case ASK_TRATAMIENTOS_USADOS -> askWithText(p, OnboardingStep.ASK_TRATAMIENTOS_USADOS, M_14);
            case ASK_ALERGIAS -> askWithButtons(p, OnboardingStep.ASK_ALERGIAS, M_15, M_15_OPTIONS);
            case ASK_ALERGIAS_DETALLES -> askWithText(p, OnboardingStep.ASK_ALERGIAS_DETALLES, M_16);
            case ASK_MEDICAMENTOS -> askWithButtons(p, OnboardingStep.ASK_MEDICAMENTOS, M_17, M_17_OPTIONS);
            case ASK_MEDICAMENTOS_DETALLES -> askWithText(p, OnboardingStep.ASK_MEDICAMENTOS_DETALLES, M_18);
            case ASK_ENFERMEDADES -> askWithButtons(p, OnboardingStep.ASK_ENFERMEDADES, M_29, M_29_OPTIONS);
            case ASK_ENFERMEDADES_DETALLES -> askWithText(p, OnboardingStep.ASK_ENFERMEDADES_DETALLES, M_43);
            case ASK_MEJORA_PRINCIPAL ->
                    askWithList(p, OnboardingStep.ASK_MEJORA_PRINCIPAL, M_26, "Elegir mejora", M_26_OPTIONS, "mejora_principal");
            case ASK_TIPO_PIEL ->
                    askWithList(p, OnboardingStep.ASK_TIPO_PIEL, M_27, "👉Seleccionar tipo", M_27_OPTIONS, "tipo_piel");
            case ASK_SENSIBILIDAD_PIEL ->
                    askWithList(p, OnboardingStep.ASK_SENSIBILIDAD_PIEL, M_28, "👉Seleccionar opción", M_28_OPTIONS, "sensibilidad_piel");
            case ASK_EXPOSICION_SOL ->
                    askWithList(p, OnboardingStep.ASK_EXPOSICION_SOL, M_30, "👉Seleccionar opción", M_30_OPTIONS, "exposicion_sol");
            case ASK_USA_PROTECTOR ->
                    askWithList(p, OnboardingStep.ASK_USA_PROTECTOR, M_31, "👉Seleccionar opción", M_31_OPTIONS, "uso-protector");
            case ASK_AREA_CAIDA -> askWithButtons(p, OnboardingStep.ASK_AREA_CAIDA, M_34, M_34_OPTIONS);
            case ASK_ANTECEDENTES_FAMILIA ->
                    askWithList(p, OnboardingStep.ASK_ANTECEDENTES_FAMILIA, M_35, "👉Seleccionar opción", M_35_OPTIONS, "antecedentes_familia");
            case ASK_STATUS_EMBARAZO ->
                    askWithList(p, OnboardingStep.ASK_STATUS_EMBARAZO, M_33, "👉Seleccionar opción", M_33_OPTIONS, "status_embarazo");
            case ASK_NOTAS_ADICIONALES ->
                    askWithButtons(p, OnboardingStep.ASK_NOTAS_ADICIONALES, getNotasMessage(p.getPadecimiento()), M_15_OPTIONS);
            case ASK_NOTAS_ADICIONALES_DETALLES -> askWithText(p, OnboardingStep.ASK_NOTAS_ADICIONALES_DETALLES, M_44);
            case ASK_FOTOS -> askWithButtons(p, OnboardingStep.ASK_FOTOS, M_20, M_20_OPTIONS);
            case ASK_EXCESO_FOTOS -> askWithButtons(p, OnboardingStep.ASK_EXCESO_FOTOS, M_EXCESO_FOTOS, M_EXCESO_FOTOS_OPTIONS);
            case ASK_MAS_FOTOS -> sendText(from, M_21);
            case ASK_METODO_PAGO -> askWithButtons(p, OnboardingStep.ASK_METODO_PAGO, M_METODO_PAGO, M_METODO_PAGO_OPTIONS);
            case ASK_CODIGO_DESCUENTO -> askWithButtons(p, OnboardingStep.ASK_CODIGO_DESCUENTO, M_CODIGO_DESCUENTO, M_CODIGO_DESCUENTO_OPTIONS);
            case ASK_INGRESAR_CODIGO -> askWithText(p, OnboardingStep.ASK_INGRESAR_CODIGO, M_INGRESAR_CODIGO);
            case PROCESS_PAYMENT -> sendText(from, "Por favor realiza el pago y escribe *PAGADO* cuando termines.");
            default -> sendText(from, "Continuemos donde te quedaste. ¿En qué puedo ayudarte?");
        }
    }


    public void processImage(String from, Image image) {
        // Obtener lock exclusivo para este usuario
        Object lock = userLocks.computeIfAbsent(from, k -> new Object());

        synchronized (lock) {
            processImageSync(from, image);
        }
    }

    private void processImageSync(String from, Image image) {
        // Re-cargar paciente FRESCO dentro del bloque sincronizado
        Patient p = patientRepository.findByWhatsappId(from)
                .orElseGet(() -> getOrCreatePatient(from));

        // NUEVA VALIDACIÓN: Si ya está en ASK_EXCESO_FOTOS, ignorar silenciosamente
        if (p.getCurrentStep() == OnboardingStep.ASK_EXCESO_FOTOS) {
            log.info("Imagen ignorada para {} - esperando respuesta de exceso de fotos", from);
            return; // No enviar nada, solo ignorar
        }

        // Validar paso correcto
        if (p.getCurrentStep() != OnboardingStep.ASK_MAS_FOTOS) {
            log.warn("Imagen recibida fuera del flujo de fotos. Paso actual: {}", p.getCurrentStep());
            sendText(from, "⚠️ No se esperaban imágenes en este momento. Por favor sigue el flujo actual.");
            return;
        }

        // Validar límite con datos FRESCOS
        int currentCount = p.getPhotoUrls() != null ? p.getPhotoUrls().size() : 0;
        if (currentCount >= 5) {
            log.info("Imagen ignorada para {} - ya tiene {} fotos", from, currentCount);

            // Cancelar tareas pendientes
            java.util.concurrent.ScheduledFuture<?> existing = pendingResponses.remove(from);
            if (existing != null && !existing.isDone()) {
                existing.cancel(false);
            }

            // Enviar mensaje y opciones
            askWithButtons(p, OnboardingStep.ASK_EXCESO_FOTOS, M_EXCESO_FOTOS, M_EXCESO_FOTOS_OPTIONS);
            return;
        }


        // Guardar imagen
        LocalDateTime now = LocalDateTime.now();
        String mockUrl = "https://mock-fotos.com/foto_" + image.getId() + ".jpg";
        p.getPhotoUrls().add(mockUrl);
        p.setLastImageReceivedAt(now);

        // FLUSH inmediato para que el siguiente hilo vea el cambio
        patientRepository.save(p);
        patientRepository.flush();

        int newCount = p.getPhotoUrls().size();
        log.info("Imagen {} de 5 recibida para {} → {}", newCount, from, mockUrl);

        // Si alcanzó el límite, avanzar directo
        if (newCount >= 5) {
            java.util.concurrent.ScheduledFuture<?> existing = pendingResponses.remove(from);
            if (existing != null && !existing.isDone()) {
                existing.cancel(false);
            }
            askWithButtons(p, OnboardingStep.ASK_EXCESO_FOTOS,
                    "✅ 5 imagen(es) guardada(s) correctamente.\n📊 Total: 5 de 5\n\n⚠️ Has alcanzado el límite máximo.",
                    M_EXCESO_FOTOS_OPTIONS);
            return;
        }

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
            if (p == null || p.getCurrentStep() != OnboardingStep.ASK_MAS_FOTOS) return;

            int totalFotos = p.getPhotoUrls().size();

            // Si ya pasó a pago, no hacer nada
            if (totalFotos >= 5) {
                pendingResponses.remove(whatsappId);
                return;
            }

            String mensaje = String.format("✅ %d imagen(es) guardada(s) correctamente.\n📊 Total: %d de 5",
                    totalFotos, totalFotos);

            sendText(whatsappId, mensaje);
            askWithButtons(p, OnboardingStep.ASK_MAS_FOTOS, "¿Deseas cargar más imágenes?", M_22_OPTIONS);

            pendingResponses.remove(whatsappId);
        } catch (Exception e) {
            log.error("Error en respuesta diferida de imágenes para {}", whatsappId, e);
        }
    }


    // ====================== TODOS LOS HANDLERS ======================

    public void handleWelcomeMessage(Patient p) {
        p.setCurrentStep(OnboardingStep.START);
        save(p);
        whatsAppClient.sendWelcomeImage(p.getWhatsappId(), M_WELCOME);
        sendText(p.getWhatsappId(), M_TERMINOS);
        askWithButtons(p, OnboardingStep.START, M_ACCEPT_TERMINOS, M_TERMINOS_OPTIONS);
    }

    private void handleStart(Patient p, String text) {
        String selected = getSelectedOption(text, M_TERMINOS_OPTIONS);
        log.info("Opcion de aceptar terminos y condiciones: " + selected);
        if (selected == null) {
            invalidOption(p);
            return;
        }
        if ("No".equals(selected)) {
            sendText(p.getWhatsappId(), M_NO_ACCEPT_TERMINOS);
            askWithButtons(p, OnboardingStep.START, M_ACCEPT_TERMINOS, M_TERMINOS_OPTIONS);
            return;
        }
        // Usuario aceptó términos y condiciones
        p.setAceptaTerminosYPrivacidad(true);
        save(p);
        askWithList(p, OnboardingStep.ASK_PADECIMIENTO, M_1, "Ver opciones", M_1_OPTIONS, "motivo");
    }

    private void handlePadecimiento(Patient p, String text) {
        String selected = getSelectedOption(text, M_1_OPTIONS);
        if (selected == null) {
            invalidOption(p);
            return;
        }
        p.setPadecimiento(mapPadecimiento(selected));
        p.setCurrentStep(OnboardingStep.CONFIRM_PADECIMIENTO);
        save(p);
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

    private void handleConfirmPadecimiento(Patient p, String text) {
        String selected = getSelectedOption(text, M_10_OPTIONS);
        if (!selected.equalsIgnoreCase("Sí")) {
            askWithList(p, OnboardingStep.ASK_PADECIMIENTO, M_1, "Ver opciones", M_1_OPTIONS, "motivo");
            return;
        }
        askWithText(p, OnboardingStep.ASK_EMAIL, M_2);

    }

    private void handleEmail(Patient p, String text) {
        if (!text.matches("^[\\w-\\.]+@([\\w-]+\\.)+[\\w-]{2,4}$")) {
            sendText(p.getWhatsappId(), "Por favor ingresa un correo válido (ejemplo: nombre@dominio.com), NOTA: sin espacios, ni acentos.");
            return;
        }
        p.setEmail(text.trim().toLowerCase());
        p.setCurrentStep(OnboardingStep.CONFIRM_EMAIL);
        save(p);
        sendText(p.getWhatsappId(), M_3 + "\n\n" + text.trim().toLowerCase() + "\n\n¿Es correcto?");
        sendButtons(p, "¿Confirmas tu correo?", List.of("👍Sí", "👎No"));
    }

    private void handleConfirmEmail(Patient p, String text) {
        String selected = getSelectedOption(text, M_10_OPTIONS);
        if (!selected.equalsIgnoreCase("Sí")) {
            askWithText(p, OnboardingStep.ASK_EMAIL, M_2);
            return;
        }
        
        // ========== VERIFICAR PACIENTE EN API EXTERNA ==========
        log.info("📧 Email confirmado. Verificando paciente en API externa...");
        verificarPacienteEnApiExterna(p);
        // ========== FIN VERIFICACIÓN ==========
        
        askWithButtons(p, OnboardingStep.ASK_MAYORIA_EDAD, M_4, M_4_OPTIONS);
    }

    private void handleMayoriaEdad(Patient p, String text) {
        String selected = getSelectedOption(text, M_4_OPTIONS);

        log.info("Opción seleccionada en mayoría de edad: '{}' → mapeado a: '{}'", text, selected);

        // Si no se reconoce la opción
        if (selected == null) {
            sendText(p.getWhatsappId(), "Por favor elige una opción válida.");
            askWithButtons(p, OnboardingStep.ASK_MAYORIA_EDAD, M_4, M_4_OPTIONS);
            return;
        }

        // Si es menor de edad
        if (selected.equals("Menor de edad")) {
            sendText(p.getWhatsappId(), "Lo sentimos, debes ser mayor de 18 años o estar autorizado para continuar.\nConsulta terminada.");
            p.setCurrentStep(OnboardingStep.WELCOME);
            save(p);
            return;
        }

        // Si es válido
        boolean paraOtraPersona = selected.equals("Para otra persona.");
        p.setConsultaParaOtraPersona(paraOtraPersona);

        String msg = paraOtraPersona
                ? "¿Cuál es el nombre completo de la persona para quien es la consulta?"
                : M_5;

        askWithText(p, OnboardingStep.ASK_NOMBRE, msg);
    }

    private void handleNombre(Patient p, String text) {
        p.setNombreCompleto(text.trim());
        askWithButtons(p, OnboardingStep.ASK_GENERO, M_6, M_6_OPTIONS);
    }

    private void handleGenero(Patient p, String text) {
        String selected = getSelectedOption(text, M_6_OPTIONS);
        if (selected == null) {
            invalidOption(p);
            return;
        }
        p.setGenero(selected);
        askWithText(p, OnboardingStep.ASK_FECHA_NAC, M_7);
    }

    private void handleFechaNac(Patient p, String text) {
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
                p.setCurrentStep(OnboardingStep.WELCOME);
                save(p);
                return;
            }
            p.setFechaNacimiento(fecha);
            askWithText(p, OnboardingStep.ASK_PESO, M_8);

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

    private void handlePeso(Patient p, String text) {
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
            askWithText(p, OnboardingStep.ASK_ALTURA, M_9);

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

    private void handleAltura(Patient p, String text) {
        try {
            double altura = Double.parseDouble(text.trim().replace(",", "."));
            if (altura > 2.5) {
                sendText(p.getWhatsappId(), "Ingresa una altura realista (ej. 1.70)");
                return;
            }
            p.setAlturaM(altura);
            askWithButtons(p, OnboardingStep.ASK_FUMA, M_10, M_10_OPTIONS);
        } catch (Exception e) {
            sendText(p.getWhatsappId(), "Ingresa un número válido (ej. 1.70)");
        }
    }

    private void handleFuma(Patient p, String text) {
        String selected = getSelectedOption(text, M_10_OPTIONS);
        if (selected == null) {
            invalidOption(p);
            return;
        }
        p.setFuma("Sí".equalsIgnoreCase(selected));
        save(p);
        String padecimiento = p.getPadecimiento();
        if (padecimiento != null && padecimiento.toLowerCase().contains("anti-edad".toLowerCase())) {
            askWithList(p, OnboardingStep.ASK_MEJORA_PRINCIPAL, M_26, "Elegir mejora", M_26_OPTIONS, "mejora_principal");
        } else {
            askWithList(p, OnboardingStep.ASK_DESDE_CUANDO, M_11, "Elegir tiempo", M_11_OPTIONS, "desde_cuando");
        }
    }

    private void handleDesdeCuando(Patient p, String text) {
        String selected = getSelectedOption(text, M_11_OPTIONS);
        if (selected == null) {
            invalidOption(p);
            return;
        }
        p.setDesdeCuando(selected);
        p.setCurrentStep(nextStepAfterDesdeCuando(p.getPadecimiento()));
        save(p);
        sendNextQuestionAfterDesdeCuando(p);
    }

    private void sendNextQuestionAfterDesdeCuando(Patient p) {
        switch (p.getCurrentStep()) {
            case ASK_GRAVEDAD -> {
                var options = getGravedadOptions(p.getPadecimiento());
                if (options.size() > 3) {
                    askWithList(p, OnboardingStep.ASK_GRAVEDAD,
                            getGravedadMessage(p.getPadecimiento()),
                            "👉Seleccionar opción",
                            options,
                            "gravedad_" + p.getPadecimiento().toLowerCase().replace(" ", "_"));
                } else {
                    askWithButtons(p, OnboardingStep.ASK_GRAVEDAD,
                            getGravedadMessage(p.getPadecimiento()), options);
                }
            }
            case ASK_MEJORA_PRINCIPAL -> {
                // 4 opciones → también lista
                askWithList(p, OnboardingStep.ASK_MEJORA_PRINCIPAL,
                        M_26, "Elegir mejora", M_26_OPTIONS, "mejora_principal");
            }
            case ASK_AREA_CAIDA -> {
                // Solo 3 → botones están bien
                askWithButtons(p, OnboardingStep.ASK_AREA_CAIDA, M_34, M_34_OPTIONS);
            }
            default -> askWithButtons(p, OnboardingStep.ASK_TRATAMIENTO_ANTERIOR, M_13, M_13_OPTIONS);
        }
    }

    private void handleGravedad(Patient p, String text) {
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
        askWithButtons(p, OnboardingStep.ASK_TRATAMIENTO_ANTERIOR, M_13, M_13_OPTIONS);
    }

    private void handleTratamientoAnterior(Patient p, String text) {
        String selected = getSelectedOption(text, M_13_OPTIONS);
        if (selected == null) {
            invalidOption(p);
            return;
        }
        p.setTratamientoAnterior("Sí".equalsIgnoreCase(selected));
        if (p.getTratamientoAnterior()) {
            askWithText(p, OnboardingStep.ASK_TRATAMIENTOS_USADOS, M_14);
        } else {
            askWithButtons(p, OnboardingStep.ASK_ALERGIAS, M_15, M_15_OPTIONS);
        }
    }

    private void handleTratamientosUsados(Patient p, String text) {
        p.setTratamientosUsados(text.trim());
        askWithButtons(p, OnboardingStep.ASK_ALERGIAS, M_15, M_15_OPTIONS);
    }

    private void handleAlergias(Patient p, String text) {
        String selected = getSelectedOption(text, M_15_OPTIONS);
        if (selected == null) {
            invalidOption(p);
            return;
        }
        p.setAlergias("Sí".equalsIgnoreCase(selected));
        if (p.getAlergias()) {
            askWithText(p, OnboardingStep.ASK_ALERGIAS_DETALLES, M_16);
        } else {
            askWithButtons(p, OnboardingStep.ASK_MEDICAMENTOS, M_17, M_17_OPTIONS);
        }
    }

    private void handleAlergiasDetalles(Patient p, String text) {
        p.setAlergiasDetalles(text.trim());
        askWithButtons(p, OnboardingStep.ASK_MEDICAMENTOS, M_17, M_17_OPTIONS);
    }

    private void handleMedicamentos(Patient p, String text) {
        String selected = getSelectedOption(text, M_17_OPTIONS);
        if (selected == null) {
            invalidOption(p);
            return;
        }
        p.setMedicamentos("Sí".equalsIgnoreCase(selected));
        if (p.getMedicamentos()) {
            askWithText(p, OnboardingStep.ASK_MEDICAMENTOS_DETALLES, M_18);
        } else {
            askWithButtons(p, OnboardingStep.ASK_ENFERMEDADES, M_29, M_29_OPTIONS);
        }
    }

    private void handleMedicamentosDetalles(Patient p, String text) {
        p.setMedicamentosDetalles(text.trim());
        askWithButtons(p, OnboardingStep.ASK_ENFERMEDADES, M_29, M_29_OPTIONS);
    }

    private void handleEnfermedades(Patient p, String text) {
        String selected = getSelectedOption(text, M_29_OPTIONS);
        if (selected == null) {
            invalidOption(p);
            return;
        }
        p.setEnfermedades("Sí".equalsIgnoreCase(selected));
        if (p.getEnfermedades()) {
            askWithText(p, OnboardingStep.ASK_ENFERMEDADES_DETALLES, M_43);
        } else {
            goToNextAfterEnfermedades(p);
        }
    }

    private void handleEnfermedadesDetalles(Patient p, String text) {
        p.setEnfermedadesDetalles(text.trim());
        goToNextAfterEnfermedades(p);
    }

    private void goToNextAfterEnfermedades(Patient p) {
        if ("Femenino".equals(p.getGenero())) {
            askWithList(
                    p,
                    OnboardingStep.ASK_STATUS_EMBARAZO,
                    M_33,
                    "👉Seleccionar opción",
                    M_33_OPTIONS,
                    "status_embarazo"   // ← clave única para el mapeo
            );
        } else {
            goToNotasAdicionales(p);
        }
    }

    // ==================== RAMAS ESPECÍFICAS ====================

    private void handleMejoraPrincipal(Patient p, String text) {
        String selected = getSelectedOption(text, M_26_OPTIONS);
        if (selected == null) {
            invalidOption(p);
            return;
        }
        p.setMejoraPrincipal(selected);
        askWithList(
                p,
                OnboardingStep.ASK_TIPO_PIEL,
                M_27,
                "👉Seleccionar tipo",
                M_27_OPTIONS,
                "tipo_piel"
        );
    }

    private void handleTipoPiel(Patient p, String text) {
        String selected = getSelectedOption(text, M_27_OPTIONS);
        if (selected == null) {
            invalidOption(p);
            return;
        }
        p.setTipoPiel(selected);
        askWithList(
                p,
                OnboardingStep.ASK_SENSIBILIDAD_PIEL,
                M_28,
                "👉Seleccionar opción",
                M_28_OPTIONS,
                "sensibilidad_piel"
        );

    }

    private void handleSensibilidadPiel(Patient p, String text) {
        String selected = getSelectedOption(text, M_28_OPTIONS);
        if (selected == null) {
            invalidOption(p);
            return;
        }
        p.setSensibilidadPiel(selected);
        askWithList(
                p,
                OnboardingStep.ASK_EXPOSICION_SOL,
                M_30,
                "👉Seleccionar opción",
                M_30_OPTIONS,
                "exposicion_sol"
        );
    }

    private void handleExposicionSol(Patient p, String text) {
        String selected = getSelectedOption(text, M_30_OPTIONS);
        if (selected == null) {
            invalidOption(p);
            return;
        }
        p.setExposicionSol(selected);
        askWithList(
                p,
                OnboardingStep.ASK_USA_PROTECTOR,
                M_31,
                "👉Seleccionar opción",
                M_31_OPTIONS,
                "uso-protector"
        );
    }

    private void handleUsaProtector(Patient p, String text) {
        String selected = getSelectedOption(text, M_31_OPTIONS);
        if (selected == null) {
            invalidOption(p);
            return;
        }
        p.setUsaProtector(selected);
        askWithButtons(p, OnboardingStep.ASK_ALERGIAS, M_15, M_15_OPTIONS);
    }

    private void handleAreaCaida(Patient p, String text) {
        String selected = getSelectedOption(text, M_34_OPTIONS);
        if (selected == null) {
            invalidOption(p);
            return;
        }
        p.setAreaCaida(selected);
        askWithList(
                p,
                OnboardingStep.ASK_ANTECEDENTES_FAMILIA,
                M_35,
                "👉Seleccionar opción",
                M_35_OPTIONS,
                "antecedentes_familia"
        );
    }

    private void handleAntecedentesFamilia(Patient p, String text) {
        p.setAntecedentesFamiliares(text.trim());
        askWithButtons(p, OnboardingStep.ASK_ALERGIAS, M_15, M_15_OPTIONS);
    }

    private void handleStatusEmbarazo(Patient p, String text) {
        String selected = getSelectedOption(text, M_33_OPTIONS);
        if (selected == null) {
            invalidOption(p);
            return;
        }
        p.setStatusEmbarazo(selected);
        goToNotasAdicionales(p);
    }

    private void goToNotasAdicionales(Patient p) {
        askWithButtons(p, OnboardingStep.ASK_NOTAS_ADICIONALES, getNotasMessage(p.getPadecimiento()), M_15_OPTIONS);
    }

    private void handleNotasAdicionales(Patient p, String text) {
        String selected = getSelectedOption(text, M_15_OPTIONS);
        if (selected == null) {
            invalidOption(p);
            return;
        }
        if ("Sí".equalsIgnoreCase(selected)) {
            askWithText(p, OnboardingStep.ASK_NOTAS_ADICIONALES_DETALLES, M_44);
            return;
        }
        // Toda la información recopilada, registrar en API externa ANTES de pedir fotos
        registrarConsultaEnApiExterna(p);
        askWithButtons(p, OnboardingStep.ASK_FOTOS, M_20, M_20_OPTIONS);
    }

    private void handleNotasAdicionalesDetalles(Patient p, String text) {
        p.setNotasAdicionales(text.trim());
        save(p);
        // Toda la información recopilada, registrar en API externa ANTES de pedir fotos
        registrarConsultaEnApiExterna(p);
        askWithButtons(p, OnboardingStep.ASK_FOTOS, M_20, M_20_OPTIONS);
    }

    private void handleFotos(Patient p, String text) {
        String selected = getSelectedOption(text, M_20_OPTIONS);
        if (selected == null) {
            invalidOption(p);
            return;
        }
        if ("Cargar ahora".equalsIgnoreCase(selected)) {
            p.setCurrentStep(OnboardingStep.ASK_MAS_FOTOS);
            save(p);
            sendText(p.getWhatsappId(), M_21);
        } else {
            goToPayment(p);
        }
    }

    private void handleMasFotos(Patient p, String text) {
        String selected = getSelectedOption(text, M_22_OPTIONS);
        if (selected == null) {
            invalidOption(p);
            return;
        }
        if ("Sí".equalsIgnoreCase(selected)) {
            sendText(p.getWhatsappId(), M_21);
        } else {
            goToPayment(p);
        }
    }

    private void handleExcesoFotos(Patient p, String text) {
        String selected = getSelectedOption(text, M_EXCESO_FOTOS_OPTIONS);
        if (selected == null) {
            invalidOption(p);
            return;
        }

        if (selected.equalsIgnoreCase("Continuar")) {
            // Continuar al pago con las 5 fotos actuales
            int totalFotos = p.getPhotoUrls().size();
            sendText(p.getWhatsappId(),
                    String.format("✅Excelente, se han guardado %d imagen(es) correctamente.\n\nProcedemos al pago.",
                            totalFotos, totalFotos));
            goToPayment(p);
        } else {
            // Reiniciar carga - limpiar fotos y volver a pedir
            p.getPhotoUrls().clear();
            p.setLastImageReceivedAt(null);
            p.setCurrentStep(OnboardingStep.ASK_MAS_FOTOS);
            save(p);
            sendText(p.getWhatsappId(),
                    "🔄 Se han eliminado todas las fotos.\n\n" + M_21);
        }
    }


    private void goToPayment(Patient p) {
        // En lugar de ir directo a pago, preguntar método de pago
        askWithButtons(p, OnboardingStep.ASK_METODO_PAGO, M_METODO_PAGO, M_METODO_PAGO_OPTIONS);
    }

    /**
     * Handler para cuando el usuario selecciona el método de pago (Stripe o MercadoPago)
     */
    private void handleMetodoPago(Patient p, String text) {
        String selected = getSelectedOption(text, M_METODO_PAGO_OPTIONS);
        
        if (selected == null) {
            sendText(p.getWhatsappId(), "❌ Por favor selecciona una opción válida usando los botones.");
            askWithButtons(p, OnboardingStep.ASK_METODO_PAGO, M_METODO_PAGO, M_METODO_PAGO_OPTIONS);
            return;
        }
        
        // Guardar la selección (0 = Stripe, 1 = MercadoPago)
        if (selected.contains("Stripe")) {
            p.setMetodoPagoElegido(0);
            log.info("💳 Usuario {} seleccionó Stripe", p.getWhatsappId());
        } else if (selected.contains("Mercado Pago")) {
            p.setMetodoPagoElegido(1);
            log.info("🛍️ Usuario {} seleccionó MercadoPago", p.getWhatsappId());
        }
        
        save(p);
        
        // Preguntar si tiene código de descuento
        askWithButtons(p, OnboardingStep.ASK_CODIGO_DESCUENTO, M_CODIGO_DESCUENTO, M_CODIGO_DESCUENTO_OPTIONS);
    }

    /**
     * Handler para cuando preguntamos si tiene código de descuento
     */
    private void handleCodigoDescuento(Patient p, String text) {
        String selected = getSelectedOption(text, M_CODIGO_DESCUENTO_OPTIONS);
        
        if (selected == null) {
            sendText(p.getWhatsappId(), "❌ Por favor selecciona una opción válida usando los botones.");
            askWithButtons(p, OnboardingStep.ASK_CODIGO_DESCUENTO, M_CODIGO_DESCUENTO, M_CODIGO_DESCUENTO_OPTIONS);
            return;
        }
        
        if (selected.contains("Sí")) {
            // Pedir que ingrese el código
            askWithText(p, OnboardingStep.ASK_INGRESAR_CODIGO, M_INGRESAR_CODIGO);
        } else {
            // No tiene código, proceder a generar link de pago
            p.setCodigoDescuento(null);
            save(p);
            procesarPago(p);
        }
    }

    /**
     * Handler para cuando el usuario ingresa su código de descuento
     */
    private void handleIngresarCodigo(Patient p, String text) {
        String codigo = text.trim().toUpperCase();
        
        if (codigo.isBlank()) {
            sendText(p.getWhatsappId(), "❌ Por favor escribe un código válido.");
            return;
        }
        
        // Guardar el código (validación se hará en el backend de pagos)
        p.setCodigoDescuento(codigo);
        save(p);
        
        log.info("🏷️ Usuario {} ingresó código de descuento: {}", p.getWhatsappId(), codigo);
        
        sendText(p.getWhatsappId(), "✅ Código *" + codigo + "* guardado. Validaremos el descuento al procesar tu pago.");
        
        // Proceder a generar link de pago
        procesarPago(p);
    }

    /**
     * Procesa el pago: integra con API externa y genera link según método elegido
     */
    private void procesarPago(Patient p) {
        p.setCurrentStep(OnboardingStep.PROCESS_PAYMENT);
        save(p);

        // ========== INTEGRACIÓN CON API EXTERNA: SUBIR FOTOS ==========
        // Nota: La consulta ya fue registrada antes de pedir las fotos
        // Ahora solo subimos las fotos si tenemos consultaId
        
        if (p.getConsultaId() != null && p.getPhotoUrls() != null && !p.getPhotoUrls().isEmpty()) {
            log.info("📸 Subiendo fotos a la API externa para consultaId: {}", p.getConsultaId());
            boolean fotosSubidas = subirFotosAApiExterna(p.getConsultaId(), p);
            
            if (!fotosSubidas) {
                log.warn("⚠️ Las fotos no se pudieron subir a la API externa");
                sendText(p.getWhatsappId(),
                    "⚠️ *Nota:* Tus fotos se procesarán manualmente por nuestro equipo médico. " +
                    "Esto no afecta tu consulta. 📸\n\n" +
                    "Continuemos con el pago. 💳"
                );
            } else {
                log.info("✅ Fotos subidas exitosamente a la API externa");
            }
        } else {
            log.warn("⚠️ No hay consultaId o fotos para subir a la API externa");
        }
        
        log.info("🔗 Integración con API externa completada");
        // ========== FIN INTEGRACIÓN ==========

        // Usar el consultId real si existe, o el whatsappId como fallback
        String paymentReferenceId = (p.getConsultaId() != null && !p.getConsultaId().isBlank()) 
            ? p.getConsultaId() 
            : p.getWhatsappId();

        // Generar link de pago según el método elegido
        String paymentUrl;
        String metodoPagoNombre;
        
        if (p.getMetodoPagoElegido() != null && p.getMetodoPagoElegido() == 1) {
            // MercadoPago
            metodoPagoNombre = "Mercado Pago";
            log.info("💳 Generando link de pago con MercadoPago para {}", p.getWhatsappId());
            paymentUrl = mercadoPagoService.crearPaymentLink(
                paymentReferenceId,
                p.getWhatsappId(),
                p.getEmail()
            );
        } else {
            // Stripe (por defecto)
            metodoPagoNombre = "Stripe";
            log.info("💳 Generando link de pago con Stripe para {}", p.getWhatsappId());
            paymentUrl = stripeService.crearPaymentLink(
                paymentReferenceId,
                p.getWhatsappId(),
                p.getEmail()
            );
        }

        if (paymentUrl == null || paymentUrl.isBlank()) {
            sendText(p.getWhatsappId(), "⚠️ Ocurrió un problema al generar el enlace de pago. Por favor intenta más tarde o escribe *HOLA* para reiniciar.");
            p.setCurrentStep(OnboardingStep.WELCOME);
            save(p);
            return;
        }

        // Guardar el URL de pago
        p.setPaymentUrl(paymentUrl);
        save(p);

        // Mensaje personalizado según el método de pago
        String mensajePago = String.format(
            "¡Todo listo! 🎉\n\n" +
            "Solo falta realizar el pago de tu consulta dermatológica.\n\n" +
            "💳 Costo: $360 MXN (impuestos incluidos)\n" +
            "🔒 Pago 100%% seguro procesado por %s\n\n" +
            "Da clic en el botón para pagar:",
            metodoPagoNombre
        );

        // Mensaje con botón grande azul
        whatsAppClient.sendCtaUrlButton(
                p.getWhatsappId(),
                mensajePago,
                "Pagar $360 💳",
                paymentUrl
        );

        // Mensaje adicional (opcional) para reforzar
        sendText(p.getWhatsappId(),
                "Tan pronto completes el pago, recibirás automáticamente un mensaje de confirmación y el acceso a tu panel de paciente.\n\n" +
                        "¡Gracias por confiar en Elara! 💙");
    }

    private void handlePayment(Patient p, String text) {
        if (!text.equalsIgnoreCase("PAGADO")) {
            sendText(p.getWhatsappId(), M_25);
            return;
        }
        p.setPagoProcesado(true);
        p.setCurrentStep(OnboardingStep.WELCOME);
        save(p);
        sendText(p.getWhatsappId(), M_24 + "\n\nhttps://panel.tuclinica.com/patient/" + p.getWhatsappId() + "\n\n Si deseas realizar una consulta nueva, escribe: hola");
    }

    // ==================== DOCUMENTOS LEGALES Y PAGO ====================

    private void advanceToLegalDocuments(Patient p) {
        p.setCurrentStep(OnboardingStep.ASK_TERMINOS);
        save(p);
        sendDocument(p.getWhatsappId(), TERMINOS_URL, "Términos y Condiciones.pdf");
        sendText(p.getWhatsappId(), "¿Aceptas los Términos y Condiciones?\n\nResponde *Sí* o *No*");
    }

    private void handleTerminos(Patient p, String text) {
        if (!text.equalsIgnoreCase("Sí") && !text.equalsIgnoreCase("Si")) {
            sendText(p.getWhatsappId(), "Debes aceptar los Términos y Condiciones para continuar.");
            return;
        }
        p.setCurrentStep(OnboardingStep.ASK_AVISO_PRIVACIDAD);
        save(p);
        sendDocument(p.getWhatsappId(), AVISO_PRIVACIDAD_URL, "Aviso de Privacidad.pdf");
        sendText(p.getWhatsappId(), "¿Aceptas el Aviso de Privacidad?\n\nResponde *Sí* o *No*");
    }

    private void handleAvisoPrivacidad(Patient p, String text) {
        if (!text.equalsIgnoreCase("Sí") && !text.equalsIgnoreCase("Si")) {
            sendText(p.getWhatsappId(), "Debes aceptar el Aviso de Privacidad para continuar.");
            return;
        }
        p.setCurrentStep(OnboardingStep.ASK_CONSENTIMIENTO);
        save(p);
        sendDocument(p.getWhatsappId(), CONSENTIMIENTO_URL, "Consentimiento Informado de Telemedicina.pdf");
        sendText(p.getWhatsappId(), "¿Aceptas el Consentimiento Informado de Telemedicina?\n\nResponde *Sí* o *No*");
    }

    private void handleConsentimiento(Patient p, String text) {
        if (!text.equalsIgnoreCase("Sí") && !text.equalsIgnoreCase("Si")) {
            sendText(p.getWhatsappId(), "Debes aceptar el Consentimiento para continuar.");
            return;
        }
        p.setCurrentStep(OnboardingStep.PROCESS_PAYMENT);
        save(p);
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
        String normalized = whatsappId.replaceFirst("^521?", "521"); // asegura formato 521...

        return patientRepository.findByWhatsappId(normalized)
                .orElseGet(() -> {
                    Patient nuevo = Patient.builder()
                            .whatsappId(normalized)
                            .currentStep(OnboardingStep.WELCOME)
                            .photoUrls(new ArrayList<>())
                            .createdAt(LocalDateTime.now())
                            .build();
                    return patientRepository.save(nuevo);
                });
    }

    private void askWithButtons(Patient p, OnboardingStep nextStep, String message, List<String> options) {
        p.setCurrentStep(nextStep);
        save(p);
        sendButtons(p, adaptarMensaje(p, message), options);
    }

    private void askWithText(Patient p, OnboardingStep nextStep, String message) {
        p.setCurrentStep(nextStep);
        save(p);
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
        log.info("processListSelection → listId: '{}'", listId);

        if (listId == null || listId.isBlank()) {
            sendText(from, "Error: opción inválida.");
            return;
        }

        Patient p = patientRepository.findByWhatsappId(from)
                .orElseThrow(() -> new RuntimeException("Paciente no encontrado: " + from));

        String context = p.getLastListContext();

        if (context == null) {
            log.error("lastListContext es NULL para {} – aunque la columna existe", from);
            sendText(from, "Error interno. Escribe *HOLA* para reiniciar.");
            return;
        }

        // ... resto del código igual (ya está perfecto)
        if (!listId.startsWith(context + "_")) {
            log.warn("listId '{}' no coincide con contexto '{}'", listId, context);
            sendText(from, "Error de flujo. Escribe *HOLA* para reiniciar.");
            return;
        }

        int index = Integer.parseInt(listId.substring(listId.lastIndexOf("_") + 1)) - 1;
        List<String> options = getOptionsForContext(context);

        if (index < 0 || index >= options.size()) {
            sendText(from, "Opción inválida.");
            return;
        }

        String selected = options.get(index);
        log.info("✓ Selección correcta → contexto: {}, opción: {}", context, selected);

        processText(from, selected);  // ← aquí sigue el flujo normal
    }

    // ==== NUEVO: askWithList GENÉRICO ====

    private void askWithList(
            Patient p,
            OnboardingStep nextStep,
            String bodyText,
            String buttonText,
            List<String> options,
            String contextKey
    ) {
        p.setCurrentStep(nextStep);
        p.setLastListContext(contextKey);
        save(p);

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

    // ====================== INTEGRACIÓN CON API EXTERNA DE ELARA ======================

    /**
     * Verifica si el paciente ya existe en la API externa
     * Se llama después de confirmar el email
     * 
     * @param patient Paciente con whatsappId y email
     */
    private void verificarPacienteEnApiExterna(Patient patient) {
        try {
            log.info("🔍 Verificando paciente en API externa...");
            log.info("   WhatsApp ID: {}", patient.getWhatsappId());
            log.info("   Email: {}", patient.getEmail());
            
            // Crear request
            com.bot.elara.Infrastructure.External.Clients.ElaraApi.dto.request.VerifyPatientRequest request =
                com.bot.elara.Infrastructure.External.Clients.ElaraApi.dto.request.VerifyPatientRequest.builder()
                    .whatsappId(patient.getWhatsappId())
                    .email(patient.getEmail())
                    .build();
            
            // Llamar al Feign Client: POST https://www.sv-lara.com/api/patient/verify/
            com.bot.elara.Infrastructure.External.Clients.ElaraApi.dto.response.VerifyPatientResponse response =
                elaraPatientClient.verifyPatient(request);
            
            // Procesar respuesta según nuevo formato
            if (response != null && response.getCode() != null) {
                
                if (response.getCode() == 200) {
                    log.info("✅ Respuesta exitosa de API externa (code: 200)");
                    log.info("   Paciente existe: {}", response.getExiste());
                    
                    if (response.getExiste() != null && response.getExiste()) {
                        log.info("👤 Paciente YA EXISTE en la base de datos de Elara");
                        // Guardar el patientApiId si lo devuelve la API (opcional)
                        if (response.getPatientId() != null) {
                            patient.setPatientApiId(response.getPatientId());
                            save(patient);
                            log.info("   Patient API ID guardado: {}", response.getPatientId());
                        }
                    } else {
                        log.info("👤 Paciente NUEVO - se registrará al finalizar el flujo");
                    }
                    
                } else if (response.getCode() == 400) {
                    log.warn("⚠️ Error de validación en API externa (code: 400)");
                    log.warn("   Tipo: {}", response.getType());
                    log.warn("   Error: {}", response.getError());
                    log.info("   Continuando flujo normalmente - paciente se guardará localmente");
                } else {
                    log.warn("⚠️ Código inesperado de API externa: {}", response.getCode());
                }
                
            } else {
                log.warn("⚠️ Respuesta null o sin código al verificar paciente");
            }
            
        } catch (Exception e) {
            // No bloqueamos el flujo si la verificación falla
            log.error("❌ Error al verificar paciente en API externa (continuando flujo)", e);
        }
        
        // IMPORTANTE: El paciente SIEMPRE se guarda en H2 (base de datos local)
        // independientemente de si la API externa responde o no.
        // Esto asegura que el flujo continúe sin problemas.
    }

    /**
     * Registra la consulta completa en la API externa
     * Debe llamarse DESPUÉS de recopilar toda la información y ANTES de pedir las fotos
     * 
     * @param patient Paciente con toda la información recopilada
     * @return consultaId generado por la API, o null si falló
     */
    private String registrarConsultaEnApiExterna(Patient patient) {
        try {
            log.info("📤 Registrando consulta en API externa para: {}", patient.getWhatsappId());
            
            // Formatear fecha de nacimiento al formato esperado: DD-MM-YYYY
            String fechaNacFormateada = null;
            if (patient.getFechaNacimiento() != null) {
                fechaNacFormateada = String.format("%02d-%02d-%04d",
                        patient.getFechaNacimiento().getDayOfMonth(),
                        patient.getFechaNacimiento().getMonthValue(),
                        patient.getFechaNacimiento().getYear());
            }
            
            // Determinar el tipo de consulta
            Integer tipoConsulta = 1; // Por defecto "para mí"
            if (Boolean.TRUE.equals(patient.getConsultaParaOtraPersona())) {
                tipoConsulta = 2; // "para otra persona"
            }
            // TODO: Agregar lógica para menor de edad (3) si aplica
            
            // Determinar aceptaciones (basadas en el paso inicial de términos)
            Boolean aceptaTerminos = Boolean.TRUE.equals(patient.getAceptaTerminosYPrivacidad());
            
            // Construir el request con todos los datos
            com.bot.elara.Infrastructure.External.Clients.ElaraApi.dto.request.RegisterConsultRequest request = 
                com.bot.elara.Infrastructure.External.Clients.ElaraApi.dto.request.RegisterConsultRequest.builder()
                    // Identificación
                    .whatsappId(patient.getWhatsappId())
                    .consultaId(null) // Siempre null en registro inicial según requerimiento
                    .padecimiento(patient.getPadecimiento())
                    .email(patient.getEmail())
                    .consulta(tipoConsulta)
                    
                    // Datos personales
                    .nombreCompleto(patient.getNombreCompleto())
                    .genero(patient.getGenero())
                    .fechaNacimiento(fechaNacFormateada)
                    .pesoKg(patient.getPesoKg())
                    .alturaM(patient.getAlturaM())
                    .fuma(patient.getFuma())
                    
                    // Historial médico
                    .desdeCuando(patient.getDesdeCuando())
                    .tratamientoAnterior(patient.getTratamientoAnterior())
                    .tratamientosUsados(patient.getTratamientosUsados())
                    .alergias(patient.getAlergias())
                    .alergiasDetalles(patient.getAlergiasDetalles())
                    .medicamentos(patient.getMedicamentos())
                    .medicamentosDetalles(patient.getMedicamentosDetalles())
                    .enfermedades(patient.getEnfermedades())
                    .enfermedadesDetalles(patient.getEnfermedadesDetalles())
                    .notadermatologo(patient.getNotasAdicionales())
                    
                    // Campos condicionales según padecimiento
                    .areaCaida(patient.getAreaCaida())
                    .antecedentesFamiliares(patient.getAntecedentesFamiliares())
                    .gravedadAcne(patient.getGravedadAcne())
                    .gravedadManchas(patient.getGravedadManchas())
                    .gravedadRosacea(patient.getGravedadRosacea())
                    .mejoraPrincipal(patient.getMejoraPrincipal())
                    .tipoPiel(patient.getTipoPiel())
                    .sensibilidadPiel(patient.getSensibilidadPiel())
                    .exposicionSol(patient.getExposicionSol())
                    .usaProtector(patient.getUsaProtector())
                    .statusEmbarazo(patient.getStatusEmbarazo())
                    
                    // Fotos: array vacío porque se suben DESPUÉS del registro
                    .photoUrls(new ArrayList<>())
                    
                    // Notas adicionales
                    .notasAdicionales(patient.getNotasAdicionales())
                    
                    // Aceptaciones basadas en términos iniciales
                    .aceptaTerminos(aceptaTerminos)
                    .aceptaAvisoPrivacidad(aceptaTerminos)
                    .aceptaConsentimiento(aceptaTerminos)
                    
                    // Pago (aún no procesado al momento del registro)
                    .pagoProcesado(false)
                    .pagoId(null)
                    
                    // Metadata
                    .canalOrigen("WHATSAPP")
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            
            log.info("📋 Request preparado - Enviando a POST /api/consult/registry/");
            
            // Llamar al Feign Client: POST https://www.sv-lara.com/api/consult/registry/
            com.bot.elara.Infrastructure.External.Clients.ElaraApi.dto.response.RegisterConsultResponse response = 
                elaraPatientClient.registerConsult(request);
            
            // Verificar respuesta según formato de la API
            if (response != null && response.getCode() != null) {
                
                if (response.getCode() == 200) {
                    // Éxito: {"code": 200, "status": "ok", "mensaje": "...", "consultaId": "..."}
                    log.info("✅ Consulta registrada exitosamente (code: 200)");
                    log.info("   Status: {}", response.getStatus());
                    log.info("   Mensaje: {}", response.getMensaje());
                    log.info("   Consulta ID: {}", response.getConsultaId());
                    
                    if (response.getConsultaId() != null) {
                        // Guardar el consultaId en el paciente local
                        patient.setConsultaId(response.getConsultaId());
                        save(patient);
                        return response.getConsultaId();
                    } else {
                        log.warn("⚠️ Respuesta 200 pero consultaId es null");
                        return null;
                    }
                    
                } else if (response.getCode() == 400) {
                    // Error validación: {"code": 400, "type": "validation_error", "mensaje": "..."}
                    log.error("❌ Error de validación al registrar consulta (code: 400)");
                    log.error("   Type: {}", response.getType());
                    log.error("   Mensaje: {}", response.getMensaje());
                    return null;
                    
                } else {
                    log.error("❌ Código inesperado al registrar consulta: {}", response.getCode());
                    log.error("   Mensaje: {}", response.getMensaje());
                    return null;
                }
                
            } else {
                log.error("❌ Respuesta null o sin código al registrar consulta");
                return null;
            }
            
        } catch (Exception e) {
            log.error("❌ Excepción al registrar consulta en API externa", e);
            return null;
        }
    }

    /**
     * Sube las fotos a la API externa usando el consultaId
     * 
     * @param consultId ID de consulta obtenido del registro
     * @param patient Paciente con las URLs de las fotos
     * @return true si se subieron exitosamente, false si falló
     */
    private boolean subirFotosAApiExterna(String consultId, Patient patient) {
        if (consultId == null || consultId.isBlank()) {
            log.error("❌ No se puede subir fotos sin consultId");
            return false;
        }
        
        if (patient.getPhotoUrls() == null || patient.getPhotoUrls().isEmpty()) {
            log.warn("⚠️ No hay fotos para subir");
            return true; // No es error, simplemente no hay fotos
        }
        
        try {
            log.info("📤 Subiendo {} fotos a la API externa para consulta: {}", 
                    patient.getPhotoUrls().size(), consultId);
            
            // Convertir las URLs de WhatsApp a Base64
            List<com.bot.elara.Infrastructure.External.Clients.ElaraApi.dto.request.UploadPhotosRequest.PhotoData> photos = 
                new ArrayList<>();
            
            int photoNumber = 1;
            for (String photoUrl : patient.getPhotoUrls()) {
                try {
                    log.debug("📸 Procesando foto {}/{}: {}", photoNumber, patient.getPhotoUrls().size(), photoUrl);
                    
                    // ✅ DESCARGA REAL de la foto desde WhatsApp
                    String base64Data = whatsAppClient.descargarFotoComoBase64(photoUrl);
                    
                    if (base64Data == null || base64Data.isBlank()) {
                        log.error("❌ No se pudo descargar la foto {} desde WhatsApp", photoNumber);
                        // Continuar con las demás fotos en lugar de fallar todo
                        photoNumber++;
                        continue;
                    }
                    
                    // Determinar el tipo de contenido basándose en la foto
                    String contentType = "image/jpeg"; // Por defecto JPG
                    String extension = ".jpg";
                    
                    // Detectar tipo de imagen por los primeros bytes del Base64
                    if (base64Data.startsWith("/9j/")) {
                        contentType = "image/jpeg";
                        extension = ".jpg";
                    } else if (base64Data.startsWith("iVBORw")) {
                        contentType = "image/png";
                        extension = ".png";
                    }
                    
                    // Crear el objeto PhotoData con la foto REAL
                    photos.add(com.bot.elara.Infrastructure.External.Clients.ElaraApi.dto.request.UploadPhotosRequest.PhotoData.builder()
                            .filename("lesion_" + photoNumber + extension)
                            .contentType(contentType)
                            .data(base64Data)
                            .build());
                    
                    log.info("✅ Foto {} descargada y convertida a Base64: {} caracteres", 
                            photoNumber, base64Data.length());
                    
                    photoNumber++;
                    
                } catch (Exception e) {
                    log.error("❌ Excepción al procesar foto {}: {}", photoNumber, photoUrl, e);
                    photoNumber++;
                    // Continuar con las demás fotos
                }
            }
            
            if (photos.isEmpty()) {
                log.error("❌ No se pudo procesar ninguna foto exitosamente");
                return false;
            }
            
            log.info("📦 Total de fotos preparadas para subir: {}/{}", 
                    photos.size(), patient.getPhotoUrls().size());
            
            // Crear el request
            com.bot.elara.Infrastructure.External.Clients.ElaraApi.dto.request.UploadPhotosRequest request = 
                com.bot.elara.Infrastructure.External.Clients.ElaraApi.dto.request.UploadPhotosRequest.builder()
                    .photos(photos)
                    .build();
            
            // Llamar al Feign Client
            com.bot.elara.Infrastructure.External.Clients.ElaraApi.dto.response.UploadPhotosResponse response = 
                elaraPatientClient.uploadPhotos(consultId, request);
            
            // Verificar respuesta
            if (response != null && response.getCode() == 200) {
                log.info("✅ Fotos subidas exitosamente: {}/{}", 
                        response.getTotalUploaded(), photos.size());
                
                if (response.getPhotoUrls() != null && !response.getPhotoUrls().isEmpty()) {
                    log.info("📷 URLs de fotos en la API:");
                    response.getPhotoUrls().forEach(url -> log.info("   - {}", url));
                    
                    // Opcional: Actualizar las URLs del paciente con las de la API
                    // patient.setPhotoUrls(response.getPhotoUrls());
                    // save(patient);
                }
                
                return true;
            } else {
                log.error("❌ Error al subir fotos: {}", response != null ? response.getMensaje() : "Respuesta null");
                return false;
            }
            
        } catch (Exception e) {
            log.error("❌ Excepción al subir fotos a API externa", e);
            return false;
        }
    }
}
