package image;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.UUID;

@RequiredArgsConstructor
@ApplicationScoped
public class ImageService {

    private final S3Client s3; // Injecté automatiquement par Quarkus

    @ConfigProperty(name = "game.bucket.name")
    String bucketName;

    public String uploadImage(byte[] data, String mimeType) {
        String key = UUID.randomUUID().toString();

        s3.putObject(
                PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(key)
                        .contentType(mimeType)
                        .build(),
                RequestBody.fromBytes(data)
        );

        return key;
    }

    public String getImageUrl(String key) {
        return s3.utilities().getUrl(builder -> builder.bucket(bucketName).key(key)).toExternalForm();
    }
}