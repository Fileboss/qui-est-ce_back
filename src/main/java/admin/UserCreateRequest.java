package admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UserCreateRequest(
        @NotBlank @Size(min = 3, max = 30) String username,
        @NotBlank
        @Size(min = 12)
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
                message = "Password must contain at least one lowercase, one uppercase, and one digit"
        )
        String password,
        @NotBlank @Pattern(regexp = "^(player|admin)$") String role
) {}
