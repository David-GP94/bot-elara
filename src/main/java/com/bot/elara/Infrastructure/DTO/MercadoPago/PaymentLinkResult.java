package com.bot.elara.Infrastructure.DTO.MercadoPago;

public record PaymentLinkResult(
        String paymentUrl,
        String externalReference,
        String preferenceId
) {}