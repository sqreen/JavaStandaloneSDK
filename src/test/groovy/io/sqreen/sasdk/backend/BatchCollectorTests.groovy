package io.sqreen.sasdk.backend

import io.sqreen.sasdk.signals_dto.PointSignal
import io.sqreen.sasdk.signals_dto.Signal
import org.gmock.WithGMock
import org.junit.After
import org.junit.Before
import org.junit.Test

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

import static org.hamcrest.MatcherAssert.assertThat
import static org.hamcrest.Matchers.*

@WithGMock
class BatchCollectorTests {

    int triggerSize = 3
    int maxConcurrentRequests = 2
    int maxQueueSize = 10
    long maxDelayInMs = 500

    @Lazy
    BatchCollector testee = BatchCollector.builder(client)
            .withTriggerSize(triggerSize)
            // allow us to set 0
            .with { maxConcurrentRequests = owner.maxConcurrentRequests; it }
            .withMaxQueueSize(maxQueueSize)
            .withMaxDelayInMs(maxDelayInMs)
            .build()

    def requestsExpected = 1

    @Lazy
    CountDownLatch latch = new CountDownLatch(requestsExpected)

    Throwable reportedException

    @Lazy
    IngestionHttpClient.WithAuthentication mockClient =
            mock(IngestionHttpClient.WithAuthentication)

    IngestionHttpClient.WithAuthentication client = [
            reportBatch: { Collection<Signal> signalsAndTraces ->
                try {
                    mockClient.reportBatch(signalsAndTraces)
                } catch (Throwable e) {
                    reportedException = e
                } finally {
                    latch.countDown()
                }
            }
    ] as IngestionHttpClient.WithAuthentication

    private boolean doNotClose

    @Before
    void before() {
        mockClient // for side effects
    }

    @After
    void after() {
        if (doNotClose) {
            return
        }
        testee.discard()
        testee.close()
        boolean res = testee.awaitTermination(500)
    }

    private void await(long ms = 5000, CountDownLatch l = latch) {
        if (!l.await(ms, TimeUnit.MILLISECONDS)) {
            throw new RuntimeException('timeout lapsed')
        }
        if (reportedException) {
            throw reportedException
        }
    }

    @Test
    void 'submits the batch when trigger size is reached'() {
            mockClient.reportBatch(hasSize(3))

        play {
            4.times {
                testee.add new PointSignal()
            }
            await()
        }
    }

    @Test
    void 'force report forces a report and reschedules next flush'() {
        maxDelayInMs = 500

        mockClient.reportBatch(hasSize(1))

        testee // for side effect
        sleep 200
        play {
            testee.add new PointSignal()
            testee.forceReport()
        }

        assertThat testee.delayTillNextFlush, is(greaterThan(320L))
    }

    @Test
    void 'submits the batch when the delay has run'() {
        maxDelayInMs = 100

        mockClient.reportBatch(hasSize(2))

        play {
            2.times { testee.add new PointSignal() }
            await 150 // 100 ms plus some margin
        }
    }

    @Test
    void 'triggering batch resets the delay'() {
        maxDelayInMs = 500
        testee // for side effect (create the object and start timer)

        mockClient.reportBatch(hasSize(2))

        play {
            sleep 100
            2.times { testee.add new PointSignal() }
            await()
        }

        assertThat testee.delayTillNextFlush, is(greaterThan(400L))
    }

    @Test
    void 'delay is reset even if there is no submission because of no entries'() {
        maxDelayInMs = 100

        testee // for side effects
        sleep 120

        assertThat testee.delayTillNextFlush, is(greaterThan(60L))
    }

    @Test
    void 'delay is reset even if there is no submission because of max pending tasks'() {
        maxDelayInMs = 100
        maxConcurrentRequests = 0

        play {
            testee.add new PointSignal()
            sleep 120
        }

        assertThat testee.delayTillNextFlush, is(greaterThan(60L))
    }

    @Test
    void 'maxQueueSize and maxConcurrentRequests is honored'() {
        def waitBarrier = new CountDownLatch(1)
        def submissionDoneLatch = new CountDownLatch(2)
        client = [
                reportBatch: { Collection<Signal> signalsAndTraces ->
                    waitBarrier.await(5, TimeUnit.SECONDS)
                    try {
                        mockClient.reportBatch(signalsAndTraces)
                    } catch (Throwable e) {
                        reportedException = e
                    } finally {
                        submissionDoneLatch.countDown()
                    }
                }
        ] as IngestionHttpClient.WithAuthentication
        maxQueueSize = 4
        maxDelayInMs = 300000

        ordered {
            mockClient.reportBatch(hasSize(3)).times(2)
            mockClient.reportBatch(hasSize(4))
        }

        play {
            10.times { // 3 + 3 + 4
                assertThat testee.add(new PointSignal()), is(true)
            }
            assertThat testee.add(new PointSignal()), is(false)
            waitBarrier.countDown()
            await(500, submissionDoneLatch) // wait for the two calls

            // still 4 objects there. Reset latch for 1 request
            submissionDoneLatch = new CountDownLatch(1)

            int i = 0
            for (; i < 5; i++) {
                if (testee.add(new PointSignal())) {
                    break
                }
                sleep 25
            }
            if (i == 5) {
                throw new AssertionError('Could not submit object 12')
            }
            await(500, submissionDoneLatch) // wait for the call with 4 objects
        }
    }

    @Test(expected = IllegalStateException)
    void 'throws if already closed'() {
        testee.close()
        try {
            testee.add(new PointSignal())
        } finally {
            doNotClose = true
        }
    }

}
