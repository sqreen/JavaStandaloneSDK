package io.sqreen.sasdk.signals_dto

import org.exparity.hamcrest.date.DateMatchers
import org.junit.Test

import java.time.temporal.ChronoUnit

import static org.hamcrest.MatcherAssert.assertThat

class SignalTests {

    @Test
    void 'signal time is not set and return current time'() {
        def signal = new PointSignal()
        assertThat signal.getTime(), DateMatchers.within(50, ChronoUnit.MILLIS, new Date());
    }

    @Test
    void 'return signal time when time is set'() {
        def time = new Date()
        def signal = new PointSignal(time: time)
        assertThat signal.getTime(), DateMatchers.within(0, ChronoUnit.MILLIS, time);
    }
}
