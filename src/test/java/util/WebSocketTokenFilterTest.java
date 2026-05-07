package util;

import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebSocketTokenFilterTest {

    private WebSocketTokenFilter filter;
    private RoutingContext rc;
    private HttpServerRequest request;
    private MultiMap headers;

    @BeforeEach
    void setUp() {
        filter = new WebSocketTokenFilter();
        rc = mock(RoutingContext.class);
        request = mock(HttpServerRequest.class);
        headers = mock(MultiMap.class);
        when(rc.request()).thenReturn(request);
        when(request.headers()).thenReturn(headers);
    }

    @Test
    void addsBearerHeader_whenAccessTokenInQuery_andPathStartsWithWs() {
        when(request.path()).thenReturn("/ws/game/abc");
        when(request.getHeader("Authorization")).thenReturn(null);
        when(request.getParam("access_token")).thenReturn("foo");

        filter.promoteAccessTokenQueryParamToHeader(rc);

        verify(headers).add("Authorization", "Bearer foo");
        verify(rc).next();
    }

    @Test
    void doesNotOverwrite_whenAuthorizationAlreadyPresent() {
        when(request.path()).thenReturn("/ws/game/abc");
        when(request.getHeader("Authorization")).thenReturn("Bearer existing");
        when(request.getParam("access_token")).thenReturn("foo");

        filter.promoteAccessTokenQueryParamToHeader(rc);

        verify(headers, never()).add(anyString(), anyString());
        verify(rc).next();
    }

    @Test
    void noChange_whenAccessTokenMissing() {
        when(request.path()).thenReturn("/ws/game/abc");
        when(request.getHeader("Authorization")).thenReturn(null);
        when(request.getParam("access_token")).thenReturn(null);

        filter.promoteAccessTokenQueryParamToHeader(rc);

        verify(headers, never()).add(anyString(), anyString());
        verify(rc).next();
    }

    @Test
    void noChange_whenAccessTokenIsEmptyString() {
        when(request.path()).thenReturn("/ws/game/abc");
        when(request.getHeader("Authorization")).thenReturn(null);
        when(request.getParam("access_token")).thenReturn("");

        filter.promoteAccessTokenQueryParamToHeader(rc);

        verify(headers, never()).add(anyString(), anyString());
        verify(rc).next();
    }

    @Test
    void noChange_whenPathDoesNotStartWithWs() {
        when(request.path()).thenReturn("/api/foo");

        filter.promoteAccessTokenQueryParamToHeader(rc);

        verify(headers, never()).add(anyString(), anyString());
        verify(rc).next();
    }
}
