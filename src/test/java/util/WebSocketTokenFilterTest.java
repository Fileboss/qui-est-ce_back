package util;

import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

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
    private MultiMap queryParams;

    @BeforeEach
    void setUp() {
        filter = new WebSocketTokenFilter();
        rc = mock(RoutingContext.class);
        request = mock(HttpServerRequest.class);
        headers = mock(MultiMap.class);
        queryParams = mock(MultiMap.class);
        when(rc.request()).thenReturn(request);
        when(rc.queryParams()).thenReturn(queryParams);
        when(request.headers()).thenReturn(headers);
    }

    @Test
    void addsBearerHeader_whenAccessTokenInQuery_andPathStartsWithWs() {
        when(request.path()).thenReturn("/ws/game/abc");
        when(request.getHeader("Authorization")).thenReturn(null);
        when(request.getParam("access_token")).thenReturn("foo");

        filter.promoteAccessTokenQueryParamToHeader(rc);

        verify(headers).add("Authorization", "Bearer foo");
        verify(queryParams).remove("access_token");
        verify(rc).next();
    }

    @ParameterizedTest(name = "Auth Header: ''{0}'', Token Param: ''{1}''")
    @MethodSource("provideNoChangeScenarios")
    void doesNotOverwrite_whenAuthorizationPresentOrTokenInvalid(String authHeader, String accessToken) {
        when(request.path()).thenReturn("/ws/game/abc");
        when(request.getHeader("Authorization")).thenReturn(authHeader);
        when(request.getParam("access_token")).thenReturn(accessToken);

        filter.promoteAccessTokenQueryParamToHeader(rc);

        verify(headers, never()).add(anyString(), anyString());
        verify(rc).next();
    }

    private static Stream<Arguments> provideNoChangeScenarios() {
        return Stream.of(
                Arguments.of("Bearer existing", "foo"), // Authorization already present
                Arguments.of(null, null),               // access_token missing
                Arguments.of(null, "")                  // access_token is empty string
        );
    }

    @Test
    void noChange_whenPathDoesNotStartWithWs() {
        when(request.path()).thenReturn("/api/foo");

        filter.promoteAccessTokenQueryParamToHeader(rc);

        verify(headers, never()).add(anyString(), anyString());
        verify(rc).next();
    }
}
