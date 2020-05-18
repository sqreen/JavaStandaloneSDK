package io.sqreen.sasdk.backend;

import com.google.common.collect.ImmutableMultimap;

public interface AuthHeadersProvider {
    ImmutableMultimap<String, String> getHeaders();

    /**
     * Configures authentication using a legacy per-app API key. These are they
     * the keys that do not start with <code>env_org_</code>.
     */
    class Api implements AuthHeadersProvider {
        private final String apiKey;

        public Api(String apiKey) {
            this.apiKey = apiKey;
        }

        @Override
        public ImmutableMultimap<String, String> getHeaders() {
            return ImmutableMultimap.of("X-API-Key", apiKey);
        }
    }

    /**
     * Configures authentication using an organization token and an application name.
     */
    class App implements AuthHeadersProvider {
        private final String apiKey;
        private final String appName;

        public App(String apiKey, String appName) {
            this.apiKey = apiKey;
            this.appName = appName;
        }

        @Override
        public ImmutableMultimap<String, String> getHeaders() {
            return ImmutableMultimap.of(
                    "X-API-Key", apiKey,
                    "X-App-Name", appName
            );
        }
    }

    /**
     * Configures authentication using a session key. Session keys are
     * obtained through a login call that is not supported by this SDK.
     */
    class Session implements AuthHeadersProvider {
        private final String sessionKey;

        public Session(String sessionKey) {
            this.sessionKey = sessionKey;
        }

        @Override
        public ImmutableMultimap<String, String> getHeaders() {
            return ImmutableMultimap.of("X-Session-Key", sessionKey);
        }
    }
}
