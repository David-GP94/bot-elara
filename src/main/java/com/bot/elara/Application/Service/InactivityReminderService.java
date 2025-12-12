package com.bot.elara.Application.Service;

import com.bot.elara.Domain.Model.OnboardingStep;
import com.bot.elara.Domain.Model.Patient;
import com.bot.elara.Domain.Repository.PatientRepository;
import com.bot.elara.Infrastructure.External.Whatsapp.WhatsAppCloudApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class InactivityReminderService {

    private final PatientRepository patientRepository;
    private final WhatsAppCloudApiClient whatsAppClient;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendReminder(String whatsappId) {
        log.info(">>> EJECUTANDO sendReminder para {}", whatsappId); // ← LOG CLAVE

        try {
            Patient p = patientRepository.findByWhatsappId(whatsappId).orElse(null);

            if (p == null) {
                log.warn("Paciente no encontrado: {}", whatsappId);
                return;
            }

            log.info("Paciente encontrado. Step: {}, PendingResponse: {}",
                    p.getCurrentStep(), p.getPendingInactivityResponse());

            if (p.getCurrentStep() == OnboardingStep.COMPLETED ||
                    p.getCurrentStep() == OnboardingStep.WELCOME) {
                log.info("Recordatorio cancelado - paso no válido: {}", p.getCurrentStep());
                return;
            }

            log.info("Enviando mensaje de recordatorio a {}", whatsappId);

            whatsAppClient.sendReplyButtons(
                    whatsappId,
                    "⏰ ¡Hola! Notamos que no has continuado con tu consulta.\n\n¿Deseas continuar donde te quedaste?",
                    List.of("✅ Sí, continuar", "❌ No, cancelar")
            );

            p.setPendingInactivityResponse(true);
            patientRepository.save(p);
            patientRepository.flush();

            log.info("✅ Recordatorio enviado exitosamente a {}", whatsappId);
        } catch (Exception e) {
            log.error("❌ Error enviando recordatorio a {}", whatsappId, e);
        }
    }
}

