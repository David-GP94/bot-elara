package com.bot.elara.Infrastructure.External.Whatsapp.Model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class Image {
    @JsonProperty("caption")
    private String caption;

    @JsonProperty("mime_type")
    private String mimeType;

    @JsonProperty("sha256")
    private String sha256;

    @JsonProperty("id")
    private String id;

    @JsonProperty("url")
    private String url;
}