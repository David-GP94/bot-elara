package com.bot.elara.Infrastructure.DTO.MercadoPago;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PreferenceResponse {

    @JsonProperty("id")
    private String id;

    @JsonProperty("init_point")
    private String initPoint;

    @JsonProperty("sandbox_init_point")
    private String sandboxInitPoint;

    @JsonProperty("client_id")
    private String clientId;

    @JsonProperty("collector_id")
    private Long collectorId;

    @JsonProperty("operation_type")
    private String operationType;

    @JsonProperty("date_created")
    private String dateCreated;

    @JsonProperty("last_updated")
    private String lastUpdated;

    @JsonProperty("external_reference")
    private String externalReference;
}