package com.bot.elara.Infrastructure.External.Clients.ElaraApi;

import com.bot.elara.Infrastructure.External.Clients.ElaraApi.dto.request.LoginRequest;
import com.bot.elara.Infrastructure.External.Clients.ElaraApi.dto.request.RefreshTokenRequest;
import com.bot.elara.Infrastructure.External.Clients.ElaraApi.dto.response.LoginResponse;
import com.bot.elara.Infrastructure.External.Clients.ElaraApi.dto.response.RefreshTokenResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Feign Client para autenticación con la API externa de Elara
 * 
 * Endpoints:
 * - POST /api/auth/login - Autenticación con email y password
 * - POST /api/auth/refresh - Renovación de token con refresh token
 */
@FeignClient(
        name = "elara-auth-client",
        url = "${elara.api.base-url}"
)
public interface ElaraApiAuthClient {

    /**
     * POST /api/auth/login
     * 
     * Request JSON:
     * {
     *   "email": "usuario@ejemplo.com",
     *   "password": "contraseña123"
     * }
     * 
     * Response JSON:
     * {
     *   "access": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
     *   "refresh": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
     * }
     */
    @PostMapping("/api/auth/login")
    LoginResponse login(@RequestBody LoginRequest request);

    /**
     * POST /api/auth/refresh
     * 
     * Request JSON:
     * {
     *   "refresh": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
     * }
     * 
     * Response JSON:
     * {
     *   "access": "nuevo_token_access...",
     *   "refresh": "nuevo_token_refresh..."
     * }
     */
    @PostMapping("/api/auth/refresh")
    RefreshTokenResponse refreshToken(@RequestBody RefreshTokenRequest request);
}
