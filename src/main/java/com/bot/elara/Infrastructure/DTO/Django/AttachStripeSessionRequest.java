package com.bot.elara.Infrastructure.DTO.Django;

import lombok.Data;

@Data
public class AttachStripeSessionRequest {

    private Long consulta_id;
    private String stripe_session_id;
    private String payment_intent_id;
}