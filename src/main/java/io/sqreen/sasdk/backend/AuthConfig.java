package io.sqreen.sasdk.backend;

import com.google.common.base.Optional;

public interface AuthConfig {

    Optional<String> getAPIKey();
    Optional<String> getAppName();
    Optional<String> getSessionKey();



}
