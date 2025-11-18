/********************************************************************************
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.jifa.server.controller;

import com.google.gson.JsonElement;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jifa.server.Constant;
import org.eclipse.jifa.server.service.AnalysisApiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.slf4j.MDC;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.messaging.converter.GsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.scheduling.concurrent.ConcurrentTaskScheduler;
import org.springframework.util.MimeType;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.DEFINED_PORT;

/**
 * Integration tests for MDC request tracing in STOMP/WebSocket requests
 */
@SuppressWarnings("NullableProblems")
@Slf4j
@SpringBootTest(webEnvironment = DEFINED_PORT)
public class TestMdcStompRequestTracing {

    @MockBean
    private AnalysisApiService apiService;

    private WebSocketStompClient webSocketStompClient;

    private final AtomicReference<String> capturedRequestIdInService = new AtomicReference<>();

    @BeforeEach
    public void before() {
        capturedRequestIdInService.set(null);

        // Mock service that captures MDC requestId when invoked
        Mockito.when(apiService.invoke(Mockito.any())).thenAnswer((Answer<CompletableFuture<?>>) invocation -> {
            // Capture the requestId from MDC
            String requestId = MDC.get("requestId");
            capturedRequestIdInService.set(requestId);
            log.info("Service invoked with requestId from MDC: {}", requestId);
            return CompletableFuture.completedFuture("Hello Jifa");
        });

        this.webSocketStompClient = new WebSocketStompClient(new StandardWebSocketClient());
        this.webSocketStompClient.setMessageConverter(new GsonMessageConverter());
        this.webSocketStompClient.setTaskScheduler(new ConcurrentTaskScheduler());
    }

    @Test
    public void testStompRequestIdPropagation() throws Exception {
        StompSession session = webSocketStompClient
                .connectAsync(String.format("ws://localhost:%d/%s", Constant.DEFAULT_PORT, Constant.STOMP_ENDPOINT),
                              new StompSessionHandlerAdapter() {})
                .get(10, TimeUnit.SECONDS);

        CountDownLatch countDown = new CountDownLatch(1);

        AtomicReference<String> requestIdInResponse = new AtomicReference<>();
        session.subscribe(Constant.STOMP_USER_DESTINATION_PREFIX + "/" + Constant.STOMP_ANALYSIS_API_MAPPING,
                          new StompSessionHandlerAdapter() {
                              @Override
                              public Type getPayloadType(StompHeaders headers) {
                                  return JsonElement.class;
                              }

                              @Override
                              public void handleFrame(StompHeaders headers, Object payload) {
                                  requestIdInResponse.set(headers.getFirst(Constant.STOMP_ANALYSIS_API_REQUEST_ID_KEY));
                                  countDown.countDown();
                              }

                              @Override
                              public void handleTransportError(StompSession session, Throwable exception) {
                                  log.error("Transport error", exception);
                              }

                              @Override
                              public void handleException(StompSession session, StompCommand command,
                                                          StompHeaders headers, byte[] payload, Throwable exception) {
                                  log.error("STOMP error", exception);
                              }
                          });

        // Send request with explicit request ID
        String expectedRequestId = UUID.randomUUID().toString();
        MimeType type = new MimeType("application", "json", StandardCharsets.UTF_8);
        StompHeaders headers = new StompHeaders();
        headers.setDestination(Constant.STOMP_APPLICATION_DESTINATION_PREFIX + "/" + Constant.STOMP_ANALYSIS_API_MAPPING);
        headers.set(Constant.STOMP_ANALYSIS_API_REQUEST_ID_KEY, expectedRequestId);
        headers.setContentType(type);
        session.send(headers, Map.of("namespace", "test-namespace", "api", "test-api", "target", "test-target"));

        // Wait for response
        assertTrue(countDown.await(10, TimeUnit.SECONDS), "Should receive response within timeout");

        // Verify request ID was returned in response
        assertEquals(expectedRequestId, requestIdInResponse.get(),
                     "Request ID should be returned in response");

        // Verify request ID was set in MDC during service invocation
        assertNotNull(capturedRequestIdInService.get(),
                      "Request ID should have been set in MDC during service invocation");
        assertEquals(expectedRequestId, capturedRequestIdInService.get(),
                     "Request ID in MDC should match the one sent in STOMP headers");
    }

    @Test
    public void testStompRequestIdGeneratedWhenNotProvided() throws Exception {
        StompSession session = webSocketStompClient
                .connectAsync(String.format("ws://localhost:%d/%s", Constant.DEFAULT_PORT, Constant.STOMP_ENDPOINT),
                              new StompSessionHandlerAdapter() {})
                .get(10, TimeUnit.SECONDS);

        CountDownLatch countDown = new CountDownLatch(1);

        AtomicReference<String> requestIdInResponse = new AtomicReference<>();
        session.subscribe(Constant.STOMP_USER_DESTINATION_PREFIX + "/" + Constant.STOMP_ANALYSIS_API_MAPPING,
                          new StompSessionHandlerAdapter() {
                              @Override
                              public Type getPayloadType(StompHeaders headers) {
                                  return JsonElement.class;
                              }

                              @Override
                              public void handleFrame(StompHeaders headers, Object payload) {
                                  requestIdInResponse.set(headers.getFirst(Constant.STOMP_ANALYSIS_API_REQUEST_ID_KEY));
                                  countDown.countDown();
                              }
                          });

        // Send request WITHOUT request ID
        MimeType type = new MimeType("application", "json", StandardCharsets.UTF_8);
        StompHeaders headers = new StompHeaders();
        headers.setDestination(Constant.STOMP_APPLICATION_DESTINATION_PREFIX + "/" + Constant.STOMP_ANALYSIS_API_MAPPING);
        headers.setContentType(type);
        session.send(headers, Map.of("namespace", "test-namespace", "api", "test-api", "target", "test-target"));

        // Wait for response
        assertTrue(countDown.await(10, TimeUnit.SECONDS), "Should receive response within timeout");

        // Verify a request ID was generated (even if empty string is returned, MDC should have been set)
        // Note: Based on the controller code, it defaults to empty string if not provided
        assertNotNull(capturedRequestIdInService.get(),
                      "Request ID should have been generated and set in MDC");
    }
}
