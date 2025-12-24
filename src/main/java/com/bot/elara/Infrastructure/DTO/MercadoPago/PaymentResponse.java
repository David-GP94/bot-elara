package com. bot.elara.Infrastructure. DTO.MercadoPago;

import com.fasterxml. jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {

    @JsonProperty("id")
    private Long id;

    @JsonProperty("status")
    private String status;

    @JsonProperty("status_detail")
    private String statusDetail;

    @JsonProperty("external_reference")
    private String externalReference;

    @JsonProperty("transaction_amount")
    private BigDecimal transactionAmount;

    @JsonProperty("currency_id")
    private String currencyId;

    @JsonProperty("payment_method_id")
    private String paymentMethodId;

    @JsonProperty("payment_type_id")
    private String paymentTypeId;

    @JsonProperty("date_created")
    private String dateCreated;

    @JsonProperty("date_approved")
    private String dateApproved;

    @JsonProperty("payer")
    private Payer payer;

    @JsonProperty("metadata")
    private Object metadata;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Payer {
        @JsonProperty("id")
        private Long id;

        @JsonProperty("email")
        private String email;

        @JsonProperty("identification")
        private Identification identification;

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Identification {
            @JsonProperty("type")
            private String type;

            @JsonProperty("number")
            private String number;
        }
    }
}