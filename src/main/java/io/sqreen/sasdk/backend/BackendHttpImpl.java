package io.sqreen.sasdk.backend;

import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.cfg.ContextAttributes;
import com.google.common.base.Charsets;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.io.ByteSource;
import io.sqreen.sasdk.backend.exception.AuthenticationException;
import io.sqreen.sasdk.backend.exception.BadHttpStatusException;
import io.sqreen.sasdk.backend.exception.InvalidPayloadException;
import org.apache.http.client.methods.*;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.io.Closeables.closeQuietly;

class BackendHttpImpl implements Closeable {
    public final static String JACKSON_ATTRIBUTE = "backend http service";

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final ObjectReader objectReader;
    private final ObjectWriter objectWriter;

    private final CloseableHttpClient httpClient;
    private final Class<?> errorResponseClass;
    private final boolean httpClientOwned;

    // not-null
    private IngestionErrorListener errorListener;

    // supported http methods
    public enum HttpMethod {
        GET, POST
    }

    private BackendHttpImpl(Builder b) {
        this.httpClient = b.httpClient;
        this.httpClientOwned = b.httpClientOwned;
        if (b.errorResponseClass != IgnoredResponse.class) {
            this.objectReader = b.objectReader.with(
                    ContextAttributes.getEmpty().withSharedAttribute(JACKSON_ATTRIBUTE, this));
        } else {
            this.objectReader = null;
        }

        this.errorListener = b.errorListener != null ?
                b.errorListener : IngestionErrorListener.NoActionIngestionErrorListener.INSTANCE;

        this.errorResponseClass = b.errorResponseClass;

        // we can add JACKSON_ATTRIBUTE if we ever need it to report errors during serialization
        this.objectWriter = b.objectWriter;
    }

    public static Builder builder(CloseableHttpClient httpClient, boolean httpClientOwned) {
        return new Builder(httpClient, httpClientOwned);
    }

    public static class Builder {
        private final CloseableHttpClient httpClient;
        private final boolean httpClientOwned;
        private ObjectReader objectReader;
        private ObjectWriter objectWriter;
        private IngestionErrorListener errorListener;
        private Class<?> errorResponseClass;

        private Builder(CloseableHttpClient httpClient, boolean ownedClient) {
            checkArgument(httpClient != null);
            this.httpClient = httpClient;
            this.httpClientOwned = ownedClient;

            this.errorResponseClass = IgnoredResponse.class;
        }

        Builder objectWriter(ObjectWriter objectWriter) {
            checkArgument(objectWriter != null);
            this.objectWriter = objectWriter;
            return this;
        }

        Builder objectReader(ObjectReader objectReader) {
            checkArgument(objectReader != null);
            this.objectReader = objectReader;
            return this;
        }

        Builder errorResponseClass(Class<?> clazz) {
            this.errorResponseClass = clazz;
            return this;
        }

        public Builder errorListener(IngestionErrorListener errorListener) {
            this.errorListener = errorListener;
            return this;
        }

        public BackendHttpImpl build() {
            return new BackendHttpImpl(this);
        }
    }

    public void setErrorListener(IngestionErrorListener errorListener) {
        this.errorListener = errorListener;
    }

    public RequestBuilder newRequest(HttpMethod method, String host, String path) {
        return new RequestBuilder(method, host, path);
    }

    public class RequestBuilder {

        private final HttpMethod method;
        private final String path;
        private Multimap<String, String> headers;
        private Object payload;
        private boolean compression;
        private String host;

        private RequestBuilder(HttpMethod method, String host, String path) {
            this.method = method;
            this.host = host;
            this.path = path;
            this.headers = ArrayListMultimap.create();
        }

        public RequestBuilder header(String headerName, String headerValue) {
            this.headers.put(headerName, headerValue);
            return this;
        }

        public RequestBuilder headers(Multimap<String, String> headers) {
            this.headers.putAll(headers);
            return this;
        }

        public RequestBuilder headers(Map<String, String> headers) {
            this.headers.putAll(Multimaps.forMap(headers));
            return this;
        }

        public RequestBuilder payload(Object payload) {
            this.payload = payload;
            return this;
        }

        public RequestBuilder compression(boolean compression) {
            this.compression = compression;
            return this;
        }

        public <T> BackendResponse<T> execute(Class<T> returnType) {
            BackendResponse<T> response;
            try {
                response = BackendResponse.from(BackendHttpImpl.this.doRequest(this, returnType));

            } catch (IOException e) {
                logger.warn(String.format(
                        "Error in communication with ingestion backend (%s on %s)",
                        this.method, this.host), e);
                if (BackendHttpImpl.this.errorListener != null) {
                    BackendHttpImpl.this.errorListener.onError(path, e);
                }
                response = BackendResponse.from(e);
            }
            return response;
        }
    }

    private static String getBackendUrl(String backendUrl, String path) {
        return backendUrl + path;
    }

    private <T> T doRequest(RequestBuilder r, Class<T> returnType) throws IOException {
        logger.debug("Backend {} request to {} {} with payload {}",
                r.method, r.host, r.path, r.payload);

        HttpUriRequest request;
        String url = getBackendUrl(r.host, r.path);
        switch (r.method) {
            case GET:
                request = new HttpGet(url);
                break;
            case POST:
                HttpPost httpPost = new HttpPost(url);
                httpPost.setHeader("Content-Type", "application/json");
                if (r.compression) {
                    httpPost.setHeader("Content-Encoding", "gzip");
                }
                writeRequestBody(r.payload, r.compression, httpPost);
                request = httpPost;
                break;
            default:
                throw new IllegalArgumentException("unsupported HTTP method " + r.method);
        }

        for (Map.Entry<String, String> h : r.headers.entries()) {
            request.addHeader(h.getKey(), h.getValue());
        }

        CloseableHttpResponse response;
        try {
            response = httpClient.execute(request);
        } catch (IllegalStateException ise) {
            if ("Connection pool shut down".equals(ise.getMessage())) {
                throw new IOException("Request failed due to http client thread pool shutdown", ise);
            } else {
                throw ise;
            }
        }

        try {
            int status = response.getStatusLine().getStatusCode();

            logger.debug("Backend response {}", status);

            final InputStream content = response.getEntity().getContent();

            if (status != 200 && status != 202) {
                ByteSource source = new ByteSource() {
                    @Override
                    public InputStream openStream() {
                        return content;
                    }
                };
                String responseBody = source.asCharSource(Charsets.UTF_8).read();
                Object errorResponse = parseErrorResponse(responseBody);
                String message;
                if (errorResponse != null) {
                    message = "Backend sent status " + status + ": " + errorResponse;
                } else {
                    message = String.format("Unexpected response code: %d. Body %s", status, responseBody);
                }

                if (AuthenticationException.BAD_AUTH_STATUS_CODES.contains(status)) {
                    throw new AuthenticationException(status, message);
                } else if (status == InvalidPayloadException.INVALID_PAYLOAD_STATUS_CODE) {
                    throw new InvalidPayloadException(message);
                } else {
                    throw new BadHttpStatusException(status,
                            String.format("Unexpected response code: %d. Body %s", status, responseBody));
                }
            }

            if (logger.isTraceEnabled()) {
                logger.trace("Server response is: {}", content);
            }

            try {
                if (returnType == IgnoredResponse.class) {
                    return (T) IgnoredResponse.INSTANCE;
                }
                return objectReader.forType(returnType).readValue(content);
            } finally {
                closeQuietly(content);
            }

        } finally {
            response.close();
        }

    }

    private void writeRequestBody(Object payload, boolean compression, HttpEntityEnclosingRequestBase httpRequest) throws IOException {
        // write json to memory (uncompressed)
        ByteArrayOutputStream rawJson = new ByteArrayOutputStream();

        this.objectWriter.writeValue(rawJson, payload);
        byte[] binPayload = rawJson.toByteArray();

        // compression : repack payload in-memory
        if (compression) {
            ByteArrayOutputStream compressedOutput = new ByteArrayOutputStream();
            GZIPOutputStream gzipStream = new GZIPOutputStream(compressedOutput);
            ByteSource.wrap(binPayload).copyTo(gzipStream);
            gzipStream.close();
            binPayload = compressedOutput.toByteArray();
        }

        httpRequest.setEntity(new ByteArrayEntity(binPayload));
    }

    private Object parseErrorResponse(String responseBody) {
        if (this.errorResponseClass == IgnoredResponse.class) {
            return IgnoredResponse.INSTANCE;
        }

        Object errorResponse = null;
        try {
            errorResponse = objectReader.forType(this.errorResponseClass).readValue(responseBody);
        } catch (IOException e) {
            logger.debug("Error reading error response: {}", e.getMessage());
            // silently ignored
        }
        return errorResponse;
    }

    @Override
    public void close() throws IOException {
        if (this.httpClientOwned) {
            this.httpClient.close();
        }
    }

    public enum IgnoredResponse {
        INSTANCE;

        public String toString() {
            return "(ignored response)";
        }
    }
}
