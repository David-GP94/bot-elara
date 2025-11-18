package com.bot.elara.Infrastructure.External.Respondio;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties (prefix = "respondio")
@Data
public class RespondIoConfig {
    private String apiUrl;
    private String apiKey;
}
