package io.sqreen.sasdk.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.util.ISO8601DateFormat;
import io.sqreen.sasdk.signals_dto.MetricSignal;
import io.sqreen.sasdk.signals_dto.PointSignal;
import io.sqreen.sasdk.signals_dto.Trace;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.conn.socket.LayeredConnectionSocketFactory;
import org.apache.http.impl.NoConnectionReuseStrategy;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.TimeZone;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Fluent builder for {@link IngestionHttpClient.WithAuthentication} and
 * {@link IngestionHttpClient.WithoutAuthentication} objects.
 *
 * Sample usage:
 *
 * <pre>
 * IngestionHttpClient.WithoutAuthentication service = new IngestionHttpClientBuilder()
 *          .withAlternativeIngestionURL("https://example.com/")
 *          .buildingHttpClient()
 *          .withConnectionTimeoutInMs(5000)
 *          .withReadTimeoutInMs(10000)
 *          .withProxy("http://proxy.com/")
 *          .buildHttpClient()
 *          .withErrorListener(IngestionErrorListener.LoggingErrorListener.INSTANCE)
 *          .createWithAuthentication(
 *                  IngestionHttpClientBuilder.authConfigWithAPIKey("apiKey", "appName"))
 * </pre>
 */
public class IngestionHttpClientBuilder {
    /* this can be a small value, as we we should always have enough connections */
    private static final int CONNECTION_BORROW_TIMEOUT = 200; // in ms
    private static final int MAX_REDIRECTS = 2;
    private static final int MAX_CONCURRENT_CONN = 40;
    private static final String DEFAULT_INJECTION_URL = "https://ingestion.sqreen.com/";
    private static final int DEFAULT_CONNECT_TIMEOUT = 15000;
    private static final int DEFAULT_READ_TIMEOUT = 10000;

    // 1. configure injection url (optional)
    private String url = DEFAULT_INJECTION_URL;

    /**
     * Specifies a alternate endpoint for the HTTP requests of the resulting
     * client. The default is <code>https://ingestion.sqreen.com</code>.
     *
     * @param url an http or https URL
     * @return <code>this</code>, for chaining
     */
    public IngestionHttpClientBuilder withAlternativeIngestionURL(String url) {
        this.url = url;
        return this;
    }

    // 2. configure http client
    private CloseableHttpClient httpClient;
    private boolean httpClientOwned;

    /**
     * Specifies an arbitrary Apache HttpClient 4 to use.
     * @param client the client to use
     * @return an object for chaining
     */
    public WithConfiguredHttpClient withExplicitHttpClient(CloseableHttpClient client) {
        this.httpClientOwned = false;
        this.httpClient = client;
        return new WithConfiguredHttpClient();
    }

    /**
     * Builds an Apache HttpClient 4 object with the default settings.
     * Equivalent to <code>buildingHttpClient().buildHttpClient()</code>
     * @return an object for chaining
     */
    public WithConfiguredHttpClient withDefaultHttpClient() {
        return new BuildingHttpClient().buildHttpClient();
    }

    /**
     * Starts the parameterization of an Apache httpClient 4 object.
     * @return an object for chaining
     */
    public BuildingHttpClient buildingHttpClient() {
        this.httpClientOwned = true;
        return new BuildingHttpClient();
    }

    public class BuildingHttpClient {
        private LayeredConnectionSocketFactory connectionSocketFactory;
        private int connectTimeout = DEFAULT_CONNECT_TIMEOUT;
        private int readTimeout = DEFAULT_READ_TIMEOUT;
        private ProxyConfig proxy = ProxyConfig.DISABLED;

        /**
         * Sets the maximum time for establishing connections with the
         * ingestion backend.
         * @param timeout a timeout value in milliseconds
         * @return <code>this</code>, for chaining
         */
        public BuildingHttpClient withConnectionTimeoutInMs(int timeout) {
            connectTimeout = timeout;
            return this;
        }

        /**
         * Sets the maximum time that the Apache HttpClient will wait
         * for the ingestion backend to respond to a request.
         * @param timeout a timeout value in milliseconds
         * @return <code>this</code>, for chaining
         */
        public BuildingHttpClient withReadTimeoutInMs(int timeout) {
            readTimeout = timeout;
            return this;
        }

        /**
         * Configures a proxy server for the Apache HttpClient to use.
         *
         * Only http and https proxies are supported. Credentials can
         * be given with specifications such as
         * <code>http://user:password@myproxy.com:7171/</code>.
         * The authentication methods supported are those supported
         * by Apache HttpClient 4.
         *
         * @param proxySpec a URL indicating the proxy to use, or null
         *                  to use none
         * @return <code>this</code>, for chaining
         */
        public BuildingHttpClient withProxy(String proxySpec) {
            proxy = ProxyConfig.parse(proxySpec);
            return this;
        }

        /**
         * Configures a socket factory, in order to customize protocols,
         * ciphers and trust, for instance.
         *
         * @param connectionSocketFactory the factory
         * @return <code>this</code>, for chaining
         */
        public BuildingHttpClient withConnectionSocketFactory(
                LayeredConnectionSocketFactory connectionSocketFactory) {
            this.connectionSocketFactory = connectionSocketFactory;
            return this;
        }

        /**
         * Finishes the configuration of the Apache HttpClient.
         * @return an object for chaining
         */
        public WithConfiguredHttpClient buildHttpClient() {
            RequestConfig.Builder requestConfig = RequestConfig.custom()
                    .setMaxRedirects(MAX_REDIRECTS)
                    // max time to obtain a connection
                    .setConnectTimeout(connectTimeout)
                    // max time to wait for a connection from pool
                    .setConnectionRequestTimeout(CONNECTION_BORROW_TIMEOUT)
                    // max time to wait for new data
                    .setSocketTimeout(readTimeout);

            BasicCredentialsProvider proxyCredentials = null;

            if (!proxy.isDisabled()) {
                if (proxy.hasCredentials()) {
                    proxyCredentials = new BasicCredentialsProvider();
                    proxyCredentials.setCredentials(
                            new AuthScope(proxy.getHost(), proxy.getPort()),
                            new UsernamePasswordCredentials(proxy.getUserLogin(), proxy.getUserPassword()));
                }
                requestConfig.setProxy(
                        new HttpHost(proxy.getHost(), proxy.getPort(), proxy.getProtocol()));
            }

            httpClient = HttpClientBuilder.create()
                    .setMaxConnPerRoute(MAX_CONCURRENT_CONN)
                    .setMaxConnTotal(MAX_CONCURRENT_CONN) // we only have one route
                    .setDefaultRequestConfig(requestConfig.build())
                    // don't reuse connections for now, might be sub-optimal but ensures no resources to clean
                    .setConnectionReuseStrategy(new NoConnectionReuseStrategy())
                    // null when there are no proxy credentials
                    .setDefaultCredentialsProvider(proxyCredentials)
                    // null to use JDK default SSL keystore, non-null to use our own embedded keystore
                    .setSSLSocketFactory(this.connectionSocketFactory)
                    .build();

            return new WithConfiguredHttpClient();
        }
    }

    public class WithConfiguredHttpClient {
        private ObjectWriter objectWriter;
        private IngestionErrorListener errorListener;

        /**
         * Specifies a callback that will be invoked when an error occurs during
         * a call to the ingestion backend
         * @param errorListener the callback
         * @see IngestionErrorListener
         * @see IngestionErrorListener.LoggingIngestionErrorListener
         * @see IngestionErrorListener.NoActionIngestionErrorListener
         * @return <code>this</code>, for chaining
         */
        public WithConfiguredHttpClient withErrorListener(IngestionErrorListener errorListener) {
            this.errorListener = errorListener;
            return this;
        }

        /**
         * Specifies a Jackson {@link ObjectWriter} to use for serializing the
         * objects passed to client.
         *
         * You'll likely need to specify this if you want to pass objects other
         * than {@link MetricSignal}, {@link PointSignal} and {@link Trace}.
         *
         * The default {@link ObjectWriter} is created as follows:
         *
         * <pre>
         * ObjectMapper mapper = new ObjectMapper();
         * mapper.setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);
         * mapper.setTimeZone(TimeZone.getTimeZone("GMT+00"));
         * mapper.setDateFormat(new ISO8601DateFormat());
         * return mapper.writer();
         * </pre>
         *
         * @param objectWriter a custom Jackson {@code ObjectWriter}
         * @return <code>this</code>, for chaining
         */
        public WithConfiguredHttpClient withCustomObjectWriter(ObjectWriter objectWriter) {
            this.objectWriter = objectWriter;
            return this;
        }

        private BackendHttpImpl createBackendHttpImpl() {
            if (this.objectWriter == null) {
                this.objectWriter = createDefaultObjectWriter();
            }

            return BackendHttpImpl.builder(httpClient, httpClientOwned)
                    .errorListener(this.errorListener)
                    .errorResponseClass(BackendHttpImpl.IgnoredResponse.class)
                    .objectWriter(this.objectWriter)
                    .build();
        }

        private ObjectWriter createDefaultObjectWriter() {
            ObjectMapper mapper = new ObjectMapper();
            mapper.setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);
            mapper.setTimeZone(TimeZone.getTimeZone("GMT+00"));
            mapper.setDateFormat(new ISO8601DateFormat());
            return mapper.writer();
        }

        // final methods

        /**
         * Finishes the configuration and creates the client without automatic
         * authentication.
         * @return the client with no automatic authentication
         */
        public IngestionHttpClient.WithoutAuthentication createWithoutAuthentication() {
            return new IngestionHttpClient.IngestionHttpClientImpl(
                    url,
                    createBackendHttpImpl());
        }

        /**
         * Finishes the configuration and creates the client with automatic
         * authentication.
         * @see #authConfigWithAPIKey(String)
         * @see #authConfigWithAPIKey(String, String)
         * @see #authConfigWithSessionKey(String)
         * @return the client with automatic authentication
         */
        public IngestionHttpClient.WithAuthentication createWithAuthentication(
                AuthHeadersProvider authHeadersProvider) {
            return new IngestionHttpClient.IngestionHttpAuthClientImpl(
                    url,
                    createBackendHttpImpl(),
                    authHeadersProvider);
        }

        private String getAgentApiKey() {
            throw new IllegalStateException("Sqreen agent either not present, " +
                    "too old, or found no supported web server");
        }

        private String getAgentAppName() {
            throw new IllegalStateException("Sqreen agent either not present, " +
                    "too old, or found no supported web server");
        }

        /**
         * If the Sqreen agent is loaded, creates an authenticated instance with
         * either the global credentials (token and possibly app name configured),
         * or, preferably, if available, the app-specific credentials of the
         * application from which this call was made.
         *
         * The resulting client will use the api key and app name, not the session
         * key, so the returned value can and should be cached, as the the client
         * is not vulnerable to the session being dropped.
         *
         * @return the authenticated client
         * @throws IllegalStateException is the sqreen agent is not present or
         * it's not configured with credentials.
         */
        public IngestionHttpClient.WithAuthentication createWithAgentAuthentication() {
            return createWithAuthentication(authConfigWithAPIKey(
                    getAgentApiKey(), getAgentAppName()));
        }
    }

    /**
     * Configures authentication using a session key. Session keys are
     * obtained through a login call that is not supported by this SDK.
     * See instead {@link #authConfigWithAPIKey(String, String)}.
     *
     * @param sessionKey the session id identifying the instance
     * @return a configuration object to pass to
     *         {@link WithConfiguredHttpClient#createWithAuthentication(AuthHeadersProvider)}
     */
    public static AuthHeadersProvider authConfigWithSessionKey(String sessionKey) {
        return new AuthHeadersProvider.Session(sessionKey);
    }

    /**
     * Configures authentication using a legacy per-app API key. These are they
     * the keys that do not start with <code>org_</code>.
     * @param apiKey the legacy (per-app) API key. Cannot be null
     * @return a configuration object to pass to
     *         {@link WithConfiguredHttpClient#createWithAuthentication(AuthHeadersProvider)}
     * @see #authConfigWithAPIKey(String, String)
     */
    public static AuthHeadersProvider authConfigWithAPIKey(String apiKey) {
        return new AuthHeadersProvider.Api(apiKey);
    }

    /**
     * Configures authentication using an organization token and an application name.
     *
     * (If app name is null, this method behaves like
     * {@link #authConfigWithAPIKey(String)}.)
     *
     * @param apiKey the organization key. Cannot be null
     * @param appName the app name
     * @return a configuration object to pass to
     *         {@link WithConfiguredHttpClient#createWithAuthentication(AuthHeadersProvider)}
     */
    public static AuthHeadersProvider authConfigWithAPIKey(String apiKey, String appName) {
        return new AuthHeadersProvider.App(apiKey, appName);
    }

    public static class ProxyConfig {

        private static final ProxyConfig DISABLED = new ProxyConfig(null, null, -1, null, null);

        private final String host;
        private final int port;
        private final String userName;
        private final String userPwd;
        private final String protocol;

        private ProxyConfig(String protocol, String host, int port, String userName, String userPwd) {
            this.protocol = protocol;
            this.host = host;
            this.port = port;
            this.userName = userName;
            this.userPwd = userPwd;
        }

        /**
         * @return disabled proxy configuration
         */
        public static ProxyConfig disabled() {
            return DISABLED;
        }

        /**
         * Parses proxy configuration from string
         *
         * @param proxy proxy definition in URI format
         * @return proxy configuration
         * @throws IllegalArgumentException when proxy definition is invalid
         */
        public static ProxyConfig parse(String proxy) {
            if (proxy == null || proxy.isEmpty()) {
                return DISABLED;
            }

            URI uri;
            String protocol;
            try {
                uri = new URI(proxy);
                protocol = uri.getScheme();
            } catch (URISyntaxException e) {
                throw new IllegalArgumentException("invalid proxy uri");
            }
            checkArgument(protocol != null && !protocol.isEmpty(), "missing or empty protocol");

            String host = uri.getHost();
            checkArgument(host != null && !host.isEmpty(), "missing or empty protocol");

            boolean isHttp = protocol.equals("http");
            boolean isHttps = protocol.equals("https");

            checkArgument(isHttp || isHttps, "unsupported protocol %s", protocol);

            int port = uri.getPort();
            if (port <= 0) {
                port = isHttp ? 80 : 443;
            }

            String userName = null;
            String userPwd = null;
            String userInfo = uri.getUserInfo();
            if (userInfo != null && !userInfo.isEmpty()) {
                int sepIndex = userInfo.indexOf(':');
                checkArgument(sepIndex >= 0 && sepIndex < userInfo.length(), "missing user/password separator ':'");

                userName = userInfo.substring(0, sepIndex);
                userPwd = userInfo.substring(sepIndex + 1);

                checkArgument(!userName.isEmpty(), "empty or missing user name");
                checkArgument(!userPwd.isEmpty(), "empty or missing user password");
            }
            return new ProxyConfig(protocol, host, port, userName, userPwd);
        }

        /**
         * @return true if proxy is disabled
         */
        public boolean isDisabled() {
            return host == null;
        }

        /**
         * @return proxy hostname, null if proxy is disabled
         */
        public String getHost() {
            return host;
        }

        /**
         * @return proxy port, negative value if proxy is disabled
         */
        public int getPort() {
            return port;
        }

        /**
         * @return proxy protocol, null if proxy is disabled
         */
        public String getProtocol() {
            return protocol;
        }

        /**
         * @return true if configuration has user credentials
         */
        public boolean hasCredentials() {
            return userName != null && userPwd != null;
        }

        /**
         * @return user login, null if no user credentials
         */
        public String getUserLogin() {
            return userName;
        }

        /**
         * @return user password, null if no user credentials
         */
        public String getUserPassword() {
            return userPwd;
        }

        @Override
        public String toString() {
            if (isDisabled()) {
                return "disabled";
            }
            String userInfo = "";
            if (hasCredentials()) {
                userInfo = this.userName + ":***@";
            }
            return String.format("%s://%s%s:%d/", protocol, userInfo, host, port);
        }
    }
}
