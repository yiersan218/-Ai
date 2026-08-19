/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nageoffer.ai.ragent.user.config;

import com.nageoffer.ai.ragent.framework.exception.ClientException;
import com.nageoffer.ai.ragent.user.service.RegistrationRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RegistrationRequestRateLimitInterceptorTest {

    private final RegistrationRateLimiter rateLimiter = mock(RegistrationRateLimiter.class);
    private final ClientIpResolver clientIpResolver = mock(ClientIpResolver.class);
    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final HttpServletResponse response = mock(HttpServletResponse.class);
    private final Object handler = new Object();

    @Test
    void shouldRejectPostWhenRegistrationIsDisabled() {
        RegistrationProperties properties = new RegistrationProperties();
        properties.setEnabled(false);
        RegistrationRequestRateLimitInterceptor interceptor =
                new RegistrationRequestRateLimitInterceptor(rateLimiter, clientIpResolver, properties);
        when(request.getMethod()).thenReturn("POST");

        ClientException exception = assertThrows(
                ClientException.class,
                () -> interceptor.preHandle(request, response, handler)
        );

        assertEquals("注册功能暂未开放", exception.getMessage());
        verifyNoInteractions(rateLimiter, clientIpResolver);
    }

    @Test
    void shouldRateLimitPostWhenRegistrationIsEnabled() {
        RegistrationProperties properties = new RegistrationProperties();
        properties.setEnabled(true);
        RegistrationRequestRateLimitInterceptor interceptor =
                new RegistrationRequestRateLimitInterceptor(rateLimiter, clientIpResolver, properties);
        when(request.getMethod()).thenReturn("POST");
        when(clientIpResolver.resolve(request)).thenReturn("192.0.2.10");

        assertTrue(interceptor.preHandle(request, response, handler));

        verify(rateLimiter).recordRequest("192.0.2.10");
    }
}
