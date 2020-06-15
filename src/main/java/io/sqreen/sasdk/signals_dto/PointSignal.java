package io.sqreen.sasdk.signals_dto;

/**
 * A point signal is a signal with <code>type: point</code>.
 *
 * Points are, as of this point, used to report:
 * <ul>
 *     <li>attacks (schema <code>attack/2020-01-01T00:00:00.000Z</code>),</li>
 *     <li>exceptions (schema <code>sqreen_exception/2020-01-01T00:00:00.000Z</code>),</li>
 *     <li><code>track</code> calls (events) (schema <code>track_event/2020-01-01T00:00:00.000Z</code>),</li>
 *     <li>and generic error messages sent to the backend
 *         (schema <code>agent_message/2020-01-01T00:00:00.000Z</code>).</li>
 * </ul>
 *
 * See the definition of <code>Signal</code> in the
 * <a href="https://ingestion.sqreen.com/openapi.yaml">schema</a>.
 */
public class PointSignal extends Signal {

    /**
     * The type of the Signal. Always <code>"point"</code> for points.
     * @return always <code>"point"</code>
     */
    @Override
    SignalType getType() {
        return SignalType.POINT;
    }
}
