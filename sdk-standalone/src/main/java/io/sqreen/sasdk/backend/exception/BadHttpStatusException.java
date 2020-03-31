package io.sqreen.sasdk.backend.exception;

import java.io.IOException;

public class BadHttpStatusException extends IOException {

    private int responseCode;

    public BadHttpStatusException(int responseCode, String message) {
        super(message);
        this.responseCode = responseCode;
    }

    public int getResponseCode() {
        return responseCode;
    }
}
