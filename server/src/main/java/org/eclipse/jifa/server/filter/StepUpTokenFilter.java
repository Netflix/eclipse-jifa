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
package org.eclipse.jifa.server.filter;

import java.io.IOException;

import org.eclipse.jifa.server.service.impl.netflix.NetflixGandalfUserAccessService;
import org.springframework.web.filter.OncePerRequestFilter;

import com.netflix.infosec.stairmaster.enforcement.client.StepUpConstants;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * Publishes the step-up token carried on a REST request so that
 * {@link NetflixGandalfUserAccessService} can hand it to the enforcer.
 * <p>
 * The authority check runs on the REST path (GET /jifa-api/files/{target} ->
 * ReadOnlyFileServiceImpl#getFileByUniqueName), but before this filter existed the
 * only writers of the thread local were on the STOMP path. The header the frontend
 * attaches was therefore dropped and the enforcer was always called with a null token.
 * <p>
 * The token is always cleared on the way out: these are pooled container threads, and
 * a token left behind would be visible to the next request served by the same thread.
 */
@Slf4j
public class StepUpTokenFilter extends OncePerRequestFilter {

    @SuppressWarnings("NullableProblems")
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = request.getHeader(StepUpConstants.STEP_UP_AUTHENTICATION_HEADER_NAME);
        try {
            if (token != null) {
                NetflixGandalfUserAccessService.logStepUpToken("http-request", token);
                NetflixGandalfUserAccessService.STEP_UP_TOKEN.set(token);
            }
            chain.doFilter(request, response);
        } finally {
            NetflixGandalfUserAccessService.STEP_UP_TOKEN.remove();
        }
    }
}
