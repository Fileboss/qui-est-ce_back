package util;

import com.fasterxml.jackson.core.JsonProcessingException;

/** Wraps {@link JsonProcessingException} as an unchecked exception for contexts that cannot declare checked throws. */
public class JsonSerializationException extends RuntimeException {

    public JsonSerializationException(JsonProcessingException cause) {
        super(cause);
    }
}
