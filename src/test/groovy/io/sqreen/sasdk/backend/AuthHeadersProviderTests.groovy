package io.sqreen.sasdk.backend

import io.sqreen.sasdk.signals_dto.PointSignal
import org.hamcrest.Matchers
import org.junit.Test

import static org.hamcrest.MatcherAssert.assertThat
import static org.hamcrest.Matchers.contains
import static org.hamcrest.Matchers.greaterThan
import static org.hamcrest.Matchers.is
import static org.hamcrest.collection.IsMapContaining.hasEntry

class AuthHeadersProviderTests {

    @Test
    void 'create AuthHeaderProvider for Api authentication method'() {

        def authHeaderProvider = new AuthHeadersProvider.Api('test-key');

        assertThat authHeaderProvider.headers.size(), is(1)
        assertThat authHeaderProvider.headers.get('X-API-Key'), contains('test-key')
    }

    @Test
    void 'create AuthHeaderProvider for App authentication method'() {

        def authHeaderProvider = new AuthHeadersProvider.App('test-key', 'test-app');

        assertThat authHeaderProvider.headers.size(), is(2)
        assertThat authHeaderProvider.headers.get('X-API-Key'), contains('test-key')
        assertThat authHeaderProvider.headers.get('X-App-Name'), contains('test-app')
    }

    @Test
    void 'create AuthHeaderProvider for Session authentication method'() {

        def authHeaderProvider = new AuthHeadersProvider.Session('session-key');

        assertThat authHeaderProvider.headers.size(), is(1)
        assertThat authHeaderProvider.headers.get('X-Session-Key'), contains('session-key')
    }
}
