package com.bot.elara.Infrastructure.External.Clients.ElaraApi.config;

import feign.Logger;
import feign.RequestInterceptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración global para los Feign Clients de la API de Elara
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class FeignConfig {

    private final FeignAuthInterceptor authInterceptor;

    /**
     * Nivel de logs para Feign
     * FULL = muestra headers, body, y metadata de request/response
     */
    @Bean
    Logger.Level feignLoggerLevel() {
        log.info("🔧 Configurando Feign Logger en modo FULL");
        return Logger.Level.FULL;
    }

    /**
     * Logger personalizado para Feign que muestra requests/responses formateados
     */
    @Bean
    public Logger feignLogger() {
        return new feign.slf4j.Slf4jLogger();
    }

    /**
     * Interceptor para agregar el token de autenticación automáticamente
     */
    @Bean
    public RequestInterceptor requestInterceptor() {
        return authInterceptor;
    }
}
