package com.bot.elara.Application.Service;

import com.bot.elara.Infrastructure.External.Clients.ElaraApi.ElaraApiAuthClient;
import com.bot.elara.Infrastructure.External.Clients.ElaraApi.dto.request.LoginRequest;
import com.bot.elara.Infrastructure.External.Clients.ElaraApi.dto.request.RefreshTokenRequest;
import com.bot.elara.Infrastructure.External.Clients.ElaraApi.dto.response.LoginResponse;
import com.bot.elara.Infrastructure.External.Clients.ElaraApi.dto.response.RefreshTokenResponse;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Servicio para manejar la autenticación con la API externa de Elara
 * 
 * Este servicio NO es para usuarios del bot, es para obtener un token
 * de sistema que se usa para autenticar todas las peticiones a las APIs.
 * 
 * El login se hace automáticamente al iniciar la aplicación usando
 * credenciales configuradas en application.yml
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ElaraApiAuthService {

    private final ElaraApiAuthClient authClient;

    @Value("${elara.api.auth.email}")
    private String systemEmail;

    @Value("${elara.api.auth.password}")
    private String systemPassword;

    // Estado de la sesión actual (en memoria)
    private String currentAccessToken;
    private String currentRefreshToken;
    private Instant tokenExpiration;
    
    // Tiempo de expiración del token en segundos (por defecto 1 hora)
    private static final long DEFAULT_TOKEN_EXPIRATION = 3600L;

    /**
     * Se ejecuta automáticamente al iniciar la aplicación
     * Hace login con las credenciales del sistema
     */
    @PostConstruct
    public void init() {
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.info("🔐 Inicializando autenticación con API Elara");
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        
        if (systemEmail == null || systemEmail.isBlank()) {
            log.error("❌ No se configuró elara.api.auth.email en application.yml");
            return;
        }

        if (systemPassword == null || systemPassword.isBlank()) {
            log.error("❌ No se configuró elara.api.auth.password en application.yml");
            return;
        }

        // Hacer login automáticamente
        LoginResponse response = login(systemEmail, systemPassword);
        
        if (response != null) {
            log.info("✅ Autenticación exitosa al iniciar la aplicación");
            log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        } else {
            log.error("❌ No se pudo autenticar con la API al iniciar");
            log.error("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        }
    }

    /**
     * Realiza login con email y password del sistema
     */
    private LoginResponse login(String email, String password) {
        try {
            log.info("🔐 Realizando login del sistema...");
            
            LoginRequest request = LoginRequest.builder()
                    .email(email)
                    .password(password)
                    .build();

            // Feign hace: POST http://v2lara.com/api/auth/login
            LoginResponse response = authClient.login(request);

            if (response != null && response.getAccess() != null) {
                // Guardar tokens en memoria
                this.currentAccessToken = response.getAccess();
                this.currentRefreshToken = response.getRefresh();
                
                // Calcular expiración (restar 5 minutos de margen)
                this.tokenExpiration = Instant.now().plusSeconds(DEFAULT_TOKEN_EXPIRATION - 300);

                log.info("✅ Token de acceso obtenido");
                log.debug("Access token preview: {}...", response.getAccess().substring(0, Math.min(30, response.getAccess().length())));
                
                return response;
            }

            log.error("❌ Login falló - respuesta vacía o sin tokens");
            return null;

        } catch (Exception e) {
            log.error("❌ Error en login del sistema", e);
            return null;
        }
    }

    /**
     * Refresca el token de acceso cuando está por expirar
     */
    private RefreshTokenResponse refreshToken() {
        try {
            if (currentRefreshToken == null || currentRefreshToken.isBlank()) {
                log.warn("⚠️ No hay refresh token disponible, reintentando login...");
                LoginResponse loginResponse = login(systemEmail, systemPassword);
                return loginResponse != null ? RefreshTokenResponse.builder()
                        .access(loginResponse.getAccess())
                        .refresh(loginResponse.getRefresh())
                        .build() : null;
            }

            log.info("🔄 Refrescando token de acceso...");

            RefreshTokenRequest request = RefreshTokenRequest.builder()
                    .refresh(currentRefreshToken)
                    .build();

            // Feign hace: POST http://v2lara.com/api/auth/refresh
            RefreshTokenResponse response = authClient.refreshToken(request);

            if (response != null && response.getAccess() != null) {
                // Actualizar tokens en memoria
                this.currentAccessToken = response.getAccess();
                this.currentRefreshToken = response.getRefresh();
                
                // Renovar expiración
                this.tokenExpiration = Instant.now().plusSeconds(DEFAULT_TOKEN_EXPIRATION - 300);

                log.info("✅ Token refrescado exitosamente");
                return response;
            }

            log.error("❌ Refresh token falló");
            return null;

        } catch (Exception e) {
            log.error("❌ Error refrescando token", e);
            return null;
        }
    }

    /**
     * Obtiene un token de acceso válido
     * Si el token está expirado, lo refresca automáticamente
     * 
     * Este método es usado por el FeignAuthInterceptor para agregar
     * el token a TODAS las peticiones a las otras APIs
     * 
     * @return Token de acceso válido o null si no hay sesión
     */
    public String getValidToken() {
        // Si no hay token, intentar hacer login
        if (currentAccessToken == null) {
            log.warn("⚠️ No hay token de acceso. Intentando login...");
            LoginResponse response = login(systemEmail, systemPassword);
            return response != null ? response.getAccess() : null;
        }

        // Verificar si el token está por expirar
        if (tokenExpiration != null && Instant.now().isAfter(tokenExpiration)) {
            log.info("⏰ Token expirado, refrescando automáticamente...");
            
            RefreshTokenResponse response = refreshToken();
            
            if (response == null) {
                log.error("❌ No se pudo refrescar el token. Reintentando login...");
                LoginResponse loginResponse = login(systemEmail, systemPassword);
                return loginResponse != null ? loginResponse.getAccess() : null;
            }
        }

        return currentAccessToken;
    }

    /**
     * Verifica si hay una sesión activa válida
     */
    public boolean hasValidSession() {
        return currentAccessToken != null && 
               (tokenExpiration == null || Instant.now().isBefore(tokenExpiration));
    }

    /**
     * Limpia la sesión actual
     */
    public void clearSession() {
        this.currentAccessToken = null;
        this.currentRefreshToken = null;
        this.tokenExpiration = null;
        log.info("🧹 Sesión limpiada");
    }

    /**
     * Obtiene el token de acceso actual (sin validar expiración)
     * Usar con precaución, preferir getValidToken()
     */
    public String getCurrentAccessToken() {
        return currentAccessToken;
    }

    /**
     * Obtiene el token de refresh actual
     */
    public String getCurrentRefreshToken() {
        return currentRefreshToken;
    }
}
