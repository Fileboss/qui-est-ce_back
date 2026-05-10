package util;

import io.quarkus.vertx.web.RouteFilter;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Browsers can't set Authorization headers on WebSocket handshakes, so the
 * frontend appends ?access_token=&lt;jwt&gt;. Quarkus has no built-in property
 * to read it from there, so we promote the query param to a Bearer header
 * before the OIDC security handler runs.
 */
@ApplicationScoped
public class WebSocketTokenFilter {

    @RouteFilter(500)
    void promoteAccessTokenQueryParamToHeader(RoutingContext rc) {
        if (rc.request().path().startsWith("/ws/")
                && rc.request().getHeader("Authorization") == null) {
            String token = rc.request().getParam("access_token");
            if (token != null && !token.isEmpty()) {
                rc.request().headers().add("Authorization", "Bearer " + token);
                // Scrub the token from the parsed query map so downstream handlers don't read it.
                // Note: rc.request().uri() retains the raw query string; access logs on /ws/* must
                // be disabled or stripped — see application.properties.
                rc.queryParams().remove("access_token");
            }
        }
        rc.next();
    }
}
