package com.bot.elara.Infrastructure.External.Whatsapp.Model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class Change {
    @JsonProperty("value")
    private Value value;

    @JsonProperty("field")
    private String field;
}