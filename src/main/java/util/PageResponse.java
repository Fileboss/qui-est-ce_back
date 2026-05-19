package util;

import java.util.List;

/** Generic offset/limit page wrapper. {@code total} is the unpaginated count of matching rows. */
public record PageResponse<T>(List<T> items, int first, int max, long total) {
}
