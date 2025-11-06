package com.bot.elara.Infrastructure.External.Whatsapp;

import com.bot.elara.Infrastructure.External.Whatsapp.Config.WhatsAppConfig;
import com.bot.elara.Infrastructure.External.Whatsapp.Model.TextBody;
import com.bot.elara.Infrastructure.External.Whatsapp.Model.WhatsAppMessageRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@RequiredArgsConstructor
public class WhatsAppCloudApiClient {

    private final WhatsAppConfig config;
    private final RestTemplate restTemplate = new RestTemplate();

    public void sendText(String to, String text) {
        var request = WhatsAppMessageRequest.builder()
                .messagingProduct("whatsapp")
                .to(to)
                .type("text")
                .text(new TextBody(text))
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(config.getAccessToken());
        headers.setContentType(MediaType.APPLICATION_JSON);

        restTemplate.postForEntity(config.getMessagesUrl(), new HttpEntity<>(request, headers), String.class);
    }
}