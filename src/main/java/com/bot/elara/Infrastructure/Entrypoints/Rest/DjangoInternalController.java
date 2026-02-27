package com.bot.elara.Infrastructure.Entrypoints.Rest;

import com.bot.elara.Application.Service.PaymentService;
import com.bot.elara.Config.BotInternalProperties;
import com.bot.elara.Infrastructure.DTO.Django.BotPaymentConfirmedRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class DjangoInternalController {

    private final PaymentService paymentService;
    private final BotInternalProperties botInternalProperties;

    @PostMapping("/payment-confirmed")
    public ResponseEntity<Void> paymentConfirmed(
            @RequestHeader("X-BOT-SECRET") String secret,
            @RequestBody BotPaymentConfirmedRequest request
    ) {

        if (!secret.equals(botInternalProperties.getSecret())) {
            return ResponseEntity.status(401).build();
        }

        paymentService.handlePaymentConfirmed(
                request.getPublic_id(),
                request.getConsulta_id()
        );

        return ResponseEntity.ok().build();
    }
}
