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
package org.eclipse.jifa.server.domain.exception;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jifa.common.domain.exception.ErrorCodeException;
import org.eclipse.jifa.server.enums.ServerErrorCode;

/**
 * Thrown when step-up enforcement rejects a request. Carries the Stairmaster
 * authentication URL as a first class field so that clients do not have to
 * recover it by parsing the human readable message.
 */
public class StepUpRequiredException extends ErrorCodeException {

    private static final Pattern URL_IN_MESSAGE = Pattern.compile("https?://\\S+");

    private final String redirectUrl;

    public StepUpRequiredException(String message, String redirectUrl) {
        super(ServerErrorCode.ACCESS_DENIED_STEPUP, message);
        this.redirectUrl = redirectUrl;
    }

    public String getRedirectUrl() {
        return redirectUrl;
    }

    /**
     * Recover the step-up URL from an enforcement failure.
     * <p>
     * TODO the stairmaster client only surfaces the URL inside the exception
     * message today. If a future release exposes it as a property, read it
     * from there instead of scraping the message.
     */
    public static StepUpRequiredException fromMessage(String message) {
        String url = null;
        if (message != null) {
            Matcher matcher = URL_IN_MESSAGE.matcher(message);
            if (matcher.find()) {
                url = stripTrailingPunctuation(matcher.group());
            }
        }
        return new StepUpRequiredException(message, url);
    }

    private static String stripTrailingPunctuation(String url) {
        int end = url.length();
        while (end > 0 && ".,;:)]}'\"".indexOf(url.charAt(end - 1)) >= 0) {
            end--;
        }
        return url.substring(0, end);
    }
}
