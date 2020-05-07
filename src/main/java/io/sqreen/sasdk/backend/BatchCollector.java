package io.sqreen.sasdk.backend;

import com.google.common.collect.Lists;
import io.sqreen.sasdk.signals_dto.Signal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A class that accepts signals and traces for subsequent batch submission.
 * Submission happens a certain number of objects have been queued or if
 * no submission has happened for a certain amount of time, whichever happens
 * first.
 */
public class BatchCollector implements Closeable {
    private final int maxConcurrentRequests;
    private final long maxDelayInMs;
    private final int triggerSize;
    private final IngestionHttpClient.WithAuthentication client;
    private final int maxQueueSize;
    private final AtomicInteger activeBatches = new AtomicInteger(0);
    private final ScheduledExecutorService pool;
    private volatile boolean closed;

    private static final Logger LOGGER = LoggerFactory.getLogger(BatchCollector.class);
    private static final AtomicInteger THREAD_SERIAL = new AtomicInteger();

    private final ScheduledReportRunnable reportRunnable = new ScheduledReportRunnable();
    private final BlockingDeque<Signal> queue = new LinkedBlockingDeque<Signal>();

    // guarded by this; never null
    // rescheduled: 1) on construction 2) when there's a submission 3) when the timer expires
    private ScheduledFuture<?> nextTimedSubmission;

    private BatchCollector(BatchCollectorBuilder builder) {
        this.triggerSize = builder.triggerSize;
        this.maxQueueSize = builder.maxQueueSize;
        this.maxConcurrentRequests = builder.maxConcurrentRequests;
        this.maxDelayInMs = builder.maxDelayInMs;
        this.client = builder.client;
        this.pool = builder.service;
        this.nextTimedSubmission = scheduleReport();
    }

    /**
     * Instantiates a builder object for parameterizing and constructing
     * a {@link BatchCollector}.
     * @param client the authenticated http client created with {@link IngestionHttpClientBuilder}
     * @return the builder object
     */
    public static BatchCollectorBuilder builder(IngestionHttpClient.WithAuthentication client) {
        return new BatchCollectorBuilder(client);
    }

    /**
     * Adds a signal or trace for batching. Because serialization will happen on
     * a separate thread, the object should not be changed further.
     * The serialized form of the object should follow the schema for signals
     * or traces.
     * @param signalOrTrace the signal or trace
     * @return whether the object was accepted
     * @throws IllegalStateException if {@link #close()} has been called.
     */
    public boolean add(Signal signalOrTrace) {
        if (this.closed) {
            throw new IllegalStateException("close() has already been called");
        }

        int size = this.queue.size();
        if (size >= this.maxQueueSize) {
            if (this.activeBatches.get() < this.maxConcurrentRequests) {
                LOGGER.debug("Submitting batch to try and clear the queue");
                submitBatch();
            }
            size = this.queue.size();
            if (size >= this.maxQueueSize) {
                LOGGER.debug(
                        "Dropping object {} because max queue size has been reached",
                        signalOrTrace);
                return false;
            }
        }

        this.queue.add(signalOrTrace);
        if (size + 1 >= this.triggerSize) {
            // at this point the queue may have been drained
            // since we checked, but this is not a problematic race
            submitBatch();
        }
        return true;
    }

    /**
     * Forces a report call on the current thread.
     * @throws IOException if the request to the ingestion backend fails
     */
    public void forceReport() throws IOException {
        rescheduleNextTimedSubmission();
        List<Signal> signals = Lists.newArrayList();
        int num = this.queue.drainTo(signals);
        if (num == 0) {
            return;
        }
        this.client.reportBatch(signals);
    }

    /**
     * @return if the batch was submitted
     */
    private boolean submitBatch() {
        int curActive = this.activeBatches.get();
        if (curActive >= this.maxConcurrentRequests) {
            LOGGER.debug("Not submitting batch. " +
                    "maxConcurrentRequests reached (active: {})", curActive);
            return false;
        }

        List<Signal> signals = Lists.newArrayList();
        int num = this.queue.drainTo(signals);
        if (num == 0) {
            LOGGER.debug("Queue drained between before call");
            return false;
        }

        BatchRunnable batchRunnable = new BatchRunnable(signals);
        try {
            newActiveBatch();
            this.pool.submit(batchRunnable);
        } catch (RejectedExecutionException exc) {
            finishActiveBatch();
            LOGGER.info("Submission rejected. Likely pool was already closed. " +
                    "{} signals were lost", signals.size());
            return false;
        }

        rescheduleNextTimedSubmission();
        return true;
    }

    private void newActiveBatch() {
        int i = this.activeBatches.incrementAndGet();
        LOGGER.debug("Active batches: {}", i);
    }

    private void finishActiveBatch() {
        int i = this.activeBatches.decrementAndGet();
        LOGGER.debug("Active batches: {}", i);
    }

    private void rescheduleNextTimedSubmission() {
        synchronized (this) {
            if (this.nextTimedSubmission != null) {
                boolean cancel = this.nextTimedSubmission.cancel(false);
                LOGGER.debug("Cancel next submission. Result: {}", cancel);
            }
            this.nextTimedSubmission = scheduleReport();
        }
    }

    private ScheduledFuture<?> scheduleReport() {
        ScheduledFuture<?> schedule = this.pool.schedule(
                this.reportRunnable, this.maxDelayInMs, TimeUnit.MILLISECONDS);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Scheduled next automatic batch flush in {} ms",
                    schedule.getDelay(TimeUnit.MILLISECONDS));
        }
        return schedule;
    }

    // for testing
    long getDelayTillNextFlush() {
        ScheduledFuture<?> nextTimedSubmission;
        synchronized (this) {
            nextTimedSubmission = this.nextTimedSubmission;
        }
        return nextTimedSubmission.getDelay(TimeUnit.MILLISECONDS);
    }

    /**
     * Discards the objects queued for the next batch submission.
     */
    public void discard() {
        this.queue.clear();
    }

    /**
     * Shuts down the associated thread pool where http requests are run.
     * Further batch submissions will become impossible afterwards.
     * In order not to avoid losing unsent objects, {@link #forceReport()}
     * can be called before.
     */
    @Override
    public void close() {
        synchronized (this) {
            if (this.nextTimedSubmission != null) {
                this.nextTimedSubmission.cancel(false);
                this.nextTimedSubmission = null;
            }
        }
        this.pool.shutdown();
        this.closed = true;
    }

    /**
     * Reports failures to close the <code>BatchCollector</code>.
     * @throws Throwable the {@code Exception} raised by this method
     */
    @Override
    protected void finalize() throws Throwable {
        try {
            if (!this.closed) {
                LOGGER.warn("Close not called on BatchCollector");
            }
        } finally {
            super.finalize();
        }
    }

    /**
     * Awaits until all batch submissions are finished.
     * @param timeInMs the maximum time to wait
     * @return true iif all the batch submissions terminated in the interim
     * @throws InterruptedException if interrupted
     */
    public boolean awaitTermination(long timeInMs) throws InterruptedException {
        return this.pool.awaitTermination(timeInMs, TimeUnit.MILLISECONDS);
    }

    private class ScheduledReportRunnable implements Runnable {
        @Override
        public void run() {
            // we're running, there's nothing to cancel;
            // cancellations after this task starts but before
            // this block is reached have no effect
            synchronized (BatchCollector.this) {
                nextTimedSubmission = null;
            }

            LOGGER.debug("Periodic batch flush running");

            if (queue.isEmpty() || !submitBatch()) {
                LOGGER.debug("No submission happened; rescheduling");
                // if submitBatch succeeds, the rescheduling will have been done
                synchronized (BatchCollector.this) {
                    nextTimedSubmission = scheduleReport();
                }
            }
        }
    }

    private class BatchRunnable implements Runnable {
        private final List<Signal> batch;

        private BatchRunnable(List<Signal> batch) {
            this.batch = batch;
        }

        @Override
        public void run() {
            try {
                client.reportBatch(batch);
            } catch (Throwable e) {
                LOGGER.warn("Batch report failed: " + e.getMessage(), e);
            } finally {
                finishActiveBatch();
            }
        }
    }

    private static class BatchThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r,
                    "sqreen-batch-collector-" + THREAD_SERIAL.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }

    /**
     * A builder class for creating and parameterizing a {@link BatchCollector}.
     */
    public static class BatchCollectorBuilder {
        private static final int DEFAULT_TRIGGER_SIZE = 30;
        private static final int DEFAULT_MAX_CONCURRENT_REQUESTS = 3;
        private static final int DEFAULT_MAX_INTERVAL_IN_MS = 60000;

        final IngestionHttpClient.WithAuthentication client;
        private int triggerSize = DEFAULT_TRIGGER_SIZE;
        private long maxDelayInMs = DEFAULT_MAX_INTERVAL_IN_MS;
        private int maxQueueSize;
        private int maxConcurrentRequests = DEFAULT_MAX_CONCURRENT_REQUESTS;
        private ScheduledExecutorService service;

        private BatchCollectorBuilder(IngestionHttpClient.WithAuthentication client) {
            this.client = client;
        }

        /**
         * Defines the number of accumulated objects that will trigger a batch
         * to be sent.
         *
         * The default value is 30.
         *
         * @param triggerSize the number of items that will trigger a batch
         * @return <code>this</code>, for chaining
         */
        public BatchCollectorBuilder withTriggerSize(int triggerSize) {
            if (triggerSize <= 0) {
                throw new IllegalArgumentException("triggerSize must positive");
            }
            this.triggerSize = triggerSize;
            return this;
        }

        /**
         * Defines the maximum number of neither sent nor in the process of being
         * sent that will be accepted. Additional objects will be rejected.
         *
         * This limit can be hit if objects are not sent fast enough due
         * to saturation of http connections. Having this limit prevent runaway
         * memory usage in those circumstances.
         *
         * The default value is 10 times the trigger size.
         *
         * @param maxQueueSize the max number of objects that will held in the queue
         * @return <code>this</code>, for chaining
         */
        public BatchCollectorBuilder withMaxQueueSize(int maxQueueSize) {
            if (maxQueueSize <= 0) {
                throw new IllegalArgumentException("maxQueueSize must be positive");
            }
            this.maxQueueSize = maxQueueSize;
            return this;
        }

        /**
         * Defines the maximum amount of time, in milliseconds, between two
         * submissions. This maximum delay is respected unless when the delay
         * passes the maximum number of concurrent connections has been
         * reached.
         *
         * @param maxDelayInMs the maximum time between two submissions
         * @return <code>this</code>, for chaining
         */
        public BatchCollectorBuilder withMaxDelayInMs(long maxDelayInMs) {
            if (maxDelayInMs <= 0) {
                throw new IllegalArgumentException("maxDelayInMs must be positive");
            }
            this.maxDelayInMs = maxDelayInMs;
            return this;
        }

        /**
         * Defines the maximum number of concurrent http connections.
         * The default value is 3.
         *
         * @param maxConcurrentRequests the maximum number of concurrent submissions
         * @return <code>this</code>, for chaining.
         */
        public BatchCollectorBuilder withMaxConcurrentRequests(int maxConcurrentRequests) {
            if (maxConcurrentRequests <= 0) {
                throw new IllegalArgumentException("maxConcurrentRequests must be positive");
            }
            this.maxConcurrentRequests = maxConcurrentRequests;
            return this;
        }

        /**
         * Builds the configured <code>BatchCollector</code>.
         * @return the new <code>BatchCollector</code>
         */
        public BatchCollector build() {
            if (this.maxQueueSize <= 0) {
                this.maxQueueSize = this.triggerSize > Integer.MAX_VALUE / 10 ?
                        Integer.MAX_VALUE : this.triggerSize * 10;
            }

            this.service = Executors.newScheduledThreadPool(1, new BatchThreadFactory());

            return new BatchCollector(this);
        }
    }
}
