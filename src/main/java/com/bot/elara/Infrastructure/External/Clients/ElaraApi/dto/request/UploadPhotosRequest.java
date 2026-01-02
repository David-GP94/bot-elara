package com.bot.elara.Infrastructure.External.Clients.ElaraApi.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadPhotosRequest {
    private List<PhotoData> photos;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PhotoData {
        private String filename;      // "lesion1.jpg"
        private String contentType;   // "image/jpeg"
        private String data;          // Base64 string: "/9j/4AAQSkZJRgABAQAAAQABAAD..."
    }
}
