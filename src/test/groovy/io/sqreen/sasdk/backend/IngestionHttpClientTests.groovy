package io.sqreen.sasdk.backend

import io.sqreen.agent.test.WireMockTrait
import io.sqreen.sasdk.backend.exception.AuthenticationException
import io.sqreen.sasdk.backend.exception.BadHttpStatusException
import io.sqreen.sasdk.backend.exception.InvalidPayloadException
import io.sqreen.sasdk.signals_dto.MetricSignal
import io.sqreen.sasdk.signals_dto.PointSignal
import io.sqreen.sasdk.signals_dto.Trace
import org.junit.Test

import java.text.SimpleDateFormat

import static com.github.tomakehurst.wiremock.client.WireMock.*
import static groovy.test.GroovyAssert.shouldFail

class IngestionHttpClientTests implements WireMockTrait {

    String apiKey = 'api key'

    def sdf = new SimpleDateFormat('yyyy-MM-dd HH:mm:ss').with {
        timeZone = TimeZone.getTimeZone('UTC')
        it
    }

    @Lazy
    IngestionHttpClient.WithAuthentication service = new IngestionHttpClientBuilder()
            .withAlternativeIngestionURL(String.format("http://localhost:%d/", wireMockRule.port()))
            .buildingHttpClient()
            .buildHttpClient()
            .createWithAuthentication(
                    IngestionHttpClientBuilder.authConfigWithAPIKey(apiKey))

    @Test
    void 'point signal'() {
        def jsonExpected = '''
            {
               "type" : "point",
               "signal_name" : "signalName",
               "source" : "srcName",
               "payload_schema" : "mySchema",
               "payload" : {
                  "g" : "h"
               },
               "location_infra" : {
                  "c" : "d"
               },
               "time" : "2020-01-01T00:00:00Z",
               "actor" : {
                  "ip" : "127.0.0.1"
               },
               "trigger" : {
                  "e" : "f"
               },
               "location" : {
                  "a" : "b"
               },
               "context" : {
                  "i" : "j"
               }
            }
        '''
        wireMockRule.stubFor(post(urlEqualTo('/signals'))
                .withHeader('X-Api-Key', equalTo(apiKey))
                .withHeader('Content-type', equalTo('application/json'))
                .withRequestBody(equalToJson(jsonExpected, true, true))
                .willReturn(aResponse().withStatus(202).withBody("null")))

        def signal = new PointSignal(
                name: 'signalName',
                payloadSchema: 'mySchema',
                time: sdf.parse('2020-01-01 00:00:00'),
                actor: [ip: '127.0.0.1'],
                location: [a: 'b'],
                locationInfra: [c: 'd'],
                source: 'srcName',
                trigger: [e: 'f'],
                payload: [g: 'h'],
                context: [i: 'j']
        )

//        proxyRequests('https://ingestion.sqreen.com/')

        service.reportSignal(signal)

        wireMockRule.verify(postRequestedFor(urlEqualTo('/signals')))
    }

    @Test
    void 'metric signal'() {
        def jsonExpected = '''
            {
               "type" : "metric",
               "signal_name" : "signalName",
               "payload" : {
                  "g" : "h"
               }
            }
        '''

        wireMockRule.stubFor(post(urlEqualTo('/signals'))
                .withRequestBody(equalToJson(jsonExpected, true, true))
                .willReturn(aResponse().withStatus(202).withBody("null")))

        def signal = new MetricSignal(
                name: 'signalName',
                payload: [g: 'h'],
        )

        service.reportSignal(signal)

        wireMockRule.verify(postRequestedFor(urlEqualTo('/signals')))
    }

    @Test
    void batch() {
        def jsonExpected = '''
            [{
               "type" : "metric",
               "signal_name" : "signalName",
               "payload" : {
                  "g" : "h"
               }
            }]
        '''

        wireMockRule.stubFor(post(urlEqualTo('/batches'))
                .withRequestBody(equalToJson(jsonExpected, true, true))
                .willReturn(aResponse().withStatus(202).withBody("null")))

        def signal = new MetricSignal(
                name: 'signalName',
                payload: [g: 'h'],
        )

        service.reportBatch([signal])

        wireMockRule.verify(postRequestedFor(urlEqualTo('/batches')))
    }

    @Test
    void trace() {
        def jsonExpected = '''
            {
               "payload" : {},
               "data" : [
                  {
                     "payload" : {
                        "g" : "h"
                     },
                     "type" : "metric",
                     "signal_name" : "signalName"
                  }
               ],
               "signal_name" : "traceName"
            }
        '''

        wireMockRule.stubFor(post(urlEqualTo('/traces'))
                .withRequestBody(equalToJson(jsonExpected, true, true))
                .willReturn(aResponse().withStatus(202).withBody("null")))

        def signal = new MetricSignal(
                name: 'signalName',
                payload: [g: 'h'],
        )
        def trace = new Trace(name: 'traceName', payload: [:])
        trace.nestedSignals = [signal]

        service.reportTrace(trace)

        wireMockRule.verify(postRequestedFor(urlEqualTo('/traces')))
    }

    @Test
    void 'server responds with 401'() {
        wireMockRule.stubFor(post(urlEqualTo('/signals'))
                .willReturn(aResponse().withStatus(401)))

        def signal = new PointSignal(name: 'the name', payload: [:])

        shouldFail(AuthenticationException) {
            service.reportSignal(signal)
        }
    }

    @Test
    void 'server responds with 422'() {
        wireMockRule.stubFor(post(urlEqualTo('/signals'))
                .willReturn(aResponse().withStatus(422)))

        def signal = new PointSignal(name: 'the name', payload: [:])

        shouldFail(InvalidPayloadException) {
            service.reportSignal(signal)
        }
    }

    @Test
    void 'server responds with another error code'() {
        wireMockRule.stubFor(post(urlEqualTo('/signals'))
                .willReturn(aResponse().withStatus(500)))

        def signal = new PointSignal(name: 'the name', payload: [:])

        shouldFail(BadHttpStatusException) {
            service.reportSignal(signal)
        }
    }
}
