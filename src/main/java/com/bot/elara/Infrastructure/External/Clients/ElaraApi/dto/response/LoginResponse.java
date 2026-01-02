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
public class LoginResponse {
    
    @JsonProperty("access")
    private String access;
    
    @JsonProperty("refresh")
    private String refresh;
}
