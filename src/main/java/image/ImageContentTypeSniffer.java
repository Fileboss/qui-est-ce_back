package image;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;
import java.util.Set;

/** Detects an image's MIME type by inspecting its magic bytes; rejects anything else. */
@ApplicationScoped
public class ImageContentTypeSniffer {

    public static final Set<String> ALLOWED = Set.of("image/jpeg", "image/png", "image/webp");

    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };
    private static final byte[] JPEG_SIGNATURE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] RIFF = {'R', 'I', 'F', 'F'};
    private static final byte[] WEBP = {'W', 'E', 'B', 'P'};

    public Optional<String> sniff(byte[] bytes) {
        if (bytes == null) {
            return Optional.empty();
        }
        if (startsWith(bytes, 0, PNG_SIGNATURE)) {
            return Optional.of("image/png");
        }
        if (startsWith(bytes, 0, JPEG_SIGNATURE)) {
            return Optional.of("image/jpeg");
        }
        if (startsWith(bytes, 0, RIFF) && startsWith(bytes, 8, WEBP)) {
            return Optional.of("image/webp");
        }
        return Optional.empty();
    }

    /** Returns true if {@code declared} is in the allowlist AND the bytes' magic match it. */
    public boolean matches(String declared, byte[] bytes) {
        if (declared == null || !ALLOWED.contains(declared)) {
            return false;
        }
        return sniff(bytes).map(declared::equals).orElse(false);
    }

    private static boolean startsWith(byte[] bytes, int offset, byte[] prefix) {
        if (bytes.length < offset + prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (bytes[offset + i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
