package image;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.net.URI;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

/** Service for uploading card images to S3 and resolving their public URLs. */
@ApplicationScoped
public class ImageService {

    /**
     * Maximum size of an uploaded card image. Enforced at the resource layer
     * (CardResource) before the bytes are materialized, and matches the global
     * {@code quarkus.http.limits.max-body-size} so a non-multipart bypass also hits the cap.
     */
    public static final long MAX_IMAGE_BYTES = 5_000_000L;

    private final S3Client s3;
    private final String bucketName;
    private final Optional<String> publicBaseUrl;

    public ImageService(
            S3Client s3,
            @ConfigProperty(name = "game.bucket.name") String bucketName,
            @ConfigProperty(name = "game.image.public-base-url") Optional<String> publicBaseUrl) {
        this.s3 = s3;
        this.bucketName = bucketName;
        this.publicBaseUrl = publicBaseUrl;
    }

    /** Uploads raw image bytes to S3 and returns the generated object key. */
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

    /** Returns the public URL for the S3 object identified by the given key. */
    public String getImageUrl(String key) {
        // In prod the S3 endpoint is the Docker-internal http://minio:9000, which the
        // browser can't resolve and would also trigger mixed-content. Override with a
        // public, https base URL (e.g. https://s3.qui-est-qui.lepgu.fr) when configured.
        return publicBaseUrl
                .map(base -> base + "/" + bucketName + "/" + key)
                .orElseGet(() -> s3.utilities().getUrl(b -> b.bucket(bucketName).key(key)).toExternalForm());
    }

    /** Deletes the S3 object whose URL was returned by {@link #getImageUrl}. */
    public void deleteImage(String imageUrl) {
        String key = extractKey(imageUrl);
        s3.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build());
    }

    // Works for both virtual-hosted (https://bucket.s3.region.../{key}) and path-style
    // (https://s3.region.../{bucket}/{key}) URLs — the key is always the last non-empty segment.
    private String extractKey(String imageUrl) {
        String path = URI.create(imageUrl).getPath();
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("Image URL has no path: " + imageUrl);
        }
        String[] segments = Arrays.stream(path.split("/"))
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
        if (segments.length == 0) {
            throw new IllegalArgumentException("Image URL has no key: " + imageUrl);
        }
        return segments[segments.length - 1];
    }
}
