package com.bot.elara.Application.Service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class S3StorageService {

    private final S3Client s3Client;

    @Value("${aws.s3.bucket}")
    private String bucket;

    public String upload(byte[] fileBytes, String mimeType, String whatsappId) {

        String extension = resolveExtension(mimeType);

        String fileName = "consultas/bot/" +
                whatsappId + "/" +
                UUID.randomUUID() + extension;

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(fileName)
                .contentType(mimeType)
                .build();

        s3Client.putObject(request, RequestBody.fromBytes(fileBytes));

        return fileName;
    }

    private String resolveExtension(String mimeType) {
        if (mimeType == null) {
            return ".jpg";
        }

        return switch (mimeType) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/jpeg" -> ".jpg";
            default -> ".jpg";
        };
    }
}