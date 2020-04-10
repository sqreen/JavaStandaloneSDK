package io.sqreen.sasdk.backend;

import com.google.common.collect.ImmutableMultimap;

public class AuthConfig {

    private final ImmutableMultimap<String, String> props;

    private AuthConfig(ImmutableMultimap<String, String> props) {
        this.props = props;
    }

    public ImmutableMultimap<String, String> getAllProps() {
        return props;
    }

    static AuthConfig createSessionKeyAuthConfig(String sessionKey) {
        return new AuthConfig(ImmutableMultimap.of(
                "X-Session-Key", sessionKey
        ));
    }

    static AuthConfig createApiKeyAuthConfig(String apiKey) {
        return new AuthConfig(ImmutableMultimap.of(
                "X-API-Key", apiKey
        ));
    }

    static AuthConfig createApiKeyAuthConfig(String apiKey, String appName) {
        return new AuthConfig(ImmutableMultimap.of(
                "X-API-Key", apiKey,
                "X-App-Name", appName
        ));
    }

}
