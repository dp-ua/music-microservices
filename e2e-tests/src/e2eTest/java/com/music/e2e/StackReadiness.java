package com.music.e2e;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.awaitility.core.ConditionTimeoutException;

import io.restassured.RestAssured;

import static org.awaitility.Awaitility.await;

/**
 * Waits until the running stack can serve requests, once per test run. A started container is not a ready service:
 * song-service has no healthcheck in compose.yaml, and the gateway needs a few seconds to see the services in Eureka.
 * <p>
 * "Ready" means that the gateway answers 404 (not 503 and not a connection error) for an unknown id of both
 * services: the route, Eureka and the service itself work.
 */
final class StackReadiness {

    private static final Duration TIMEOUT = Duration.ofMinutes(3);
    private static boolean checked;
    private static IllegalStateException failure;

    private StackReadiness() {
    }

    static synchronized void awaitStack() {
        if (checked) {
            return;
        }
        if (failure != null) {
            // the first scenario already waited the whole time: the others fail at once instead of waiting again
            throw failure;
        }
        var baseUrl = E2eConfig.baseUrl();
        try {
            await().atMost(TIMEOUT.toSeconds(), TimeUnit.SECONDS)
                    .pollInterval(2, TimeUnit.SECONDS)
                    .ignoreExceptions()
                    .until(() -> answers404(baseUrl + "/resources/999999999")
                            && answers404(baseUrl + "/songs/999999999"));
        } catch (ConditionTimeoutException e) {
            failure = new IllegalStateException("The stack is not ready at " + baseUrl + " after "
                    + TIMEOUT.toMinutes() + " minutes. Start it first (01_start_services_wsl.cmd) and check "
                    + "'docker compose ps'.", e);
            throw failure;
        }
        checked = true;
    }

    private static boolean answers404(String url) {
        return RestAssured.get(url).statusCode() == 404;
    }

}
