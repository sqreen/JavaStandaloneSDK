package io.sqreen.sasdk.signals_dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.google.common.base.MoreObjects;

import java.util.Date;
import java.util.Map;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

/**
 *
 */
@JsonInclude(NON_NULL)
public abstract class Signal {

    /**
     * The type of the signal. Required.
     * @return the type of the signal.
     */
    @JsonSerialize(using = ToStringSerializer.class)
    abstract SignalType getType(); // required

    /**
     * The name of the signal. Required.
     *
     * The name is not completely arbitrary. The format depends on the type of
     * the signal and is documented elsewhere.
     */
    @JsonProperty("signal_name")
    public String name;

    /**
     * A string in the form name/time (e.g.
     * <code>attack/2020-01-01T00:00:00.000Z</code>), identifying the format
     * of {@link #payload}.
     */
    public String payloadSchema;

    /**
     * The payload of the signal. Required.
     *
     * Its format is constrained by {@link #payloadSchema}.
     */
    public Map<String, Object> payload; // required

    /**
     * A map describing the user that generated this signal, if any.
     * Typically includes usernames, ip addresses, user agents.
     * The exact schema is documented elsewhere.
     */
    public Map<String, Object> actor;

    /**
     * A string in the form name/time (e.g.
     * <code>http/2020-01-01T00:00:00.000Z</code>), identifying the format
     * of {@link #context}.
     */
    public String contextSchema;

    /**
     * Additional context for the situation where the signal was generated.
     * As of the time of this writing, it can only be a set of details about the
     * http request.
     */
    public Map<String, Object> context;

    /**
     * The source of the signal, typically identifying a rule or an agent.
     */
    public String source;

    /**
     * Currently unused.
     */
    public Map<String, Object> trigger;

    /**
     * Identifies the location within the code where the signal was generated.
     * This will typically be a stacktrace.
     */
    public Map<String, Object> location;

    /**
     * A map supplying information about the runtime.
     */
    public Map<String, Object> locationInfra;

    private Date time;

    /**
     * The time of the signal, or the current time if not set.
     * @return the time of the signal
     */
    public Date getTime() {
        if (this.time == null) {
            return new Date();
        }
        return this.time;
    }

    /**
     * Sets the time associated with the request
     * @param time the instant this event occurred
     */
    public void setTime(Date time) {
        this.time = time;
    }

    enum SignalType {
        METRIC("metric"),
        POINT("point");

        private final String textValue;

        SignalType(String textValue) {
            this.textValue = textValue;
        }

        @Override
        public String toString() {
            return textValue;
        }
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .omitNullValues()
                .add("name", name)
                .add("payload", payload)
                .add("payloadSchema", payloadSchema)
                .add("actor", actor)
                .add("context", context)
                .add("source", source)
                .add("trigger", trigger)
                .add("location", location)
                .add("locationInfra", locationInfra)
                .add("time", time)
                .toString();
    }
}
