package io.sqreen.sasdk.backend;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface IngestionErrorListener {

    void onError(String path, Exception e);

    final class NoActionIngestionErrorListener implements IngestionErrorListener {
        public static final IngestionErrorListener INSTANCE = new NoActionIngestionErrorListener();

        private NoActionIngestionErrorListener() {}

        @Override
        public void onError(String path, Exception e) {
            // purposefully left empty
        }
    }

    final class LoggingIngestionErrorListener implements IngestionErrorListener {
        public static final IngestionErrorListener INSTANCE = new LoggingIngestionErrorListener();

        private final Logger logger = LoggerFactory.getLogger(getClass());

        private LoggingIngestionErrorListener() {}

        @Override
        public void onError(String path, Exception e) {
            this.logger.warn("Error on {}", path, e);
        }
    }
}
