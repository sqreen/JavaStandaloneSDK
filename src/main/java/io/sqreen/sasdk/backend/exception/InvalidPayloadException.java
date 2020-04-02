package io.sqreen.sasdk.backend.exception;

public class InvalidPayloadException extends BadHttpStatusException {
    public final static int INVALID_PAYLOAD_STATUS_CODE = 422;

    public InvalidPayloadException(String message) {
        super(INVALID_PAYLOAD_STATUS_CODE, message);
    }
}
