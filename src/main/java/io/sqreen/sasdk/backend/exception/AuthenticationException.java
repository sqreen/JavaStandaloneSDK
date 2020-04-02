package io.sqreen.sasdk.backend.exception;

import com.google.common.collect.ImmutableSet;

import java.util.Set;

public class AuthenticationException extends BadHttpStatusException {
    public final static Set<Integer> BAD_AUTH_STATUS_CODES = ImmutableSet.of(401, 403);

    public AuthenticationException(int code, String message) {
        super(code, message);
    }
}
