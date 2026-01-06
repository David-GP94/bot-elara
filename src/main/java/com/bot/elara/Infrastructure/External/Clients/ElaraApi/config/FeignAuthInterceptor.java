package com.bot.elara.Infrastructure.External.Clients.ElaraApi.config;

import com.bot.elara.Application.Service.ElaraApiAuthService;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Interceptor de Feign que agrega automáticamente el token de autenticación
 * a todas las peticiones a la API de Elara (excepto login y refresh)
 */
@Component
@Slf4j
public class FeignAuthInterceptor implements RequestInterceptor {

    private final ElaraApiAuthService authService;

    // @Lazy rompe el ciclo de dependencias circular
    public FeignAuthInterceptor(@Lazy ElaraApiAuthService authService) {
        this.authService = authService;
    }

    @Override
    public void apply(RequestTemplate template) {
        // No agregar token a las rutas de autenticación
        if (template.path().contains("/api/auth/login") || 
            template.path().contains("/api/auth/refresh")) {
            log.debug("⚪ Saltando autenticación para: {}", template.path());
            return;
        }

        // Obtener token válido (se refresca automáticamente si expiró)
        String token = authService.getValidToken();
        
        if (token != null && !token.isBlank()) {
            template.header("Authorization", "Bearer " + token);
            log.info("🔑 Token agregado a la petición: {} {} (Token: {}...)", 
                template.method(), template.path(), token.substring(0, Math.min(20, token.length())));
        } else {
            log.error("❌ No se pudo obtener token válido para: {} {}", template.method(), template.path());
            log.error("   authService: {}", authService);
            log.error("   hasValidSession: {}", authService != null ? authService.hasValidSession() : "null");
        }
    }
}
