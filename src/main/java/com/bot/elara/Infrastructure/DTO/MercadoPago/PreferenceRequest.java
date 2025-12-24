package com.bot.elara.Infrastructure. DTO.MercadoPago;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok. AllArgsConstructor;
import lombok.Data;
import lombok. NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PreferenceRequest {

    @JsonProperty("items")
    private List<Item> items;

    @JsonProperty("payer")
    private Payer payer;

    @JsonProperty("back_urls")
    private BackUrls backUrls;

    @JsonProperty("auto_return")
    private String autoReturn;

    @JsonProperty("notification_url")
    private String notificationUrl;

    @JsonProperty("external_reference")
    private String externalReference;

    @JsonProperty("statement_descriptor")
    private String statementDescriptor;

    @JsonProperty("expires")
    private Boolean expires;

    @JsonProperty("expiration_date_from")
    private String expirationDateFrom;

    @JsonProperty("expiration_date_to")
    private String expirationDateTo;

    @JsonProperty("metadata")
    private Map<String, Object> metadata;

    @JsonProperty("payment_methods")
    private PaymentMethods paymentMethods;

    // ============================================
    // INNER CLASSES
    // ============================================

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        @JsonProperty("title")
        private String title;

        @JsonProperty("description")
        private String description;

        @JsonProperty("quantity")
        private Integer quantity;

        @JsonProperty("currency_id")
        private String currencyId;

        @JsonProperty("unit_price")
        private BigDecimal unitPrice;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Payer {
        @JsonProperty("email")
        private String email;

        @JsonProperty("phone")
        private Phone phone;

        public Payer(String email, String phoneNumber) {
            this.email = email;
            this.phone = new Phone(phoneNumber);
        }

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Phone {
            @JsonProperty("number")
            private String number;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BackUrls {
        @JsonProperty("success")
        private String success;

        @JsonProperty("failure")
        private String failure;

        @JsonProperty("pending")
        private String pending;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentMethods {
        @JsonProperty("excluded_payment_types")
        private List<String> excludedPaymentTypes;

        @JsonProperty("excluded_payment_methods")
        private List<String> excludedPaymentMethods;

        @JsonProperty("installments")
        private Integer installments;
    }
}