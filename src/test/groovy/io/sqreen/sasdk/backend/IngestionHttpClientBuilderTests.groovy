package io.sqreen.sasdk.backend

import com.fasterxml.jackson.databind.ObjectWriter
import org.apache.http.conn.ssl.SSLConnectionSocketFactory
import org.apache.http.impl.client.CloseableHttpClient
import org.gmock.WithGMock
import org.junit.Test

import javax.net.ssl.SSLContext

import static groovy.test.GroovyAssert.shouldFail
import static org.hamcrest.MatcherAssert.assertThat
import static org.hamcrest.Matchers.is

@WithGMock
class IngestionHttpClientBuilderTests {

    @Test
    void 'creation of unauthenticated service'() {
        IngestionHttpClient.WithoutAuthentication service = new IngestionHttpClientBuilder()
                .withAlternativeIngestionURL("https://example.com/")
                .buildingHttpClient()
                .withConnectionSocketFactory(new SSLConnectionSocketFactory(SSLContext.getDefault()))
                .withConnectionTimeoutInMs(5000)
                .withReadTimeoutInMs(10000)
                .withProxy("http://proxy.com/")
                .buildHttpClient()
                .withErrorListener(mock(IngestionErrorListener))
                .withCustomObjectWriter(mock(ObjectWriter))
                .createWithoutAuthentication()

        // test only a bit of internals
        // testing that the remaining configuration is applied is done elsewhere
        assertThat service.host, is("https://example.com/")
    }

    @Test
    void 'creation of authenticated service with explicit http client'() {
        CloseableHttpClient httpClient = mock(CloseableHttpClient)

        IngestionHttpClient.WithAuthentication service = new IngestionHttpClientBuilder()
                .withExplicitHttpClient(httpClient)
                .createWithAuthentication(
                        IngestionHttpClientBuilder.authConfigWithAPIKey("apiKey", "appName"))

        //assertThat service.host, is('https://ingestion.sqreen.com/')
        assertThat service.config.APIKey.get(), is("apiKey")
        assertThat service.config.appName.get(), is("appName")
    }

    @Test
    void 'creation of authenticated service with session key'() {
        IngestionHttpClient.WithAuthentication service = new IngestionHttpClientBuilder()
                .buildingHttpClient()
                .buildHttpClient()
                .createWithAuthentication(
                        IngestionHttpClientBuilder.authConfigWithSessionKey("session key"))

        assertThat service.config.sessionKey.get(), is('session key')
    }

    @Test
    void 'proxy with username and password'() {
        new IngestionHttpClientBuilder()
                .buildingHttpClient()
                .withProxy('https://foo:bar@127.0.0.1:8080/')
                .buildHttpClient()
                .createWithoutAuthentication()
    }

    @Test
    void 'allows empty proxy'() {
        new IngestionHttpClientBuilder()
                .buildingHttpClient()
                .withProxy(null)
    }

    @Test
    void 'use of invalid url proxy'() {
        shouldFail(IllegalArgumentException) {
            new IngestionHttpClientBuilder()
                    .buildingHttpClient()
                    .withProxy('foobar')
        }
    }

    @Test
    void 'empty username'() {
        def e = shouldFail(IllegalArgumentException) {
            new IngestionHttpClientBuilder()
                    .buildingHttpClient()
                    .withProxy('http://:bar@127.0.0.1:8080/')
        }

        assertThat e.message, is('empty or missing user name')
    }

    @Test
    void 'empty password'() {
        def e = shouldFail(IllegalArgumentException) {
            new IngestionHttpClientBuilder()
                    .buildingHttpClient()
                    .withProxy('http://user:@127.0.0.1:8080/')
        }

        assertThat e.message, is('empty or missing user password')
    }
}
