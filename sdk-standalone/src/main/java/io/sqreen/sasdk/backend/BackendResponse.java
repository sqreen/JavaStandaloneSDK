package io.sqreen.sasdk.backend;

import io.sqreen.sasdk.backend.exception.BadHttpStatusException;

import java.io.IOException;

public final class BackendResponse<T> {

    private final Exception error;
    private final T response;
    private final int status;

    /**
     * Builds a backend response from entity
     *
     * @param response response entity
     * @param <T>      type of response entity
     * @return backend response from entity
     */
    public static <T> BackendResponse<T> from(T response) {
        return new BackendResponse<T>(response, null, -1);
    }


    /**
     * Builds a backend response from a thrown exception
     *
     * @param error thrown exception
     * @param <T>   type of response body
     * @return backend response from error
     */
    public static <T> BackendResponse<T> from(IOException error) {
        int status = error instanceof BadHttpStatusException ? ((BadHttpStatusException)error).getResponseCode() : -1;
        return new BackendResponse<T>(null, error, status);
    }


    private BackendResponse(T response, Exception error, int status) {
        this.response = response;
        if (error != null) {
            this.error = error;
        } else if (response == null) {
            this.error = new NullPointerException("response is null");
        } else {
            this.error = null;
        }
        this.status = status;
    }

    public boolean isError() {
        return error != null;
    }

    // not getError() to avoid property conflict with isError()
    public Exception fetchError() {
        return error;
    }

    public int getStatus(){
        return status;
    }

    public T getResponse() {
        return response;
    }
}
