package com.bot.elara.Infrastructure.External.Clients.ElaraApi.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Respuesta del endpoint POST /api/consult/registry/
 * 
 * Éxito (code 200):
 * {
 *   "code": 200,
 *   "status": "ok",
 *   "mensaje": "Consulta registrada/actualizada correctamente",
 *   "consultaId": "343caec9-c7d0-43bf-b11e-a479433c78b4"
 * }
 * 
 * Error de validación (code 400):
 * {
 *   "code": 400,
 *   "type": "validation_error",
 *   "mensaje": "Faltan datos obligatorios: whatsappId"
 * }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterConsultResponse {
    private Integer code;           // 200 = éxito, 400 = error validación
    private String status;          // "ok" cuando code = 200
    private String mensaje;         // Mensaje descriptivo
    private String consultaId;      // UUID de la consulta generada
    private String type;            // "validation_error" cuando code = 400
}
