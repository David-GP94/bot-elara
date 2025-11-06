package com.bot.elara.Infrastructure.External.Whatsapp.Model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

@Data
public class Value {
    @JsonProperty("messaging_product")
    private String messagingProduct;

    @JsonProperty("metadata")
    private Metadata metadata;

    @JsonProperty("messages")
    private List<Message> messages;
}