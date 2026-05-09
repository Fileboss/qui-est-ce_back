package image;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;

@ApplicationScoped
public class BucketInitializer {

    private static final Logger log = Logger.getLogger(BucketInitializer.class);

    private final S3Client s3;
    private final String bucketName;

    public BucketInitializer(S3Client s3, @ConfigProperty(name = "game.bucket.name") String bucketName) {
        this.s3 = s3;
        this.bucketName = bucketName;
    }

    void onStart(@Observes StartupEvent event) {
        try {
            s3.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
        } catch (NoSuchBucketException e) {
            log.infof("Bucket '%s' not found — creating it", bucketName);
            s3.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());
        }
    }
}
