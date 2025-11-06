package com.bot.elara.Infrastructure.External.Whatsapp.Model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TextBody {

    @JsonProperty("preview_url")
    private boolean previewUrl = false;

    @JsonProperty("body")
    private String body;

    public TextBody(String body) {
        this.body = body;
        this.previewUrl = false;
    }
}