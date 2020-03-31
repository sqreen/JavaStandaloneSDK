package io.sqreen.agent.test

import com.github.tomakehurst.wiremock.common.ConsoleNotifier
import com.github.tomakehurst.wiremock.common.SingleRootFileSource
import com.github.tomakehurst.wiremock.junit.WireMockRule
import org.junit.Rule

import static com.github.tomakehurst.wiremock.client.WireMock.proxyAllTo
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options

trait WireMockTrait {

    @Rule
    public WireMockRule wireMockRule = { ->
        URL resource = getClass().classLoader.getResource('mappings/anchor')
        File f
        if (resource) {
            f = new File(resource.toURI())
        }

        def config = options().
                dynamicPort().
                recordRequestHeadersForMatching([
                        'Content-type', 'X-Api-Key', 'X-App-Name', 'X-Session-Key', 'Content-Encoding']).
                notifier(new ConsoleNotifier(true))
        if (f) {
            config = config.usingFilesUnderDirectory(f.parentFile.parentFile.absolutePath)
        }

        new WireMockRule(config)
    }()

    WireMockRule getWireMockRule() {
        wireMockRule
    }

    /* the rest is needed only when developing the tests */

    def proxyRequests(String proxyTo = 'https://back.sqreen.io') {
        stubFor(proxyAllTo(proxyTo).atPriority(1))
        wireMockRule.enableRecordMappings(
                new SingleRootFileSource('src/test/resources/mappings'),
                new SingleRootFileSource('src/test/resources/__files'))
    }
}
