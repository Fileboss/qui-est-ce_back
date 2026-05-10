package admin;

import io.quarkus.arc.profile.IfBuildProfile;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Set;

@ApplicationScoped
@IfBuildProfile("prod")
@Startup
public class SecretGuard {

    private static final Set<String> FORBIDDEN = Set.of(
            "change_me", "dev-secret", "REPLACE_WITH_PROD_SECRET");

    @ConfigProperty(name = "quarkus.oidc.credentials.secret")
    String oidcSecret;

    @ConfigProperty(name = "quarkus.keycloak.admin-client.client-secret")
    String adminSecret;

    @PostConstruct
    void check() {
        validate("OIDC_CLIENT_SECRET", oidcSecret);
        validate("KEYCLOAK_ADMIN_SECRET", adminSecret);
        if (oidcSecret.equals(adminSecret)) {
            throw new IllegalStateException(
                    "OIDC_CLIENT_SECRET and KEYCLOAK_ADMIN_SECRET must differ — they belong to two distinct Keycloak clients.");
        }
    }

    private static void validate(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is not set");
        }
        if (FORBIDDEN.contains(value)) {
            throw new IllegalStateException(name + " is set to a placeholder value (" + value + ")");
        }
    }
}
