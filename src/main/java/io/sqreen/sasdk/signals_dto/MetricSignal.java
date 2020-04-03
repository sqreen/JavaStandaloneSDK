package io.sqreen.sasdk.signals_dto;

/**
 * A metric signal is a signal with <code>type: metric</code>.
 *
 * As of this point, two schemas are supported for metrics:
 * <ul>
 *     <li>metrics reporting arbitrary key/value pairs
 *         (schema <code>metric/2020-01-01T00:00:00.000Z</code>),</li>
 *     <li>and binned performance metrics (schema <code>metric_binning/2020-01-01T00:00:00.000Z</code>).</li>
 * </ul>
 *
 * See the definition of <code>Signal</code> in the
 * <a href="https://ingestion.sqreen.com/openapi.yaml">OpenAPI schema</a>.
 *
 */
public class MetricSignal extends Signal {

    /**
     * The type of the Signal. Always <code>"metric"</code> for metrics.
     * @return always <code>"metric"</code>
     */
    @Override
    SignalType getType() {
        return SignalType.METRIC;
    }
}
