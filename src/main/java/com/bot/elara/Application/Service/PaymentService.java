package com.bot.elara.Application.Service;

import com.bot.elara.Domain.Model.BotSession;
import com.bot.elara.Domain.Model.OnboardingStep;
import com.bot.elara.Infrastructure.External.Whatsapp.WhatsAppCloudApiClient;
import com.bot.elara.Infrastructure.Persistence.Jpa.BotSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.bot.elara.Domain.Constants.MessageConstants.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final BotSessionRepository botSessionRepository;
    private final WhatsAppCloudApiClient whatsAppClient;

    @Transactional
    public void handlePaymentConfirmed(String publicId, Long consultaId) {
        log.info("Processing payment for publicId={}", publicId);
        BotSession session = botSessionRepository
                .findByConsultaPublicId(publicId)
                .orElse(null);

        if (session == null) {
            log.error("No se encontró sesión para publicId {}", publicId);
            return;
        }

        // Idempotencia correcta
        if (Boolean.TRUE.equals(session.getPaymentConfirmed())) {
            return;
        }

        session.setConsultaId(consultaId);
        session.setPaymentConfirmed(true);
        session.setCurrentStep(OnboardingStep.COMPLETED);

        botSessionRepository.save(session);

        String phone = session.getWhatsappId();

        whatsAppClient.sendText(phone, M_PAGO_CONFIRMADO);

        whatsAppClient.sendVideo(phone, M_CONOCE_TU_DERMATOLOGO);
    }
}

