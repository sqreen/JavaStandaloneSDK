package io.sqreen.sasdk.backend;

import com.google.common.base.Optional;

public interface AuthenticationConfig {
    Optional<String> getAPIKey();
    Optional<String> getAppName();
    Optional<String> getSessionKey();

    enum EmptyConfig implements AuthenticationConfig {
        INSTANCE;

        @Override
        public Optional<String> getAPIKey() {
            return Optional.absent();
        }

        @Override
        public Optional<String> getAppName() {
            return Optional.absent();
        }

        @Override
        public Optional<String> getSessionKey() {
            return Optional.absent();
        }
    }

}
