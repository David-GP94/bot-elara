package com.bot.elara.Infrastructure.External.Clients.ElaraApi.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerifyPatientResponse {
    private Boolean exists;
    private String patientId;
    private String message;
}
