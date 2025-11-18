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
package org.eclipse.jifa.common.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestExecutorFactory {

    @BeforeEach
    public void setup() {
        MDC.clear();
    }

    @AfterEach
    public void cleanup() {
        MDC.clear();
    }

    @Test
    public void test() throws InterruptedException {
        Executor executor = ExecutorFactory.newExecutor("test1");
        assertNotNull(executor);
        CountDownLatch countDownLatch1 = new CountDownLatch(1);
        executor.execute(() -> {
            if (Thread.currentThread().getName().startsWith("test1")) {
                countDownLatch1.countDown();
            }
        });
        assertTrue(countDownLatch1.await(1, TimeUnit.SECONDS));

        executor = ExecutorFactory.newExecutor("test2", 1, 1);
        assertNotNull(executor);
        CountDownLatch countDownLatch2 = new CountDownLatch(1);
        executor.execute(() -> {
            if (Thread.currentThread().getName().startsWith("test2")) {
                countDownLatch2.countDown();
            }
        });
        assertTrue(countDownLatch2.await(1, TimeUnit.SECONDS));

        ScheduledExecutorService scheduledExecutorService = ExecutorFactory.newScheduledExecutorService("test3", 1);
        assertNotNull(scheduledExecutorService);
        CountDownLatch countDownLatch3 = new CountDownLatch(1);
        scheduledExecutorService.schedule(() -> {
            if (Thread.currentThread().getName().startsWith("test3")) {
                countDownLatch3.countDown();
            }
        }, 100, TimeUnit.MILLISECONDS);
        assertTrue(countDownLatch3.await(1, TimeUnit.SECONDS));

        ExecutorFactory.printStatistic();
    }

    @Test
    public void testThrows() {
        assertThrows(IllegalStateException.class, () -> {
            ExecutorFactory.initialize(8);
            ExecutorFactory.initialize(8);
        });

        assertThrows(IllegalArgumentException.class, () -> ExecutorFactory.newExecutor(null, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> ExecutorFactory.newExecutor("prefix", 0, 1));
        assertThrows(IllegalArgumentException.class, () -> ExecutorFactory.newScheduledExecutorService(null, 1));
        assertThrows(IllegalArgumentException.class, () -> ExecutorFactory.newScheduledExecutorService("prefix", 0));
    }

    // MDC Propagation Tests

    @Test
    public void testMdcPropagationAcrossExecutor() throws InterruptedException {
        // Set up MDC in main thread
        String expectedRequestId = "test-request-123";
        MDC.put("requestId", expectedRequestId);

        // Create an executor wrapped with MDC awareness
        Executor plainExecutor = Runnable::run; // Simple inline executor
        Executor mdcAwareExecutor = ExecutorFactory.mdcAware(plainExecutor);

        AtomicReference<String> capturedRequestId = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        // Execute task in wrapped executor
        mdcAwareExecutor.execute(() -> {
            // This should have the MDC context from the parent thread
            capturedRequestId.set(MDC.get("requestId"));
            latch.countDown();
        });

        latch.await(5, TimeUnit.SECONDS);

        // Verify MDC was propagated
        assertEquals(expectedRequestId, capturedRequestId.get(),
                     "MDC context should propagate to executor task");
    }

    @Test
    public void testMdcPropagationWithThreadPoolExecutor() throws Exception {
        // Create a real thread pool executor
        Executor plainExecutor = ExecutorFactory.newExecutor("TestMDC", 2, 10);
        Executor mdcAwareExecutor = ExecutorFactory.mdcAware(plainExecutor);

        String expectedRequestId = "thread-pool-test-456";
        MDC.put("requestId", expectedRequestId);
        MDC.put("userId", "test-user");

        AtomicReference<String> capturedRequestId = new AtomicReference<>();
        AtomicReference<String> capturedUserId = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        // Execute in thread pool
        mdcAwareExecutor.execute(() -> {
            capturedRequestId.set(MDC.get("requestId"));
            capturedUserId.set(MDC.get("userId"));
            latch.countDown();
        });

        latch.await(5, TimeUnit.SECONDS);

        // Verify both MDC values propagated
        assertEquals(expectedRequestId, capturedRequestId.get());
        assertEquals("test-user", capturedUserId.get());
    }

    @Test
    public void testMdcClearedWhenNull() throws InterruptedException {
        // Don't set any MDC (context is empty/null)
        var contextMap = MDC.getCopyOfContextMap();
        assertTrue(contextMap == null || contextMap.isEmpty(),
                   "MDC should start empty or null");

        Executor plainExecutor = Runnable::run;
        Executor mdcAwareExecutor = ExecutorFactory.mdcAware(plainExecutor);

        AtomicReference<String> capturedRequestId = new AtomicReference<>("not-null");
        CountDownLatch latch = new CountDownLatch(1);

        mdcAwareExecutor.execute(() -> {
            // MDC should be cleared (not inherit anything)
            capturedRequestId.set(MDC.get("requestId"));
            latch.countDown();
        });

        latch.await(5, TimeUnit.SECONDS);

        // Verify MDC was cleared
        assertNull(capturedRequestId.get(),
                   "MDC should be cleared when parent context is null");
    }

    @Test
    public void testMdcRestoresAfterExecution() throws InterruptedException {
        // Set up initial MDC in executor thread
        Executor plainExecutor = Runnable::run;
        Executor mdcAwareExecutor = ExecutorFactory.mdcAware(plainExecutor);

        String parentRequestId = "parent-request-789";
        MDC.put("requestId", parentRequestId);

        CountDownLatch latch = new CountDownLatch(1);

        mdcAwareExecutor.execute(() -> {
            // Task sees parent MDC
            assertEquals(parentRequestId, MDC.get("requestId"));

            // Task modifies MDC
            MDC.put("requestId", "modified-in-task");
            MDC.put("taskSpecific", "task-value");

            latch.countDown();
        });

        latch.await(5, TimeUnit.SECONDS);

        // Verify original thread's MDC is unchanged
        assertEquals(parentRequestId, MDC.get("requestId"),
                     "Parent thread MDC should be unchanged");
        assertNull(MDC.get("taskSpecific"),
                   "Task-specific MDC should not leak to parent");
    }

    @Test
    public void testMdcPropagationThroughCompletableFuture() throws Exception {
        String expectedRequestId = "async-future-test-999";
        MDC.put("requestId", expectedRequestId);

        // Create MDC-aware executor
        Executor mdcAwareExecutor = ExecutorFactory.mdcAware(ExecutorFactory.newExecutor("TestAsync"));

        AtomicReference<String> capturedInFirstStage = new AtomicReference<>();
        AtomicReference<String> capturedInSecondStage = new AtomicReference<>();

        // Create async chain with MDC-aware executor
        CompletableFuture<String> future = CompletableFuture
                .supplyAsync(() -> {
                    capturedInFirstStage.set(MDC.get("requestId"));
                    return "first";
                }, mdcAwareExecutor)
                .thenApplyAsync(result -> {
                    capturedInSecondStage.set(MDC.get("requestId"));
                    return result + "-second";
                }, mdcAwareExecutor);

        future.get(5, TimeUnit.SECONDS);

        // Verify MDC propagated through both stages
        assertEquals(expectedRequestId, capturedInFirstStage.get(),
                     "MDC should propagate to first async stage");
        assertEquals(expectedRequestId, capturedInSecondStage.get(),
                     "MDC should propagate to second async stage");
    }

    @Test
    public void testMultipleContextValues() throws InterruptedException {
        // Set multiple MDC values
        MDC.put("requestId", "req-123");
        MDC.put("userId", "user-456");
        MDC.put("sessionId", "session-789");
        MDC.put("correlationId", "corr-abc");

        Executor mdcAwareExecutor = ExecutorFactory.mdcAware(ExecutorFactory.newExecutor("TestMulti"));

        AtomicReference<String> reqId = new AtomicReference<>();
        AtomicReference<String> userId = new AtomicReference<>();
        AtomicReference<String> sessionId = new AtomicReference<>();
        AtomicReference<String> corrId = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        mdcAwareExecutor.execute(() -> {
            reqId.set(MDC.get("requestId"));
            userId.set(MDC.get("userId"));
            sessionId.set(MDC.get("sessionId"));
            corrId.set(MDC.get("correlationId"));
            latch.countDown();
        });

        latch.await(5, TimeUnit.SECONDS);

        // Verify all MDC values propagated
        assertEquals("req-123", reqId.get());
        assertEquals("user-456", userId.get());
        assertEquals("session-789", sessionId.get());
        assertEquals("corr-abc", corrId.get());
    }
}
