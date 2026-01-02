package com.bot.elara.Infrastructure.External.Clients.ElaraApi.config;

import feign.Logger;
import feign.RequestInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración global para los Feign Clients de la API de Elara
 */
@Configuration
@RequiredArgsConstructor
public class FeignConfig {

    private final FeignAuthInterceptor authInterceptor;

    /**
     * Nivel de logs para Feign
     * FULL = muestra headers, body, y metadata de request/response
     */
    @Bean
    Logger.Level feignLoggerLevel() {
        return Logger.Level.FULL;
    }

    /**
     * Interceptor para agregar el token de autenticación automáticamente
     */
    @Bean
    public RequestInterceptor requestInterceptor() {
        return authInterceptor;
    }
}
