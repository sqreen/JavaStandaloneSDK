package io.sqreen.sasdk.signals_dto

import org.junit.Test

import static org.hamcrest.Matchers.*;
import static org.hamcrest.MatcherAssert.assertThat

class TraceTests {

    @Test
    void 'add signal to trace'() {
        def signal = new PointSignal(name: 'test')
        def trace = new Trace()
        trace.addSignal(signal)
        assertThat trace.signals, hasItem(signal)
    }

    @Test
    void 'get signals from trace'() {
        def point1 = new PointSignal(name: 'point 1')
        def point2 = new PointSignal(name: 'point 2')
        def metric = new MetricSignal(name: 'metric')

        def trace = new Trace(
                nestedSignals: [point1, point2, metric]
        )
        assertThat trace.getSignals(), contains(point1, point2, metric)
    }

    @Test
    void 'get empty trace if no signals added'() {
        def trace = new Trace()
        assertThat trace.getSignals(), empty()
    }
}
