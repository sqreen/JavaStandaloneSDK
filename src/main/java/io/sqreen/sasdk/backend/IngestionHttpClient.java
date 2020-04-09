package io.sqreen.sasdk.backend;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.base.MoreObjects;
import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import io.sqreen.sasdk.backend.exception.AuthenticationException;
import io.sqreen.sasdk.backend.exception.BadHttpStatusException;
import io.sqreen.sasdk.backend.exception.InvalidPayloadException;
import io.sqreen.sasdk.signals_dto.MetricSignal;
import io.sqreen.sasdk.signals_dto.PointSignal;
import io.sqreen.sasdk.signals_dto.Trace;

import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.Collection;

import static com.google.common.base.Preconditions.checkNotNull;

public final class IngestionHttpClient {

    /**
     * An HTTP client interface that doesn't add authentication headers to the
     * request.
     *
     * The requests run synchronously.
     */
    public interface WithoutAuthentication extends Closeable {
        /**
         * Submits a set of signal and trace objects. There need not be
         * any relationship between the submitted objects (except for the fact
         * that they pertain to the same application, as identified by the
         * passed authentication headers).
         *
         * @param signalsAndTraces a collection of objects that are serialized
         *                         by the configured Jackson {@link ObjectWriter}
         *                         to representations compatible with the
         *                         <code>Trace</code> or the <code>Signal</code>
         *                         types, as described in <a
         *                         href="https://ingestion.sqreen.com/openapi.yaml">
         *                         the schema</a>.
         * @param headers the headers to send. At least an authentication header is
         *                required. <code>Content-type</code> is automatically added.
         * @throws IOException if any error occurs, including
         * {@link AuthenticationException}, {@link BadHttpStatusException} and
         * {@link InvalidPayloadException}.
         */
        void reportBatch(Collection<?> signalsAndTraces, Multimap<String, String> headers) throws IOException;

        /**
         * Submits a single signal object. A signal object represents an
         * arbitrary event. Types of events are currently points and metrics,
         * and point can represent, attacks, exceptions or agent messages.
         *
         * @param signal an object is transformed by the configured Jackson
         *               {@link ObjectWriter} to a compatible representation,
         *               such as {@link PointSignal} or {@link MetricSignal}
         *               when used with the default Jackson configuration.
         *                See <a href="https://ingestion.sqreen.com/openapi.yaml">
         *                the schema</a>.
         * @param headers the headers to send. At least an authentication header is
         *                required. <code>Content-type</code> is automatically added.
         * @throws IOException if any error occurs, including
         * {@link AuthenticationException}, {@link BadHttpStatusException} and
         * {@link InvalidPayloadException}.
         */
        void reportSignal(Object signal, Multimap<String, String> headers) throws IOException;

        /**
         * Submits a single trace object. A trace object is a set of signals
         * originating from a common context, typically an HTTP request.
         *
         * @param trace an object that is transformed by the configured Jackson
         *              {@link ObjectWriter} to a compatible representation, such
         *              as {@link Trace} when used with the default Jackson
         *              configuration.
         *              See <a href="https://ingestion.sqreen.com/openapi.yaml">
         *              the schema</a>.
         * @param headers the headers to send. At least an authentication header is
         *                required. <code>Content-type</code> is automatically added.
         * @throws IOException if any error occurs, including
         * {@link AuthenticationException}, {@link BadHttpStatusException} and
         * {@link InvalidPayloadException}.
         */
        void reportTrace(Object trace, Multimap<String, String> headers) throws IOException;
    }

    /**
     * An HTTP client interface that authomatically adds the authentication
     * headers to the request.
     */
    public interface WithAuthentication extends Closeable {
        /**
         * Submits a set of signal and trace objects. There need not be
         * any relationship between the submitted objects (except for the fact
         * that they pertain to the same application).
         *
         * @param signalsAndTraces a collection of objects that are serialized
         *                         by the configured Jackson {@link ObjectWriter}
         *                         to representations compatible with the
         *                         <code>Trace</code> or the <code>Signal</code>
         *                         types, as described in <a
         *                         href="https://ingestion.sqreen.com/openapi.yaml">
         *                         the schema</a>.
         * @throws IOException if any error occurs, including
         * {@link AuthenticationException}, {@link BadHttpStatusException} and
         * {@link InvalidPayloadException}.
         */
        void reportBatch(Collection<?> signalsAndTraces) throws IOException;

        /**
         * Submits a single signal object. A signal object represents an
         * arbitrary event. Types of events are currently points and metrics,
         * and point can represent, attacks, exceptions or agent messages.
         *
         * @param signal an object is transformed by the configured Jackson
         *               {@link ObjectWriter} to a compatible representation,
         *               such as {@link PointSignal} or {@link MetricSignal}
         *               when used with the default Jackson configuration.
         *                See <a href="https://ingestion.sqreen.com/openapi.yaml">
         *                the schema</a>.
         * @throws IOException if any error occurs, including
         * {@link AuthenticationException}, {@link BadHttpStatusException} and
         * {@link InvalidPayloadException}.
         */
        void reportSignal(Object signal) throws IOException;

        /**
         * Submits a single trace object. A trace object is a set of signals
         * originating from a common context, typically an HTTP request.
         *
         * @param trace an object that is transformed by the configured Jackson
         *              {@link ObjectWriter} to a compatible representation, such
         *              as {@link Trace} when used with the default Jackson
         *              configuration.
         *              See <a href="https://ingestion.sqreen.com/openapi.yaml">
         *              the schema</a>.
         * @throws IOException if any error occurs, including
         * {@link AuthenticationException}, {@link BadHttpStatusException} and
         * {@link InvalidPayloadException}.
         */
        void reportTrace(Object trace) throws IOException;
    }


    static class IngestionHttpAuthClientImpl implements WithAuthentication {

        protected final AuthenticationConfig config;
        private final Multimap<String, String> headers = ArrayListMultimap.create();
        private final WithoutAuthentication client;

        public IngestionHttpAuthClientImpl(String host,
                                           BackendHttpImpl backendHttp,
                                           AuthenticationConfig config) {
            client = new IngestionHttpClientImpl(host, backendHttp);
            this.config = config != null ? config : AuthenticationConfig.EmptyConfig.INSTANCE;

            Optional<String> sessionKey = this.config.getSessionKey();
            if (sessionKey.isPresent()) {
                this.headers.put("X-Session-Key", sessionKey.get());
            } else {
                Optional<String> apiKey = this.config.getAPIKey();
                if (apiKey.isPresent()) {
                    this.headers.put("X-API-Key", apiKey.get());
                    Optional<String> appName = this.config.getAppName();
                    if (appName.isPresent()) {
                        this.headers.put("X-App-Name", appName.get());
                    }
                }
            }
        }

        @Override
        public void reportBatch(Collection<?> signalsAndTraces) throws IOException {
            client.reportBatch(signalsAndTraces, headers);
        }

        @Override
        public void reportSignal(Object signal) throws IOException {
            client.reportSignal(signal, headers);
        }

        @Override
        public void reportTrace(Object trace) throws IOException {
            client.reportTrace(trace, headers);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    //.add("host", host)
                    //.add("backendHttp", backendHttp)
                    .add("config", config)
                    .toString();
        }

        @Override
        public void close() throws IOException {
            client.close();
        }
    }


    static class IngestionHttpClientImpl implements WithoutAuthentication {

        protected final String host;
        protected final BackendHttpImpl backendHttp;

        public IngestionHttpClientImpl(String host,
                                       BackendHttpImpl backendHttp) {
            checkNotNull(host);
            checkNotNull(backendHttp);
            this.host = host;
            this.backendHttp = backendHttp;
        }

        @Override
        public void reportBatch(Collection<?> signalsAndTraces, Multimap<String, String> headers) throws IOException {
            doRequest("batches", signalsAndTraces, headers);
        }

        @Override
        public void reportSignal(Object signal, Multimap<String, String> headers) throws IOException {
            doRequest("signals", signal, headers);
        }

        @Override
        public void reportTrace(Object trace, Multimap<String, String> headers) throws IOException {
            doRequest("traces", trace, headers);
        }

        private void doRequest(String path, Object payload,
                               Multimap<String, String> headers) throws IOException {
            BackendHttpImpl.RequestBuilder reqBuilder = this.backendHttp.newRequest(
                    BackendHttpImpl.HttpMethod.POST, this.host, path);

            BackendResponse<BackendHttpImpl.IgnoredResponse> result = reqBuilder
                    .header("Content-Type", "application/json")
                    .compression(false)
                    .headers(headers)
                    .payload(payload)
                    .execute(BackendHttpImpl.IgnoredResponse.class);

            handleErrors(result);
        }

        private void handleErrors(BackendResponse<BackendHttpImpl.IgnoredResponse> result) throws IOException {
            if (result.isError()) {
                Exception exception = result.fetchError();
                Throwables.throwIfInstanceOf(exception, IOException.class);
                Throwables.throwIfUnchecked(exception);
                throw new UndeclaredThrowableException(exception);
            }
        }

        @Override
        public void close() throws IOException {
            this.backendHttp.close();
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("host", host)
                    .add("backendHttp", backendHttp)
                    .toString();
        }
    }
}
