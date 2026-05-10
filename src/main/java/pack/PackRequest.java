package pack;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PackRequest(@NotBlank @Size(max = 128) String packName) {
}
