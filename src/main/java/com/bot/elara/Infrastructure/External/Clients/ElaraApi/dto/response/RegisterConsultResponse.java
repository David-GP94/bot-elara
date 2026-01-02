package com.bot.elara.Infrastructure.External.Clients.ElaraApi.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterConsultResponse {
    private Integer code;
    private String status;
    private String mensaje;
    private String consultaId;
}
