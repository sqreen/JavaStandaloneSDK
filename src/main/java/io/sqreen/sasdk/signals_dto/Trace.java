package io.sqreen.sasdk.signals_dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import io.sqreen.sasdk.backend.IngestionHttpClient;
import io.sqreen.sasdk.backend.IngestionHttpClientBuilder;

import java.util.Collection;

/**
 * A <code>Trace</code> is a collection of signals. In addition, its schema
 * includes all the same fields that <code>Signal</code> does. Besides
 * <code>payload</code> <code>payload_schema</code> and name, a value in the
 * other common fields indicates that the same value should be inferred for all
 * the contained signals, unless the same field is specified in a contained
 * signal, in which case, the value specified in the signal overrides the one
 * specified in the trace.
 *
 * A trace represents a set of signals sharing some communality; batch
 * submission of signals can be done without traces (see
 * {@link IngestionHttpClient.WithAuthentication#reportBatch(Collection)}).
 *
 * The only supported schemas as of this writing is
 * <code>http/2020-01-01T00:00:00.000Z</code> (the trace represents a set of
 * signals collected during an http request).
 *
 */
public class Trace extends Signal {
    @Override
    SignalType getType() {
        return SignalType.TRACE;
    }

    private Collection<Signal> nestedSignals;

    /**
     * Adds a signal to this trace's collection.
     *
     * This method accepts an arbitrary object because
     * {@link IngestionHttpClientBuilder.WithConfiguredHttpClient#withCustomObjectWriter(ObjectWriter)}
     * accepts an arbitrary Jackson serializer that can configure any object to
     * be serialized into a JSON representation acceptable by the API.
     *
     * See {@link MetricSignal} and {@link PointSignal} for types acceptable by
     * the default serializer.
     *
     * @param signal the signal to add
     */
    public synchronized void addSignal(Signal signal) {
        if (this.nestedSignals == null) {
            this.nestedSignals = Lists.newArrayList();
        }

        this.nestedSignals.add(signal);
    }

    /**
     * Returns the signals added to this trace with {@link #addSignal(Signal)}.
     *
     * Required with at least size 1.
     *
     * @return a non-null collection.
     */
    @JsonProperty("data")
    public synchronized Collection<Signal> getSignals() {
        return this.nestedSignals == null ?
                ImmutableList.<Signal>of() : this.nestedSignals;
    }
}
