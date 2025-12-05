package com.bot.elara.Infrastructure.External.Whatsapp.Config;

import lombok.Data;
import lombok.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@ConfigurationProperties(prefix = "whatsapp")
@Data
@Configuration
public class WhatsAppConfig {
    private String phoneNumberId;
    private String accessToken;
    private String webhookVerifyToken;
    private String baseUrl;
    private String logoPath;

    public String getMessagesUrl() {
        return baseUrl + "/" + phoneNumberId + "/messages";
    }
}
