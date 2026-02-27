package com.bot.elara.Infrastructure.DTO.Stripe;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StripeCheckoutResult {
    private String checkoutUrl;
    private String sessionId;
    private String paymentIntentId;
}
