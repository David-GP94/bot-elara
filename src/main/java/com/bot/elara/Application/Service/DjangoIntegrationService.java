package com.bot.elara.Application.Service;

import com.bot.elara.Infrastructure.DTO.Django.*;
import com.bot.elara.Infrastructure.External.Clients.DjangoApiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DjangoIntegrationService {

    private final DjangoApiClient djangoApiClient;

    @Value("${django.api.key}")
    private String apiKey;

    public BotUserResponse crearUsuario(String email, String nombre) {

        BotUserRequest request = new BotUserRequest();
        request.setEmail(email);
        request.setNombre(nombre);

        return djangoApiClient.crearUsuario(apiKey, request);
    }

    public BotCreateConsultaResponse crearConsulta(BotCreateConsultaRequest request) {
        return djangoApiClient.crearConsulta(apiKey, request);
    }

    public GenericSuccessResponse attachStripeSession(AttachStripeSessionRequest request) {
        return djangoApiClient.attachStripeSession(apiKey, request);
    }

}
