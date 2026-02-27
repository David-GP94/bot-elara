package com.bot.elara.Infrastructure.External.Clients;

import com.bot.elara.Infrastructure.DTO.Django.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(
        name = "djangoApiClient",
        url = "${django.api.url}"
)
public interface DjangoApiClient {

    @PostMapping("/api/bot/usuario/")
    BotUserResponse crearUsuario(
            @RequestHeader("X-API-KEY") String apiKey,
            @RequestBody BotUserRequest request
    );

    @PostMapping("/api/bot/crear-consulta/")
    BotCreateConsultaResponse crearConsulta(
            @RequestHeader("X-API-KEY") String apiKey,
            @RequestBody BotCreateConsultaRequest request
    );

    @PostMapping("/api/bot/attach-stripe-session/")
    GenericSuccessResponse attachStripeSession(
            @RequestHeader("X-API-KEY") String apiKey,
            @RequestBody AttachStripeSessionRequest request
    );

}
