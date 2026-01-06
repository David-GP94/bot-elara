package com.bot.elara.Infrastructure.External.Clients.ElaraApi.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerifyPatientResponse {
    private Integer code;
    
    @JsonProperty("existe")
    private Boolean existe;
    
    // Campos opcionales para errores
    private String type;
    private String error;
    
    // Campo opcional para patient ID (si lo devuelve después)
    private String patientId;
}
