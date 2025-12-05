package com.bot.elara.Application.Service;

import com.bot.elara.Domain.Model.OnboardingStep;
import com.bot.elara.Domain.Model.Patient;
import com.bot.elara.Domain.Repository.PatientRepository;
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
    private final java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.ScheduledFuture<?>> pendingResponses = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ScheduledExecutorService imageScheduler = java.util.concurrent.Executors.newScheduledThreadPool(2);
    private final java.util.concurrent.ConcurrentHashMap<String, Object> userLocks = new java.util.concurrent.ConcurrentHashMap<>();

    // URLs reales de tus documentos (ponlas en S3 o en tu dominio)
    private static final String TERMINOS_URL = "https://tu-dominio.com/docs/terminos-y-condiciones.pdf";
    private static final String AVISO_PRIVACIDAD_URL = "https://tu-dominio.com/docs/aviso-de-privacidad.pdf";
    private static final String CONSENTIMIENTO_URL = "https://tu-dominio.com/docs/consentimiento-telemedicina.pdf";

    // ====================== ENTRY POINTS ======================

    public void processText(String from, String text) {
        Patient patient = getOrCreatePatient(from);
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
            case ASK_FOTOS -> handleFotos(patient, text);
            case ASK_MAS_FOTOS -> handleMasFotos(patient, text);
            case ASK_EXCESO_FOTOS -> handleExcesoFotos(patient, text);
            case ASK_TERMINOS -> handleTerminos(patient, text);
            case ASK_AVISO_PRIVACIDAD -> handleAvisoPrivacidad(patient, text);
            case ASK_CONSENTIMIENTO -> handleConsentimiento(patient, text);
            case PROCESS_PAYMENT -> handlePayment(patient, text);
            case COMPLETED ->
                    sendText(from, "¡Tu consulta ya está completada! Tu dermatóloga la revisará pronto. Te avisaremos cuando esté lista.");
            default -> sendText(from, "Algo salió mal. Escribe *HOLA* para reiniciar el proceso.");
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
            String mensaje = String.format(
                    "⚠️Ya tienes %d fotos cargadas (máximo permitido: 5).\n\n" +
                            "¿Deseas continuar con las fotos actuales o reiniciar la carga?",
                    currentCount
            );
            askWithButtons(p, OnboardingStep.ASK_EXCESO_FOTOS, mensaje, M_EXCESO_FOTOS_OPTIONS);
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
            sendText(p.getWhatsappId(), "Por favor ingresa un correo válido (ejemplo: nombre@dominio.com)");
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
            if (altura < 1.0 || altura > 2.5) {
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
        askWithText(p, OnboardingStep.ASK_NOTAS_ADICIONALES, getNotasMessage(p.getPadecimiento()));
    }

    private void handleNotasAdicionales(Patient p, String text) {
        p.setNotasAdicionales(text.trim());
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
        p.setCurrentStep(OnboardingStep.PROCESS_PAYMENT);
        save(p);
        String link = "https://pago.tuclinica.com/pay/" + p.getWhatsappId();
        sendText(p.getWhatsappId(),
                "¡Perfecto! Ya tenemos todo.\n\n" +
                        "Solo falta el pago de tu consulta:\n\n" +
                        link + "\n\n" +
                        "Costo: $650 MXN\n\n" +
                        "Cuando pagues, escribe: *PAGADO*");
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
}
