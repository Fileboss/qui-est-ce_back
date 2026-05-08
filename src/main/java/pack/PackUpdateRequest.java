package pack;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PackUpdateRequest(@NotBlank @Size(max = 128) String packName) {
}
