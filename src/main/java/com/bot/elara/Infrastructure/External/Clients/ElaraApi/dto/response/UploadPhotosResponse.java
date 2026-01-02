package com.bot.elara.Infrastructure.External.Clients.ElaraApi.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadPhotosResponse {
    private Integer code;
    private String status;
    private String mensaje;
    private List<String> photoUrls;  // URLs de las fotos subidas
    private Integer totalUploaded;   // Cantidad de fotos subidas exitosamente
}
