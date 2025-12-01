//package com.bot.elara.Application.Service;
//
//import com.bot.elara.Domain.Constants.MessageConstants;
//import com.bot.elara.Domain.Model.OnboardingStep;
//import com.bot.elara.Domain.Model.Patient;
//import com.bot.elara.Domain.Repository.PatientRepository;
//import com.bot.elara.Infrastructure.External.Clients.RespondIoClient;
//import com.bot.elara.Infrastructure.External.Storage.S3Service;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//
//import java.time.LocalDate;
//import java.time.LocalDateTime;
//import java.time.format.DateTimeFormatter;
//import java.time.format.DateTimeParseException;
//import java.util.List;
//
//import static com.bot.elara.Domain.Constants.MessageConstants.*;
//
//@Service
//@RequiredArgsConstructor
//@Transactional
//@Slf4j
//public class OnboardingRespondIoService {
//
//    private final PatientRepository patientRepository;
//    private final RespondIoClient respondIoClient;
//    private final S3Service s3Service;
//
//    public void processText(String contactId, String text) {
//        Patient patient = getOrCreatePatient(contactId);
//        OnboardingStep step = patient.getCurrentStep();
//
//        text = text.trim(); // Normalizar input
//
//        switch (step) {
//            case START -> handleStart(patient, contactId);
//            case ASK_MOTIVO -> handleMotivo(patient, text, contactId);
//            case ASK_EMAIL -> handleEmail(patient, text, contactId);
//            case CONFIRM_EMAIL -> handleConfirmEmail(patient, text, contactId);
//            case ASK_MAYORIA_EDAD -> handleMayoriaEdad(patient, text, contactId);
//            case ASK_NOMBRE -> handleNombre(patient, text, contactId);
//            case ASK_GENERO -> handleGenero(patient, text, contactId);
//            case ASK_FECHA_NAC -> handleFechaNac(patient, text, contactId);
//            case ASK_PESO -> handlePeso(patient, text, contactId);
//            case ASK_ALTURA -> handleAltura(patient, text, contactId);
//            case ASK_FUMA -> handleFuma(patient, text, contactId);
//            case ASK_DESDE_CUANDO -> handleDesdeCuando(patient, text, contactId);
//            case ASK_GRAVEDAD -> handleGravedad(patient, text, contactId);
//            case ASK_TRATAMIENTO_ANTERIOR -> handleTratamientoAnterior(patient, text, contactId);
//            case ASK_TRATAMIENTOS_USADOS -> handleTratamientosUsados(patient, text, contactId);
//            case ASK_ALERGIAS -> handleAlergias(patient, text, contactId);
//            case ASK_ALERGIAS_DETALLES -> handleAlergiasDetalles(patient, text, contactId);
//            case ASK_MEDICAMENTOS -> handleMedicamentos(patient, text, contactId);
//            case ASK_MEDICAMENTOS_DETALLES -> handleMedicamentosDetalles(patient, text, contactId);
//            case ASK_ENFERMEDADES -> handleEnfermedades(patient, text, contactId);
//            case ASK_ENFERMEDADES_DETALLES -> handleEnfermedadesDetalles(patient, text, contactId);
//            case ASK_MEJORA_PRINCIPAL -> handleMejoraPrincipal(patient, text, contactId);
//            case ASK_TIPO_PIEL -> handleTipoPiel(patient, text, contactId);
//            case ASK_SENSIBILIDAD_PIEL -> handleSensibilidadPiel(patient, text, contactId);
//            case ASK_EXPOSICION_SOL -> handleExposicionSol(patient, text, contactId);
//            case ASK_USA_PROTECTOR -> handleUsaProtector(patient, text, contactId);
//            case ASK_AREA_CAIDA -> handleAreaCaida(patient, text, contactId);
//            case ASK_ANTECEDENTES_FAMILIA -> handleAntecedentesFamilia(patient, text, contactId);
//            case ASK_STATUS_EMBARAZO -> handleStatusEmbarazo(patient, text, contactId);
//            case ASK_NOTAS_ADICIONALES -> handleNotasAdicionales(patient, text, contactId);
//            case ASK_FOTOS -> handleFotos(patient, text, contactId);
//            case ASK_MAS_FOTOS -> handleMasFotos(patient, text, contactId);
//            case ASK_TERMINOS -> handleTerminos(patient, text, contactId);
//            case ASK_AVISO_PRIVACIDAD -> handleAvisoPrivacidad(patient, text, contactId);
//            case ASK_CONSENTIMIENTO -> handleConsentimiento(patient, text, contactId);
//            case PROCESS_PAYMENT -> handlePayment(patient, text, contactId);
//            default -> sendMessage(contactId, "Proceso completado. ¡Gracias!");
//        }
//    }
//
//    public void processImage(String contactId, String imageUrl, String imageId) {
//        Patient patient = getOrCreatePatient(contactId);
//
//        if (patient.getCurrentStep() != OnboardingStep.ASK_FOTOS
//                && patient.getCurrentStep() != OnboardingStep.ASK_MAS_FOTOS) {
//            sendMessage(contactId, "No estamos esperando una imagen en este momento.");
//            return;
//        }
//
//        if (patient.getPhotoUrls().size() >= 5) {
//            sendMessage(contactId, "Has alcanzado el límite de 5 imágenes. Procediendo al siguiente paso.");
//            advanceToNextStepAfterPhotos(patient, contactId);
//            return;
//        }
//
//        try {
//            // Sube a S3 usando el URL temporal
//            String uploadedUrl = s3Service.uploadFromUrl(imageUrl, imageId);
//            patient.getPhotoUrls().add(uploadedUrl);
//            save(patient);
//
//            // Pregunta si quiere subir más
//            sendMessage(contactId, M_22 + "\n" + String.join("\n", M_22_OPTIONS));
//            patient.setCurrentStep(OnboardingStep.ASK_MAS_FOTOS);
//            save(patient);
//
//        } catch (Exception e) {
//            log.error("Error subiendo imagen a S3", e);
//            sendMessage(contactId, "Error al procesar la imagen. Intenta de nuevo.");
//        }
//    }
//
//    // ========================================
//    // HANDLERS PARA CADA STEP
//    // ========================================
//
//    private void handleStart(Patient patient, String contactId) {
//        patient.setCurrentStep(OnboardingStep.ASK_MOTIVO);
//        save(patient);
//        sendMessage(contactId, M_1 + "\n" + String.join("\n", M_1_OPTIONS));
//    }
//
//    private void handleMotivo(Patient patient, String text, String contactId) {
//        if (isValidOption(text, M_1_OPTIONS)) {
//            patient.setPadecimiento(normalizePadecimiento(text));
//            patient.setCurrentStep(OnboardingStep.ASK_EMAIL);
//            save(patient);
//            sendMessage(contactId, M_2);
//        } else {
//            sendMessage(contactId, "Opción inválida. Por favor selecciona una de las opciones proporcionadas.");
//        }
//    }
//
//    private void handleEmail(Patient patient, String text, String contactId) {
//        // Valida email simple (puedes agregar regex)
//        patient.setEmail(text);
//        patient.setCurrentStep(OnboardingStep.CONFIRM_EMAIL);
//        save(patient);
//        sendMessage(contactId, M_3 + " (" + text + ") ¿Es correcto? (Sí/No)");
//    }
//
//    private void handleConfirmEmail(Patient patient, String text, String contactId) {
//        if ("Sí".equalsIgnoreCase(text)) {
//            patient.setCurrentStep(OnboardingStep.ASK_MAYORIA_EDAD);
//            save(patient);
//            sendMessage(contactId, M_4 + "\n" + String.join("\n", M_4_OPTIONS));
//        } else {
//            patient.setCurrentStep(OnboardingStep.ASK_EMAIL);
//            save(patient);
//            sendMessage(contactId, M_2);
//        }
//    }
//
//    private void handleMayoriaEdad(Patient patient, String text, String contactId) {
//        if (M_4_OPTIONS.stream().anyMatch(opt -> opt.equals(text))) {
//            if ("No".equals(text)) {
//                sendMessage(contactId, "Lo sentimos, debes ser mayor de 18 años o estar autorizado. Consulta terminada.");
//                patient.setCurrentStep(OnboardingStep.ERROR);
//                save(patient);
//                return;
//            }
//            patient.setMayorEdad(true);
//            patient.setCurrentStep(OnboardingStep.ASK_NOMBRE);
//            save(patient);
//            sendMessage(contactId, M_5);
//        } else {
//            sendMessage(contactId, "Opción inválida.");
//        }
//    }
//
//    private void handleNombre(Patient patient, String text, String contactId) {
//        patient.setNombreCompleto(text);
//        patient.setCurrentStep(OnboardingStep.ASK_GENERO);
//        save(patient);
//        sendMessage(contactId, M_6 + "\n" + String.join("\n", M_6_OPTIONS));
//    }
//
//    private void handleGenero(Patient patient, String text, String contactId) {
//        if (isValidOption(text, M_6_OPTIONS)) {
//            patient.setGenero(text);
//            OnboardingStep next = OnboardingStep.ASK_FECHA_NAC;
//            if ("Femenino".equals(text)) {
//                // Insertar ASK_STATUS_EMBARAZO después de preguntas comunes si aplica
//                // Por ahora, lo ponemos después de fuma, ajusta flujo
//            }
//            patient.setCurrentStep(next);
//            save(patient);
//            sendMessage(contactId, M_7);
//        } else {
//            sendMessage(contactId, "Opción inválida.");
//        }
//    }
//
//    private void handleFechaNac(Patient patient, String text, String contactId) {
//        try {
//            LocalDate fecha = LocalDate.parse(text, DateTimeFormatter.ofPattern("yyyy-MM-dd")); // Asume formato YYYY-MM-DD
//            patient.setFechaNacimiento(fecha);
//            patient.setCurrentStep(OnboardingStep.ASK_PESO);
//            save(patient);
//            sendMessage(contactId, M_8);
//        } catch (DateTimeParseException e) {
//            sendMessage(contactId, "Formato inválido. Usa YYYY-MM-DD.");
//        }
//    }
//
//    private void handlePeso(Patient patient, String text, String contactId) {
//        try {
//            patient.setPesoKg(Integer.parseInt(text.replace("kg", "").trim()));
//            patient.setCurrentStep(OnboardingStep.ASK_ALTURA);
//            save(patient);
//            sendMessage(contactId, M_9);
//        } catch (NumberFormatException e) {
//            sendMessage(contactId, "Ingresa un número válido (ej. 81).");
//        }
//    }
//
//    private void handleAltura(Patient patient, String text, String contactId) {
//        try {
//            patient.setAlturaM(Double.parseDouble(text));
//            patient.setCurrentStep(OnboardingStep.ASK_FUMA);
//            save(patient);
//            sendMessage(contactId, M_10 + "\n" + String.join("\n", M_10_OPTIONS));
//        } catch (NumberFormatException e) {
//            sendMessage(contactId, "Ingresa un número válido (ej. 1.67).");
//        }
//    }
//
//    private void handleFuma(Patient patient, String text, String contactId) {
//        if (isValidOption(text, M_10_OPTIONS)) {
//            patient.setFuma("Sí".equals(text));
//            patient.setCurrentStep(OnboardingStep.ASK_DESDE_CUANDO);
//            save(patient);
//            sendMessage(contactId, M_11 + "\n" + String.join("\n", M_11_OPTIONS));
//        } else {
//            sendMessage(contactId, "Opción inválida.");
//        }
//    }
//
//    private void handleDesdeCuando(Patient patient, String text, String contactId) {
//        if (isValidOption(text, M_11_OPTIONS)) {
//            patient.setDesdeCuando(text);
//            patient.setCurrentStep(getNextStepAfterDesdeCuando(patient));
//            save(patient);
//            sendNextQuestion(patient, contactId);
//        } else {
//            sendMessage(contactId, "Opción inválida.");
//        }
//    }
//
//    // Handlers para steps específicos por padecimiento (ejemplos)
//    private void handleGravedad(Patient patient, String text, String contactId) {
//        List<String> options = switch (patient.getPadecimiento()) {
//            case "Acne" -> M_12_OPTIONS;
//            case "Manchas" -> M_37_OPTIONS;
//            case "Rosacea" -> M_40_OPTIONS;
//            default -> List.of();
//        };
//        if (isValidOption(text, options)) {
//            if ("Acne".equals(patient.getPadecimiento())) patient.setGravedadAcne(text);
//            // Setear campos específicos similares para otros
//            patient.setCurrentStep(OnboardingStep.ASK_TRATAMIENTO_ANTERIOR);
//            save(patient);
//            sendMessage(contactId, M_13 + "\n" + String.join("\n", M_13_OPTIONS));
//        } else {
//            sendMessage(contactId, "Opción inválida.");
//        }
//    }
//
//    private void handleTratamientoAnterior(Patient patient, String text, String contactId) {
//        if (isValidOption(text, M_13_OPTIONS)) {
//            patient.setTratamientoAnterior("Sí".equals(text));
//            if (Boolean.TRUE.equals(patient.getTratamientoAnterior())) {
//                patient.setCurrentStep(OnboardingStep.ASK_TRATAMIENTOS_USADOS);
//                save(patient);
//                sendMessage(contactId, M_14);
//            } else {
//                patient.setCurrentStep(OnboardingStep.ASK_ALERGIAS);
//                save(patient);
//                sendMessage(contactId, M_15 + "\n" + String.join("\n", M_15_OPTIONS));
//            }
//        } else {
//            sendMessage(contactId, "Opción inválida.");
//        }
//    }
//
//    private void handleTratamientosUsados(Patient patient, String text, String contactId) {
//        patient.setTratamientosUsados(text);
//        patient.setCurrentStep(OnboardingStep.ASK_ALERGIAS);
//        save(patient);
//        sendMessage(contactId, M_15 + "\n" + String.join("\n", M_15_OPTIONS));
//    }
//
//    private void handleAlergias(Patient patient, String text, String contactId) {
//        if (isValidOption(text, M_15_OPTIONS)) {
//            patient.setAlergias("Sí.".equals(text));
//            if (patient.getAlergias()) {
//                patient.setCurrentStep(OnboardingStep.ASK_ALERGIAS_DETALLES);
//                save(patient);
//                sendMessage(contactId, M_16);
//            } else {
//                patient.setCurrentStep(OnboardingStep.ASK_MEDICAMENTOS);
//                save(patient);
//                sendMessage(contactId, M_17 + "\n" + String.join("\n", M_17_OPTIONS));
//            }
//        } else {
//            sendMessage(contactId, "Opción inválida.");
//        }
//    }
//
//    private void handleAlergiasDetalles(Patient patient, String text, String contactId) {
//        patient.setAlergiasDetalles(text);
//        patient.setCurrentStep(OnboardingStep.ASK_MEDICAMENTOS);
//        save(patient);
//        sendMessage(contactId, M_17 + "\n" + String.join("\n", M_17_OPTIONS));
//    }
//
//    private void handleMedicamentos(Patient patient, String text, String contactId) {
//        if (isValidOption(text, M_17_OPTIONS)) {
//            patient.setMedicamentos("Sí, “el usuario escribe cuales”.".equals(text)); // Ajusta comparación
//            if (patient.getMedicamentos()) {
//                patient.setCurrentStep(OnboardingStep.ASK_MEDICAMENTOS_DETALLES);
//                save(patient);
//                sendMessage(contactId, M_18);
//            } else {
//                patient.setCurrentStep(OnboardingStep.ASK_ENFERMEDADES);
//                save(patient);
//                sendMessage(contactId, M_29 + "\n" + String.join("\n", M_29_OPTIONS));
//            }
//        } else {
//            sendMessage(contactId, "Opción inválida.");
//        }
//    }
//
//    private void handleMedicamentosDetalles(Patient patient, String text, String contactId) {
//        patient.setMedicamentosDetalles(text);
//        patient.setCurrentStep(OnboardingStep.ASK_ENFERMEDADES);
//        save(patient);
//        sendMessage(contactId, M_29 + "\n" + String.join("\n", M_29_OPTIONS));
//    }
//
//    private void handleEnfermedades(Patient patient, String text, String contactId) {
//        if (isValidOption(text, M_29_OPTIONS)) {
//            patient.setEnfermedades("Sí, “el usuario escribe cuales”.".equals(text));
//            if (patient.getEnfermedades()) {
//                patient.setCurrentStep(OnboardingStep.ASK_ENFERMEDADES_DETALLES);
//                save(patient);
//                sendMessage(contactId, "Por favor ingresa qué enfermedades tienes.");
//            } else {
//                patient.setCurrentStep(getNextStepAfterEnfermedades(patient));
//                save(patient);
//                sendNextQuestion(patient, contactId);
//            }
//        } else {
//            sendMessage(contactId, "Opción inválida.");
//        }
//    }
//
//    private void handleEnfermedadesDetalles(Patient patient, String text, String contactId) {
//        patient.setEnfermedadesDetalles(text);
//        patient.setCurrentStep(getNextStepAfterEnfermedades(patient));
//        save(patient);
//        sendNextQuestion(patient, contactId);
//    }
//
//    // Handlers específicos para Anti-edad
//    private void handleMejoraPrincipal(Patient patient, String text, String contactId) {
//        if (isValidOption(text, M_26_OPTIONS)) {
//            patient.setMejoraPrincipal(text);
//            patient.setCurrentStep(OnboardingStep.ASK_TIPO_PIEL);
//            save(patient);
//            sendMessage(contactId, M_27 + "\n" + String.join("\n", M_27_OPTIONS));
//        } else {
//            sendMessage(contactId, "Opción inválida.");
//        }
//    }
//
//    private void handleTipoPiel(Patient patient, String text, String contactId) {
//        if (isValidOption(text, M_27_OPTIONS)) {
//            patient.setTipoPiel(text);
//            patient.setCurrentStep(OnboardingStep.ASK_SENSIBILIDAD_PIEL);
//            save(patient);
//            sendMessage(contactId, M_28 + "\n" + String.join("\n", M_28_OPTIONS));
//        } else {
//            sendMessage(contactId, "Opción inválida.");
//        }
//    }
//
//    private void handleSensibilidadPiel(Patient patient, String text, String contactId) {
//        if (isValidOption(text, M_28_OPTIONS)) {
//            patient.setSensibilidadPiel(text);
//            patient.setCurrentStep(OnboardingStep.ASK_EXPOSICION_SOL);
//            save(patient);
//            sendMessage(contactId, M_30 + "\n" + String.join("\n", M_30_OPTIONS));
//        } else {
//            sendMessage(contactId, "Opción inválida.");
//        }
//    }
//
//    private void handleExposicionSol(Patient patient, String text, String contactId) {
//        if (isValidOption(text, M_30_OPTIONS)) {
//            patient.setExposicionSol(text);
//            patient.setCurrentStep(OnboardingStep.ASK_USA_PROTECTOR);
//            save(patient);
//            sendMessage(contactId, M_31 + "\n" + String.join("\n", M_31_OPTIONS));
//        } else {
//            sendMessage(contactId, "Opción inválida.");
//        }
//    }
//
//    private void handleUsaProtector(Patient patient, String text, String contactId) {
//        if (isValidOption(text, M_31_OPTIONS)) {
//            patient.setUsaProtector(text);
//            patient.setCurrentStep(OnboardingStep.ASK_NOTAS_ADICIONALES);
//            save(patient);
//            sendMessage(contactId, getNotasMessage(patient.getPadecimiento()));
//        } else {
//            sendMessage(contactId, "Opción inválida.");
//        }
//    }
//
//    // Handlers para Caida de Pelo
//    private void handleAreaCaida(Patient patient, String text, String contactId) {
//        if (isValidOption(text, M_34_OPTIONS)) {
//            patient.setAreaCaida(text);
//            patient.setCurrentStep(OnboardingStep.ASK_ANTECEDENTES_FAMILIA);
//            save(patient);
//            sendMessage(contactId, M_35 + "\n" + String.join("\n", M_35_OPTIONS));
//        } else {
//            sendMessage(contactId, "Opción inválida.");
//        }
//    }
//
//    private void handleAntecedentesFamilia(Patient patient, String text, String contactId) {
//        // Permite múltiples, e.g., comma-separated
//        patient.setAntecedentesFamiliares(text);
//        patient.setCurrentStep(OnboardingStep.ASK_NOTAS_ADICIONALES);
//        save(patient);
//        sendMessage(contactId, getNotasMessage(patient.getPadecimiento()));
//    }
//
//    private void handleStatusEmbarazo(Patient patient, String text, String contactId) {
//        if (isValidOption(text, M_33_OPTIONS)) {
//            patient.setStatusEmbarazo(text);
//            patient.setCurrentStep(getNextStepAfterEmbarazo(patient));
//            save(patient);
//            sendNextQuestion(patient, contactId);
//        } else {
//            sendMessage(contactId, "Opción inválida.");
//        }
//    }
//
//    private void handleNotasAdicionales(Patient patient, String text, String contactId) {
//        patient.setNotasAdicionales(text);
//        patient.setCurrentStep(OnboardingStep.ASK_FOTOS);
//        save(patient);
//        sendMessage(contactId, M_20 + "\n" + String.join("\n", M_20_OPTIONS));
//    }
//
//    private void handleFotos(Patient patient, String text, String contactId) {
//        if (isValidOption(text, M_20_OPTIONS)) {
//            if ("Cargar ahora".equals(text)) {
//                patient.setCurrentStep(OnboardingStep.ASK_MAS_FOTOS);
//                save(patient);
//                sendMessage(contactId, M_21);
//            } else {
//                advanceToNextStepAfterPhotos(patient, contactId);
//            }
//        } else {
//            sendMessage(contactId, "Opción inválida.");
//        }
//    }
//
//    private void handleMasFotos(Patient patient, String text, String contactId) {
//        if (isValidOption(text, M_22_OPTIONS)) {
//            if ("Sí.".equals(text)) {
//                sendMessage(contactId, M_21);
//            } else {
//                advanceToNextStepAfterPhotos(patient, contactId);
//            }
//        } else {
//            sendMessage(contactId, "Opción inválida.");
//        }
//    }
//
//    private void advanceToNextStepAfterPhotos(Patient patient, String contactId) {
//        patient.setCurrentStep(OnboardingStep.ASK_TERMINOS);
//        save(patient);
//        // Envía documento términos (asume URL en config o constante)
//        String terminosUrl = "https://tu-dominio/terminos.pdf"; // Configura esto
//        respondIoClient.sendFile(contactId, terminosUrl, "Terminos y Condiciones");
//        sendMessage(contactId, "Aceptas terminos y condiciones? (Sí/No)");
//    }
//
//    private void handleTerminos(Patient patient, String text, String contactId) {
//        if ("Sí".equalsIgnoreCase(text)) {
//            patient.setCurrentStep(OnboardingStep.ASK_AVISO_PRIVACIDAD);
//            save(patient);
//            String privacidadUrl = "https://tu-dominio/privacidad.pdf";
//            respondIoClient.sendFile(contactId, privacidadUrl, "Aviso de Privacidad");
//            sendMessage(contactId, "Aceptas aviso de privacidad? (Sí/No)");
//        } else {
//            sendMessage(contactId, "Debes aceptar para continuar.");
//        }
//    }
//
//    private void handleAvisoPrivacidad(Patient patient, String text, String contactId) {
//        if ("Sí".equalsIgnoreCase(text)) {
//            patient.setCurrentStep(OnboardingStep.ASK_CONSENTIMIENTO);
//            save(patient);
//            String consentimientoUrl = "https://tu-dominio/consentimiento.pdf";
//            respondIoClient.sendFile(contactId, consentimientoUrl, "Consentimiento Informado de Telemedicina");
//            sendMessage(contactId, "Aceptas consentimiento informado? (Sí/No)");
//        } else {
//            sendMessage(contactId, "Debes aceptar para continuar.");
//        }
//    }
//
//    private void handleConsentimiento(Patient patient, String text, String contactId) {
//        if ("Sí".equalsIgnoreCase(text)) {
//            patient.setCurrentStep(OnboardingStep.PROCESS_PAYMENT);
//            save(patient);
//            String paymentLink = "https://pago.clinica.com/" + patient.getWhatsappId(); // Integra real
//            sendMessage(contactId, M_23 + " " + paymentLink);
//        } else {
//            sendMessage(contactId, "Debes aceptar para continuar.");
//        }
//    }
//
//    private void handlePayment(Patient patient, String text, String contactId) {
//        // Asume confirmación manual; mejor usa webhook de pasarela
//        if ("confirmar".equalsIgnoreCase(text)) { // O maneja webhook
//            patient.setPagoProcesado(true);
//            patient.setCurrentStep(OnboardingStep.COMPLETED);
//            save(patient);
//            String adminLink = "https://panel.clinica.com/" + patient.getWhatsappId();
//            sendMessage(contactId, M_24.replace("(Enviar link para el panel administrativo)", adminLink));
//        } else {
//            sendMessage(contactId, M_25);
//        }
//    }
//
//    // ========================================
//    // MÉTODOS DE APOYO
//    // ========================================
//
//    private Patient getOrCreatePatient(String contactId) {
//        return patientRepository.findByWhatsappId(contactId)
//                .orElseGet(() -> {
//                    Patient newPatient = Patient.builder()
//                            .whatsappId(contactId)
//                            .currentStep(OnboardingStep.START)
//                            .createdAt(LocalDateTime.now())
//                            .build();
//                    return patientRepository.save(newPatient);
//                });
//    }
//
//    private void save(Patient patient) {
//        patient.setUpdatedAt(LocalDateTime.now());
//        patientRepository.save(patient);
//    }
//
//    private void sendMessage(String contactId, String text) {
//        respondIoClient.sendText(contactId, text);
//    }
//
//    private void sendNextQuestion(Patient patient, String contactId) {
//        OnboardingStep step = patient.getCurrentStep();
//        String message = switch (step) {
//            case ASK_MEJORA_PRINCIPAL -> M_26 + "\n" + String.join("\n", M_26_OPTIONS);
//            case ASK_AREA_CAIDA -> M_34 + "\n" + String.join("\n", M_34_OPTIONS);
//            case ASK_GRAVEDAD ->
//                    getGravedadMessage(patient.getPadecimiento()) + "\n" + getGravedadOptions(patient.getPadecimiento());
//            case ASK_STATUS_EMBARAZO -> M_33 + "\n" + String.join("\n", M_33_OPTIONS);
//            case ASK_NOTAS_ADICIONALES -> getNotasMessage(patient.getPadecimiento());
//            // Agrega más según necesites
//            default -> "Siguiente pregunta...";
//        };
//        sendMessage(contactId, message);
//    }
//
//    private OnboardingStep getNextStepAfterDesdeCuando(Patient patient) {
//        return switch (patient.getPadecimiento()) {
//            case "Acne", "Manchas", "Rosacea" -> OnboardingStep.ASK_GRAVEDAD;
//            case "Anti-edad" -> OnboardingStep.ASK_MEJORA_PRINCIPAL;
//            case "Caida de Pelo" -> OnboardingStep.ASK_AREA_CAIDA;
//            default -> OnboardingStep.ASK_TRATAMIENTO_ANTERIOR;
//        };
//    }
//
//    private OnboardingStep getNextStepAfterEnfermedades(Patient patient) {
//        // Ajusta según flujo; e.g., para Anti-edad va después de sensibilidad, pero aquí es común
//        if ("Femenino".equals(patient.getGenero())) {
//            return OnboardingStep.ASK_STATUS_EMBARAZO;
//        }
//        return OnboardingStep.ASK_NOTAS_ADICIONALES;
//    }
//
//    private OnboardingStep getNextStepAfterEmbarazo(Patient patient) {
//        return OnboardingStep.ASK_NOTAS_ADICIONALES;
//    }
//
//    private String getGravedadMessage(String padecimiento) {
//        return switch (padecimiento) {
//            case "Acne" -> M_12;
//            case "Manchas" -> M_37;
//            case "Rosacea" -> M_40;
//            default -> "";
//        };
//    }
//
//    private List<String> getGravedadOptions(String padecimiento) {
//        return switch (padecimiento) {
//            case "Acne" -> M_12_OPTIONS;
//            case "Manchas" -> M_37_OPTIONS;
//            case "Rosacea" -> M_40_OPTIONS;
//            default -> List.of();
//        };
//    }
//
//    private String getNotasMessage(String padecimiento) {
//        return switch (padecimiento) {
//            case "Acne" -> M_19;
//            case "Anti-edad" -> M_32;
//            case "Caida de Pelo" -> M_36;
//            case "Manchas" -> M_38;
//            case "Otros" -> M_39;
//            case "Rosacea" -> M_41;
//            default -> "Y por último, ¿Tienes algo más que compartirle a tu dermatólogo?";
//        };
//    }
//
//    private boolean isValidOption(String text, List<String> options) {
//        return options.stream().anyMatch(opt -> opt.equalsIgnoreCase(text) || opt.contains(text));
//    }
//
//    private String normalizePadecimiento(String text) {
//        // Normaliza a valores internos, e.g., "Caída de pelo" -> "Caida de Pelo"
//        return text.replace("í", "i").replace("á", "a"); // Simple normalización
//    }
//}