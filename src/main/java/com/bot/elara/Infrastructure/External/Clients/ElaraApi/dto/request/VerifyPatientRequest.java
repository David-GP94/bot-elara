package com.bot.elara.Infrastructure.External.Clients.ElaraApi.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerifyPatientRequest {
    private String whatsappId;
    private String email;
}
