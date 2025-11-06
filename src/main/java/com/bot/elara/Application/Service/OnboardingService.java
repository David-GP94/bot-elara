package com.bot.elara.Application.Service;


import com.bot.elara.Domain.Model.OnboardingStep;
import com.bot.elara.Domain.Model.Patient;
import com.bot.elara.Domain.Repository.PatientRepository;
import com.bot.elara.Infrastructure.External.Storage.S3Service;
import com.bot.elara.Infrastructure.External.Whatsapp.Model.Image;
import com.bot.elara.Infrastructure.External.Whatsapp.WhatsAppCloudApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class OnboardingService {

    private final PatientRepository patientRepository;
    private final WhatsAppCloudApiClient whatsAppClient;
    private final S3Service s3Service;

    public void processText(String from, String text) {
        Patient patient = getOrCreatePatient(from);

        switch (patient.getCurrentStep()) {
            case START -> askName(patient);
            case ASK_NAME -> saveNameAndAskAge(patient, text);
            case ASK_AGE -> saveAgeAndAskConcern(patient, text);
            case ASK_CONCERN -> saveConcernAndAskPhoto(patient, text);
            case ASK_PAYMENT -> processPaymentResponse(patient, text);
            default -> sendMessage(from, "Proceso completado. ¡Gracias!");
        }
    }

    public void processImage(String from, Image image) {
        Patient patient = getOrCreatePatient(from);
        if (patient.getCurrentStep() == OnboardingStep.ASK_PHOTO) {
            String photoUrl = s3Service.uploadFromUrl(image.getUrl(), image.getId());
            patient.setPhotoUrl(photoUrl);
            patient.setCurrentStep(OnboardingStep.ASK_PAYMENT);
            patientRepository.save(patient);
            sendPaymentLink(patient);
        }
    }

    // ========================================
    // MÉTODOS PRIVADOS DE FLUJO
    // ========================================

    private void askName(Patient p) {
        p.setCurrentStep(OnboardingStep.ASK_NAME);
        patientRepository.save(p);
        sendMessage(p.getWhatsappId(), "¡Hola! ¿Cómo te llamas?");
    }

    private void saveNameAndAskAge(Patient p, String text) {
        p.setName(text.trim());
        p.setCurrentStep(OnboardingStep.ASK_AGE);
        patientRepository.save(p);
        sendMessage(p.getWhatsappId(), "Perfecto, " + p.getName() + ". ¿Cuál es tu edad?");
    }

    private void saveAgeAndAskConcern(Patient p, String text) {
        try {
            p.setAge(Integer.parseInt(text.trim()));
            p.setCurrentStep(OnboardingStep.ASK_CONCERN);
            patientRepository.save(p);
            sendMessage(p.getWhatsappId(), "Gracias. ¿Qué problema de piel te gustaría tratar?");
        } catch (NumberFormatException e) {
            sendMessage(p.getWhatsappId(), "Por favor, ingresa una edad válida (solo números).");
        }
    }

    private void saveConcernAndAskPhoto(Patient p, String text) {
        p.setSkinConcern(text.trim());
        p.setCurrentStep(OnboardingStep.ASK_PHOTO);
        patientRepository.save(p);
        sendMessage(p.getWhatsappId(),
                "Último paso: por favor, envía una foto clara de la zona afectada.\n" +
                        "Recuerda que será revisada por un dermatólogo.");
    }

    private void processPaymentResponse(Patient p, String text) {
        if ("pagar".equalsIgnoreCase(text.trim())) {
            p.setCurrentStep(OnboardingStep.COMPLETED);
            patientRepository.save(p);
            sendMessage(p.getWhatsappId(), "¡Pago recibido! Tu cita está confirmada. Te contactaremos pronto.");
        } else {
            sendMessage(p.getWhatsappId(), "Escribe *pagar* para confirmar el pago de $500 MXN.");
        }
    }

    private void sendPaymentLink(Patient p) {
        // Aquí puedes integrar Mercado Pago, Stripe, etc.
        String mockLink = "https://pago.clinica.com/12345";
        sendMessage(p.getWhatsappId(),
                "Tu foto ha sido recibida.\n" +
                        "Costo de consulta: *500 MXN*\n" +
                        "Paga aquí: " + mockLink + "\n" +
                        "Después escribe *pagar* para confirmar.");
    }

    // ========================================
    // MÉTODOS DE APOYO
    // ========================================

    private String normalizePhone(String phone) {
        // Elimina 52 (México) y 1 (EEUU) al inicio
        return phone.replaceFirst("^52", "").replaceFirst("^1", "");
    }

    private Patient getOrCreatePatient(String from) {
        String normalized = normalizePhone(from);
        log.info("Buscando paciente con whatsapp_id: {}", normalized);

        return patientRepository.findByWhatsappId(normalized)
                .orElseGet(() -> {
                    log.info("Paciente NO encontrado → CREANDO NUEVO con id: {}", normalized);
                    Patient newPatient = Patient.builder()
                            .whatsappId(normalized)
                            .currentStep(OnboardingStep.START)
                            .createdAt(java.time.LocalDateTime.now())
                            .build();
                    Patient saved = patientRepository.save(newPatient);
                    log.info("Paciente CREADO: id={} | step={}", saved.getWhatsappId(), saved.getCurrentStep());
                    return saved;
                });
    }
    private void sendMessage(String to, String text) {
        String formatted = to.startsWith("521") ? to : "521" + to;
        whatsAppClient.sendText(formatted, text);
    }
}