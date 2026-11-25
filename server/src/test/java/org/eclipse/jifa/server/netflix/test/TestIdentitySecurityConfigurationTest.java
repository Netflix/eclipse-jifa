package org.eclipse.jifa.server.netflix.test;

import org.eclipse.jifa.server.Constant;
import org.eclipse.jifa.server.service.AnalysisApiService;
import org.eclipse.jifa.server.service.impl.netflix.NetflixGandalfUserAccessService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.scheduling.concurrent.ConcurrentTaskScheduler;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.DEFINED_PORT;

/**
 * Proves that the test identity chain rejects requests without an identity.
 * Without this check, the STOMP tests could pass against a permissive harness.
 */
@SpringBootTest(webEnvironment = DEFINED_PORT)
public class TestIdentitySecurityConfigurationTest {

    // Same mocks as the other full-context tests, so Spring reuses the cached context.
    @MockBean
    private AnalysisApiService apiService;

    @MockBean
    private NetflixGandalfUserAccessService gandalfUserAccessService;

    @Test
    public void handshakeWithoutIdentityIsRejected() {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setTaskScheduler(new ConcurrentTaskScheduler());

        ExecutionException e = assertThrows(ExecutionException.class, () -> client
                .connectAsync(String.format("ws://localhost:%d/%s", Constant.DEFAULT_PORT, Constant.STOMP_ENDPOINT),
                              new WebSocketHttpHeaders(),
                              new StompSessionHandlerAdapter() {})
                .get(10, TimeUnit.SECONDS));

        assertTrue(String.valueOf(e.getCause().getMessage()).contains("401"),
                   "expected HTTP 401 from the handshake, got: " + e.getCause());
    }
}
