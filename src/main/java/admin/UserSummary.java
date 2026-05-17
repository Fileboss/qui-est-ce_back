package admin;

import java.util.List;

public record UserSummary(
        String id,
        String username,
        boolean enabled,
        long createdTimestamp,
        List<String> roles
) {}
