package com.bot.elara.Infrastructure.External.Clients;

import com.bot.elara.Infrastructure.External.Respondio.RespondIoConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class RespondIoClient {

    @Autowired
    private RespondIoConfig config;

    private final RestTemplate restTemplate = new RestTemplate();

    // Record para payload de texto (Java 21+)
    private record SendTextRequest(String channel, String message) {}

    // Record para payload de file (ajusta según docs; ejemplo genérico)
    private record SendFileRequest(String channel, String type, Attachment attachment) {
        private record Attachment(String url, String caption) {}
    }

    public void sendText(String contactId, String text) {
        SendTextRequest request = new SendTextRequest("whatsapp", text); // Ajusta "channel" si es channelId numérico
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + config.getApiKey());
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Endpoint con query param ?contact=
        String url = config.getApiUrl() + "/message/send?contact=" + contactId;
        restTemplate.postForEntity(url, new HttpEntity<>(request, headers), String.class);
    }

    public void sendFile(String contactId, String fileUrl, String caption) {
        // Ejemplo: Asume payload con type="document" para PDFs/términos, o "image" para fotos
        SendFileRequest.Attachment attachment = new SendFileRequest.Attachment(fileUrl, caption);
        SendFileRequest request = new SendFileRequest("whatsapp", "document", attachment); // Ajusta type según channel

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + config.getApiKey());
        headers.setContentType(MediaType.APPLICATION_JSON);

        String url = config.getApiUrl() + "/message/send?contact=" + contactId;
        restTemplate.postForEntity(url, new HttpEntity<>(request, headers), String.class);
    }
}
