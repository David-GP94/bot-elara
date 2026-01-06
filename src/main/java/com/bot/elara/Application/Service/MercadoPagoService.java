package com.bot. elara.Application.Service;

import com.bot.elara. Infrastructure. DTO.MercadoPago.PreferenceRequest;
import com.bot.elara.Infrastructure.DTO.MercadoPago.PreferenceResponse;
import com.bot. elara.Infrastructure.DTO. MercadoPago.PaymentResponse;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org. springframework.beans.factory.annotation.Value;
import org.springframework. http.*;
import org.springframework.stereotype.Service;
import org.springframework. web.client.HttpClientErrorException;
import org. springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
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
    public String crearPaymentLink(String consultaId, String whatsappId, String email) {
        log.info("🛍️ INICIANDO creación de link MercadoPago");
        log.info("   consultaId: {}", consultaId);
        log.info("   whatsappId: {}", whatsappId);
        log.info("   email: {}", email);
        log.info("   accessToken presente: {}", accessToken != null && !accessToken.isBlank());
        
        // Validaciones
        if (consultaId == null || consultaId.isBlank()) {
            log.error("❌ consultaId no puede estar vacío");
            return null;
        }

        if (whatsappId == null || whatsappId.isBlank()) {
            log.error("❌ whatsappId no puede estar vacío");
            return null;
        }
        
        if (accessToken == null || accessToken.isBlank()) {
            log.error("❌ ACCESS TOKEN DE MERCADOPAGO NO CONFIGURADO");
            log.error("   Verifica que mercadopago.access.token esté en application.yml o .env");
            return null;
        }

        if (email == null || !email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {
            log.warn("⚠️ Email inválido: {}, usando default", email);
            email = "noreply@elara.com";
        }

        try {
            // Construir la preferencia de pago
            PreferenceRequest preference = new PreferenceRequest();

            // Convertir amount de centavos a decimal
            BigDecimal priceDecimal = BigDecimal.valueOf(amount)
                    .divide(BigDecimal.valueOf(100));

            // Items del pago
            List<PreferenceRequest.Item> items = new ArrayList<>();
            PreferenceRequest.Item item = new PreferenceRequest.Item(
                    "Consulta Dermatológica Elara",
                    "Revisión personalizada + diagnóstico + tratamiento",
                    1,
                    currency,
                    priceDecimal
            );
            items.add(item);
            preference.setItems(items);

            // Datos del comprador (opcional, permite guest checkout)
            PreferenceRequest.Payer payer = new PreferenceRequest.Payer(
                    email,
                    whatsappId
            );
            preference.setPayer(payer);

            // URLs de retorno - DEBEN estar configuradas cuando usas auto_return
            PreferenceRequest.BackUrls backUrls = new PreferenceRequest.BackUrls(
                    baseUrl + "/pago-exito-whatsapp",  // success
                    baseUrl + "/pago-cancelado-whatsapp",  // failure
                    baseUrl + "/pago-pendiente-whatsapp"  // pending
            );
            preference.setBackUrls(backUrls);
            
            // Auto return - Comentado porque requiere HTTPS y URLs públicas válidas
            // Para desarrollo/test es mejor omitirlo
            // preference.setAutoReturn("approved");

            // Webhook
            preference.setNotificationUrl(notificationUrl);

            // External reference mejorado
            String externalReference = String.format("ELARA-%s-%s-%d",
                    consultaId,
                    whatsappId. replaceAll("[^0-9]", ""),
                    System.currentTimeMillis()
            );
            preference.setExternalReference(externalReference);

            // Statement descriptor
            preference.setStatementDescriptor("ELARA DERMATO");

            // Metadata
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("consulta_id", consultaId);
            metadata.put("whatsapp_id", whatsappId);
            metadata.put("timestamp", System.currentTimeMillis());
            metadata.put("channel", "whatsapp_bot");
            preference.setMetadata(metadata);

            // Configuración de métodos de pago
            PreferenceRequest.PaymentMethods paymentMethods = new PreferenceRequest.PaymentMethods();
            paymentMethods.setInstallments(1);  // Solo 1 pago sin cuotas
            preference.setPaymentMethods(paymentMethods);

            // Expiración (2 horas)
            OffsetDateTime now = OffsetDateTime.now();
            preference.setExpires(true);
            preference.setExpirationDateFrom(now.toString());
            preference.setExpirationDateTo(now.plusHours(2).toString());

            // Headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(accessToken);

            HttpEntity<PreferenceRequest> request = new HttpEntity<>(preference, headers);

            // Llamada a la API
            String url = apiUrl + "/checkout/preferences";
            log.info("📤 Creando preferencia de pago para {} → Consulta:  {}", whatsappId, consultaId);

            ResponseEntity<PreferenceResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    request,
                    PreferenceResponse.class
            );

            if (response.getStatusCode() == HttpStatus.CREATED ||
                    response.getStatusCode() == HttpStatus.OK) {

                PreferenceResponse preferenceResponse = response.getBody();

                if (preferenceResponse == null) {
                    log. error("❌ Respuesta vacía de MercadoPago");
                    return null;
                }

                String paymentUrl = isTestMode()
                        ? preferenceResponse.getSandboxInitPoint()
                        : preferenceResponse.getInitPoint();

                if (paymentUrl == null || paymentUrl.isBlank()) {
                    log.error("❌ URL de pago no generada");
                    return null;
                }

                log.info("✅ Preferencia creada exitosamente");
                log.info("   📋 Preference ID: {}", preferenceResponse. getId());
                log.info("   🔗 Payment URL: {}", paymentUrl);
                log.info("   📝 External Ref: {}", externalReference);

                return paymentUrl;
            }

            log.error("❌ Error al crear preferencia:  Status {}", response.getStatusCode());
            return null;

        } catch (HttpClientErrorException e) {
            log.error("❌ Error del cliente (4xx) para {}: {} - {}",
                    whatsappId, e.getStatusCode(), e.getResponseBodyAsString());
            log.error("   Headers enviados: Authorization=Bearer {}...", 
                accessToken != null ? accessToken.substring(0, Math.min(20, accessToken.length())) : "null");
            return null;

        } catch (HttpServerErrorException e) {
            log.error("❌ Error del servidor MercadoPago (5xx) para {}: {} - {}",
                    whatsappId, e.getStatusCode(), e.getResponseBodyAsString());
            return null;

        } catch (ResourceAccessException e) {
            log.error("❌ Error de conexión con MercadoPago para {}: {}",
                    whatsappId, e.getMessage());
            return null;
        
        } catch (Exception e) {
            log.error("❌ Error inesperado al crear payment link de MercadoPago", e);
            log.error("   Clase: {}", e.getClass().getName());
            log.error("   Mensaje: {}", e.getMessage());
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

            ResponseEntity<PaymentResponse> response = restTemplate. exchange(
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