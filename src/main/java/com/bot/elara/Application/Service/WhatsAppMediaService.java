package com.bot.elara.Application.Service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class WhatsAppMediaService {

    @Value("${whatsapp.access-token}")
    private String accessToken;

    private final RestTemplate restTemplate = new RestTemplate();

    public byte[] downloadMedia(String mediaId) {

        // Paso 1: Obtener URL temporal
        String urlEndpoint = "https://graph.facebook.com/v20.0/" + mediaId;

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                urlEndpoint,
                HttpMethod.GET,
                entity,
                Map.class
        );

        String mediaUrl = (String) response.getBody().get("url");

        if (mediaUrl == null) {
            throw new RuntimeException("No se pudo obtener URL del media");
        }

        // Paso 2: Descargar binario
        ResponseEntity<byte[]> mediaResponse = restTemplate.exchange(
                mediaUrl,
                HttpMethod.GET,
                entity,
                byte[].class
        );

        return mediaResponse.getBody();
    }
}