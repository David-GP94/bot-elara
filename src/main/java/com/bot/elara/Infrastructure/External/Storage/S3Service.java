package com.bot.elara.Infrastructure.External.Storage;

import org.springframework.stereotype.Service;
import java.net.URL;
import java.util.UUID;

@Service
public class S3Service {

    // MOCK: Solo para pruebas (dev/sandbox)
    public String uploadFromUrl(String imageUrl, String mediaId) {
        // En desarrollo: simula una URL de S3
        String mockS3Url = "https://s3.mock.clinica.com/fotos/" + mediaId + "-" + UUID.randomUUID() + ".jpg";
        System.out.println("Foto simulada subida a: " + mockS3Url);
        return mockS3Url;
    }

    // VERSIÓN REAL (descomenta cuando estés listo)
    /*
    @Autowired
    private AmazonS3 s3Client;

    @Value("${aws.s3.bucket}")
    private String bucketName;

    public String uploadFromUrl(String imageUrl, String mediaId) throws IOException {
        // 1. Descargar imagen de WhatsApp
        byte[] imageBytes = downloadImage(imageUrl);

        // 2. Subir a S3
        String fileName = "patients/" + mediaId + ".jpg";
        s3Client.putObject(bucketName, fileName, new ByteArrayInputStream(imageBytes), null);

        // 3. Devolver URL pública
        return s3Client.getUrl(bucketName, fileName).toString();
    }

    private byte[] downloadImage(String url) throws IOException {
        return new URL(url).openStream().readAllBytes();
    }
    */
}
