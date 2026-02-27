package com.bot.elara.Application.Service;

import com.bot.elara.Domain.Model.BotSession;
import com.bot.elara.Domain.Model.OnboardingStep;
import com.bot.elara.Infrastructure.External.Whatsapp.WhatsAppCloudApiClient;
import com.bot.elara.Infrastructure.Persistence.Jpa.BotSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final BotSessionRepository botSessionRepository;
    private final WhatsAppCloudApiClient whatsAppClient;

    @Transactional
    public void handlePaymentConfirmed(String publicId, Long consultaId) {

        // 1️⃣ Buscar sesión existente por consultaPublicId
        BotSession session = botSessionRepository
                .findByConsultaPublicId(publicId)
                .orElseThrow(() -> new RuntimeException(
                        "No se encontró sesión para publicId: " + publicId
                ));

        // 2️⃣ Idempotencia (muy importante)
        if (Boolean.TRUE.equals(session.getPaymentConfirmed())) {
            return;
        }

        // 3️⃣ Actualizar estado interno
        session.setConsultaId(consultaId);
        session.setPaymentConfirmed(true);
        session.setCurrentStep(OnboardingStep.COMPLETED);

        botSessionRepository.save(session);

        // 4️⃣ Enviar mensaje WhatsApp
        String phone = session.getWhatsappId();

        String message = """
                ✅ Pago confirmado correctamente.

                Tu consulta fue enviada al médico.
                En breve recibirás respuesta.

                Gracias por confiar en Elara 💙
                """;

        whatsAppClient.sendText(phone, message);
    }
}

