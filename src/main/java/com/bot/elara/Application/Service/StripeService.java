package com.bot.elara.Application.Service;

import com.bot.elara.Infrastructure.DTO.Stripe.StripeCheckoutResult;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class StripeService {

    @Value("${stripe.price-amount:99900}")
    private long amount;

    @Value("${app.base-url:https://tu-dominio.com}")
    private String baseUrl;

    @Value("${stripe.api-key}")
    private String stripeApiKey;

    @PostConstruct
    public void init() {
        Stripe.apiKey = stripeApiKey;
        log.info("Stripe API Key configurada correctamente (longitud: {})", stripeApiKey.length());
    }

    public StripeCheckoutResult crearPaymentLink(
            String consultaPublicId,
            String whatsappId,
            String email
    ) {
        try {

            SessionCreateParams params = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.PAYMENT)
                    .setCustomerEmail(email)
                    .setPhoneNumberCollection(
                            SessionCreateParams.PhoneNumberCollection.builder()
                                    .setEnabled(true)
                                    .build()
                    )
                    .setSuccessUrl(baseUrl + "/pago-exito-whatsapp")
                    .setCancelUrl(baseUrl + "/pago-cancelado-whatsapp")
                    .setExpiresAt(
                            java.time.Instant.now()
                                    .plus(java.time.Duration.ofHours(2))
                                    .getEpochSecond()
                    )

                    .addLineItem(
                            SessionCreateParams.LineItem.builder()
                                    .setQuantity(1L)
                                    .setPriceData(
                                            SessionCreateParams.LineItem.PriceData.builder()
                                                    .setCurrency("mxn")
                                                    .setUnitAmount(amount)
                                                    .setProductData(
                                                            SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                                    .setName("Consulta Dermatológica Elara")
                                                                    .setDescription("Revisión personalizada + diagnóstico + tratamiento")
                                                                    .build()
                                                    )
                                                    .build()
                                    )
                                    .build()
                    )

                    // 🔥 METADATA IMPORTANTE
                    .putMetadata("channel", "whatsapp_bot")
                    .putMetadata("consulta_public_id", consultaPublicId)
                    .putMetadata("whatsapp_id", whatsappId)

                    .build();

            Session session = Session.create(params);

            log.info("Checkout Session creada para {} → {}", whatsappId, session.getUrl());

            return StripeCheckoutResult.builder()
                    .checkoutUrl(session.getUrl())
                    .sessionId(session.getId())
                    .paymentIntentId(session.getPaymentIntent())
                    .build();

        } catch (StripeException e) {
            log.error("Error creando Checkout Session para {}", whatsappId, e);
            return null;
        }
    }
}