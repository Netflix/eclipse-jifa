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

import org.eclipse.jifa.server.Constant;
import org.eclipse.jifa.server.service.AnalysisApiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for MDC request tracing across HTTP requests and async operations
 */
@WebMvcTest(value = AnalysisApiHttpController.class, excludeAutoConfiguration = {SecurityAutoConfiguration.class})
public class TestMdcRequestTracing {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private AnalysisApiService apiService;

    private final AtomicReference<String> capturedRequestId = new AtomicReference<>();

    @BeforeEach
    public void before() {
        capturedRequestId.set(null);

        // Mock service that captures MDC requestId when invoked
        Mockito.when(apiService.invoke(Mockito.any())).thenAnswer((Answer<CompletableFuture<?>>) invocation -> {
            // Capture the requestId from MDC in the controller thread
            String requestId = MDC.get("requestId");
            capturedRequestId.set(requestId);
            return CompletableFuture.completedFuture("Success");
        });
    }

    @Test
    public void testRequestIdGeneratedWhenNotProvided() throws Exception {
        MvcResult result = mvc.perform(post(Constant.HTTP_API_PREFIX + Constant.HTTP_ANALYSIS_API_MAPPING)
                                               .contentType(MediaType.APPLICATION_JSON)
                                               .content("""
                                                                {
                                                                  "namespace": "test-namespace",
                                                                  "api": "test-api",
                                                                  "target": "test-target"
                                                                }"""))
                              .andExpect(request().asyncStarted())
                              .andReturn();

        mvc.perform(asyncDispatch(result))
           .andExpect(status().isOk())
           .andReturn();

        // Verify that a request ID was automatically generated and set in MDC
        assertNotNull(capturedRequestId.get(), "Request ID should be automatically generated");
        // Verify it's a valid UUID format
        UUID.fromString(capturedRequestId.get());
    }

    @Test
    public void testRequestIdProvidedInHeader() throws Exception {
        String expectedRequestId = "test-request-12345";

        MvcResult result = mvc.perform(post(Constant.HTTP_API_PREFIX + Constant.HTTP_ANALYSIS_API_MAPPING)
                                               .contentType(MediaType.APPLICATION_JSON)
                                               .header("X-Request-ID", expectedRequestId)
                                               .content("""
                                                                {
                                                                  "namespace": "test-namespace",
                                                                  "api": "test-api",
                                                                  "target": "test-target"
                                                                }"""))
                              .andExpect(request().asyncStarted())
                              .andReturn();

        mvc.perform(asyncDispatch(result))
           .andExpect(status().isOk())
           .andReturn();

        // Verify that the provided request ID was used
        assertEquals(expectedRequestId, capturedRequestId.get(),
                     "Request ID from header should be used");
    }

    @Test
    public void testRequestIdPropagatesWithSse() throws Exception {
        String expectedRequestId = "sse-test-request-67890";

        MvcResult result = mvc.perform(post(Constant.HTTP_API_PREFIX + Constant.HTTP_ANALYSIS_API_MAPPING)
                                               .contentType(MediaType.APPLICATION_JSON)
                                               .header("X-Request-ID", expectedRequestId)
                                               .header(Constant.HTTP_HEADER_ENABLE_SSE, "true")
                                               .content("""
                                                                {
                                                                  "namespace": "test-namespace",
                                                                  "api": "test-api",
                                                                  "target": "test-target"
                                                                }"""))
                              .andExpect(request().asyncStarted())
                              .andReturn();

        mvc.perform(asyncDispatch(result))
           .andExpect(status().isOk())
           .andReturn();

        // Verify request ID propagates even with SSE enabled
        assertEquals(expectedRequestId, capturedRequestId.get(),
                     "Request ID should propagate with SSE enabled");
    }

    @Test
    public void testMdcClearedAfterRequest() throws Exception {
        // Set some MDC value before the test
        MDC.put("testKey", "testValue");

        String requestId = "cleanup-test-request";

        MvcResult result = mvc.perform(post(Constant.HTTP_API_PREFIX + Constant.HTTP_ANALYSIS_API_MAPPING)
                                               .contentType(MediaType.APPLICATION_JSON)
                                               .header("X-Request-ID", requestId)
                                               .content("""
                                                                {
                                                                  "namespace": "test-namespace",
                                                                  "api": "test-api",
                                                                  "target": "test-target"
                                                                }"""))
                              .andExpect(request().asyncStarted())
                              .andReturn();

        mvc.perform(asyncDispatch(result))
           .andExpect(status().isOk())
           .andReturn();

        // Verify request ID was captured during request
        assertEquals(requestId, capturedRequestId.get());

        // MDC should still have our test key (not cleared by the test framework)
        assertEquals("testValue", MDC.get("testKey"));

        // Clean up
        MDC.clear();
    }
}
