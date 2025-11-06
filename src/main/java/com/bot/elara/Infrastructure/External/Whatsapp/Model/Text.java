package com.bot.elara.Infrastructure.External.Whatsapp.Model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class Text {
    @JsonProperty("body")
    private String body;
}