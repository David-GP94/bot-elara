package com.bot.elara.Application.Service;

import com.bot.elara.Infrastructure.DTO.MercadoPago.PaymentLinkResult;
import com.bot.elara.Infrastructure.DTO.MercadoPago.PreferenceRequest;
import com.bot.elara.Infrastructure.DTO.MercadoPago.PreferenceResponse;
import com.bot.elara.Infrastructure.DTO.MercadoPago.PaymentResponse;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class MercadoPagoService {

    private final RestTemplate restTemplate;

    @Value("${mercadopago.price-amount: 99900}")
    private long amount;

    @Value("${app.base-url:https://tu-dominio.com}")
    private String baseUrl;

    @Value("${mercadopago.access.token}")
    private String accessToken;

    @Value("${mercadopago.api.url:https://api.mercadopago.com}")
    private String apiUrl;

    @Value("${mercadopago.notification.url}")
    private String notificationUrl;

    @Value("${mercadopago.currency: MXN}")
    private String currency;

    @PostConstruct
    public void init() {
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.info("🔧 Configuración de MercadoPago");
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        if (accessToken != null && !accessToken.isBlank()) {
            log.info("✅ Access Token configurado (longitud: {})", accessToken.length());
            log.info("🔑 Modo:  {}", isTestMode() ? "TEST" : "PRODUCTION");
        } else {
            log.error("❌ Access Token NO configurado!");
        }

        log.info("💰 Monto: {} centavos = ${} {}",
                amount,
                BigDecimal.valueOf(amount).divide(BigDecimal.valueOf(100)),
                currency);
        log.info("💵 Moneda: {}", currency);
        log.info("🌐 API URL: {}", apiUrl);
        log.info("🔗 Base URL: {}", baseUrl);
        log.info("📡 Webhook URL: {}", notificationUrl);
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    /**
     * Crea un link de pago en MercadoPago usando Checkout Pro
     */
    public PaymentLinkResult crearPaymentLink(String consultaId,
                                              String whatsappId,
                                              String email,
                                              Double precioFinal) {

        if (consultaId == null || consultaId.isBlank()) {
            log.error("❌ consultaId no puede estar vacío");
            return null;
        }

        if (whatsappId == null || whatsappId.isBlank()) {
            log.error("❌ whatsappId no puede estar vacío");
            return null;
        }

        if (precioFinal == null || precioFinal <= 0) {
            log.error("❌ precioFinal inválido: {}", precioFinal);
            return null;
        }

        if (email == null || !email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {
            log.warn("⚠️ Email inválido: {}, usando default", email);
            email = "noreply@elara.com";
        }

        try {

            PreferenceRequest preference = new PreferenceRequest();

            // 🔹 Precio correcto en MXN (no centavos)
            BigDecimal priceDecimal = BigDecimal.valueOf(precioFinal);

            // 🔹 Item
            List<PreferenceRequest.Item> items = new ArrayList<>();
            items.add(new PreferenceRequest.Item(
                    "Consulta Dermatológica Elara",
                    "Revisión personalizada + diagnóstico + tratamiento",
                    1,
                    currency,
                    priceDecimal
            ));
            preference.setItems(items);

            // 🔹 MANEJO DE PAYER (Igualando la lógica ganadora de Django)
            if (isTestMode()) {
                // En Java lo dejamos en null para que tu DTO no envíe el campo "payer"
                // y la preferencia sea totalmente anónima.
                preference.setPayer(null);
            } else {
                // En Producción sí mandamos los datos reales del usuario
                PreferenceRequest.Payer payer = new PreferenceRequest.Payer(
                        email,
                        whatsappId
                );
                preference.setPayer(payer);
            }

            // 🔹 URLs
            PreferenceRequest.BackUrls backUrls = new PreferenceRequest.BackUrls(
                    baseUrl + "/consulta/mercadopago/success/",
                    baseUrl + "/consulta/mercadopago/failure/",
                    baseUrl + "/consulta/mercadopago/pending/"
            );

            preference.setBackUrls(backUrls);
            preference.setAutoReturn("approved");
            preference.setNotificationUrl(notificationUrl);

            // 🔹 External reference fuerte (clave para webhook)
            String externalReference = consultaId;

            preference.setExternalReference(externalReference);
            preference.setStatementDescriptor("ELARA DERMATO");

            // 🔹 Metadata
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("consulta_id", consultaId);
            metadata.put("whatsapp_id", whatsappId);
            metadata.put("precio_final", precioFinal);
            metadata.put("channel", "whatsapp_bot");
            preference.setMetadata(metadata);

            // 🔹 Métodos de pago y Binary Mode (Igual a Django)
            PreferenceRequest.PaymentMethods paymentMethods =
                    new PreferenceRequest.PaymentMethods();
            paymentMethods.setInstallments(1); // Forzar 1 pago para evitar errores en Sandbox
            preference.setPaymentMethods(paymentMethods);

            // Nota: Asegúrate de tener el atributo 'binaryMode' y su setter en tu clase PreferenceRequest (DTO)
            preference.setBinaryMode(true);

            // 🔹 Expiración: ELIMINADA POR COMPLETO EN ESTA VERSIÓN
            // Al no enviar fechas de expiración, evitamos el error de link caducado
            // causado por el desfase de zonas horarias entre tu servidor y Mercado Pago.

            // 🔹 Headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(accessToken);

            HttpEntity<PreferenceRequest> request =
                    new HttpEntity<>(preference, headers);

            String url = apiUrl + "/checkout/preferences";

            log.info("📤 Creando preferencia MP → Consulta: {}, Usuario: {}, Monto: {} {}",
                    consultaId, whatsappId, precioFinal, currency);

            ResponseEntity<PreferenceResponse> response =
                    restTemplate.exchange(
                            url,
                            HttpMethod.POST,
                            request,
                            PreferenceResponse.class
                    );

            if (response.getStatusCode() == HttpStatus.CREATED ||
                    response.getStatusCode() == HttpStatus.OK) {

                PreferenceResponse body = response.getBody();

                if (body == null) {
                    log.error("❌ Respuesta vacía de MercadoPago");
                    return null;
                }

                String paymentUrl = isTestMode()
                        ? body.getSandboxInitPoint()
                        : body.getInitPoint();

                if (paymentUrl == null || paymentUrl.isBlank()) {
                    log.error("❌ URL de pago no generada");
                    return null;
                }

                log.info("✅ Preferencia creada");
                log.info("   📋 Preference ID: {}", body.getId());
                log.info("   📝 External Ref: {}", externalReference);
                log.info("   🔗 Payment URL: {}", paymentUrl);

                return new PaymentLinkResult(
                        paymentUrl,
                        externalReference,
                        body.getId()
                );
            }

            log.error("❌ Error creando preferencia MP. Status: {}", response.getStatusCode());
            return null;

        } catch (HttpClientErrorException e) {
            log.error("❌ Error 4xx MP → {} - {}",
                    e.getStatusCode(),
                    e.getResponseBodyAsString());
            return null;

        } catch (HttpServerErrorException e) {
            log.error("❌ Error 5xx MP → {}", e.getStatusCode());
            return null;

        } catch (ResourceAccessException e) {
            log.error("❌ Error conexión MP → {}", e.getMessage());
            return null;

        } catch (Exception e) {
            log.error("❌ Error inesperado creando preferencia MP", e);
            return null;
        }
    }

    /**
     * Obtiene información de un pago específico
     */
    public PaymentResponse obtenerInfoPago(Long paymentId) {
        try {
            String url = apiUrl + "/v1/payments/" + paymentId;

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);

            HttpEntity<String> request = new HttpEntity<>(headers);

            ResponseEntity<PaymentResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    request,
                    PaymentResponse.class
            );

            PaymentResponse payment = response.getBody();

            if (payment != null) {
                log.info("📊 Info de pago obtenida:  ID {}, Estado: {}",
                        paymentId, payment.getStatus());
            }

            return payment;

        } catch (Exception e) {
            log.error("❌ Error obteniendo pago {}", paymentId, e);
            return null;
        }
    }

    /**
     * Verifica si estamos en modo de prueba
     */
    private boolean isTestMode() {
        return accessToken != null && accessToken.startsWith("TEST-");
    }

    /**
     * Obtiene el nombre del proveedor
     */
    public String getProviderName() {
        return "MercadoPago";
    }
}