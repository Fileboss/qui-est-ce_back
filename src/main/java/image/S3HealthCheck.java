package image;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;

@Readiness
@ApplicationScoped
public class S3HealthCheck implements HealthCheck {

    private final S3Client s3;
    private final String bucketName;

    public S3HealthCheck(S3Client s3, @ConfigProperty(name = "game.bucket.name") String bucketName) {
        this.s3 = s3;
        this.bucketName = bucketName;
    }

    @Override
    public HealthCheckResponse call() {
        try {
            s3.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
            return HealthCheckResponse.up("s3");
        } catch (Exception e) {
            return HealthCheckResponse.down("s3");
        }
    }
}
